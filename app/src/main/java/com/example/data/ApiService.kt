package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.logging.HttpLoggingInterceptor
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object ApiService {
    private const val TAG = "ApiService"
    private const val BASE_URL = ApiConfig.baseUrl // from ApiConfig.kt
    
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
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                Log.d(TAG, "API Response: $responseBody")
                parseAnimeList(responseBody)
            } else {
                Log.e(TAG, "API Error: ${response.code}")
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            // Fallback to local scraper if API fails
            AnichinScraper.getLatestShows()
        }
    }
    
    suspend fun fetchAnimeOnly(): List<AnimeVideo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/anime")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                parseAnimeList(responseBody)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun fetchDonghuaOnly(): List<AnimeVideo> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/donghua")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                parseAnimeList(responseBody)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            emptyList()
        }
    }
    
    suspend fun searchAnime(query: String, genre: String = "", year: String = ""): List<AnimeVideo> = withContext(Dispatchers.IO) {
        try {
            val url = StringBuilder("$BASE_URL/api/search")
            if (query.isNotEmpty()) url.append("?q=$query")
            if (genre.isNotEmpty()) url.append("&genre=$genre")
            if (year.isNotEmpty()) url.append("&year=$year")
            
            val request = Request.Builder()
                .url(url.toString())
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                parseAnimeList(responseBody)
            } else {
                emptyList()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            AnichinScraper.searchShows(query, genre, year)
        }
    }
    
    suspend fun fetchAnimeDetail(animeId: String): AnimeVideo? = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder()
                .url("$BASE_URL/api/anime/$animeId")
                .build()
            
            val response = client.newCall(request).execute()
            val responseBody = response.body?.string()
            
            if (response.isSuccessful && responseBody != null) {
                parseAnime(responseBody)
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Network Error: ${e.message}")
            null
        }
    }
    
    private fun parseAnimeList(json: String): List<AnimeVideo> {
        val jsonObject = JSONObject(json)
        val dataArray = jsonObject.optJSONArray("data") ?: JSONArray()
        
        val animeList = mutableListOf<AnimeVideo>()
        for (i in 0 until dataArray.length()) {
            val animeObj = dataArray.getJSONObject(i)
            animeList.add(parseAnimeFromJson(animeObj))
        }
        return animeList
    }
    
    private fun parseAnime(json: String): AnimeVideo? {
        val jsonObject = JSONObject(json)
        return parseAnimeFromJson(jsonObject)
    }
    
    private fun parseAnimeFromJson(obj: JSONObject): AnimeVideo {
        val episodesArray = obj.optJSONArray("episodes") ?: JSONArray()
        val episodes = mutableListOf<Episode>()
        
        for (i in 0 until episodesArray.length()) {
            val epObj = episodesArray.getJSONObject(i)
            episodes.add(
                Episode(
                    id = epObj.getString("id"),
                    animeId = epObj.getString("anime_id"),
                    title = epObj.getString("title"),
                    episodeNumber = epObj.getString("episode_number"),
                    videoUrl = epObj.getString("video_url"),
                    releaseDate = epObj.optString("release_date", "Baru Saja")
                )
            )
        }
        
        val genresArray = obj.optJSONArray("genres") ?: JSONArray()
        val genres = mutableListOf<String>()
        for (i in 0 until genresArray.length()) {
            genres.add(genresArray.getString(i))
        }
        
        return AnimeVideo(
            id = obj.getString("id"),
            title = obj.getString("title"),
            type = obj.getString("type"),
            imageUrl = obj.getString("image_url"),
            description = obj.getString("description"),
            rating = obj.getString("rating"),
            status = obj.getString("status"),
            genres = genres,
            releaseYear = obj.getString("release_year"),
            studio = obj.optString("studio", "Unknown"),
            episodeCount = obj.optInt("episode_count", 12),
            episodes = episodes
        )
    }
    
    fun setBaseUrl(url: String) {
        // You can call this to change the base URL at runtime
        // For example, when user enters their Pterodactyl server URL
    }
}
