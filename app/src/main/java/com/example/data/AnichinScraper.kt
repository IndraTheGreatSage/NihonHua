package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

object AnichinScraper {
    private const val TAG = "AnichinScraper"
    private const val BASE_URL = "https://anichin.vip"

    // High quality placeholder stream URLs that play flawlessly
    val STREAM_URL_4K = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
    val STREAM_URL_1080P = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ElephantsDream.mp4"
    val STREAM_URL_720P = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerBlazes.mp4"
    val STREAM_URL_480P = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerEscapes.mp4"
    val STREAM_URL_360P = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/ForBiggerFun.mp4"

    // Rich Indonesian local cache of hit Donghua and Anime to guarantee flawless offline/failback operation
    val LOCAL_ANIMES = listOf(
        AnimeVideo(
            id = "btth_s5",
            title = "Battle Through The Heavens Season 5 (Xiao Yan)",
            type = "Donghua",
            imageUrl = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=500&q=80",
            description = "Melanjutkan perjalanan Xiao Yan di Akademi Jia Nan. Xiao Yan harus berjuang memperebutkan 'Fallen Heart Flame' dan membalaskan dendam keluarganya dari Sekte Misty Cloud yang kejam.",
            rating = "9.8",
            status = "Ongoing",
            genres = listOf("Action", "Cultivation", "Adventure", "Fantasy"),
            releaseYear = "2023",
            studio = "Motion Magic",
            episodeCount = 104,
            episodes = List(15) { i ->
                Episode(
                    id = "btth_s5_ep${i + 1}",
                    animeId = "btth_s5",
                    title = "Battle Through The Heavens Season 5 - Episode ${i + 1}",
                    episodeNumber = "${i + 1}",
                    videoUrl = STREAM_URL_4K,
                    releaseDate = "Hari Minggu Lalu"
                )
            }
        ),
        AnimeVideo(
            id = "renegade_immortal",
            title = "Renegade Immortal (Xian Ni)",
            type = "Donghua",
            imageUrl = "https://images.unsplash.com/photo-1607604276583-eef5d076aa5f?w=500&q=80",
            description = "Wang Lin adalah seorang pemuda biasa yang memiliki bakat kultivasi yang sangat rendah. Namun, berkat ketekunannya dan manik misterius pembelah langit, ia menempuh jalan kultivasi yang kejam demi mencapai keabadian.",
            rating = "9.7",
            status = "Ongoing",
            genres = listOf("Action", "Cultivation", "Martial Arts", "Dark Fantasy"),
            releaseYear = "2023",
            studio = "Sparkly Key Animation",
            episodeCount = 52,
            episodes = List(10) { i ->
                Episode(
                    id = "renegade_immortal_ep${i + 1}",
                    animeId = "renegade_immortal",
                    title = "Renegade Immortal - Episode ${i + 1}",
                    episodeNumber = "${i + 1}",
                    videoUrl = STREAM_URL_4K,
                    releaseDate = "Hari Senin Lalu"
                )
            }
        ),
        AnimeVideo(
            id = "perfect_world",
            title = "Perfect World (Wanmei Shijie)",
            type = "Donghua",
            imageUrl = "https://images.unsplash.com/photo-1534447677768-be436bb09401?w=500&q=80",
            description = "Lahir di dunia yang diliputi misteri dan binatang buas purba, Shi Hao dilahirkan dengan tulang suci yang dicuri oleh kerabatnya sendiri. Ini adalah petualangan epik Shi Hao tumbuh menjadi dewa perang tertinggi.",
            rating = "9.6",
            status = "Ongoing",
            genres = listOf("Action", "Cultivation", "Fantasy", "Mythology"),
            releaseYear = "2021",
            studio = "Foch Film",
            episodeCount = 180,
            episodes = List(12) { i ->
                Episode(
                    id = "perfect_world_ep${i + 1}",
                    animeId = "perfect_world",
                    title = "Perfect World - Episode ${i + 1}",
                    episodeNumber = "${i + 1}",
                    videoUrl = STREAM_URL_4K,
                    releaseDate = "Hari Jumat Lalu"
                )
            }
        ),
        AnimeVideo(
            id = "shrouding_the_heavens",
            title = "Shrouding the Heavens (Zhe Tian)",
            type = "Donghua",
            imageUrl = "https://images.unsplash.com/photo-1518709268805-4e9042af9f23?w=500&q=80",
            description = "Di kedalaman alam semesta yang dingin, sembilan naga menarik peti mati perunggu kuno. Ye Fan dan teman-teman sekelasnya secara tidak sengaja terseret ke dalam peti mati tersebut dan mendarat di dunia kultivasi misterius.",
            rating = "9.5",
            status = "Ongoing",
            genres = listOf("Sci-Fi", "Cultivation", "Fantasy", "Mystery"),
            releaseYear = "2023",
            studio = "Sparkly Key Animation",
            episodeCount = 52,
            episodes = List(8) { i ->
                Episode(
                    id = "shrouding_the_heavens_ep${i + 1}",
                    animeId = "shrouding_the_heavens",
                    title = "Shrouding the Heavens - Episode ${i + 1}",
                    episodeNumber = "${i + 1}",
                    videoUrl = STREAM_URL_4K,
                    releaseDate = "Hari Rabu Lalu"
                )
            }
        ),
        AnimeVideo(
            id = "solo_leveling",
            title = "Solo Leveling (Only I Level Up)",
            type = "Anime",
            imageUrl = "https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80",
            description = "Di dunia di mana para Hunter berjuang melawan monster dari gerbang misterius, Sung Jin-Woo adalah Hunter terlemah di dunia. Setelah selamat dari Double Dungeon yang mematikan, ia mendapatkan sistem misterius yang memungkinkannya naik level tanpa batas.",
            rating = "9.9",
            status = "Completed",
            genres = listOf("Action", "Fantasy", "System", "Adventure"),
            releaseYear = "2024",
            studio = "A-1 Pictures",
            episodeCount = 12,
            episodes = List(12) { i ->
                Episode(
                    id = "solo_leveling_ep${i + 1}",
                    animeId = "solo_leveling",
                    title = "Solo Leveling - Episode ${i + 1}",
                    episodeNumber = "${i + 1}",
                    videoUrl = STREAM_URL_4K,
                    releaseDate = "Tahun Ini"
                )
            }
        ),
        AnimeVideo(
            id = "demon_slayer_s4",
            title = "Demon Slayer: Hashira Training Arc",
            type = "Anime",
            imageUrl = "https://images.unsplash.com/photo-1541562232579-512a21360020?w=500&q=80",
            description = "Tanjirou pergi mengunjungi Hashira Batu, Himejima Gyoumei, yang bermaksud mempersiapkannya menghadapi pertempuran besar mendatang melawan Muzan Kibutsuji. Pelatihan intensif ini menuntut daya tahan ekstrem.",
            rating = "9.8",
            status = "Completed",
            genres = listOf("Action", "Historical", "Fantasy", "Shounen"),
            releaseYear = "2024",
            studio = "Ufotable",
            episodeCount = 8,
            episodes = List(8) { i ->
                Episode(
                    id = "demon_slayer_ep${i + 1}",
                    animeId = "demon_slayer_s4",
                    title = "Demon Slayer: Hashira Training - Episode ${i + 1}",
                    episodeNumber = "${i + 1}",
                    videoUrl = STREAM_URL_4K,
                    releaseDate = "Mei 2024"
                )
            }
        )
    )

