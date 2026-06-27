/**
 * NihonHua Backend Server - MERGED v3.0
 * Gabungan dari dua versi server.js:
 *   - v1 (uploaded): background scraper, file cache JSON, Jikan API, dedup canggih, scrapeAnimeDetail
 *   - v2 (paste): resolveStreamtape, resolveDoodstream, resolveEmbedUrl, /api/status, /api/cache/clear
 *
 * Install: npm install express axios cheerio cors
 * Run:     node server.js
 * Port:    3536
 */

const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const cors = require('cors');
const fs = require('fs');

const app = express();
const PORT = process.env.PORT || 3536;

app.use(cors());
app.use(express.json());

// ─── PERSISTENT FILE CACHE ────────────────────────────────────────────────────
const CACHE_FILE = './data_cache.json';
let globalCache = [];
let isScraping = false;
let lastCacheTime = 0;
const CACHE_TTL = 30 * 60 * 1000; // 30 menit (untuk in-memory)

function loadCache() {
    try {
        if (fs.existsSync(CACHE_FILE)) {
            const data = fs.readFileSync(CACHE_FILE, 'utf8');
            globalCache = JSON.parse(data);
            lastCacheTime = Date.now();
            console.log(`[Cache] Loaded ${globalCache.length} items from data_cache.json`);
        } else {
            globalCache = [];
            console.log('[Cache] No cache file found, starting fresh.');
        }
    } catch (e) {
        globalCache = [];
        console.error('[Cache] Failed to load cache:', e.message);
    }
}

function saveCache(data) {
    try {
        fs.writeFileSync(CACHE_FILE, JSON.stringify(data, null, 2));
        globalCache = data;
        lastCacheTime = Date.now();
        console.log(`[Cache] Saved ${data.length} items to data_cache.json`);
    } catch (e) {
        console.error('[Cache] Failed to save cache:', e.message);
    }
}

// ─── HELPERS ──────────────────────────────────────────────────────────────────
const sleep = ms => new Promise(res => setTimeout(res, ms));

function makeId(prefix, title) {
    return prefix + title.toLowerCase().replace(/[^a-z0-9]/g, '_').replace(/^_+|_+$/g, '').substring(0, 60);
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

function normalizeEpisodeNumber(value, fallbackIndex) {
    const raw = String(value || '').trim();
    const match = raw.match(/\d+(?:\.\d+)?/);
    return match ? match[0] : String(fallbackIndex + 1);
}

function isNoiseEpisode(text, href) {
    const label = String(text || '').trim().toLowerCase();
    const url = String(href || '').toLowerCase();
    if (!label && !url) return true;
    if (['home', 'homepage', 'beranda', 'donghua', 'anime', 'download', 'batch'].includes(label)) return true;
    if (url === '/' || url.endsWith('/home/') || url.includes('#respond')) return true;
    return false;
}

function dedupeEpisodes(episodes) {
    const byNumber = new Map();
    episodes.forEach((ep, index) => {
        if (isNoiseEpisode(ep.title, ep.video_url)) return;
        const num = normalizeEpisodeNumber(ep.episode_number, index);
        if (!num || Number(num) <= 0) return;
        const normalized = {
            ...ep,
            episode_number: num,
            id: `${ep.anime_id}_ep_${num.replace(/[^a-zA-Z0-9]/g, '_')}`
        };
        const current = byNumber.get(num);
        if (!current || normalized.video_url.length > current.video_url.length) {
            byNumber.set(num, normalized);
        }
    });
    return Array.from(byNumber.values()).sort((a, b) => {
        const la = Number(a.episode_number), lb = Number(b.episode_number);
        if (isNaN(la) || isNaN(lb)) return a.episode_number.localeCompare(b.episode_number);
        return la - lb;
    });
}

// ─── HTTP HEADERS ─────────────────────────────────────────────────────────────
const HEADERS = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
    'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8',
    'Accept-Language': 'en-US,en;q=0.9,id;q=0.8',
    'DNT': '1',
    'Connection': 'keep-alive',
};

const axiosInstance = axios.create({
    timeout: 20000,
    headers: HEADERS,
    maxRedirects: 5,
});

