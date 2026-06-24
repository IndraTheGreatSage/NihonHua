# NihonHua Anime Scraper Backend

Backend web scraper API untuk mengambil data anime dan donghua dari berbagai website sumber.

## Fitur

- Scraping dari Anichin.vip (Donghua)
- Scraping dari Otakudesu.cloud (Anime)
- Fallback data lokal jika scraping gagal
- RESTful API dengan Express.js (Node.js)
- Siap untuk Pterodactyl Panel (Node.js Docker image)

## API Endpoints

- `GET /` - Status API
- `GET /api/scrape` - Scrape semua sumber
- `GET /api/anime` - Ambil hanya anime
- `GET /api/donghua` - Ambil hanya donghua
- `GET /api/search?q=query&genre=Action&year=2024` - Pencarian dengan filter
- `GET /api/anime/{animeId}` - Detail anime spesifik

## Instalasi Lokal

```bash
# Install dependencies
npm install

# Run server
npm start
# atau
node server.js
```

API akan berjalan di http://localhost:3536

## Konfigurasi Pterodactyl (Node.js)

### Direct Upload (Simplest)

1. Upload semua file ke server Pterodactyl:
   - `server.js`
   - `package.json`

2. Konfigurasi di Pterodactyl Panel:
   - **Startup Command**: `npm start` (atau `node server.js`)
   - **Port Allocation**: `3536`
   - **Environment Variables**: (tidak perlu)

3. Start server

Pterodactyl akan otomatis install dependencies dari package.json dan menjalankan server.

## Contoh Response

```json
{
  "success": true,
  "data": [
    {
      "id": "btth_s5",
      "title": "Battle Through The Heavens Season 5",
      "type": "Donghua",
      "image_url": "https://...",
      "description": "...",
      "rating": "9.8",
      "status": "Ongoing",
      "genres": ["Action", "Cultivation"],
      "release_year": "2023",
      "studio": "Motion Magic",
      "episode_count": 104,
      "episodes": [...]
    }
  ],
  "message": "Successfully scraped...",
  "timestamp": "2024-01-01T00:00:00"
}
```