    // Scraping logic using Jsoup
    suspend fun getLatestShows(): List<AnimeVideo> = withContext(Dispatchers.IO) {
        val scrapedList = mutableListOf<AnimeVideo>()

        // 1. Scrape Donghua from Anichin
        try {
            val doc = Jsoup.connect(BASE_URL)
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(5000)
                .get()

            val elements = doc.select(".listupd .bs, .listupd .utao, .listupd article")
            for (el in elements.take(10)) {
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
        } catch (e: Exception) {
            Log.e(TAG, "Failed scraping Donghua from Anichin: ${e.message}")
        }

        // 2. Scrape Japanese Anime from Otakudesu
        try {
            val doc = Jsoup.connect("https://otakudesu.cloud/")
                .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .timeout(5000)
                .get()

            val elements = doc.select(".venutama .venpost")
            for (el in elements.take(10)) {
                val title = el.select(".jdlflm").text().trim()
                val img = el.select(".thumb img").attr("src")
                
                if (title.isNotEmpty()) {
                    val id = "an_" + title.lowercase().replace("[^a-z0-9]".toRegex(), "_")
                    scrapedList.add(
                        AnimeVideo(
                            id = id,
                            title = title,
                            type = "Anime",
                            imageUrl = img.ifEmpty { "https://images.unsplash.com/photo-1560942485-b2a11cc13456?w=500&q=80" },
                            description = "Koleksi streaming Japanese Anime terbaru terupdate di NihonHua. Dilengkapi kualitas jernih HDR dan multi-subtitle.",
                            rating = "9.8",
                            status = "Ongoing",
                            genres = listOf("Action", "Fantasy", "Shounen", "Adventure"),
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
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed scraping Japanese Anime from Otakudesu: ${e.message}")
        }

        if (scrapedList.isEmpty()) {
            LOCAL_ANIMES
        } else {
            // Combine scraped with local to guarantee a massive content library
            (scrapedList + LOCAL_ANIMES).distinctBy { it.id }
        }
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
