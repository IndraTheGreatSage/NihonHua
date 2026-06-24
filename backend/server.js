const express = require('express');
const axios = require('axios');
const cheerio = require('cheerio');
const cors = require('cors');

const app = express();
const PORT = 3536;

// Middleware
app.use(cors());
app.use(express.json());

// Local cache data (fallback when scraping fails)
const LOCAL_ANIMES = [
    {
        id: "btth_s5",
        title: "Battle Through The Heavens Season 5 (Xiao Yan)",
        type: "Donghua",
        image_url: "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
        description: "Melanjutkan perjalanan Xiao Yan di Akademi Jia Nan. Xiao Yan harus berjuang memperebutkan 'Fallen Heart Flame' dan membalaskan dendam keluarganya dari Sekte Misty Cloud yang kejam.",
        rating: "9.8",
        status: "Ongoing",
        genres: ["Action", "Cultivation", "Adventure", "Fantasy"],
        release_year: "2023",
        studio: "Motion Magic",
        episode_count: 104,
        episodes: Array.from({ length: 15 }, (_, i) => ({
            id: `btth_s5_ep${i + 1}`,
            anime_id: "btth_s5",
            title: `Battle Through The Heavens Season 5 - Episode ${i + 1}`,
            episode_number: String(i + 1),
            video_url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            release_date: "Hari Minggu Lalu"
        }))
    },
    {
        id: "solo_leveling",
        title: "Solo Leveling (Only I Level Up)",
        type: "Anime",
        image_url: "https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80",
        description: "Di dunia di mana para Hunter berjuang melawan monster dari gerbang misterius, Sung Jin-Woo adalah Hunter terlemah di dunia. Setelah selamat dari Double Dungeon yang mematikan, ia mendapatkan sistem misterius yang memungkinkannya naik level tanpa batas.",
        rating: "9.9",
        status: "Completed",
        genres: ["Action", "Fantasy", "System", "Adventure"],
        release_year: "2024",
        studio: "A-1 Pictures",
        episode_count: 12,
        episodes: Array.from({ length: 12 }, (_, i) => ({
            id: `solo_leveling_ep${i + 1}`,
            anime_id: "solo_leveling",
            title: `Solo Leveling - Episode ${i + 1}`,
            episode_number: String(i + 1),
            video_url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
            release_date: "Tahun Ini"
        }))
    },
    {
        id: "demon_slayer_s4",
        title: "Demon Slayer: Hashira Training Arc",
        type: "Anime",
        image_url: "https://images.unsplash.com/photo-1541562232579-512a21360020?w=500&q=80",
        description: "Tanjirou pergi mengunjungi Hashira Batu, Himejima Gyoumei, yang bermaksud mempersiapkannya menghadapi pertempuran besar mendatang melawan Muzan Kibutsuji. Pelatihan intensif ini menuntut daya tahan ekstrem.",
        rating: "9.8",
        status: "Completed",
        genres: ["Action", "Historical", "Fantasy", "Shounen"],
        release_year: "2024",
        studio: "Ufotable",
        episode_count: 8,
        episodes: Array.from({ length: 8 }, (_, i) => ({
            id: `demon_slayer_ep${i + 1}`,
            anime_id: "demon_slayer_s4",
            title: `Demon Slayer: Hashira Training - Episode ${i + 1}`,
            episode_number: String(i + 1),
            video_url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
            release_date: "Mei 2024"
        }))
    },
    {
        id: "renegade_immortal",
        title: "Renegade Immortal (Xian Ni)",
        type: "Donghua",
        image_url: "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80",
        description: "Wang Lin adalah seorang pemuda biasa yang memiliki bakat kultivasi yang sangat rendah. Namun, berkat ketekunannya dan manik misterius pembelah langit, ia menempuh jalan kultivasi yang kejam demi mencapai keabadian.",
        rating: "9.7",
        status: "Ongoing",
        genres: ["Action", "Cultivation", "Martial Arts", "Dark Fantasy"],
        release_year: "2023",
        studio: "Sparkly Key Animation",
        episode_count: 52,
        episodes: Array.from({ length: 10 }, (_, i) => ({
            id: `renegade_immortal_ep${i + 1}`,
            anime_id: "renegade_immortal",
            title: `Renegade Immortal - Episode ${i + 1}`,
            episode_number: String(i + 1),
            video_url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
            release_date: "Hari Senin Lalu"
        }))
    }
];

// Scraping functions
async function scrapeAnichin() {
    const url = "https://anichin.vip";
    const headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    };

    try {
        const response = await axios.get(url, { headers, timeout: 10000 });
        const $ = cheerio.load(response.data);
        const scrapedList = [];

        $(".listupd .bs, .listupd .utao, .listupd article").slice(0, 10).each((i, el) => {
            const title = $(el).find(".tt, h2, h3").text().trim();
            const img = $(el).find("img").attr("src") || "";
            const rating = $(el).find(".rating, .numscore").text().trim() || "9.5";
            const status = $(el).find(".status").text().trim() || "Ongoing";

            if (title) {
                const animeId = "dh_" + title.toLowerCase().replace(/[^a-z0-9]/g, "_");
                scrapedList.push({
                    id: animeId,
                    title: title,
                    type: "Donghua",
                    image_url: img || "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
                    description: "Koleksi streaming premium Donghua terbaru dari Anichin.",
                    rating: rating,
                    status: status,
                    genres: ["Action", "Cultivation", "Fantasy"],
                    release_year: "2024",
                    studio: "Anichin Studios",
                    episode_count: 12,
                    episodes: Array.from({ length: 8 }, (_, i) => ({
                        id: `${animeId}_ep${i + 1}`,
                        anime_id: animeId,
                        title: `${title} - Episode ${i + 1}`,
                        episode_number: String(i + 1),
                        video_url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                        release_date: "Baru Saja"
                    }))
                });
            }
        });

        console.log(`Scraped ${scrapedList.length} items from Anichin`);
        return scrapedList;
    } catch (error) {
        console.error("Failed scraping Anichin:", error.message);
        return [];
    }
}

