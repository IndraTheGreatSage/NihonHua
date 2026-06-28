/**
 * NihonHua Backend Server v4.1
 *
 * Sumber scraping:
 *   - Anichin (anichin.moe)   → Donghua  (tetap seperti semula)
 *   - Otakudesu (otakudesu.cloud) → Anime  (gantikan Jikan yang cuma metadata tanpa episode)
 *
 * Fixes:
 *   - Duplicate judul: uniqueByTitle() setelah combine
 *   - Cuma 25 item: cache threshold naik ke 20, Otakudesu lebih banyak page
 *   - Episode/video: lazy scrape per-host sudah benar di /api/anime/:id
 */

const express = require('express');
const axios   = require('axios');
const cheerio = require('cheerio');
const cors    = require('cors');
const fs      = require('fs');

const app  = express();
const PORT = process.env.PORT || 3536;

app.use(cors());
app.use(express.json());

// ─── CACHE ────────────────────────────────────────────────────────────────────
const CACHE_FILE  = './data_cache.json';
let globalCache   = [];
let isScraping    = false;
let lastCacheTime = 0;
const CACHE_TTL   = 6 * 60 * 60 * 1000; // 6 jam

function loadCache() {
    try {
        if (fs.existsSync(CACHE_FILE)) {
            const data    = fs.readFileSync(CACHE_FILE, 'utf8');
            globalCache   = JSON.parse(data);
            lastCacheTime = Date.now();
            console.log(`[Cache] Loaded ${globalCache.length} items`);
        } else {
            console.log('[Cache] No cache file, starting fresh.');
        }
    } catch (e) {
        globalCache = [];
        console.error('[Cache] Load error:', e.message);
    }
}

function saveCache(data) {
    try {
        fs.writeFileSync(CACHE_FILE, JSON.stringify(data, null, 2));
        globalCache   = data;
        lastCacheTime = Date.now();
        console.log(`[Cache] Saved ${data.length} items`);
    } catch (e) {
        console.error('[Cache] Save error:', e.message);
    }
}

// ─── HELPERS ──────────────────────────────────────────────────────────────────
const sleep = ms => new Promise(res => setTimeout(res, ms));

function makeId(prefix, title) {
    return prefix + title.toLowerCase()
        .replace(/[^a-z0-9]/g, '_')
        .replace(/^_+|_+$/g, '')
        .substring(0, 60);
}

function cleanTitle(raw) {
    return String(raw || '').replace(/\s+/g, ' ').trim();
}

function uniqueById(items) {
    const seen = new Set();
    return items.filter(item => {
        if (!item.id || seen.has(item.id)) return false;
        seen.add(item.id);
        return true;
    });
}

/**
 * Hapus duplikat berdasarkan judul yang dinormalisasi.
 * Kalau judul sama, simpan yang punya lebih banyak episode.
 */
function uniqueByTitle(items) {
    const map = new Map();
    items.forEach(item => {
        const key      = item.title.toLowerCase().replace(/[^a-z0-9]/g, '');
        const existing = map.get(key);
        if (!existing) {
            map.set(key, item);
        } else if ((item.episodes?.length || 0) > (existing.episodes?.length || 0)) {
            map.set(key, item);
        }
    });
    return Array.from(map.values());
}

function normalizeEpNum(value, fallback) {
    const m = String(value || '').match(/\d+(?:\.\d+)?/);
    return m ? m[0] : String(fallback + 1);
}

function isNoise(text, href) {
    const t = String(text || '').trim().toLowerCase();
    const u = String(href || '').toLowerCase();
    if (!t && !u) return true;
    if (['home','homepage','beranda','donghua','anime','download','batch'].includes(t)) return true;
    if (u === '/' || u.endsWith('/home/') || u.includes('#respond')) return true;
    return false;
}

