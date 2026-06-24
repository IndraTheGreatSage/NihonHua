package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        UserProfile::class,
        Comment::class,
        WatchHistory::class,
        OfflineDownload::class,
        PremiumGiftCode::class,
        BlockedUser::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun commentDao(): CommentDao
    abstract fun watchHistoryDao(): WatchHistoryDao
    abstract fun offlineDownloadDao(): OfflineDownloadDao
    abstract fun premiumGiftCodeDao(): PremiumGiftCodeDao
    abstract fun blockedUserDao(): BlockedUserDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "anidong_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
