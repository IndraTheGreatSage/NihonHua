package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

object ApiService {
    private const val TAG = "ApiService"
    private val BASE_URL = ApiConfig.baseUrl

    private val client: OkHttpClient by lazy {
        val loggingInterceptor = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BODY
        }
        OkHttpClient.Builder()
            .addInterceptor(loggingInterceptor)
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }

    suspend fun fetchAllAnime(): List<AnimeVideo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/scrape")
                .build()

            val response     = client.newCall(request).execute()
            val responseBody = response.body?.string()

            if (response.isSuccessful && responseBody != null) {
                Log.d(TAG, "fetchAllAnime response length: ${responseBody.length}")
                parseAnimeList(responseBody)
            } else {
                Log.e(TAG, "API Error: ${response.code}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            AnichinScraper.getLatestShows()
        }
    }

    suspend fun fetchAnimeOnly(): List<AnimeVideo> = withContext(Dispatchers.IO) {
        try {
            val request  = Request.Builder().url("$BASE_URL/api/anime").build()
            val response = client.newCall(request).execute()
            val body     = response.body?.string()
            if (response.isSuccessful && body != null) parseAnimeList(body) else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchDonghuaOnly(): List<AnimeVideo> = withContext(Dispatchers.IO) {
        try {
            val request  = Request.Builder().url("$BASE_URL/api/donghua").build()
            val response = client.newCall(request).execute()
            val body     = response.body?.string()
            if (response.isSuccessful && body != null) parseAnimeList(body) else emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            emptyList()
        }
    }

    suspend fun searchAnime(query: String, genre: String = "", year: String = ""): List<AnimeVideo> =
        withContext(Dispatchers.IO) {
            try {
                val url = buildString {
                    append("$BASE_URL/api/search")
                    if (query.isNotEmpty()) append("?q=${URLEncoder.encode(query, "UTF-8")}")
                    if (genre.isNotEmpty()) append("${if (contains('?')) '&' else '?'}genre=$genre")
                    if (year.isNotEmpty())  append("${if (contains('?')) '&' else '?'}year=$year")
                }
                val request  = Request.Builder().url(url).build()
                val response = client.newCall(request).execute()
                val body     = response.body?.string()
                if (response.isSuccessful && body != null) parseAnimeList(body) else emptyList()
            } catch (e: Exception) {
                Log.e(TAG, "Network Error: ${e.message}")
                AnichinScraper.searchShows(query, genre, year)
            }
        }

    /**
     * Fetch detailed anime info including episode list.
     * Server returns: { "success": true, "data": { ...AnimeVideo... } }
     * The old parseAnime() tried to parse the top-level object → always failed.
     * Fixed: unwrap "data" field before parsing.
     */
    suspend fun fetchAnimeDetail(animeId: String): AnimeVideo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/anime/$animeId")
                .build()

            val response = client.newCall(request).execute()
            val body     = response.body?.string()

            if (response.isSuccessful && body != null) {
                parseAnimeDetail(body)
            } else {
                Log.e(TAG, "fetchAnimeDetail error: ${response.code}")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            null
        }
    }

    suspend fun resolveEpisodeVideoUrl(pageUrl: String): String = withContext(Dispatchers.IO) {
        try {
            val encodedUrl = URLEncoder.encode(pageUrl, "UTF-8")
            val request    = Request.Builder()
                .url("$BASE_URL/api/resolve-video?url=$encodedUrl")
                .build()

            val response = client.newCall(request).execute()
            val body     = response.body?.string()
            if (response.isSuccessful && body != null) {
                JSONObject(body).optString("video_url", pageUrl).ifBlank { pageUrl }
            } else {
                pageUrl
            }
        } catch (e: Exception) {
            pageUrl
        }
    }

    // ─── Parsers ─────────────────────────────────────────────────────────────

    /**
     * Parse a list response: { "success": true, "data": [ ...AnimeVideo... ] }
     * Also deduplicates by (normalized) title to prevent same show appearing twice.
     */
    private fun parseAnimeList(json: String): List<AnimeVideo> {
        return try {
            val root      = JSONObject(json)
            val dataArray = root.optJSONArray("data") ?: JSONArray()

            val animeList  = mutableListOf<AnimeVideo>()
            val seenTitles = mutableSetOf<String>() // title-based dedup

            for (i in 0 until dataArray.length()) {
                try {
                    val animeObj = dataArray.getJSONObject(i)
                    val anime    = parseAnimeFromJson(animeObj)
                    val titleKey = anime.title.lowercase().replace(Regex("[^a-z0-9]"), "")
                    if (titleKey.isNotEmpty() && seenTitles.add(titleKey)) {
                        animeList.add(anime)
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Skipped item $i: ${e.message}")
                }
            }
            animeList
        } catch (e: Exception) {
            Log.e(TAG, "parseAnimeList error: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parse a detail response from GET /api/anime/:id
     * Server wraps the object: { "success": true, "data": { ...AnimeVideo... } }
     * This function unwraps the "data" field before passing to parseAnimeFromJson.
     */
    private fun parseAnimeDetail(json: String): AnimeVideo? {
        return try {
            val root = JSONObject(json)

            // Unwrap "data" field if present (detail endpoint wraps in { success, data })
            val animeObj = when {
                root.has("data") && !root.isNull("data") -> {
                    val d = root.opt("data")
                    if (d is JSONObject) d else root
                }
                else -> root
            }

            parseAnimeFromJson(animeObj)
        } catch (e: Exception) {
            Log.e(TAG, "parseAnimeDetail error: ${e.message}")
            null
        }
    }

    private fun parseAnimeFromJson(obj: JSONObject): AnimeVideo {
        val episodesArray = obj.optJSONArray("episodes") ?: JSONArray()
        val episodes      = mutableListOf<Episode>()

        for (i in 0 until episodesArray.length()) {
            try {
                val epObj = episodesArray.getJSONObject(i)
                episodes.add(
                    Episode(
                        id            = epObj.optString("id", "ep_$i"),
                        animeId       = epObj.optString("anime_id", obj.optString("id", "")),
                        title         = epObj.optString("title", "Episode ${i + 1}"),
                        episodeNumber = epObj.optString("episode_number", "${i + 1}"),
                        videoUrl      = epObj.optString("video_url", ""),
                        releaseDate   = epObj.optString("release_date", "Baru Saja")
                    )
                )
            } catch (e: Exception) {
                Log.w(TAG, "Skipped episode $i: ${e.message}")
            }
        }

        // Filter noise episodes and deduplicate by episode number
        val uniqueEpisodes = episodes
            .filterNot { episode ->
                val title  = episode.title.trim().lowercase()
                val number = episode.episodeNumber.trim()
                title in setOf("home", "homepage", "beranda", "donghua", "anime", "download", "batch")
                    || number.isBlank()
                    || number.toFloatOrNull() == null
            }
            .distinctBy { ep ->
                ep.episodeNumber.filter { c -> c.isDigit() || c == '.' }.ifBlank { ep.id }
            }
            .sortedWith(
                compareBy<Episode> { it.episodeNumber.toFloatOrNull() ?: Float.MAX_VALUE }
                    .thenBy { it.episodeNumber }
            )

        val genresArray = obj.optJSONArray("genres") ?: JSONArray()
        val genres      = mutableListOf<String>()
        for (i in 0 until genresArray.length()) {
            genres.add(genresArray.getString(i))
        }

        return AnimeVideo(
            id           = obj.getString("id"),
            title        = obj.getString("title"),
            type         = obj.optString("type", "Anime"),
            imageUrl     = obj.optString("image_url", ""),
            description  = obj.optString("description", ""),
            rating       = obj.optString("rating", "8.0"),
            status       = obj.optString("status", "Ongoing"),
            genres       = genres,
            releaseYear  = obj.optString("release_year", "2024"),
            studio       = obj.optString("studio", "Unknown"),
            episodeCount = obj.optInt("episode_count", uniqueEpisodes.size),
            episodes     = uniqueEpisodes
        )
    }
}