function dedupeEpisodes(episodes) {
    const map = new Map();
    episodes.forEach((ep, i) => {
        if (isNoise(ep.title, ep.video_url)) return;
        const num = normalizeEpNum(ep.episode_number, i);
        if (!num || Number(num) <= 0) return;
        const norm = { ...ep, episode_number: num,
            id: `${ep.anime_id}_ep_${num.replace(/[^a-zA-Z0-9]/g, '_')}` };
        const cur = map.get(num);
        if (!cur || norm.video_url.length > cur.video_url.length) map.set(num, norm);
    });
    return Array.from(map.values()).sort((a, b) => {
        const la = Number(a.episode_number), lb = Number(b.episode_number);
        return (isNaN(la) || isNaN(lb)) ? a.episode_number.localeCompare(b.episode_number) : la - lb;
    });
}

// ─── HTTP CLIENT ──────────────────────────────────────────────────────────────
const HEADERS = {
    'User-Agent':      'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept':          'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.9,id;q=0.8',
};

const ax = axios.create({ timeout: 20000, headers: HEADERS, maxRedirects: 5 });

// ─── VIDEO HELPERS ────────────────────────────────────────────────────────────
function isBlockedHost(url) {
    try {
        const h = new URL(url).hostname.toLowerCase();
        return ['doubleclick.net','googlesyndication.com','adsterra','popads',
                'propellerads','adnxs.com','taboola','outbrain'].some(b => h.includes(b));
    } catch { return true; }
}

function isDirectVideo(url) {
    return /\.(mp4|m3u8|webm|mkv)(\?|$)/i.test(url);
}

