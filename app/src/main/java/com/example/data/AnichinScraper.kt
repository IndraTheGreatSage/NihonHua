package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object AnichinScraper {
    private const val TAG = "AnichinScraper"
    private const val BASE_URL = "https://anichin.moe"

    // High quality placeholder stream URLs that play flawlessly
    val STREAM_URL_4K = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    val STREAM_URL_1080P = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
    val STREAM_URL_720P = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
    val STREAM_URL_480P = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
    val STREAM_URL_360P = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"

    // Scraping logic using Jsoup
    suspend fun getLatestShows(): List<AnimeVideo> = withContext(Dispatchers.IO) {
        val scrapedList = mutableListOf<AnimeVideo>()

        // 1. Scrape Donghua from Anichin
        try {
            val doc = Jsoup.connect(BASE_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(5000)
                .get()

            var page = 1
            val maxPages = 10
            while (page <= maxPages) {
                val pageUrl = "${BASE_URL}/anime/?page=$page"
                val pageDoc = Jsoup.connect(pageUrl)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(5000)
                    .get()

                val elements = pageDoc.select(".listupd .bs, .listupd .utao, .listupd article")
                if (elements.isEmpty()) break

                for (el in elements) {
                    val title = el.select(".tt, h2, h3").text().trim()
                    val img = el.select("img").attr("src")
                    val rating = el.select(".rating, .numscore").text().trim().ifEmpty { "9.5" }
                    val status = el.select(".status").text().trim().ifEmpty { "Ongoing" }
                    
                    if (title.isNotEmpty()) {
                        val id = "dh_" + title.lowercase().replace("[^a-z0-9]".toRegex(), "_")
                        scrapedList.add(
                            AnimeVideo(
                                id = id,
                                title = title,
                                type = "Donghua",
                                imageUrl = img.ifEmpty { "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80" },
                                description = "Koleksi streaming premium Donghua terbaru dari Anichin. Mengisahkan pertarungan epic kultivasi spiritual dan jalan keabadian yang membentang luas.",
                                rating = rating,
                                status = status,
                                genres = listOf("Action", "Cultivation", "Fantasy"),
                                releaseYear = "2024",
                                episodes = List(8) { i ->
                                    Episode(
                                        id = "${id}_ep${i + 1}",
                                        animeId = id,
                                        title = "$title - Episode ${i + 1}",
                                        episodeNumber = "${i + 1}",
                                        videoUrl = STREAM_URL_4K,
                                        releaseDate = "Baru Saja"
                                    )
                                }
                            )
                        )
                    }
                }
                page++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scraping Donghua from Anichin: ${e.message}")
        }


        // 2. Scrape Japanese Anime from Bilibili TV (Hot/Latest)
        try {
            val startUrl = "https://www.bilibili.tv/id/category?season_type=1,4&order=0" // HOT
            // NOTE: Bilibili pagination depends on XHR/infinite scroll.
            // We'll try to iterate using a best-effort query param `page` up to a sane cap.
            var page = 1
            val maxPages = 10
            while (page <= maxPages) {
                val url = if (page == 1) startUrl else "${startUrl}&page=$page"
                val doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(5000)
                    .get()

                val elements = doc.select("a")
                var addedThisPage = 0

                for (el in elements) {
                    if (addedThisPage >= 50) break // safety cap per page
                    val title = el.attr("title").trim()
                    val href = el.absUrl("href")
                    if (title.isNotEmpty() && href.isNotEmpty() && href.contains("/play/")) {
                        val id = "an_" + title.lowercase().replace("[^a-z0-9]".toRegex(), "_")
                        if (scrapedList.none { it.id == id }) {
                            val img = el.select("img").attr("abs:src")
                            scrapedList.add(
                                AnimeVideo(
                                    id = id,
                                    title = title,
                                    type = "Anime",
                                    imageUrl = img.ifEmpty { "https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80" },
                                    description = "Koleksi streaming Japanese Anime dari Bilibili TV.",
                                    rating = "9.0",
                                    status = "Ongoing",
                                    genres = listOf("Action", "Fantasy", "Adventure"),
                                    releaseYear = "2024",
                                    episodes = List(12) { i ->
                                        Episode(
                                            id = "${id}_ep${i + 1}",
                                            animeId = id,
                                            title = "$title - Episode ${i + 1}",
                                            episodeNumber = "${i + 1}",
                                            videoUrl = STREAM_URL_1080P,
                                            releaseDate = "Baru Saja"
                                        )
                                    }
                                )
                            )
                            addedThisPage++
                        }
                    }
                }

                if (addedThisPage == 0) break
                page++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scraping Japanese Anime from Bilibili TV: ${e.message}")
        }

        // 3. (removed old Otakudesu scraper remnants)
        

        scrapedList
    }

    suspend fun searchShows(query: String, genre: String = "", year: String = ""): List<AnimeVideo> = withContext(Dispatchers.IO) {
        val all = getLatestShows()
        all.filter { anime ->
            val matchQuery = query.isEmpty() || anime.title.contains(query, ignoreCase = true) || anime.description.contains(query, ignoreCase = true)
            val matchGenre = genre.isEmpty() || anime.genres.any { it.equals(genre, ignoreCase = true) }
            val matchYear = year.isEmpty() || anime.releaseYear == year
            matchQuery && matchGenre && matchYear
        }
    }
}