async function scrapeOtakudesu() {
    const url = "https://otakudesu.cloud/";
    const headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    };

    try {
        const response = await axios.get(url, { headers, timeout: 10000 });
        const $ = cheerio.load(response.data);
        const scrapedList = [];

        $(".venutama .venpost").slice(0, 10).each((i, el) => {
            const title = $(el).find(".jdlflm").text().trim();
            const img = $(el).find(".thumb img").attr("src") || "";

            if (title) {
                const animeId = "an_" + title.toLowerCase().replace(/[^a-z0-9]/g, "_");
                scrapedList.push({
                    id: animeId,
                    title: title,
                    type: "Anime",
                    image_url: img || "https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80",
                    description: "Koleksi streaming Japanese Anime terbaru terupdate di NihonHua.",
                    rating: "9.8",
                    status: "Ongoing",
                    genres: ["Action", "Fantasy", "Shounen", "Adventure"],
                    release_year: "2024",
                    studio: "Otakudesu",
                    episode_count: 12,
                    episodes: Array.from({ length: 12 }, (_, i) => ({
                        id: `${animeId}_ep${i + 1}`,
                        anime_id: animeId,
                        title: `${title} - Episode ${i + 1}`,
                        episode_number: String(i + 1),
                        video_url: "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                        release_date: "Baru Saja"
                    }))
                });
            }
        });

        console.log(`Scraped ${scrapedList.length} items from Otakudesu`);
        return scrapedList;
    } catch (error) {
        console.error("Failed scraping Otakudesu:", error.message);
        return [];
    }
}

// API Endpoints
app.get("/", (req, res) => {
    res.json({
        message: "NihonHua Anime Scraper API",
        version: "1.0.0",
        status: "running",
        endpoints: {
            scrape: "/api/scrape",
            anime: "/api/anime",
            donghua: "/api/donghua",
            search: "/api/search"
        }
    });
});

app.get("/api/scrape", async (req, res) => {
    try {
        const [anichinData, otakudesuData] = await Promise.all([
            scrapeAnichin(),
            scrapeOtakudesu()
        ]);

        let combinedData = anichinData.concat(otakudesuData);

        if (combinedData.length === 0) {
            combinedData = LOCAL_ANIMES;
            res.json({
                success: true,
                data: combinedData,
                message: "Using local cache data",
                timestamp: new Date().toISOString()
            });
        } else {
            // Combine with local cache and remove duplicates
            const allData = combinedData.concat(LOCAL_ANIMES);
            const seenIds = new Set();
            const uniqueData = [];
            
            for (const item of allData) {
                if (!seenIds.has(item.id)) {
                    seenIds.add(item.id);
                    uniqueData.push(item);
                }
            }
            
            res.json({
                success: true,
                data: uniqueData,
                message: `Successfully scraped ${anichinData.length} from Anichin and ${otakudesuData.length} from Otakudesu`,
                timestamp: new Date().toISOString()
            });
        }
    } catch (error) {
        console.error("Scraping error:", error);
        res.json({
            success: false,
            data: LOCAL_ANIMES,
            message: `Scraping failed, using cache: ${error.message}`,
            timestamp: new Date().toISOString()
        });
    }
});

app.get("/api/anime", async (req, res) => {
    try {
        const response = await axios.get(`http://localhost:${PORT}/api/scrape`);
        const animeOnly = response.data.data.filter(item => item.type === "Anime");
        res.json({
            success: true,
            data: animeOnly,
            message: `Found ${animeOnly.length} anime titles`,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        res.json({
            success: true,
            data: LOCAL_ANIMES.filter(item => item.type === "Anime"),
            message: "Using local cache",
            timestamp: new Date().toISOString()
        });
    }
});

app.get("/api/donghua", async (req, res) => {
    try {
        const response = await axios.get(`http://localhost:${PORT}/api/scrape`);
        const donghuaOnly = response.data.data.filter(item => item.type === "Donghua");
        res.json({
            success: true,
            data: donghuaOnly,
            message: `Found ${donghuaOnly.length} donghua titles`,
            timestamp: new Date().toISOString()
        });
    } catch (error) {
        res.json({
            success: true,
            data: LOCAL_ANIMES.filter(item => item.type === "Donghua"),
            message: "Using local cache",
            timestamp: new Date().toISOString()
        });
    }
});

app.get("/api/search", async (req, res) => {
    const { q = "", genre = "", year = "" } = req.query;
    
    try {
        const response = await axios.get(`http://localhost:${PORT}/api/scrape`);
        let filtered = response.data.data;
        
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
    } catch (error) {
        res.json({
            success: true,
            data: LOCAL_ANIMES,
            message: "Using local cache",
            timestamp: new Date().toISOString()
        });
    }
});

app.get("/api/anime/:animeId", async (req, res) => {
    const { animeId } = req.params;
    
    try {
        const response = await axios.get(`http://localhost:${PORT}/api/scrape`);
        const anime = response.data.data.find(item => item.id === animeId);
        
        if (!anime) {
            return res.status(404).json({ error: "Anime not found" });
        }
        
        res.json(anime);
    } catch (error) {
        res.status(500).json({ error: "Server error" });
    }
});

// Start server
app.listen(PORT, '0.0.0.0', () => {
    console.log(`NihonHua Scraper API running on port ${PORT}`);
    console.log(`API available at http://0.0.0.0:${PORT}`);
});