async function resolveStreamtape(id) {
    try {
        const { data: html } = await ax.get(`https://streamtape.com/e/${id}`);
        const m1 = html.match(/id=\\"([^"]+)\\"/);
        const m2 = html.match(/token=\\"([^"]+)\\"/);
        if (m1 && m2) return `https://streamtape.com/get_video?id=${m1[1]}&expires=9999999999&ip=x&token=${m2[1]}&stream=1`;
        const lm = html.match(/robotlink['"]\s*\)\.innerHTML\s*=\s*["']([^"']+)/);
        if (lm) return 'https:' + lm[1].replace('&amp;', '&');
    } catch (e) { console.error('[streamtape]', e.message); }
    return null;
}

async function resolveDood(embedUrl) {
    try {
        const { data: html } = await ax.get(embedUrl, { headers: { ...HEADERS, Referer: 'https://doodstream.com' } });
        const md = html.match(/\$\.get\(['"]\/pass_md5\/([^'"]+)['"]/);
        if (!md) return null;
        const tokenRes = await ax.get(`https://doodstream.com/pass_md5/${md[1]}`, { headers: { ...HEADERS, Referer: embedUrl } });
        const token    = html.match(/token=([a-zA-Z0-9]+)/)?.[1] || 'NihonHua';
        return `${tokenRes.data}NihonHua?token=${token}&expiry=${Date.now()}`;
    } catch (e) { console.error('[dood]', e.message); }
    return null;
}

async function resolveEmbed(embedUrl) {
    if (embedUrl.startsWith('//')) embedUrl = 'https:' + embedUrl;
    try {
        const { data: html } = await ax.get(embedUrl, { headers: { ...HEADERS, Referer: embedUrl } });
        const $ = cheerio.load(html);
        const vs = $('video source').attr('src') || $('video').attr('src');
        if (vs && isDirectVideo(vs)) return vs;
        const sc = $('script').map((_, el) => $(el).html() || '').get().join('\n');
        const st = sc.match(/streamtape\.com\/e\/([a-zA-Z0-9]+)/);
        if (st) return await resolveStreamtape(st[1]);
        const dd = sc.match(/dood(?:stream)?\.(?:com|watch|to|la|re)\/e\/([a-zA-Z0-9]+)/);
        if (dd) return await resolveDood(embedUrl);
        const jw = sc.match(/sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)/) || sc.match(/file["']?\s*:\s*["']([^"']+\.(?:mp4|m3u8)[^"']*)/);
        if (jw) return jw[1];
        const gn = sc.match(/["'](https?:\/\/[^"']+\.(?:mp4|m3u8)(?:\?[^"']*)?)/);
        if (gn) return gn[1];
    } catch (e) { console.error('[resolveEmbed]', e.message); }
    return null;
}

async function resolveVideoUrl(pageUrl) {
    if (!pageUrl || isDirectVideo(pageUrl)) return pageUrl;
    try {
        const { data } = await ax.get(pageUrl, { headers: { ...HEADERS, Referer: pageUrl } });
        const $         = cheerio.load(data);
        const cands     = [];

        $('video source[src], video[src], iframe[src], embed[src]').each((_, el) => {
            const src = $(el).attr('src');
            if (!src) return;
            try { const a = new URL(src, pageUrl).toString(); if (!isBlockedHost(a)) cands.push(a); } catch {}
        });

        const sc = $('script').map((_, el) => $(el).html() || '').get().join('\n');
        (sc.match(/https?:\\?\/\\?\/[^"'\\\s]+/g) || []).forEach(raw => {
            const n = raw.replace(/\\\//g, '/');
            if ((isDirectVideo(n) || /embed|player|stream|dood|filemoon|streamtape|mp4upload/i.test(n)) && !isBlockedHost(n)) cands.push(n);
        });

        const direct = cands.find(isDirectVideo);
        if (direct) return direct;
        const embed  = cands.find(u => /embed|player|stream|dood|filemoon|streamtape|mp4upload/i.test(u));
        if (embed) { const r = await resolveEmbed(embed); if (r) return r; return embed; }
        return cands[0] || pageUrl;
    } catch (e) { console.error('[resolveVideoUrl]', e.message); return pageUrl; }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCRAPER 1: ANICHIN → DONGHUA
// ═══════════════════════════════════════════════════════════════════════════════
async function scrapeAnichin(maxPages = 15) {
    const base    = 'https://anichin.moe';
    const results = [];

    for (let page = 1; page <= maxPages; page++) {
        const url = page === 1 ? `${base}/` : `${base}/page/${page}/`;
        console.log(`[Anichin/Donghua] Page ${page}...`);
        try {
            const { data } = await ax.get(url);
            const $         = cheerio.load(data);
            const items     = $('.listupd .bs, .listupd .bsx, .listupd .utao, .listupd article');
            if (items.length === 0) { console.log('[Anichin] No more items, stop.'); break; }

            items.each((_, el) => {
                const link    = $(el).find('a').first().attr('href') || '';
                const srcUrl  = link ? new URL(link, base).toString() : '';
                const title   = cleanTitle($(el).find('.tt, .bigor .tt, h2, h3').first().text());
                const img     = $(el).find('img').attr('src') || $(el).find('img').attr('data-src') || '';
                const rating  = $(el).find('.numscore, .rating span, .rating').first().text().trim() || '9.0';
                const status  = $(el).find('.status').text().trim() || 'Ongoing';
                const epNum   = $(el).find('.epx, .epcur').text().replace(/\D/g,'') || '1';

                if (!title || !srcUrl) return;
                const id = makeId('dh_', title);
                if (results.find(a => a.id === id)) return;

                results.push({
                    id, title,
                    type:          'Donghua',
                    image_url:     img || 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80',
                    description:   'Streaming Donghua terbaru dari Anichin.',
                    rating,
                    status,
                    genres:        ['Action', 'Cultivation', 'Fantasy'],
                    release_year:  String(new Date().getFullYear()),
                    studio:        'Anichin Studios',
                    episode_count: parseInt(epNum) || 1,
                    source_url:    srcUrl,
                    episodes:      []
                });
            });

            await sleep(3000); // sopan ke Cloudflare
        } catch (err) {
            console.error(`[Anichin] Page ${page} error:`, err.message);
            break;
        }
    }

    console.log(`[Anichin/Donghua] Total: ${results.length}`);
    return results;
}

async function scrapeAnichinDetail(anime) {
    if (!anime?.source_url) return anime;
    try {
        const { data } = await ax.get(anime.source_url);
        const $         = cheerio.load(data);

        const description = $('.entry-content p, .desc p, .synopsis p').first().text().trim() || anime.description;
        const genres      = [];
        $('.genre-info a, .genxed a, .genre a, a[rel="tag"], .spe .mgen a').each((_, el) => {
            const g = $(el).text().trim(); if (g && !genres.includes(g)) genres.push(g);
        });
        const rating = $('.num, .rating strong').first().text().replace(/[^0-9.]/g,'') || anime.rating || '8.0';
        const status = $('.info-content .spe span:contains("Status")').next().text().trim() || anime.status;

        const epLinks = [];
        $('.eplister li a, .episodelist a, .bixbox a, #episode_page li a').each((_, el) => {
            const href = $(el).attr('href'); if (!href) return;
            const abs  = new URL(href, anime.source_url).toString();
            const text = $(el).text().replace(/\s+/g,' ').trim();
            if (isNoise(text, abs)) return;
            const nm = text.match(/(?:episode|eps|ep)?\s*(\d+(?:\.\d+)?)/i) || abs.match(/(?:episode|eps|ep)-?(\d+(?:-\d+)?)/i);
            if (!nm) return;
            epLinks.push({ id: `${anime.id}_ep_${nm[1]}`, anime_id: anime.id,
                title: text || `${anime.title} - Episode ${nm[1]}`,
                episode_number: nm[1].replace('-','.'), video_url: abs, release_date: 'Baru Saja' });
        });

        const eps = dedupeEpisodes(epLinks);
        return { ...anime, description, genres: genres.length ? genres : anime.genres,
            rating, status, episode_count: eps.length || anime.episode_count,
            episodes: eps.length ? eps : anime.episodes || [] };
    } catch (e) {
        console.error(`[AnichinDetail] ${anime.title}:`, e.message);
        return anime;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCRAPER 2: OTAKUDESU → ANIME
// ═══════════════════════════════════════════════════════════════════════════════
async function scrapeOtakudesu(maxPages = 10) {
    const base    = 'https://otakudesu.cloud';
    const results = [];

    for (const section of ['ongoing-anime', 'complete-anime']) {
        const pagesForSection = section === 'ongoing-anime' ? maxPages : Math.ceil(maxPages / 2);
        for (let page = 1; page <= pagesForSection; page++) {
            const url = `${base}/${section}/page/${page}/`;
            console.log(`[Otakudesu/Anime] ${section} page ${page}...`);
            try {
                const { data } = await ax.get(url);
                const $         = cheerio.load(data);
                const items     = $('.col-anime-con, .venz ul li, .detpost');
                if (items.length === 0) { console.log(`[Otakudesu] No items on ${section} page ${page}, stop.`); break; }

                items.each((_, el) => {
                    const titleEl = $(el).find('.col-anime-title a, .thumbz h2 a, h2 a, .detname a').first();
                    const title   = cleanTitle(titleEl.text() || $(el).find('h2,h3').first().text());
                    const link    = titleEl.attr('href') || $(el).find('a').first().attr('href') || '';
                    if (!title || !link) return;

                    const img    = $(el).find('img').attr('src') || $(el).find('img').attr('data-src') || '';
                    const rating = $(el).find('.col-anime-rating, .rate strong').text().trim() || '8.0';
                    const status = section.includes('complete') ? 'Completed' : 'Ongoing';
                    const epRaw  = $(el).find('.col-anime-eps, .epz').text().replace(/\D/g,'') || '1';

                    const id = makeId('an_ot_', title);
                    if (results.find(a => a.id === id)) return;

                    results.push({
                        id, title,
                        type:          'Anime',
                        image_url:     img || 'https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80',
                        description:   'Streaming Anime dari Otakudesu.',
                        rating:        rating.replace(/[^0-9.]/g,'') || '8.0',
                        status,
                        genres:        ['Action', 'Fantasy'],
                        release_year:  String(new Date().getFullYear()),
                        studio:        'Unknown',
                        episode_count: parseInt(epRaw) || 1,
                        source_url:    link.startsWith('http') ? link : `${base}${link}`,
                        episodes:      []
                    });
                });

                await sleep(800);
            } catch (err) {
                console.error(`[Otakudesu] ${section} page ${page} error:`, err.message);
                break;
            }
        }
    }

    console.log(`[Otakudesu/Anime] Total: ${results.length}`);
    return results;
}

async function scrapeOtakudesuDetail(anime) {
    if (!anime?.source_url) return anime;
    console.log(`[OtakudesuDetail] Scraping episodes: ${anime.title}`);
    try {
        const { data } = await ax.get(anime.source_url);
        const $         = cheerio.load(data);

        const description = cleanTitle($('.sinopc p, .sinopc, .entry-content p').first().text()) || anime.description;
        const genres      = [];
        $('.genre-info a, .infozin p a, .spe .genre a').each((_, el) => {
            const g = $(el).text().trim(); if (g && !genres.includes(g)) genres.push(g);
        });
        const rating = $('.rating strong, .score, .num').first().text().replace(/[^0-9.]/g,'') || anime.rating;
        const status = cleanTitle($('.infozin p:contains("Status") span').last().text()) || anime.status;

        const epLinks = [];
        $('#episodelist ul li a, .episodelist ul li a, .eplister ul li a').each((_, el) => {
            const href = $(el).attr('href'); if (!href) return;
            const abs  = href.startsWith('http') ? href : new URL(href, anime.source_url).toString();
            const text = cleanTitle($(el).find('.epl-title, .epstitle').text() || $(el).text());
            if (isNoise(text, abs)) return;
            const nm = text.match(/(?:episode|ep)\s*(\d+(?:[.,]\d+)?)/i) || abs.match(/episode[- ](\d+)/i);
            if (!nm) return;
            const epNum = nm[1].replace(/[,\-]/, '.');
            epLinks.push({ id: `${anime.id}_ep_${epNum}`, anime_id: anime.id,
                title: text || `${anime.title} Episode ${epNum}`,
                episode_number: epNum, video_url: abs,
                release_date: cleanTitle($(el).find('.epl-date').text()) || 'Baru Saja' });
        });

        const eps = dedupeEpisodes(epLinks);
        console.log(`[OtakudesuDetail] ${anime.title}: ${eps.length} episodes`);
        return { ...anime, description, genres: genres.length ? genres : anime.genres,
            rating, status, episode_count: eps.length || anime.episode_count,
            episodes: eps.length ? eps : anime.episodes || [] };
    } catch (e) {
        console.error(`[OtakudesuDetail] ${anime.title}:`, e.message);
        return anime;
    }
}

// ─── BACKGROUND SCRAPER ───────────────────────────────────────────────────────
async function runBackgroundScraper() {
    if (isScraping) { console.log('[Scraper] Already running, skip.'); return; }
    isScraping = true;
    console.log('=== Background Scraper Started ===');
    try {
        // Anichin → Donghua
        const donghuaData = await scrapeAnichin(40);

        // Otakudesu → Anime
        const animeData = await scrapeOtakudesu(25);

        // Gabung, deduplicate by ID dulu, lalu by title
        const combined = uniqueByTitle(uniqueById([...donghuaData, ...animeData]));
        saveCache(combined);
        console.log(`=== Done. Donghua: ${donghuaData.length}, Anime: ${animeData.length}, Total unik: ${combined.length} ===`);
    } catch (e) {
        console.error('[Scraper] Error:', e);
    } finally {
        isScraping = false;
    }
}

// ─── ROUTES ───────────────────────────────────────────────────────────────────
app.get('/', (req, res) => res.json({
    message: 'NihonHua API v4.1', status: 'running',
    scrapers: { donghua: 'anichin.moe', anime: 'otakudesu.cloud' },
    total_cache: globalCache.length, is_scraping: isScraping
}));

app.get('/api/status', (req, res) => res.json({
    status: 'online', cached_anime: globalCache.length,
    cache_age_minutes: lastCacheTime ? Math.round((Date.now() - lastCacheTime) / 60000) : null,
    uptime_seconds: Math.round(process.uptime()), is_scraping: isScraping,
    donghua_count: globalCache.filter(a => a.type === 'Donghua').length,
    anime_count:   globalCache.filter(a => a.type === 'Anime').length,
}));

app.get('/api/scrape', (req, res) => {
    res.json({ success: true, data: globalCache, count: globalCache.length,
        message: `Served ${globalCache.length} items from cache.`,
        is_updating_in_background: isScraping, timestamp: new Date().toISOString() });

    const stale = (Date.now() - lastCacheTime) > CACHE_TTL;
    const thin  = globalCache.length < 20; // raised from 5
    if (!isScraping && (thin || stale)) {
        console.log(`[Scrape] Auto-trigger (thin=${thin}, stale=${stale})`);
        runBackgroundScraper();
    }
});

app.get('/api/anime', (req, res) => {
    const r = globalCache.filter(a => a.type === 'Anime');
    res.json({ success: true, data: r, count: r.length, timestamp: new Date().toISOString() });
});

app.get('/api/donghua', (req, res) => {
    const r = globalCache.filter(a => a.type === 'Donghua');
    res.json({ success: true, data: r, count: r.length, timestamp: new Date().toISOString() });
});

app.get('/api/search', async (req, res) => {
    const q     = (req.query.q    || '').toLowerCase();
    const genre = (req.query.genre || '').toLowerCase();
    const year  = req.query.year  || '';
    const type  = (req.query.type  || '').toLowerCase();
    let results = globalCache;
    if (q)     results = results.filter(a => a.title.toLowerCase().includes(q) || a.description.toLowerCase().includes(q));
    if (genre) results = results.filter(a => a.genres?.some(g => g.toLowerCase().includes(genre)));
    if (year)  results = results.filter(a => a.release_year === year);
    if (type)  results = results.filter(a => a.type?.toLowerCase() === type);
    res.json({ success: true, data: results, count: results.length,
        message: `Found ${results.length} results for "${q}"`, timestamp: new Date().toISOString() });
});

// Detail + lazy episode scrape
app.get('/api/anime/:animeId', async (req, res) => {
    const index = globalCache.findIndex(a => a.id === req.params.animeId);
    let anime   = globalCache[index];
    if (!anime) return res.status(404).json({ success: false, error: 'Not found. Call /api/scrape first.' });

    if ((!anime.episodes || anime.episodes.length === 0) && anime.source_url) {
        const host = (() => { try { return new URL(anime.source_url).hostname; } catch { return ''; } })();
        if (host.includes('otakudesu'))    anime = await scrapeOtakudesuDetail(anime);
        else if (host.includes('anichin')) anime = await scrapeAnichinDetail(anime);
        // myanimelist.net source_url → tidak ada streaming, skip
        globalCache[index] = anime;
        saveCache(globalCache);
    }

    res.json({ success: true, data: anime });
});

app.get('/api/resolve-video', async (req, res) => {
    let url = req.query.url || '';
    if (!url) return res.status(400).json({ error: 'url required' });
    try { url = decodeURIComponent(url); } catch {}
    if (isDirectVideo(url)) return res.json({ success: true, video_url: url, type: 'direct' });
    res.json({ success: true, video_url: await resolveVideoUrl(url) });
});

app.post('/api/resolve-video', async (req, res) => {
    let url = req.body?.url || '';
    if (!url) return res.status(400).json({ error: 'url required' });
    try { url = decodeURIComponent(url); } catch {}
    if (isDirectVideo(url)) return res.json({ success: true, video_url: url, type: 'direct' });
    res.json({ success: true, video_url: await resolveVideoUrl(url) });
});

app.get('/api/cache/clear', (req, res) => {
    globalCache = []; lastCacheTime = 0;
    try { fs.unlinkSync(CACHE_FILE); } catch {}
    console.log('[Cache] Cleared.');
    runBackgroundScraper();
    res.json({ success: true, message: 'Cache cleared. Background scrape started.' });
});

// ─── START ────────────────────────────────────────────────────────────────────
loadCache();
app.listen(PORT, '0.0.0.0', () => {
    console.log(`\n🚀 NihonHua v4.1 running at http://0.0.0.0:${PORT}`);
    console.log(`   Donghua source : anichin.moe`);
    console.log(`   Anime source   : otakudesu.cloud\n`);
    if (globalCache.length < 20) runBackgroundScraper();
    else console.log(`[Boot] Cache has ${globalCache.length} items, skip initial scrape.`);
    setInterval(runBackgroundScraper, 6 * 60 * 60 * 1000);
});