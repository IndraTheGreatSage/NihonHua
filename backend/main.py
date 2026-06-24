from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List, Optional
import asyncio
import aiohttp
from bs4 import BeautifulSoup
import json
from datetime import datetime
import logging

# Configure logging
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="NihonHua Anime Scraper API", version="1.0.0")

# CORS middleware
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

# Data models
class Episode(BaseModel):
    id: str
    anime_id: str
    title: str
    episode_number: str
    video_url: str
    release_date: str

class AnimeVideo(BaseModel):
    id: str
    title: str
    type: str  # "Anime" or "Donghua"
    image_url: str
    description: str
    rating: str
    status: str  # "Ongoing" or "Completed"
    genres: List[str]
    release_year: str
    studio: str = "Unknown"
    episode_count: int = 12
    episodes: List[Episode] = []

class ScrapingResponse(BaseModel):
    success: bool
    data: List[AnimeVideo]
    message: str
    timestamp: str

# Local cache data (fallback when scraping fails)
LOCAL_ANIMES = [
    {
        "id": "btth_s5",
        "title": "Battle Through The Heavens Season 5 (Xiao Yan)",
        "type": "Donghua",
        "image_url": "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
        "description": "Melanjutkan perjalanan Xiao Yan di Akademi Jia Nan. Xiao Yan harus berjuang memperebutkan 'Fallen Heart Flame' dan membalaskan dendam keluarganya dari Sekte Misty Cloud yang kejam.",
        "rating": "9.8",
        "status": "Ongoing",
        "genres": ["Action", "Cultivation", "Adventure", "Fantasy"],
        "release_year": "2023",
        "studio": "Motion Magic",
        "episode_count": 104,
        "episodes": [
            {
                "id": f"btth_s5_ep{i+1}",
                "anime_id": "btth_s5",
                "title": f"Battle Through The Heavens Season 5 - Episode {i+1}",
                "episode_number": str(i+1),
                "video_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "release_date": "Hari Minggu Lalu"
            } for i in range(15)
        ]
    },
    {
        "id": "solo_leveling",
        "title": "Solo Leveling (Only I Level Up)",
        "type": "Anime",
        "image_url": "https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80",
        "description": "Di dunia di mana para Hunter berjuang melawan monster dari gerbang misterius, Sung Jin-Woo adalah Hunter terlemah di dunia. Setelah selamat dari Double Dungeon yang mematikan, ia mendapatkan sistem misterius yang memungkinkannya naik level tanpa batas.",
        "rating": "9.9",
        "status": "Completed",
        "genres": ["Action", "Fantasy", "System", "Adventure"],
        "release_year": "2024",
        "studio": "A-1 Pictures",
        "episode_count": 12,
        "episodes": [
            {
                "id": f"solo_leveling_ep{i+1}",
                "anime_id": "solo_leveling",
                "title": f"Solo Leveling - Episode {i+1}",
                "episode_number": str(i+1),
                "video_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                "release_date": "Tahun Ini"
            } for i in range(12)
        ]
    },
    {
        "id": "demon_slayer_s4",
        "title": "Demon Slayer: Hashira Training Arc",
        "type": "Anime",
        "image_url": "https://images.unsplash.com/photo-1541562232579-512a21360020?w=500&q=80",
        "description": "Tanjirou pergi mengunjungi Hashira Batu, Himejima Gyoumei, yang bermaksud mempersiapkannya menghadapi pertempuran besar mendatang melawan Muzan Kibutsuji. Pelatihan intensif ini menuntut daya tahan ekstrem.",
        "rating": "9.8",
        "status": "Completed",
        "genres": ["Action", "Historical", "Fantasy", "Shounen"],
        "release_year": "2024",
        "studio": "Ufotable",
        "episode_count": 8,
        "episodes": [
            {
                "id": f"demon_slayer_ep{i+1}",
                "anime_id": "demon_slayer_s4",
                "title": f"Demon Slayer: Hashira Training - Episode {i+1}",
                "episode_number": str(i+1),
                "video_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4",
                "release_date": "Mei 2024"
            } for i in range(8)
        ]
    },
    {
        "id": "renegade_immortal",
        "title": "Renegade Immortal (Xian Ni)",
        "type": "Donghua",
        "image_url": "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80",
        "description": "Wang Lin adalah seorang pemuda biasa yang memiliki bakat kultivasi yang sangat rendah. Namun, berkat ketekunannya dan manik misterius pembelah langit, ia menempuh jalan kultivasi yang kejam demi mencapai keabadian.",
        "rating": "9.7",
        "status": "Ongoing",
        "genres": ["Action", "Cultivation", "Martial Arts", "Dark Fantasy"],
        "release_year": "2023",
        "studio": "Sparkly Key Animation",
        "episode_count": 52,
        "episodes": [
            {
                "id": f"renegade_immortal_ep{i+1}",
                "anime_id": "renegade_immortal",
                "title": f"Renegade Immortal - Episode {i+1}",
                "episode_number": str(i+1),
                "video_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                "release_date": "Hari Senin Lalu"
            } for i in range(10)
        ]
    }
]

