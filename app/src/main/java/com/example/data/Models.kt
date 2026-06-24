package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfile(
    @PrimaryKey val email: String,
    val displayName: String,
    val photoUrl: String,
    val joinDate: Long = System.currentTimeMillis(),
    val watchDurationMinutes: Long = 0,
    val commentCount: Int = 0,
    val isPremium: Boolean = false,
    val premiumExpiration: Long = 0L,
    val watchingList: String = "" // Comma-separated anime IDs
)

@Entity(tableName = "comments")
data class Comment(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val animeId: String,
    val animeTitle: String,
    val episodeNumber: String,
    val userEmail: String,
    val userDisplayName: String,
    val userPhotoUrl: String,
    val content: String,
    val timestamp: Long = System.currentTimeMillis(),
    val likes: Int = 0,
    val dislikes: Int = 0,
    val likedBy: String = "", // Comma-separated user emails
    val dislikedBy: String = "", // Comma-separated user emails
    val isReported: Boolean = false,
    val reportReason: String = ""
)

@Entity(tableName = "watch_history")
data class WatchHistory(
    @PrimaryKey val id: String, // format: "userEmail_animeId_episodeNumber"
    val userEmail: String,
    val animeId: String,
    val animeTitle: String,
    val animeImage: String,
    val episodeNumber: String,
    val progressPercent: Float,
    val progressSeconds: Long,
    val totalSeconds: Long,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "offline_downloads")
data class OfflineDownload(
    @PrimaryKey val id: String, // format: "animeId_episodeNumber"
    val animeId: String,
    val animeTitle: String,
    val animeImage: String,
    val episodeNumber: String,
    val videoUrl: String,
    val filePath: String,
    val downloadProgress: Float = 0f, // 1.0f when finished
    val fileSize: String = "0 MB",
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "premium_gift_codes")
data class PremiumGiftCode(
    @PrimaryKey val code: String,
    val premiumType: String, // "1_DAY", "5_DAYS", "30_DAYS", "1_YEAR"
    val maxClaims: Int,
    val claimCount: Int = 0,
    val claimedBy: String = "" // Comma-separated emails
)

@Entity(tableName = "blocked_users")
data class BlockedUser(
    @PrimaryKey val email: String,
    val reason: String,
    val blockedAt: Long = System.currentTimeMillis()
)

// Standard Anime/Donghua data class used across UI
data class AnimeVideo(
    val id: String,
    val title: String,
    val type: String, // "Anime" or "Donghua"
    val imageUrl: String,
    val description: String,
    val rating: String,
    val status: String, // "Ongoing" or "Completed"
    val genres: List<String>,
    val releaseYear: String,
    val studio: String = "Anichin Studios",
    val episodeCount: Int = 12,
    val episodes: List<Episode> = emptyList()
)

data class Episode(
    val id: String,
    val animeId: String,
    val title: String,
    val episodeNumber: String,
    val videoUrl: String,
    val releaseDate: String = "Baru Saja"
)