// ─── AD / BLOCKED HOST FILTER ────────────────────────────────────────────────
function isBlockedHost(url) {
    try {
        const host = new URL(url).hostname.toLowerCase();
        return [
            'doubleclick.net', 'googlesyndication.com', 'googleadservices.com',
            'adservice.google.com', 'adsterra', 'popads', 'propellerads',
            'onclick', 'adnxs.com', 'taboola', 'outbrain'
        ].some(b => host.includes(b));
    } catch {
        return true;
    }
}

function isDirectVideoUrl(url) {
    return /\.(mp4|m3u8|webm|mkv)(\?|$)/i.test(url);
}

// ─── VIDEO RESOLVERS ──────────────────────────────────────────────────────────

async function resolveStreamtape(videoId) {
    try {
        const url = `https://streamtape.com/e/${videoId}`;
        const res = await axiosInstance.get(url);
        const html = res.data;

        // Metode 1: token-based
        const match1 = html.match(/id=\\"([^"]+)\\"/);
        const match2 = html.match(/token=\\"([^"]+)\\"/);
        if (match1 && match2) {
            return `https://streamtape.com/get_video?id=${match1[1]}&expires=9999999999&ip=x&token=${match2[1]}&stream=1`;
        }
        // Metode 2: robotlink
        const linkMatch = html.match(/robotlink['"]\s*\)\.innerHTML\s*=\s*["']([^"']+)/);
        if (linkMatch) return 'https:' + linkMatch[1].replace('&amp;', '&');
    } catch (err) {
        console.error('[resolveStreamtape]', err.message);
    }
    return null;
}

async function resolveDoodstream(embedUrl) {
    try {
        const res = await axiosInstance.get(embedUrl, {
            headers: { ...HEADERS, 'Referer': 'https://doodstream.com' }
        });
        const html = res.data;
        const mdMatch = html.match(/\$\.get\(['"]\/pass_md5\/([^'"]+)['"]/);
        if (!mdMatch) return null;

        const md5Url = `https://doodstream.com/pass_md5/${mdMatch[1]}`;
        const tokenRes = await axiosInstance.get(md5Url, {
            headers: { ...HEADERS, 'Referer': embedUrl }
        });
        const token = html.match(/token=([a-zA-Z0-9]+)/)?.[1] || 'NihonHua';
        return `${tokenRes.data}NihonHua?token=${token}&expiry=${Date.now()}`;
    } catch (err) {
        console.error('[resolveDoodstream]', err.message);
    }
    return null;
}

async function resolveEmbedUrl(embedUrl) {
    if (embedUrl.startsWith('//')) embedUrl = 'https:' + embedUrl;
    try {
        const res = await axiosInstance.get(embedUrl, {
            headers: { ...HEADERS, 'Referer': 'https://anichin.moe' }
        });
        const html = res.data;
        const $ = cheerio.load(html);

        // Direct video tag
        const videoSrc = $('video source').attr('src') || $('video').attr('src');
        if (videoSrc && isDirectVideoUrl(videoSrc)) return videoSrc;

        const scriptText = $('script').map((i, el) => $(el).html() || '').get().join('\n');

        // Streamtape
        const stapeMatch = scriptText.match(/streamtape\.com\/e\/([a-zA-Z0-9]+)/);
        if (stapeMatch) return await resolveStreamtape(stapeMatch[1]);

        // Doodstream
        const doodMatch = scriptText.match(/dood(?:stream)?\.(?:com|watch|to|la|re)\/e\/([a-zA-Z0-9]+)/);
        if (doodMatch) return await resolveDoodstream(embedUrl);

        // JWPlayer / Filemoon / Vidhide sources pattern
        const moonMatch = scriptText.match(/sources\s*:\s*\[\s*\{\s*file\s*:\s*["']([^"']+)/);
        if (moonMatch) return moonMatch[1];

        // JWPlayer setup
        const jwMatch = scriptText.match(/jwplayer[^.]*\.setup\([^)]*file["']?\s*:\s*["']([^"']+)/);
        if (jwMatch) return jwMatch[1];

        // Generic mp4/m3u8 in script
        const genericMatch = scriptText.match(/["'](https?:\/\/[^"']+\.(?:mp4|m3u8)(?:\?[^"']*)?)/);
        if (genericMatch) return genericMatch[1];

    } catch (err) {
        console.error('[resolveEmbedUrl]', err.message);
    }
    return null;
}

/**
 * Resolve video URL dari halaman episode.
 * Tries: video tag → script patterns → embed URL → embed resolvers
 */
async function resolveEpisodeVideoUrl(pageUrl) {
    if (!pageUrl || isDirectVideoUrl(pageUrl)) return pageUrl;

    try {
        const response = await axiosInstance.get(pageUrl, {
            headers: { ...HEADERS, 'Referer': pageUrl }
        });
        const $ = cheerio.load(response.data);
        const candidates = [];

        // Video / iframe tags
        $('video source[src], video[src], iframe[src], embed[src]').each((_, el) => {
            const src = $(el).attr('src');
            if (!src) return;
            try {
                const abs = new URL(src, pageUrl).toString();
                if (!isBlockedHost(abs)) candidates.push(abs);
            } catch { /* invalid URL */ }
        });

        // Script URLs
        const scriptText = $('script').map((_, el) => $(el).html() || '').get().join('\n');
        const urlMatches = scriptText.match(/https?:\\?\/\\?\/[^"'\\\s]+/g) || [];
        urlMatches.forEach(raw => {
            const normalized = raw.replace(/\\\//g, '/');
            if ((isDirectVideoUrl(normalized) || /embed|player|stream|drive|dood|filemoon|streamtape|mp4upload/i.test(normalized)) && !isBlockedHost(normalized)) {
                candidates.push(normalized);
            }
        });

        // Prioritize: direct > embed
        const direct = candidates.find(isDirectVideoUrl);
        if (direct) return direct;

        const embed = candidates.find(u => /embed|player|stream|drive|dood|filemoon|streamtape|mp4upload/i.test(u));
        if (embed) {
            // Try to resolve embed further
            const resolved = await resolveEmbedUrl(embed);
            if (resolved) return resolved;
            return embed;
        }

        return candidates[0] || pageUrl;
    } catch (err) {
        console.error('[resolveEpisodeVideoUrl]', err.message);
        return pageUrl;
    }
}

// ─── ANICHIN DETAIL SCRAPER ───────────────────────────────────────────────────
async function scrapeAnichinDetail(anime) {
    if (!anime?.source_url) return anime;
    try {
        const response = await axiosInstance.get(anime.source_url);
        const $ = cheerio.load(response.data);

        const description = $('.entry-content p, .desc p, .synopsis p, .entry-content').first().text().trim() || anime.description;

        const genres = [];
        $('.genre-info a, .genxed a, .genre a, a[rel="tag"], .spe .mgen a').each((_, el) => {
            const g = $(el).text().trim();
            if (g && !genres.includes(g)) genres.push(g);
        });

        const releaseYear = $('.info-content .spe span:contains("Tahun")').next().text().trim()
            || String(new Date().getFullYear());
        const studio = $('.info-content .spe span:contains("Studio")').next().text().trim() || anime.studio || 'Unknown';
        const status = $('.info-content .spe span:contains("Status")').next().text().trim() || anime.status || 'Ongoing';
        const type = $('.info-content .spe span:contains("Tipe")').next().text().trim()
            || ($('.info-content').text().includes('Donghua') ? 'Donghua' : anime.type || 'Anime');
        const rating = $('.num, .rating strong, .rateit').first().text().replace(/[^0-9.]/g, '') || anime.rating || '8.0';

        // Episode list
        const episodeLinks = [];
        $('.eplister li a, .episodelist a, .bixbox a, #episode_page li a').each((_, el) => {
            const href = $(el).attr('href');
            if (!href) return;
            const absoluteUrl = new URL(href, anime.source_url).toString();
            const text = $(el).text().replace(/\s+/g, ' ').trim();
            if (isNoiseEpisode(text, absoluteUrl)) return;
            const numMatch = text.match(/(?:episode|eps|ep)?\s*(\d+(?:\.\d+)?)/i) || absoluteUrl.match(/(?:episode|eps|ep)-?(\d+(?:-\d+)?)/i);
            if (!numMatch) return;
            const epNum = numMatch[1].replace('-', '.');
            episodeLinks.push({
                id: `${anime.id}_ep_${epNum.replace(/[^a-zA-Z0-9]/g, '_')}`,
                anime_id: anime.id,
                title: text || `${anime.title} - Episode ${epNum}`,
                episode_number: epNum,
                video_url: absoluteUrl,
                release_date: 'Baru Saja'
            });
        });

        const uniqueEpisodes = dedupeEpisodes(episodeLinks);

        return {
            ...anime,
            description,
            genres: genres.length ? genres : anime.genres,
            release_year: releaseYear || anime.release_year,
            studio,
            status,
            type,
            rating,
            episode_count: uniqueEpisodes.length || anime.episode_count,
            episodes: uniqueEpisodes.length ? uniqueEpisodes : dedupeEpisodes(anime.episodes || [])
        };
    } catch (err) {
        console.error(`[scrapeAnichinDetail] ${anime.title}:`, err.message);
        return anime;
    }
}

// ─── BACKGROUND SCRAPER ───────────────────────────────────────────────────────
async function runBackgroundScraper() {
    if (isScraping) {
        console.log('[Scraper] Already running, skipped.');
        return;
    }
    isScraping = true;
    console.log('=== Background Scraper Started ===');

    try {
        // ── 1. Anichin (Donghua) ──────────────────────────────────────────
        let anichinData = [];
        let page = 1;
        let hasMore = true;
        const maxAnichinPages = 100;

        while (hasMore && page <= maxAnichinPages) {
            console.log(`[Anichin] Scraping page ${page}...`);
            try {
                const url = page === 1
                    ? 'https://anichin.moe/'
                    : `https://anichin.moe/page/${page}/`;

                const response = await axiosInstance.get(url);
                const $ = cheerio.load(response.data);
                const items = $('.listupd .bs, .listupd .bsx, .listupd .utao, .listupd article');

                if (items.length === 0) {
                    hasMore = false;
                    break;
                }

                items.each((_, el) => {
                    const link = $(el).find('a').first().attr('href') || '';
                    const sourceUrl = link ? new URL(link, 'https://anichin.moe').toString() : '';
                    const title = cleanTitle($(el).find('.tt, h2, h3, .bigor .tt').first().text());
                    const img = $(el).find('img').attr('src') || $(el).find('img').attr('data-src') || '';
                    const rating = $(el).find('.numscore, .rating span, .rating').text().trim() || '9.0';
                    const status = $(el).find('.status').text().trim() || 'Ongoing';
                    const epNum = $(el).find('.epx, .epcur').text().trim() || '1';

                    if (title && sourceUrl) {
                        const animeId = makeId('dh_', title);
                        if (!anichinData.find(a => a.id === animeId)) {
                            anichinData.push({
                                id: animeId,
                                title,
                                type: 'Donghua',
                                image_url: img || 'https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80',
                                description: 'Koleksi streaming premium Donghua terbaru dari Anichin.',
                                rating,
                                status,
                                genres: ['Action', 'Cultivation', 'Fantasy'],
                                release_year: String(new Date().getFullYear()),
                                studio: 'Anichin Studios',
                                episode_count: parseInt(epNum) || 1,
                                source_url: sourceUrl,
                                episodes: []
                            });
                        }
                    }
                });

                page++;
                await sleep(3000); // Jeda sopan ke Cloudflare
            } catch (err) {
                console.error(`[Anichin] Page ${page} error:`, err.message);
                hasMore = false;
            }
        }
        console.log(`[Anichin] Total scraped: ${anichinData.length}`);

        // ── 2. Jikan API (Anime dari MyAnimeList) ─────────────────────────
        let jikanData = [];
        const maxJikanPages = 5;
        console.log('[Jikan] Fetching anime list...');

        for (let p = 1; p <= maxJikanPages; p++) {
            try {
                const url = `https://api.jikan.moe/v4/top/anime?page=${p}`;
                const response = await axiosInstance.get(url, { timeout: 10000 });
                const data = response.data.data;

                if (!data || data.length === 0) break;

                data.forEach(item => {
                    const animeId = 'an_' + item.mal_id;
                    if (!jikanData.find(a => a.id === animeId)) {
                        jikanData.push({
                            id: animeId,
                            title: item.title_english || item.title,
                            type: 'Anime',
                            image_url: item.images?.jpg?.large_image_url || item.images?.jpg?.image_url || '',
                            description: item.synopsis || 'Anime dari MyAnimeList.',
                            rating: item.score ? String(item.score) : 'N/A',
                            status: item.status || 'Unknown',
                            genres: item.genres ? item.genres.map(g => g.name) : ['Anime'],
                            release_year: item.year ? String(item.year) : 'Unknown',
                            studio: item.studios?.length ? item.studios[0].name : 'Unknown',
                            episode_count: item.episodes || 0,
                            source_url: item.url || '',
                            episodes: []
                        });
                    }
                });

                await sleep(1500); // Patuhi rate-limit Jikan
            } catch (err) {
                console.error(`[Jikan] Page ${p} error:`, err.message);
                break;
            }
        }
        console.log(`[Jikan] Total fetched: ${jikanData.length}`);

        // ── 3. Combine & deduplicate ─────────────────────────────────────
        const combined = uniqueById([...anichinData, ...jikanData]);
        saveCache(combined);
        console.log(`=== Background Scrape Done. Total: ${combined.length} items ===`);

    } catch (e) {
        console.error('[Scraper] General error:', e);
    } finally {
        isScraping = false;
    }
}

// ─── ROUTES ───────────────────────────────────────────────────────────────────

// GET /
app.get('/', (req, res) => {
    res.json({
        message: 'NihonHua Anime Scraper API',
        version: '3.0.0 (Merged)',
        status: 'running',
        is_scraping_now: isScraping,
        total_cache: globalCache.length,
        endpoints: {
            'GET /api/scrape': 'Semua anime (cache)',
            'GET /api/anime': 'Filter tipe Anime',
            'GET /api/donghua': 'Filter tipe Donghua',
            'GET /api/anime/:id': 'Detail anime + episode list',
            'GET /api/search?q=&genre=&year=&type=': 'Pencarian dengan filter',
            'GET /api/resolve-video?url=': 'Resolve video URL dari episode page',
            'POST /api/resolve-video': 'Resolve video URL (body: {url})',
            'GET /api/status': 'Status server & cache',
            'GET /api/cache/clear': 'Force clear cache',
        }
    });
});

// GET /api/status
app.get('/api/status', (req, res) => {
    res.json({
        status: 'online',
        version: '3.0.0',
        cached_anime: globalCache.length,
        cache_age_minutes: lastCacheTime ? Math.round((Date.now() - lastCacheTime) / 60000) : null,
        uptime_seconds: Math.round(process.uptime()),
        is_scraping: isScraping,
    });
});

// GET /api/scrape — serve cache langsung, trigger background scrape kalau kosong
app.get('/api/scrape', (req, res) => {
    res.json({
        success: true,
        data: globalCache,
        message: `Served ${globalCache.length} items from cache.`,
        is_updating_in_background: isScraping,
        timestamp: new Date().toISOString()
    });

    if (!isScraping && globalCache.length < 5) {
        runBackgroundScraper();
    }
});

// GET /api/anime
app.get('/api/anime', (req, res) => {
    const result = globalCache.filter(item => item.type === 'Anime');
    res.json({ success: true, data: result, count: result.length, timestamp: new Date().toISOString() });
});

// GET /api/donghua
app.get('/api/donghua', (req, res) => {
    const result = globalCache.filter(item => item.type === 'Donghua');
    res.json({ success: true, data: result, count: result.length, timestamp: new Date().toISOString() });
});

// GET /api/search?q=&genre=&year=&type=
app.get('/api/search', async (req, res) => {
    const q = (req.query.q || '').toLowerCase();
    const genre = (req.query.genre || '').toLowerCase();
    const year = req.query.year || '';
    const type = (req.query.type || '').toLowerCase();

    let results = globalCache;

    if (q) results = results.filter(a =>
        a.title.toLowerCase().includes(q) || a.description.toLowerCase().includes(q)
    );
    if (genre) results = results.filter(a =>
        a.genres?.some(g => g.toLowerCase().includes(genre))
    );
    if (year) results = results.filter(a => a.release_year === year);
    if (type) results = results.filter(a => a.type?.toLowerCase() === type);

    // Fallback: scrape live jika cache kosong
    if (results.length === 0 && globalCache.length === 0 && q) {
        console.log('[Search] Cache empty, doing live scrape for:', q);
        try {
            const { data } = await axiosInstance.get(`https://anichin.moe/?s=${encodeURIComponent(q)}`);
            const $ = cheerio.load(data);
            const liveResults = [];
            $('.listupd .bs, .listupd .bsx, .listupd article').each((_, el) => {
                const link = $(el).find('a').first().attr('href') || '';
                const title = cleanTitle($(el).find('.tt, h2, h3').first().text());
                const img = $(el).find('img').attr('src') || $(el).find('img').attr('data-src') || '';
                if (title) {
                    const id = makeId('dh_', title);
                    liveResults.push({
                        id, title, type: 'Donghua',
                        image_url: img,
                        description: '',
                        rating: '9.0',
                        status: 'Ongoing',
                        genres: ['Action', 'Fantasy'],
                        release_year: String(new Date().getFullYear()),
                        studio: 'Unknown',
                        episode_count: 1,
                        source_url: link,
                        episodes: []
                    });
                }
            });
            results = liveResults;
        } catch (err) {
            console.error('[Search] Live fallback error:', err.message);
        }
    }

    res.json({
        success: true,
        data: results,
        count: results.length,
        message: `Found ${results.length} results for query: "${q}"`,
        timestamp: new Date().toISOString()
    });
});

// GET /api/anime/:id — detail + episode list (scrape detail kalau episodes kosong)
app.get('/api/anime/:animeId', async (req, res) => {
    const { animeId } = req.params;
    const index = globalCache.findIndex(item => item.id === animeId);
    let anime = globalCache[index];

    if (!anime) {
        return res.status(404).json({ success: false, error: 'Anime not found in cache. Try /api/scrape first.' });
    }

    // Scrape detail (episode list) kalau masih kosong
    if ((!anime.episodes || anime.episodes.length === 0) && anime.source_url) {
        console.log(`[Detail] Scraping episodes for: ${anime.title}`);
        anime = await scrapeAnichinDetail(anime);
        globalCache[index] = anime;
        saveCache(globalCache);
    }

    res.json({ success: true, data: anime });
});

// GET /api/resolve-video?url=ENCODED_URL
app.get('/api/resolve-video', async (req, res) => {
    let url = req.query.url || '';
    if (!url) return res.status(400).json({ success: false, error: 'url parameter wajib diisi' });
    try { url = decodeURIComponent(url); } catch (_) { /* already decoded */ }

    console.log('[resolve-video] Resolving:', url);

    if (isDirectVideoUrl(url)) {
        return res.json({ success: true, video_url: url, type: 'direct' });
    }

    const videoUrl = await resolveEpisodeVideoUrl(url);
    res.json({ success: true, video_url: videoUrl });
});

// POST /api/resolve-video  body: { url: "..." }
app.post('/api/resolve-video', async (req, res) => {
    let url = req.body?.url || '';
    if (!url) return res.status(400).json({ success: false, error: 'url wajib di body' });
    try { url = decodeURIComponent(url); } catch (_) { /* already decoded */ }

    console.log('[resolve-video POST] Resolving:', url);

    if (isDirectVideoUrl(url)) {
        return res.json({ success: true, video_url: url, type: 'direct' });
    }

    const videoUrl = await resolveEpisodeVideoUrl(url);
    res.json({ success: true, video_url: videoUrl });
});

// GET /api/cache/clear — Force clear & retrigger scrape
app.get('/api/cache/clear', (req, res) => {
    globalCache = [];
    lastCacheTime = 0;
    try { fs.unlinkSync(CACHE_FILE); } catch (_) { /* no file */ }
    console.log('[Cache] Cleared by API call.');
    runBackgroundScraper(); // langsung kick off scrape baru
    res.json({ success: true, message: 'Cache cleared. Background scrape started.' });
});

// ─── START ────────────────────────────────────────────────────────────────────
loadCache();

app.listen(PORT, '0.0.0.0', () => {
    console.log(`\n🚀 NihonHua Backend v3.0 running at http://0.0.0.0:${PORT}`);
    console.log(`📺 API: http://0.0.0.0:${PORT}/api/scrape`);
    console.log(`🔍 Status: http://0.0.0.0:${PORT}/api/status\n`);

    // Background scrape on boot kalau cache kosong
    if (globalCache.length < 5) {
        runBackgroundScraper();
    } else {
        console.log(`[Boot] Cache sudah ada (${globalCache.length} items), skip initial scrape.`);
    }

    // Jadwal ulang setiap 6 jam
    setInterval(runBackgroundScraper, 6 * 60 * 60 * 1000);
});