# Scraping functions
async def scrape_anichin():
    """Scrape anime/donghua from Anichin.vip"""
    url = "https://anichin.vip"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
    
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(url, headers=headers, timeout=aiohttp.ClientTimeout(total=10)) as response:
                html = await response.text()
                soup = BeautifulSoup(html, 'html.parser')
                
                scrapedList = []
                elements = soup.select(".listupd .bs, .listupd .utao, .listupd article")
                
                for el in elements[:10]:
                    title_elem = el.select(".tt, h2, h3")
                    img_elem = el.select("img")
                    rating_elem = el.select(".rating, .numscore")
                    status_elem = el.select(".status")
                    
                    title = title_elem[0].text.strip() if title_elem else ""
                    img = img_elem[0].get('src', '') if img_elem else ""
                    rating = rating_elem[0].text.strip() if rating_elem else "9.5"
                    status = status_elem[0].text.strip() if status_elem else "Ongoing"
                    
                    if title:
                        anime_id = "dh_" + title.lower().replace(" ", "_").replace("-", "_")
                        scrapedList.append({
                            "id": anime_id,
                            "title": title,
                            "type": "Donghua",
                            "image_url": img if img else "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
                            "description": "Koleksi streaming premium Donghua terbaru dari Anichin.",
                            "rating": rating,
                            "status": status,
                            "genres": ["Action", "Cultivation", "Fantasy"],
                            "release_year": "2024",
                            "studio": "Anichin Studios",
                            "episode_count": 12,
                            "episodes": [
                                {
                                    "id": f"{anime_id}_ep{i+1}",
                                    "anime_id": anime_id,
                                    "title": f"{title} - Episode {i+1}",
                                    "episode_number": str(i+1),
                                    "video_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                                    "release_date": "Baru Saja"
                                } for i in range(8)
                            ]
                        })
                
                logger.info(f"Scraped {len(scrapedList)} items from Anichin")
                return scrapedList
                
    except Exception as e:
        logger.error(f"Failed scraping Anichin: {str(e)}")
        return []

