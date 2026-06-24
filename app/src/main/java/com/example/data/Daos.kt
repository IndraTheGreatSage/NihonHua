package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles WHERE email = :email")
    fun getProfile(email: String): Flow<UserProfile?>

    @Query("SELECT * FROM user_profiles WHERE email = :email")
    suspend fun getProfileSync(email: String): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfile)

    @Update
    suspend fun updateProfile(profile: UserProfile)
}

@Dao
interface CommentDao {
    @Query("SELECT * FROM comments WHERE animeId = :animeId AND episodeNumber = :episodeNumber ORDER BY timestamp DESC")
    fun getComments(animeId: String, episodeNumber: String): Flow<List<Comment>>

    @Query("SELECT * FROM comments ORDER BY timestamp DESC")
    fun getAllComments(): Flow<List<Comment>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertComment(comment: Comment)

    @Update
    suspend fun updateComment(comment: Comment)

    @Delete
    suspend fun deleteComment(comment: Comment)

    @Query("DELETE FROM comments WHERE id = :commentId")
    suspend fun deleteCommentById(commentId: Int)

    @Query("SELECT COUNT(*) FROM comments WHERE userEmail = :email")
    suspend fun getCommentCountForUser(email: String): Int
}

@Dao
interface WatchHistoryDao {
    @Query("SELECT * FROM watch_history WHERE userEmail = :email ORDER BY timestamp DESC")
    fun getHistoryForUser(email: String): Flow<List<WatchHistory>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertHistory(history: WatchHistory)

    @Query("DELETE FROM watch_history WHERE id = :id")
    suspend fun deleteHistory(id: String)
}

@Dao
interface OfflineDownloadDao {
    @Query("SELECT * FROM offline_downloads ORDER BY timestamp DESC")
    fun getAllDownloads(): Flow<List<OfflineDownload>>

    @Query("SELECT * FROM offline_downloads WHERE id = :id")
    suspend fun getDownload(id: String): OfflineDownload?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(download: OfflineDownload)

    @Query("DELETE FROM offline_downloads WHERE id = :id")
    suspend fun deleteDownload(id: String)
}

@Dao
interface PremiumGiftCodeDao {
    @Query("SELECT * FROM premium_gift_codes WHERE code = :code")
    suspend fun getCode(code: String): PremiumGiftCode?

    @Query("SELECT * FROM premium_gift_codes")
    fun getAllCodesFlow(): Flow<List<PremiumGiftCode>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCode(code: PremiumGiftCode)

    @Update
    suspend fun updateCode(code: PremiumGiftCode)
}

@Dao
interface BlockedUserDao {
    @Query("SELECT * FROM blocked_users")
    fun getBlockedUsersFlow(): Flow<List<BlockedUser>>

    @Query("SELECT * FROM blocked_users WHERE email = :email")
    suspend fun getBlockedUser(email: String): BlockedUser?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBlockedUser(user: BlockedUser)

    @Delete
    suspend fun removeBlockedUser(user: BlockedUser)
}
