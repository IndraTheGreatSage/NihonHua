const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const cors = require('cors');
const fs = require('fs');

const app = express();
const PORT = 3536;

// Middleware
app.use(cors());
app.use(express.json());

const CACHE_FILE = './data_cache.json';
let globalCache = [];
let isScraping = false;

function loadCache() {
    try {
        if (fs.existsSync(CACHE_FILE)) {
            const data = fs.readFileSync(CACHE_FILE, 'utf8');
            globalCache = JSON.parse(data);
            console.log(`Loaded ${globalCache.length} items from local JSON cache.`);
        } else {
            globalCache = [];
            console.log("No cache found, starting with empty cache.");
        }
    } catch (e) {
        globalCache = [];
    }
}

function saveCache(data) {
    try {
        const cleaned = data;
        fs.writeFileSync(CACHE_FILE, JSON.stringify(cleaned, null, 2));
        globalCache = cleaned;
    } catch (e) {
        console.error("Failed to save cache:", e.message);
    }
}

const delay = ms => new Promise(res => setTimeout(res, ms));

function makeId(prefix, title) {
    return prefix + title.toLowerCase().replace(/[^a-z0-9]/g, "_").replace(/^_+|_+$/g, "");
}

function uniqueById(items) {
    const seenIds = new Set();
    const uniqueData = [];
    for (const item of items) {
        if (!item.id || seenIds.has(item.id)) continue;
        seenIds.add(item.id);
        uniqueData.push(item);
    }
    return uniqueData;
}

function normalizeEpisodeNumber(value, fallbackIndex) {
    const raw = String(value || "").trim();
    const match = raw.match(/\d+(?:\.\d+)?/);
    return match ? match[0] : String(fallbackIndex + 1);
}

function isNoiseEpisode(text, href) {
    const label = String(text || "").trim().toLowerCase();
    const url = String(href || "").toLowerCase();
    if (!label && !url) return true;
    if (["home", "homepage", "beranda", "donghua", "anime", "download", "batch"].includes(label)) return true;
    if (url === "/" || url.endsWith("/home/") || url.includes("#respond")) return true;
    return false;
}

function dedupeEpisodes(episodes) {
    const byNumber = new Map();
    episodes.forEach((episode, index) => {
        if (isNoiseEpisode(episode.title, episode.video_url)) return;
        const episodeNumber = normalizeEpisodeNumber(episode.episode_number, index);
        if (!episodeNumber || Number(episodeNumber) <= 0) return;
        const normalized = {
            ...episode,
            episode_number: episodeNumber,
            id: `${episode.anime_id}_ep_${episodeNumber.replace(/[^a-zA-Z0-9]/g, "_")}`
        };
        const current = byNumber.get(episodeNumber);
        if (!current || normalized.video_url.length > current.video_url.length) {
            byNumber.set(episodeNumber, normalized);
        }
    });

    return Array.from(byNumber.values()).sort((a, b) => {
        const left = Number(a.episode_number);
        const right = Number(b.episode_number);
        if (Number.isNaN(left) || Number.isNaN(right)) return a.episode_number.localeCompare(b.episode_number);
        return left - right;
    });
}

function isBlockedHost(url) {
    try {
        const host = new URL(url).hostname.toLowerCase();
        return [
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adservice.google.com", "adsterra", "popads", "propellerads",
            "onclick", "adnxs.com", "taboola", "outbrain"
        ].some(blocked => host.includes(blocked));
    } catch {
        return true;
    }
}

function isDirectVideoUrl(url) {
    return /\.(mp4|m3u8|webm|mkv)(\?|$)/i.test(url);
}