async def scrape_otakudesu():
    """Scrape Japanese anime from Otakudesu"""
    url = "https://otakudesu.cloud/"
    headers = {
        "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }
    
    try:
        async with aiohttp.ClientSession() as session:
            async with session.get(url, headers=headers, timeout=aiohttp.ClientTimeout(total=10)) as response:
                html = await response.text()
                soup = BeautifulSoup(html, 'html.parser')
                
                scrapedList = []
                elements = soup.select(".venutama .venpost")
                
                for el in elements[:10]:
                    title_elem = el.select(".jdlflm")
                    img_elem = el.select(".thumb img")
                    
                    title = title_elem[0].text.strip() if title_elem else ""
                    img = img_elem[0].get('src', '') if img_elem else ""
                    
                    if title:
                        anime_id = "an_" + title.lower().replace(" ", "_").replace("-", "_")
                        scrapedList.append({
                            "id": anime_id,
                            "title": title,
                            "type": "Anime",
                            "image_url": img if img else "https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80",
                            "description": "Koleksi streaming Japanese Anime terbaru terupdate di NihonHua.",
                            "rating": "9.8",
                            "status": "Ongoing",
                            "genres": ["Action", "Fantasy", "Shounen", "Adventure"],
                            "release_year": "2024",
                            "studio": "Otakudesu",
                            "episode_count": 12,
                            "episodes": [
                                {
                                    "id": f"{anime_id}_ep{i+1}",
                                    "anime_id": anime_id,
                                    "title": f"{title} - Episode {i+1}",
                                    "episode_number": str(i+1),
                                    "video_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4",
                                    "release_date": "Baru Saja"
                                } for i in range(12)
                            ]
                        })
                
                logger.info(f"Scraped {len(scrapedList)} items from Otakudesu")
                return scrapedList
                
    except Exception as e:
        logger.error(f"Failed scraping Otakudesu: {str(e)}")
        return []

@app.get("/")
async def root():
    return {
        "message": "NihonHua Anime Scraper API",
        "version": "1.0.0",
        "status": "running",
        "endpoints": {
            "scrape": "/api/scrape",
            "anime": "/api/anime",
            "donghua": "/api/donghua",
            "search": "/api/search"
        }
    }

@app.get("/api/scrape", response_model=ScrapingResponse)
async def scrape_all():
    """Scrape data from all sources"""
    try:
        # Run scraping tasks concurrently
        anichin_data, otakudesu_data = await asyncio.gather(
            scrape_anichin(),
            scrape_otakudesu()
        )
        
        combined_data = anichin_data + otakudesu_data
        
        # If scraping fails or returns empty, use local cache
        if not combined_data:
            combined_data = LOCAL_ANIMES
            message = "Using local cache data"
        else:
            # Combine with local cache for richer content
            combined_data = combined_data + LOCAL_ANIMES
            # Remove duplicates by ID
            seen_ids = set()
            unique_data = []
            for item in combined_data:
                if item['id'] not in seen_ids:
                    seen_ids.add(item['id'])
                    unique_data.append(item)
            combined_data = unique_data
            message = f"Successfully scraped {len(anichin_data)} from Anichin and {len(otakudesu_data)} from Otakudesu"
        
        return ScrapingResponse(
            success=True,
            data=[AnimeVideo(**item) for item in combined_data],
            message=message,
            timestamp=datetime.now().isoformat()
        )
    except Exception as e:
        logger.error(f"Scraping error: {str(e)}")
        return ScrapingResponse(
            success=False,
            data=[AnimeVideo(**item) for item in LOCAL_ANIMES],
            message=f"Scraping failed, using cache: {str(e)}",
            timestamp=datetime.now().isoformat()
        )

@app.get("/api/anime", response_model=ScrapingResponse)
async def get_anime():
    """Get only anime content"""
    response = await scrape_all()
    anime_only = [item for item in response.data if item.type == "Anime"]
    return ScrapingResponse(
        success=True,
        data=anime_only,
        message=f"Found {len(anime_only)} anime titles",
        timestamp=datetime.now().isoformat()
    )

@app.get("/api/donghua", response_model=ScrapingResponse)
async def get_donghua():
    """Get only donghua content"""
    response = await scrape_all()
    donghua_only = [item for item in response.data if item.type == "Donghua"]
    return ScrapingResponse(
        success=True,
        data=donghua_only,
        message=f"Found {len(donghua_only)} donghua titles",
        timestamp=datetime.now().isoformat()
    )

@app.get("/api/search", response_model=ScrapingResponse)
async def search_anime(q: str = "", genre: str = "", year: str = ""):
    """Search anime/donghua with filters"""
    response = await scrape_all()
    
    filtered = response.data
    if q:
        filtered = [item for item in filtered if q.lower() in item.title.lower() or q.lower() in item.description.lower()]
    if genre:
        filtered = [item for item in filtered if genre.lower() in [g.lower() for g in item.genres]]
    if year:
        filtered = [item for item in filtered if item.release_year == year]
    
    return ScrapingResponse(
        success=True,
        data=filtered,
        message=f"Found {len(filtered)} results for query: {q}",
        timestamp=datetime.now().isoformat()
    )

@app.get("/api/anime/{anime_id}", response_model=AnimeVideo)
async def get_anime_detail(anime_id: str):
    """Get detailed info for a specific anime"""
    response = await scrape_all()
    anime = next((item for item in response.data if item.id == anime_id), None)
    
    if not anime:
        raise HTTPException(status_code=404, detail="Anime not found")
    
    return anime

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=3536)
