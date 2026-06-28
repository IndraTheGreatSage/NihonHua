/**
 * NihonHua Backend Server v5.0
 *
 * Sumber scraping:
 *   - Anichin (anichin.moe)     → Donghua  (tetap, bekerja)
 *   - Samehadaku (samehadaku.tv) → Anime    (ganti Otakudesu yang 403)
 *   - Neonime (neonime.net)      → Anime    (fallback #1)
 *   - Anibatch (anibatch.id)     → Anime    (fallback #2)
 *
 * Perubahan dari v4.1:
 *   - Ganti scraper Otakudesu → Samehadaku + fallback
 *   - Scrape episode page + embed URL per-anime
 *   - Resolve embed ke direct stream (mp4/m3u8) via resolveVideoUrl()
 *   - Episode video_url berisi URL embed/stream yang bisa langsung diputar di WebView
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
    if (['home','homepage','beranda','donghua','anime','download','batch','next','prev','previous'].includes(t)) return true;
    if (u === '/' || u.endsWith('/home/') || u.includes('#')) return true;
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
    'Accept':          'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
    'Accept-Language': 'id-ID,id;q=0.9,en-US;q=0.8,en;q=0.7',
    'Cache-Control':   'no-cache',
    'Pragma':          'no-cache',
};

const ax = axios.create({ timeout: 25000, headers: HEADERS, maxRedirects: 5 });

// ─── VIDEO RESOLVERS ──────────────────────────────────────────────────────────
function isBlockedHost(url) {
    try {
        const h = new URL(url).hostname.toLowerCase();
        return ['doubleclick.net','googlesyndication.com','adsterra','popads',
                'propellerads','adnxs.com','taboola','outbrain','googletagmanager',
                'facebook.com','twitter.com'].some(b => h.includes(b));
    } catch { return true; }
}

function isDirectVideo(url) {
    return /\.(mp4|m3u8|webm|mkv)(\?|$)/i.test(url);
}

async function resolveStreamtape(id) {
    try {
        const { data: html } = await ax.get(`https://streamtape.com/e/${id}`,
            { headers: { ...HEADERS, Referer: 'https://streamtape.com' } });
        const lm = html.match(/robotlink['"]\)\.innerHTML\s*=\s*["']([^"']+)/);
        if (lm) return 'https:' + lm[1].replace('&amp;', '&');
        const m1 = html.match(/id=\\"([^"]+)\\"/);
        const m2 = html.match(/token=\\"([^"]+)\\"/);
        if (m1 && m2) return `https://streamtape.com/get_video?id=${m1[1]}&expires=9999999999&ip=x&token=${m2[1]}&stream=1`;
    } catch (e) { console.error('[streamtape]', e.message); }
    return null;
}

async function resolveDood(embedUrl) {
    try {
        const base = embedUrl.match(/https?:\/\/[^/]+/)?.[0] || 'https://doodstream.com';
        const { data: html } = await ax.get(embedUrl, { headers: { ...HEADERS, Referer: base } });
        const md = html.match(/\$\.get\(['"]\/pass_md5\/([^'"]+)['"]/);
        if (!md) return null;
        const tokenRes = await ax.get(`${base}/pass_md5/${md[1]}`, { headers: { ...HEADERS, Referer: embedUrl } });
        const token    = html.match(/token=([a-zA-Z0-9]+)/)?.[1] || 'NihonHuaStream';
        return `${tokenRes.data}NihonHua?token=${token}&expiry=${Date.now()}`;
    } catch (e) { console.error('[dood]', e.message); }
    return null;
}

async function resolveFilemoon(embedUrl) {
    try {
        const { data: html } = await ax.get(embedUrl, { headers: { ...HEADERS, Referer: embedUrl } });
        // Filemoon pakai eval() pack, cari sources di script
        const packed = html.match(/eval\(function\(p,a,c,k,e,d\).*?\)\)/s);
        if (!packed) {
            const direct = html.match(/sources:\s*\[{file:"([^"]+)"/);
            if (direct) return direct[1];
        }
        // Cari m3u8 langsung
        const m3u8 = html.match(/["'](https?:\/\/[^"']+\.m3u8[^"']*)/);
        if (m3u8) return m3u8[1];
    } catch (e) { console.error('[filemoon]', e.message); }
    return null;
}

async function resolveEmbed(embedUrl) {
    if (!embedUrl) return null;
    if (embedUrl.startsWith('//')) embedUrl = 'https:' + embedUrl;
    try {
        const { data: html } = await ax.get(embedUrl, { headers: { ...HEADERS, Referer: embedUrl } });
        const $ = cheerio.load(html);

        // Video tag langsung
        const vs = $('video source').attr('src') || $('video').attr('src');
        if (vs && isDirectVideo(vs)) return vs.startsWith('http') ? vs : new URL(vs, embedUrl).toString();

        const sc = $('script').map((_, el) => $(el).html() || '').get().join('\n');

        // JWPlayer / Video.js sources
        const jw = sc.match(/sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)/) ||
                   sc.match(/file["']?\s*:\s*["']([^"']+\.(?:mp4|m3u8)[^"']*)/);
        if (jw && !isBlockedHost(jw[1])) return jw[1];

        // Direct mp4/m3u8 di script
        const gn = sc.match(/["'](https?:\/\/[^"']+\.(?:mp4|m3u8)(?:\?[^"']*)?)/);
        if (gn && !isBlockedHost(gn[1])) return gn[1];

        // Streamtape
        const st = sc.match(/streamtape\.com\/e\/([a-zA-Z0-9_-]+)/) ||
                   embedUrl.match(/streamtape\.com\/e\/([a-zA-Z0-9_-]+)/);
        if (st) { const r = await resolveStreamtape(st[1]); if (r) return r; }

        // Doodstream
        const dd = embedUrl.match(/dood(?:stream)?\.(?:com|watch|to|la|re|so)\/(?:e|f)\/([a-zA-Z0-9]+)/);
        if (dd) { const r = await resolveDood(embedUrl); if (r) return r; }

        // Filemoon
        if (embedUrl.includes('filemoon') || embedUrl.includes('moonfil')) {
            const r = await resolveFilemoon(embedUrl);
            if (r) return r;
        }

    } catch (e) { console.error('[resolveEmbed]', embedUrl, e.message); }
    return null;
}

async function resolveVideoUrl(pageUrl) {
    if (!pageUrl || isDirectVideo(pageUrl)) return pageUrl;
    try {
        const { data } = await ax.get(pageUrl, { headers: { ...HEADERS, Referer: pageUrl } });
        const $ = cheerio.load(data);
        const cands = [];

        // Cari iframe embed / video tag
        $('video source[src], video[src], iframe[src], embed[src]').each((_, el) => {
            const src = $(el).attr('src');
            if (!src) return;
            try {
                const abs = src.startsWith('http') ? src : new URL(src, pageUrl).toString();
                if (!isBlockedHost(abs)) cands.push(abs);
            } catch {}
        });

        // Cari di script
        const sc = $('script').map((_, el) => $(el).html() || '').get().join('\n');
        const matches = sc.match(/https?:(?:\\\/\\\/|\/\/)[^"'\\\s<>]+/g) || [];
        matches.forEach(raw => {
            const n = raw.replace(/\\\//g, '/');
            if ((isDirectVideo(n) || /embed|player|stream|dood|filemoon|streamtape|mp4upload|goplay|yourupload/i.test(n))
                && !isBlockedHost(n)) cands.push(n);
        });

        // Prioritas: direct video dulu
        const direct = cands.find(isDirectVideo);
        if (direct) return direct;

        // Lalu coba resolve embed
        const embed = cands.find(u =>
            /embed|player|stream|dood|filemoon|streamtape|mp4upload|goplay|yourupload/i.test(u));
        if (embed) {
            const r = await resolveEmbed(embed);
            if (r) return r;
            return embed; // kembalikan embed URL kalau gagal resolve (WebView bisa putar)
        }

        return cands[0] || pageUrl;
    } catch (e) {
        console.error('[resolveVideoUrl]', e.message);
        return pageUrl;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCRAPER 1: ANICHIN → DONGHUA (tetap dari v4.1, bekerja)
// ═══════════════════════════════════════════════════════════════════════════════
async function scrapeAnichin(maxPages = 40) {
    const base    = 'https://anichin.moe';
    const results = [];

    for (let page = 1; page <= maxPages; page++) {
        const url = page === 1 ? `${base}/` : `${base}/page/${page}/`;
        console.log(`[Anichin/Donghua] Page ${page}...`);
        try {
            const { data } = await ax.get(url);
            const $ = cheerio.load(data);
            const items = $('.listupd .bs, .listupd .bsx, .listupd .utao, .listupd article');
            if (items.length === 0) { console.log('[Anichin] No more items, stop.'); break; }

            items.each((_, el) => {
                const link   = $(el).find('a').first().attr('href') || '';
                const srcUrl = link ? (link.startsWith('http') ? link : `${base}${link}`) : '';
                const title  = cleanTitle($(el).find('.tt, .bigor .tt, h2, h3').first().text());
                const img    = $(el).find('img').attr('src') || $(el).find('img').attr('data-src') || '';
                const rating = $(el).find('.numscore, .rating span, .rating').first().text().trim() || '9.0';
                const status = $(el).find('.status').text().trim() || 'Ongoing';
                const epNum  = $(el).find('.epx, .epcur').text().replace(/\D/g,'') || '1';

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

            await sleep(2500);
        } catch (err) {
            console.error(`[Anichin] Page ${err.config?.url || page} error:`, err.message);
            break;
        }
    }

    console.log(`[Anichin/Donghua] Total: ${results.length}`);
    return results;
}

async function scrapeAnichinDetail(anime) {
    if (!anime?.source_url) return anime;
    console.log(`[AnichinDetail] ${anime.title}`);
    try {
        const { data } = await ax.get(anime.source_url);
        const $ = cheerio.load(data);

        const description = $('.entry-content p, .desc p, .synopsis p').first().text().trim() || anime.description;
        const genres = [];
        $('.genre-info a, .genxed a, .genre a, a[rel="tag"], .spe .mgen a').each((_, el) => {
            const g = $(el).text().trim(); if (g && !genres.includes(g)) genres.push(g);
        });
        const rating = $('.num, .rating strong').first().text().replace(/[^0-9.]/g,'') || anime.rating || '8.0';
        const status = $('.info-content .spe span:contains("Status")').next().text().trim() || anime.status;

        const epLinks = [];
        $('.eplister li a, .episodelist a, .bixbox a, #episode_page li a').each((_, el) => {
            const href = $(el).attr('href'); if (!href) return;
            const abs  = href.startsWith('http') ? href : new URL(href, anime.source_url).toString();
            const text = $(el).text().replace(/\s+/g,' ').trim();
            if (isNoise(text, abs)) return;
            const nm = text.match(/(?:episode|eps|ep)?\s*(\d+(?:\.\d+)?)/i) || abs.match(/(?:episode|eps|ep)-?(\d+(?:-\d+)?)/i);
            if (!nm) return;
            epLinks.push({
                id: `${anime.id}_ep_${nm[1]}`, anime_id: anime.id,
                title: text || `${anime.title} - Episode ${nm[1]}`,
                episode_number: nm[1].replace('-','.'), video_url: abs, release_date: 'Baru Saja'
            });
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
// SCRAPER 2: SAMEHADAKU → ANIME (ganti Otakudesu)
// ═══════════════════════════════════════════════════════════════════════════════
async function scrapeSamehadaku(maxPages = 15) {
    const base    = 'https://samehadaku.email';
    const results = [];
    const sections = [
        { path: 'ongoing-anime',  status: 'Ongoing'   },
        { path: 'complete-anime', status: 'Completed' },
    ];

    for (const { path, status } of sections) {
        const maxP = path === 'ongoing-anime' ? maxPages : Math.ceil(maxPages / 2);
        for (let page = 1; page <= maxP; page++) {
            const url = `${base}/${path}/page/${page}/`;
            console.log(`[Samehadaku/Anime] ${path} page ${page}...`);
            try {
                const { data } = await ax.get(url, {
                    headers: { ...HEADERS, Referer: base }
                });
                const $ = cheerio.load(data);

                // Samehadaku selectors
                const items = $('.animepost, .col-anime-con, .venz ul li, .listupd .bs, .listupd article');
                if (items.length === 0) {
                    console.log(`[Samehadaku] No items on page ${page}, stop.`);
                    break;
                }

                items.each((_, el) => {
                    const titleEl = $(el).find('.title a, .col-anime-title a, .thumbz h2 a, h2 a, h3 a').first();
                    const title   = cleanTitle(titleEl.text() || $(el).find('h2,h3').first().text());
                    const link    = titleEl.attr('href') || $(el).find('a').first().attr('href') || '';
                    if (!title || !link) return;

                    const img    = $(el).find('img').attr('src') || $(el).find('img').attr('data-src') || '';
                    const ratingRaw = $(el).find('.rating, .score, .rate, .numscore').text().trim();
                    const rating = ratingRaw.replace(/[^0-9.]/g,'') || '8.0';
                    const epRaw  = $(el).find('.epz, .episode, .col-anime-eps').text().replace(/\D/g,'') || '1';

                    const id = makeId('an_sh_', title);
                    if (results.find(a => a.id === id)) return;

                    const srcUrl = link.startsWith('http') ? link : `${base}${link}`;
                    results.push({
                        id, title,
                        type:          'Anime',
                        image_url:     img || 'https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80',
                        description:   'Streaming Anime terbaru dari Samehadaku.',
                        rating,
                        status,
                        genres:        ['Action', 'Fantasy', 'Adventure'],
                        release_year:  String(new Date().getFullYear()),
                        studio:        'Unknown',
                        episode_count: parseInt(epRaw) || 1,
                        source_url:    srcUrl,
                        episodes:      []
                    });
                });

                await sleep(2000);
            } catch (err) {
                console.error(`[Samehadaku] ${path} page ${page} error:`, err.message);
                break;
            }
        }
    }

    console.log(`[Samehadaku/Anime] Total: ${results.length}`);
    return results;
}

async function scrapeSamehadakuDetail(anime) {
    if (!anime?.source_url) return anime;
    console.log(`[SamehadakuDetail] ${anime.title}`);
    try {
        const { data } = await ax.get(anime.source_url, {
            headers: { ...HEADERS, Referer: 'https://samehadaku.email' }
        });
        const $ = cheerio.load(data);

        const description = $('.entry-content p, .synopsis p, .desc p, .infox p').first().text().trim()
                          || anime.description;

        const genres = [];
        $('.genre-info a, .genres a, .spe span a, .mgen a, a[rel="tag"]').each((_, el) => {
            const g = $(el).text().trim();
            if (g && g.length < 30 && !genres.includes(g)) genres.push(g);
        });

        const rating = $('.rating strong, .score, .num, .rminfo span:contains("Score")').first()
                        .text().replace(/[^0-9.]/g,'') || anime.rating;

        const status = $('.infox p:contains("Status"), .spe span:contains("Status")')
                        .text().replace(/Status\s*:?\s*/i,'').trim() || anime.status;

        const img = $('.thumb img, .poster img, .headimg img').attr('src') || anime.image_url;

        const studioEl = $('.spe span:contains("Studio"), .infox p:contains("Studio")').text();
        const studio   = studioEl.replace(/Studio\s*:?\s*/i,'').trim() || anime.studio;

        const yearEl  = $('.spe span:contains("Aired"), .spe span:contains("Dirilis"), .infox p:contains("Tahun")')
                         .text();
        const yearMatch = yearEl.match(/\d{4}/);
        const releaseYear = yearMatch ? yearMatch[0] : anime.release_year;

        // Episode list - cari semua link episode
        const epLinks = [];
        // Samehadaku punya daftar episode di #episode_page atau .episodelist
        $('#episode_page li a, .episodelist li a, .eplister ul li a, .bixbox .eplisterfull li a').each((_, el) => {
            const href = $(el).attr('href'); if (!href) return;
            const abs  = href.startsWith('http') ? href : new URL(href, anime.source_url).toString();
            const text = cleanTitle($(el).text());
            if (isNoise(text, abs)) return;

            const nm = text.match(/(?:episode|eps?|ep\.?)\s*(\d+(?:[.,]\d+)?)/i)
                    || abs.match(/(?:episode|eps?|ep)-?-?(\d+)/i)
                    || text.match(/(\d+)/);
            if (!nm) return;

            const epNum = nm[1].replace(/[,]/,'.');
            epLinks.push({
                id:             `${anime.id}_ep_${epNum}`,
                anime_id:       anime.id,
                title:          text || `${anime.title} Episode ${epNum}`,
                episode_number: epNum,
                video_url:      abs,   // page URL → di-resolve saat /api/anime/:id detail call
                release_date:   cleanTitle($(el).find('.epl-date, .epdate').text()) || 'Baru Saja'
            });
        });

        const eps = dedupeEpisodes(epLinks);
        console.log(`[SamehadakuDetail] ${anime.title}: ${eps.length} episodes`);

        return {
            ...anime,
            description,
            genres:        genres.length ? genres : anime.genres,
            rating:        rating || anime.rating,
            status:        status || anime.status,
            image_url:     img || anime.image_url,
            studio:        studio || anime.studio,
            release_year:  releaseYear || anime.release_year,
            episode_count: eps.length || anime.episode_count,
            episodes:      eps.length ? eps : anime.episodes || []
        };
    } catch (e) {
        console.error(`[SamehadakuDetail] ${anime.title}:`, e.message);
        return anime;
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// SCRAPER 3: NEONIME → ANIME (fallback jika Samehadaku gagal)
// ═══════════════════════════════════════════════════════════════════════════════
async function scrapeNeonime(maxPages = 10) {
    const base    = 'https://neonime.fun';
    const results = [];
    const sections = [
        { path: 'episode',    status: 'Ongoing'   },
        { path: 'tvshows',    status: 'Ongoing'   },
    ];

    for (const { path, status } of sections) {
        for (let page = 1; page <= maxPages; page++) {
            const url = page === 1 ? `${base}/${path}/` : `${base}/${path}/page/${page}/`;
            console.log(`[Neonime/Anime] ${path} page ${page}...`);
            try {
                const { data } = await ax.get(url, { headers: { ...HEADERS, Referer: base } });
                const $ = cheerio.load(data);

                const items = $('.listupd .bs, article.bs, .animepost, .excstory');
                if (items.length === 0) { console.log(`[Neonime] No items on ${path} page ${page}.`); break; }

                items.each((_, el) => {
                    const titleEl = $(el).find('h2 a, h3 a, .tt a, .title a').first();
                    const title   = cleanTitle(titleEl.text() || $(el).find('h2,h3').first().text());
                    const link    = titleEl.attr('href') || $(el).find('a').first().attr('href') || '';
                    if (!title || !link) return;

                    const img    = $(el).find('img').attr('src') || $(el).find('img').attr('data-src') || '';
                    const rating = $(el).find('.numscore, .rating, .score').text().replace(/[^0-9.]/g,'') || '8.0';
                    const id     = makeId('an_neo_', title);
                    if (results.find(a => a.id === id)) return;

                    const srcUrl = link.startsWith('http') ? link : `${base}${link}`;
                    results.push({
                        id, title,
                        type:          'Anime',
                        image_url:     img || 'https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80',
                        description:   'Streaming Anime dari Neonime.',
                        rating,
                        status,
                        genres:        ['Action', 'Fantasy'],
                        release_year:  String(new Date().getFullYear()),
                        studio:        'Unknown',
                        episode_count: 1,
                        source_url:    srcUrl,
                        episodes:      []
                    });
                });

                await sleep(2000);
            } catch (err) {
                console.error(`[Neonime] ${path} page ${page} error:`, err.message);
                break;
            }
        }
    }

    console.log(`[Neonime/Anime] Total: ${results.length}`);
    return results;
}

// ═══════════════════════════════════════════════════════════════════════════════
// EPISODE VIDEO URL RESOLVER (dipanggil saat /api/anime/:id)
// ═══════════════════════════════════════════════════════════════════════════════
async function resolveEpisodeVideoUrls(anime) {
    if (!anime?.episodes?.length) return anime;

    const resolved = [];
    for (const ep of anime.episodes) {
        const pageUrl = ep.video_url;

        // Kalau sudah direct video, skip resolve
        if (isDirectVideo(pageUrl)) {
            resolved.push(ep);
            continue;
        }

        // Coba resolve halaman episode → embed → direct video
        try {
            const { data } = await ax.get(pageUrl, {
                headers: { ...HEADERS, Referer: pageUrl }
            });
            const $ = cheerio.load(data);

            // Kumpulkan semua candidate embed/video
            const cands = [];
            $('iframe[src], iframe[data-src]').each((_, el) => {
                const src = $(el).attr('src') || $(el).attr('data-src') || '';
                if (src && !isBlockedHost(src)) {
                    const abs = src.startsWith('http') ? src : new URL(src, pageUrl).toString();
                    cands.push(abs);
                }
            });
            $('video source, video[src]').each((_, el) => {
                const src = $(el).attr('src');
                if (src) cands.push(src.startsWith('http') ? src : new URL(src, pageUrl).toString());
            });

            // Priority: streaming embeds
            const preferredHosts = ['streamtape','dood','filemoon','yourupload','mp4upload','goplay','neonime'];
            const preferred = cands.find(u => preferredHosts.some(h => u.includes(h)));
            const embedUrl  = preferred || cands[0];

            if (embedUrl) {
                const direct = await resolveEmbed(embedUrl);
                resolved.push({
                    ...ep,
                    video_url: direct || embedUrl  // kalau resolve gagal, fallback ke embed (WebView bisa putar)
                });
            } else {
                resolved.push(ep);
            }
        } catch (e) {
            console.error(`[EpResolve] ${ep.title}:`, e.message);
            resolved.push(ep); // keep original
        }

        await sleep(800); // sopan ke server
    }

    return { ...anime, episodes: resolved };
}

// ─── BACKGROUND SCRAPER ───────────────────────────────────────────────────────
async function runBackgroundScraper() {
    if (isScraping) { console.log('[Scraper] Already running, skip.'); return; }
    isScraping = true;
    console.log('=== Background Scraper v5.0 Started ===');
    try {
        // 1. Donghua dari Anichin (bekerja)
        const donghuaData = await scrapeAnichin(40);

        // 2. Anime dari Samehadaku (ganti Otakudesu)
        let animeData = await scrapeSamehadaku(15);

        // 3. Fallback ke Neonime kalau Samehadaku hasilnya < 10
        if (animeData.length < 10) {
            console.log('[Scraper] Samehadaku thin, trying Neonime fallback...');
            const neoData = await scrapeNeonime(10);
            animeData = uniqueByTitle([...animeData, ...neoData]);
        }

        const combined = uniqueByTitle(uniqueById([...donghuaData, ...animeData]));
        saveCache(combined);
        console.log(`=== Done. Donghua: ${donghuaData.length}, Anime: ${animeData.length}, Total unik: ${combined.length} ===`);
    } catch (e) {
        console.error('[Scraper] Fatal error:', e.message);
    } finally {
        isScraping = false;
    }
}

// ─── ROUTES ───────────────────────────────────────────────────────────────────
app.get('/', (req, res) => res.json({
    message: 'NihonHua API v5.0',
    status:  'running',
    scrapers: {
        donghua: 'anichin.moe',
        anime:   'samehadaku.email (primary) + neonime.fun (fallback)'
    },
    total_cache: globalCache.length,
    is_scraping: isScraping
}));

app.get('/api/status', (req, res) => res.json({
    status:            'online',
    cached_anime:      globalCache.length,
    cache_age_minutes: lastCacheTime ? Math.round((Date.now() - lastCacheTime) / 60000) : null,
    uptime_seconds:    Math.round(process.uptime()),
    is_scraping:       isScraping,
    donghua_count:     globalCache.filter(a => a.type === 'Donghua').length,
    anime_count:       globalCache.filter(a => a.type === 'Anime').length,
}));

app.get('/api/scrape', (req, res) => {
    res.json({
        success:                   true,
        data:                      globalCache,
        count:                     globalCache.length,
        message:                   `Served ${globalCache.length} items from cache.`,
        is_updating_in_background: isScraping,
        timestamp:                 new Date().toISOString()
    });

    const stale = (Date.now() - lastCacheTime) > CACHE_TTL;
    const thin  = globalCache.length < 20;
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

app.get('/api/search', (req, res) => {
    const q     = (req.query.q     || '').toLowerCase();
    const genre = (req.query.genre || '').toLowerCase();
    const year  = req.query.year   || '';
    const type  = (req.query.type  || '').toLowerCase();

    let results = globalCache;
    if (q)     results = results.filter(a => a.title.toLowerCase().includes(q) || a.description.toLowerCase().includes(q));
    if (genre) results = results.filter(a => a.genres?.some(g => g.toLowerCase().includes(genre)));
    if (year)  results = results.filter(a => a.release_year === year);
    if (type)  results = results.filter(a => a.type?.toLowerCase() === type);

    res.json({
        success:   true,
        data:      results,
        count:     results.length,
        message:   `Found ${results.length} results`,
        timestamp: new Date().toISOString()
    });
});

// Detail + lazy scrape episode + resolve video URL
app.get('/api/anime/:animeId', async (req, res) => {
    const index = globalCache.findIndex(a => a.id === req.params.animeId);
    let anime   = globalCache[index];
    if (!anime) return res.status(404).json({ success: false, error: 'Not found. Call /api/scrape first.' });

    // Jika belum ada episodes, scrape detail dari source_url
    const needEpisodes = !anime.episodes || anime.episodes.length === 0;
    if (needEpisodes && anime.source_url) {
        const host = (() => { try { return new URL(anime.source_url).hostname; } catch { return ''; } })();

        if (host.includes('anichin'))        anime = await scrapeAnichinDetail(anime);
        else if (host.includes('samehadaku')) anime = await scrapeSamehadakuDetail(anime);
        else if (host.includes('neonime'))    anime = await scrapeSamehadakuDetail(anime); // struktur mirip
        else                                  anime = await scrapeSamehadakuDetail(anime); // generic fallback

        globalCache[index] = anime;
        saveCache(globalCache);
    }

    // Jika episodes ada tapi video_url masih page URL (bukan embed/direct),
    // resolve ke embed/direct video (max 5 episode pertama dulu agar cepat)
    const hasUnresolved = anime.episodes?.some(ep =>
        !isDirectVideo(ep.video_url) &&
        !/embed|player|stream|dood|filemoon|streamtape|mp4upload/i.test(ep.video_url)
    );

    if (hasUnresolved && anime.episodes?.length > 0) {
        const toResolve = { ...anime, episodes: anime.episodes.slice(0, 5) };
        const resolved  = await resolveEpisodeVideoUrls(toResolve);
        // Gabung: resolved episodes (0-4) + unresolved (5+)
        const mergedEps = [
            ...resolved.episodes,
            ...anime.episodes.slice(5)
        ];
        anime = { ...resolved, episodes: mergedEps };
        globalCache[index] = anime;
        saveCache(globalCache);
    }

    res.json({ success: true, data: anime });
});

// Resolve video URL on-demand (dipanggil dari Android saat ganti episode)
app.get('/api/resolve-video', async (req, res) => {
    let url = req.query.url || '';
    if (!url) return res.status(400).json({ error: 'url required' });
    try { url = decodeURIComponent(url); } catch {}
    if (isDirectVideo(url)) return res.json({ success: true, video_url: url, type: 'direct' });
    const resolved = await resolveVideoUrl(url);
    res.json({ success: true, video_url: resolved, original_url: url });
});

app.post('/api/resolve-video', async (req, res) => {
    let url = req.body?.url || '';
    if (!url) return res.status(400).json({ error: 'url required' });
    try { url = decodeURIComponent(url); } catch {}
    if (isDirectVideo(url)) return res.json({ success: true, video_url: url, type: 'direct' });
    const resolved = await resolveVideoUrl(url);
    res.json({ success: true, video_url: resolved, original_url: url });
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
    console.log(`\n🚀 NihonHua v5.0 running at http://0.0.0.0:${PORT}`);
    console.log(`   Donghua : anichin.moe`);
    console.log(`   Anime   : samehadaku.email → fallback: neonime.fun\n`);
    if (globalCache.length < 20) runBackgroundScraper();
    else {
        console.log(`[Boot] Cache has ${globalCache.length} items, skip initial scrape.`);
        const stale = (Date.now() - lastCacheTime) > CACHE_TTL;
        if (stale) { console.log('[Boot] Cache stale, triggering refresh...'); runBackgroundScraper(); }
    }
    setInterval(runBackgroundScraper, 6 * 60 * 60 * 1000);
});