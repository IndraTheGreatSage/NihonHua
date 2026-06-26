from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel
from typing import List
import asyncio
import aiohttp
from bs4 import BeautifulSoup
from datetime import datetime
import logging

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="NihonHua Anime Scraper API", version="1.0.1")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


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


def _anime_id_from_title(prefix: str, title: str) -> str:
    cleaned = title.lower().replace(" ", "_").replace("-", "_")
    return f"{prefix}{cleaned}"


async def scrape_donghua_anichin_moe(max_pages: int = 10):
    """Scrape Donghua list from Anichin.moe with pagination.

    NOTE: This uses best-effort selectors. If the site changes, scraping may return empty.
    """


    base_url = "https://anichin.moe"
    headers = {
        "User-Agent": (
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) "
            "AppleWebKit/537.36 (KHTML, like Gecko) "
            "Chrome/120.0.0.0 Safari/537.36"
        )
    }

    scraped = []

    timeout = aiohttp.ClientTimeout(total=15)
    for page in range(1, max_pages + 1):
        page_url = f"{base_url}/anime/?page={page}"
        try:
            async with aiohttp.ClientSession() as session:
                async with session.get(page_url, headers=headers, timeout=timeout) as resp:
                    html = await resp.text()
        except Exception as e:
            logger.error(f"Failed fetching Anichin page {page}: {e}")
            break

        soup = BeautifulSoup(html, "html.parser")
        elements = soup.select(".listupd .bs, .listupd .utao, .listupd article")
        if not elements:
            # If selector fails or end of list, stop.
            break

        for el in elements[:10]:
            title_elem = el.select(".tt, h2, h3")
            img_elem = el.select("img")
            rating_elem = el.select(".rating, .numscore")
            status_elem = el.select(".status")

            title = title_elem[0].get_text(strip=True) if title_elem else ""
            img = img_elem[0].get("src", "") if img_elem else ""
            rating = rating_elem[0].get_text(strip=True) if rating_elem else "9.5"
            status = status_elem[0].get_text(strip=True) if status_elem else "Ongoing"

            if not title:
                continue

            anime_id = _anime_id_from_title("dh_", title)

            scraped.append(
                {
                    "id": anime_id,
                    "title": title,
                    "type": "Donghua",
                    "image_url": img
                    if img
                    else "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
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
                            "episode_number": str(i + 1),
                            "video_url": "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4",
                            "release_date": "Baru Saja",
                        }
                        for i in range(8)
                    ],
                }
            )

    logger.info(f"Scraped {len(scraped)} items from Anichin")
    return scraped


@app.get("/")
async def root():
    return {
        "message": "NihonHua Anime Scraper API",
        "version": "1.0.1",
        "status": "running",
        "endpoints": {
            "scrape": "/api/scrape",
            "anime": "/api/anime",
            "donghua": "/api/donghua",
            "search": "/api/search",
        },
    }


@app.get("/api/scrape", response_model=ScrapingResponse)
async def scrape_all():
    try:
        anichin_data = await scrape_anichin_moe(max_pages=10)

        combined = anichin_data
        seen = set()
        unique = []
        for item in combined:
            if item["id"] not in seen:
                seen.add(item["id"])
                unique.append(item)
        combined = unique
        message = f"Successfully scraped {len(anichin_data)} from Anichin"

        return ScrapingResponse(
            success=True,
            data=[AnimeVideo(**item) for item in combined],
            message=message,
            timestamp=datetime.now().isoformat(),
        )

    except Exception as e:
        logger.error(f"Scraping error: {e}")
        return ScrapingResponse(
            success=False,
            data=[],
            message=f"Scraping failed: {e}",
            timestamp=datetime.now().isoformat(),
        )


@app.get("/api/anime", response_model=ScrapingResponse)
async def get_anime():
    response = await scrape_all()
    anime_only = [item for item in response.data if item.type == "Anime"]
    return ScrapingResponse(
        success=True,
        data=anime_only,
        message=f"Found {len(anime_only)} anime titles",
        timestamp=datetime.now().isoformat(),
    )


@app.get("/api/donghua", response_model=ScrapingResponse)
async def get_donghua():
    response = await scrape_all()
    donghua_only = [item for item in response.data if item.type == "Donghua"]
    return ScrapingResponse(
        success=True,
        data=donghua_only,
        message=f"Found {len(donghua_only)} donghua titles",
        timestamp=datetime.now().isoformat(),
    )


@app.get("/api/search", response_model=ScrapingResponse)
async def search_anime(q: str = "", genre: str = "", year: str = ""):
    response = await scrape_all()
    filtered = response.data

    if q:
        filtered = [
            item
            for item in filtered
            if q.lower() in item.title.lower() or q.lower() in item.description.lower()
        ]
    if genre:
        filtered = [
            item
            for item in filtered
            if genre.lower() in [g.lower() for g in item.genres]
        ]
    if year:
        filtered = [item for item in filtered if item.release_year == year]

    return ScrapingResponse(
        success=True,
        data=filtered,
        message=f"Found {len(filtered)} results for query: {q}",
        timestamp=datetime.now().isoformat(),
    )


@app.get("/api/anime/{anime_id}", response_model=AnimeVideo)
async def get_anime_detail(anime_id: str):
    response = await scrape_all()
    anime = next((item for item in response.data if item.id == anime_id), None)

    if not anime:
        raise HTTPException(status_code=404, detail="Anime not found")

    return anime


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(app, host="0.0.0.0", port=3536)