async function resolveEpisodeVideoUrl(pageUrl) {
    if (!pageUrl || isDirectVideoUrl(pageUrl)) return pageUrl;

    try {
        const response = await axios.get(pageUrl, {
            headers: {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Referer": pageUrl
            },
            timeout: 15000
        });
        const $ = cheerio.load(response.data);

        const candidates = [];
        $("video source[src], video[src], iframe[src], embed[src]").each((_, el) => {
            const src = $(el).attr("src");
            if (!src) return;
            const absolute = new URL(src, pageUrl).toString();
            if (!isBlockedHost(absolute)) candidates.push(absolute);
        });

        const scriptText = $("script").map((_, el) => $(el).html() || "").get().join("\n");
        const urlMatches = scriptText.match(/https?:\\?\/\\?\/[^"'\\\s]+/g) || [];
        urlMatches.forEach(raw => {
            const normalized = raw.replace(/\\\//g, "/");
            if ((isDirectVideoUrl(normalized) || normalized.includes("embed") || normalized.includes("player")) && !isBlockedHost(normalized)) {
                candidates.push(normalized);
            }
        });

        const direct = candidates.find(isDirectVideoUrl);
        const embed = candidates.find(url => /embed|player|stream|drive|dood|filemoon|streamtape|mp4upload/i.test(url));
        return direct || embed || candidates[0] || pageUrl;
    } catch (err) {
        console.error(`Failed resolving video URL:`, err.message);
        return pageUrl;
    }
}

async function scrapeAnimeDetail(anime) {
    if (!anime?.source_url) return anime;

    try {
        const response = await axios.get(anime.source_url, {
            headers: {
                "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                "Accept-Language": "id,en-US;q=0.9,en;q=0.8"
            },
            timeout: 15000
        });

        const $ = cheerio.load(response.data);
        const description = $(".entry-content p, .desc p, .synopsis p, .entry-content").first().text().trim() || anime.description;
        const genres = [];
        $(".genre-info a, .genxed a, .genre a, a[rel='tag']").each((_, el) => {
            const genre = $(el).text().trim();
            if (genre && !genres.includes(genre)) genres.push(genre);
        });

        const episodeLinks = [];
        $(".eplister li a, .episodelist a, .bixbox a").each((_, el) => {
            const href = $(el).attr("href");
            if (!href) return;
            const absoluteUrl = new URL(href, anime.source_url).toString();
            const text = $(el).text().replace(/\s+/g, " ").trim();
            if (isNoiseEpisode(text, absoluteUrl)) return;
            const numberMatch = text.match(/(?:episode|eps|ep)?\s*(\d+(?:\.\d+)?)/i) || absoluteUrl.match(/(?:episode|eps|ep)-?(\d+(?:-\d+)?)/i);
            if (!numberMatch) return;
            const episodeNumber = numberMatch ? numberMatch[1].replace("-", ".") : String(episodeLinks.length + 1);
            episodeLinks.push({
                id: `${anime.id}_ep_${episodeNumber.replace(/[^a-zA-Z0-9]/g, "_")}`,
                anime_id: anime.id,
                title: text || `${anime.title} - Episode ${episodeNumber}`,
                episode_number: episodeNumber,
                video_url: absoluteUrl,
                release_date: "Baru Saja"
            });
        });

        const uniqueEpisodes = dedupeEpisodes(episodeLinks);

        return {
            ...anime,
            description,
            genres: genres.length ? genres : anime.genres,
            episode_count: uniqueEpisodes.length || anime.episode_count,
            episodes: uniqueEpisodes.length ? uniqueEpisodes : dedupeEpisodes(anime.episodes || [])
        };
    } catch (err) {
        console.error(`Failed scraping detail for ${anime.title}:`, err.message);
        return anime;
    }
}

async function runBackgroundScraper() {
    if (isScraping) return;
    isScraping = true;
    console.log("=== Background Scraper Started ===");
    
    try {
        let anichinData = [];
        let bilibiliData = [];
        
        // 1. Scrape Anichin (Loop pages)
        let page = 1;
        let hasMore = true;
        const maxPages = 100; // Mengambil hingga 1000 halaman (Semua data)
        
        while (hasMore && page <= maxPages) {
            console.log(`Scraping Anichin Page ${page}...`);
            try {
                const url = page === 1 ? "https://anichin.moe/" : `https://anichin.moe/page/${page}/`;
                const response = await axios.get(url, {
                    headers: {
                        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
                        "Accept-Language": "en-US,en;q=0.5"
                    },
                    timeout: 15000
                });
                const $ = cheerio.load(response.data);
                const items = $(".listupd .bs, .listupd .utao, .listupd article");
                
                if (items.length === 0) {
                    hasMore = false;
                    break;
                }
                
                items.each((i, el) => {
                    const link = $(el).find("a").first().attr("href");
                    const sourceUrl = link ? new URL(link, url).toString() : "";
                    const title = $(el).find(".tt, h2, h3").text().trim();
                    const img = $(el).find("img").attr("src") || "";
                    const rating = $(el).find(".rating, .numscore").text().trim() || "9.5";
                    const status = $(el).find(".status").text().trim() || "Ongoing";

                    if (title) {
                        const animeId = makeId("dh_", title);
                        if (!anichinData.find(a => a.id === animeId)) {
                            anichinData.push({
                                id: animeId, title, type: "Donghua", image_url: img,
                                description: "Koleksi streaming premium Donghua terbaru dari Anichin.",
                                rating, status, genres: ["Action", "Cultivation", "Fantasy"],
                                release_year: "2024", studio: "Anichin Studios", episode_count: 12,
                                source_url: sourceUrl,
                                episodes: []
                            });
                        }
                    }
                });
                
                page++;
                await delay(3000); // Jeda 3 detik agar tidak keblokir Cloudflare
            } catch (err) {
                console.error(`Error scraping Anichin page ${page}:`, err.message);
                hasMore = false;
            }
        }
        console.log(`Total Anichin scraped: ${anichinData.length}`);
        
        // 2. Scrape Jikan API (Anime List)
        let jikanData = [];
        console.log("Fetching Anime from Jikan API...");
        try {
            // Jikan API rate limit is 3 requests per second, we'll fetch up to maxPages (default 5) safely.
            for (let p = 1; p <= maxPages; p++) {
                const url = `https://api.jikan.moe/v4/top/anime?page=${p}`;
                const response = await axios.get(url, { timeout: 10000 });
                const data = response.data.data;
                
                if (!data || data.length === 0) break;
                
                data.forEach(item => {
                    const animeId = "an_" + item.mal_id;
                    if (!jikanData.find(a => a.id === animeId)) {
                        jikanData.push({
                            id: animeId, 
                            title: item.title_english || item.title, 
                            type: "Anime", 
                            image_url: item.images?.jpg?.large_image_url || item.images?.jpg?.image_url || "",
                            description: item.synopsis || "Anime dari Jikan API (MyAnimeList).",
                            rating: item.score ? item.score.toString() : "N/A", 
                            status: item.status || "Unknown", 
                            genres: item.genres ? item.genres.map(g => g.name) : ["Anime"],
                            release_year: item.year ? item.year.toString() : "Unknown", 
                            studio: item.studios && item.studios.length > 0 ? item.studios[0].name : "Unknown", 
                            episode_count: item.episodes || 0,
                            source_url: item.url || "",
                            episodes: []
                        });
                    }
                });
                
                await delay(1500); // Jeda 1.5 detik untuk mematuhi rate-limit Jikan API
            }
        } catch (err) {
            console.error("Error fetching from Jikan API:", err.message);
        }
        console.log(`Total Anime (Jikan API) fetched: ${jikanData.length}`);
        
        // Combine and deduplicate
        let combinedData = anichinData.concat(jikanData);
        combinedData = uniqueById(combinedData);
        
        saveCache(combinedData);
        console.log(`=== Background Scrape Completed. Total: ${combinedData.length} items ===`);
        
    } catch (e) {
        console.error("Background scrape general error:", e);
    } finally {
        isScraping = false;
    }
}

// API Endpoints
app.get("/", (req, res) => {
    res.json({
        message: "NihonHua Anime Scraper API",
        version: "2.0.0 (Background Scraper)",
        status: "running",
        is_scraping_now: isScraping,
        total_cache: globalCache.length,
        endpoints: {
            scrape: "/api/scrape",
            anime: "/api/anime",
            donghua: "/api/donghua",
            search: "/api/search"
        }
    });
});

app.get("/api/scrape", (req, res) => {
    // Return cache immediately
    res.json({
        success: true,
        data: globalCache,
        message: `Served ${globalCache.length} items from cache.`,
        is_updating_in_background: isScraping,
        timestamp: new Date().toISOString()
    });
    
    // Trigger background scrape if not already running (optional)
    if (!isScraping && globalCache.length < 5) {
        runBackgroundScraper();
    }
});

app.get("/api/anime", (req, res) => {
    const animeOnly = globalCache.filter(item => item.type === "Anime");
    res.json({
        success: true,
        data: animeOnly,
        message: `Found ${animeOnly.length} anime titles`,
        timestamp: new Date().toISOString()
    });
});

app.get("/api/donghua", (req, res) => {
    const donghuaOnly = globalCache.filter(item => item.type === "Donghua");
    res.json({
        success: true,
        data: donghuaOnly,
        message: `Found ${donghuaOnly.length} donghua titles`,
        timestamp: new Date().toISOString()
    });
});

app.get("/api/search", (req, res) => {
    const { q = "", genre = "", year = "" } = req.query;
    
    let filtered = globalCache;
    
    if (q) {
        filtered = filtered.filter(item => 
            item.title.toLowerCase().includes(q.toLowerCase()) || 
            item.description.toLowerCase().includes(q.toLowerCase())
        );
    }
    if (genre) {
        filtered = filtered.filter(item => 
            item.genres.some(g => g.toLowerCase() === genre.toLowerCase())
        );
    }
    if (year) {
        filtered = filtered.filter(item => item.release_year === year);
    }
    
    res.json({
        success: true,
        data: filtered,
        message: `Found ${filtered.length} results for query: ${q}`,
        timestamp: new Date().toISOString()
    });
});

app.get("/api/anime/:animeId", async (req, res) => {
    const { animeId } = req.params;
    const index = globalCache.findIndex(item => item.id === animeId);
    let anime = globalCache[index];
    
    if (!anime) {
        return res.status(404).json({ error: "Anime not found" });
    }

    if ((!anime.episodes || anime.episodes.length === 0) && anime.source_url) {
        anime = await scrapeAnimeDetail(anime);
        globalCache[index] = anime;
        saveCache(globalCache);
    }
    
    res.json(anime);
});

app.get("/api/resolve-video", async (req, res) => {
    const { url = "" } = req.query;
    if (!url) {
        return res.status(400).json({ success: false, video_url: "", message: "Missing url" });
    }

    const videoUrl = await resolveEpisodeVideoUrl(url);
    res.json({
        success: true,
        video_url: videoUrl
    });
});

// Start server
loadCache();

app.listen(PORT, '0.0.0.0', () => {
    console.log(`NihonHua Scraper API running on port ${PORT}`);
    console.log(`API available at http://0.0.0.0:${PORT}`);
    
    // Start background scrape on boot
    runBackgroundScraper();
    
    // Schedule background scrape every 6 hours (6 * 60 * 60 * 1000)
    setInterval(runBackgroundScraper, 6 * 60 * 60 * 1000);
});
