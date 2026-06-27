package com.example.data

import android.content.Context
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class AnimeRepository private constructor(context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val profileDao = db.userProfileDao()
    private val commentDao = db.commentDao()
    private val historyDao = db.watchHistoryDao()
    private val downloadDao = db.offlineDownloadDao()
    private val codeDao = db.premiumGiftCodeDao()
    private val blockedDao = db.blockedUserDao()

    private val firebaseAuth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    // Logged-in user state
    private val _currentUser = MutableStateFlow<UserProfile?>(null)
    val currentUser: StateFlow<UserProfile?> = _currentUser.asStateFlow()

    // Level calculation based on EXP (up to level 1000)
    private fun calculateLevel(exp: Int): Int {
        if (exp < 100) return 1
        var level = 1
        var cumulativeExp = 0
        while (level < 1000) {
            val expForNextLevel = 100 * level * level
            if (exp < (cumulativeExp + expForNextLevel)) break
            cumulativeExp += expForNextLevel
            level++
        }
        return level
    }

    init {
        // Restore session + sync fresh data from Firebase Auth
        firebaseAuth.currentUser?.let { firebaseUser ->
            repositoryScope.launch {
                val email = firebaseUser.email ?: return@launch
                val freshName = firebaseUser.displayName ?: email.substringBefore("@")
                val freshPhoto = firebaseUser.photoUrl?.toString() ?: ""
                var profile = profileDao.getProfileSync(email)
                if (profile != null) {
                    // Update displayName & photoUrl from latest Google account
                    val updated = profile.copy(
                        displayName = freshName,
                        photoUrl = freshPhoto.ifEmpty { profile.photoUrl },
                    )
                    profileDao.updateProfile(updated)
                    _currentUser.value = updated
                } else {
                    // Create profile from Firebase data
                    loginWithGoogle(email, freshName, freshPhoto)
                }
            }
        }

        // Buat admin default jika belum ada
        repositoryScope.launch {
            val admins = listOf(
                Pair("rayx445@gmail.com", "Ray (Main Admin)"),
                Pair("niparsia433@gmail.com", "Niparsia (Developer Admin)")
            )
            admins.forEach { (adminEmail, displayName) ->
                val existingAdmin = profileDao.getProfileSync(adminEmail)
                if (existingAdmin == null) {
                    profileDao.insertProfile(
                        UserProfile(
                            email = adminEmail,
                            displayName = displayName,
                            photoUrl = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80",
                            isPremium = true,
                            premiumExpiration = Long.MAX_VALUE
                        )
                    )
                }
            }
        }
    }

    /**
     * Simpan / sinkron profil user ke Room setelah Firebase Auth sukses.
     * Bisa juga dipanggil langsung jika sudah punya data dari Firebase user object.
     */
    suspend fun loginWithGoogle(email: String, displayName: String, photoUrl: String): Boolean {
        // Cek apakah user diblokir
        val isBlocked = blockedDao.getBlockedUser(email) != null
        if (isBlocked) return false

        var profile = profileDao.getProfileSync(email)
        if (profile == null) {
            profile = UserProfile(
                email = email,
                displayName = displayName,
                photoUrl = photoUrl.ifEmpty {
                    "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=100&q=80"
                },
                isPremium = email == "rayx445@gmail.com" || email == "niparsia433@gmail.com",
                premiumExpiration = if (email == "rayx445@gmail.com") Long.MAX_VALUE else 0L,
                level = 1,
                exp = 0
            )
            profileDao.insertProfile(profile)
            syncProfileToFirestore(profile)
        } else {
            syncProfileFromFirestore(email)
        }
        _currentUser.value = profile
        return true
    }

    // Real Firebase Google Auth login
    suspend fun loginWithFirebaseGoogle(idToken: String): Boolean {
        return try {
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            val authResult = firebaseAuth.signInWithCredential(credential).await()
            val firebaseUser = authResult.user ?: return false

            val email = firebaseUser.email ?: return false
            val displayName = firebaseUser.displayName ?: email.substringBefore("@")
            val photoUrl = firebaseUser.photoUrl?.toString()
                ?: "https://images.unsplash.com/photo-1472099645785-5658abf4ff4e?w=100&q=80"

            loginWithGoogle(email, displayName, photoUrl)
        } catch (e: Exception) {
            false
        }
    }

    // Firestore sync functions
    private suspend fun syncProfileToFirestore(profile: UserProfile) {
        try {
            val userProfileMap = mapOf(
                "email" to profile.email,
                "displayName" to profile.displayName,
                "photoUrl" to profile.photoUrl,
                "joinDate" to profile.joinDate,
                "watchDurationMinutes" to profile.watchDurationMinutes,
                "commentCount" to profile.commentCount,
                "isPremium" to profile.isPremium,
                "premiumExpiration" to profile.premiumExpiration,
                "watchingList" to profile.watchingList,
                "exp" to profile.exp,
                "lastAdWatchTime" to profile.lastAdWatchTime,
                "level" to profile.level
            )
            firestore.collection("users").document(profile.email)
                .set(userProfileMap)
                .await()
        } catch (_: Exception) {
            // Silent fail for sync errors
        }
    }

    private suspend fun syncProfileFromFirestore(email: String) {
        try {
            val doc = firestore.collection("users").document(email).get().await()
            if (doc.exists()) {
                val firestoreProfile = doc.data
                val localProfile = profileDao.getProfileSync(email)
                if (localProfile != null && firestoreProfile != null) {
                    // Merge data - prefer Firestore for premium status
                    val mergedProfile = localProfile.copy(
                        isPremium = firestoreProfile["isPremium"] as? Boolean ?: localProfile.isPremium,
                        premiumExpiration = (firestoreProfile["premiumExpiration"] as? Long) ?: localProfile.premiumExpiration,
                        exp = (firestoreProfile["exp"] as? Long)?.toInt() ?: localProfile.exp,
                        level = (firestoreProfile["level"] as? Long)?.toInt() ?: localProfile.level
                    )
                    profileDao.updateProfile(mergedProfile)
                }
            }
        } catch (_: Exception) {
            // Silent fail for sync errors
        }
    }

    fun logout() {
        firebaseAuth.signOut()
        _currentUser.value = null
    }

    fun getCurrentFirebaseUser(): com.google.firebase.auth.FirebaseUser? = firebaseAuth.currentUser

    // Profiles
    fun getUserProfile(email: String): Flow<UserProfile?> {
        return profileDao.getProfile(email)
    }

    suspend fun getProfileSync(email: String): UserProfile? {
        return profileDao.getProfileSync(email)
    }

    suspend fun updateProfile(profile: UserProfile) {

        // Recalculate level based on EXP
        val newLevel = calculateLevel(profile.exp)
        val updatedProfile = profile.copy(level = newLevel)
        
        profileDao.updateProfile(updatedProfile)
        if (_currentUser.value?.email == profile.email) {
            _currentUser.value = updatedProfile
        }
        // Sync to Firestore
        syncProfileToFirestore(updatedProfile)
    }

    // Watch History & Progress Sync
    fun getWatchHistory(email: String): Flow<List<WatchHistory>> {
        return historyDao.getHistoryForUser(email)
    }

    suspend fun addWatchMinutesAndSave(email: String, minutes: Long) {
        val user = profileDao.getProfileSync(email) ?: return
        val updatedProfile = user.copy(
            watchDurationMinutes = user.watchDurationMinutes + minutes
        )
        updateProfile(updatedProfile)
    }


    suspend fun saveWatchHistory(
        animeId: String,
        animeTitle: String,
        animeImage: String,
        episodeNumber: String,
        progressPercent: Float,
        progressSeconds: Long,
        totalSeconds: Long
    ) {
        val user = _currentUser.value ?: return
        val id = "${user.email}_${animeId}_$episodeNumber"
        val history = WatchHistory(
            id = id,
            userEmail = user.email,
            animeId = animeId,
            animeTitle = animeTitle,
            animeImage = animeImage,
            episodeNumber = episodeNumber,
            progressPercent = progressPercent,
            progressSeconds = progressSeconds,
            totalSeconds = totalSeconds
        )
        historyDao.insertHistory(history)

        val additionalMinutes = 1
        val updatedProfile = user.copy(
            watchDurationMinutes = user.watchDurationMinutes + additionalMinutes,
            watchingList = if (user.watchingList.contains(animeId)) user.watchingList else {
                if (user.watchingList.isEmpty()) animeId else "${user.watchingList},$animeId"
            }
        )
        updateProfile(updatedProfile)
    }

    // Comments
    fun getEpisodeComments(animeId: String, episodeNumber: String): Flow<List<Comment>> {
        return commentDao.getComments(animeId, episodeNumber)
    }

    fun getAllComments(): Flow<List<Comment>> {
        return commentDao.getAllComments()
    }

    fun getCommentsForUser(email: String): Flow<List<Comment>> {
        return commentDao.getAllComments().map { comments ->
            comments.filter { it.userEmail == email }
        }
    }

    suspend fun addComment(animeId: String, animeTitle: String, episodeNumber: String, content: String) {
        val user = _currentUser.value ?: return

        val isBlocked = blockedDao.getBlockedUser(user.email) != null
        if (isBlocked) return

        val comment = Comment(
            animeId = animeId,
            animeTitle = animeTitle,
            episodeNumber = episodeNumber,
            userEmail = user.email,
            userDisplayName = user.displayName,
            userPhotoUrl = user.photoUrl,
            content = content
        )
        commentDao.insertComment(comment)

        val updatedProfile = user.copy(commentCount = user.commentCount + 1)
        updateProfile(updatedProfile)
    }

    suspend fun editComment(commentId: Int, newContent: String) {
        val user = _currentUser.value ?: return
        commentDao.getAllComments().firstOrNull()?.find { it.id == commentId }?.let { oldComment ->
            if (oldComment.userEmail == user.email || user.email == "rayx445@gmail.com") {
                commentDao.insertComment(oldComment.copy(content = newContent, timestamp = System.currentTimeMillis()))
            }
        }
    }

    suspend fun deleteComment(commentId: Int) {
        commentDao.deleteCommentById(commentId)
    }

    suspend fun reportComment(commentId: Int, reason: String) {
        commentDao.getAllComments().firstOrNull()?.find { it.id == commentId }?.let { oldComment ->
            commentDao.insertComment(oldComment.copy(isReported = true, reportReason = reason))
        }
    }

    suspend fun blockUser(email: String, reason: String) {
        blockedDao.insertBlockedUser(BlockedUser(email, reason))
        if (_currentUser.value?.email == email) {
            _currentUser.value = null
        }
    }

    suspend fun removeBlockedUser(email: String) {
        val blockedUser = blockedDao.getBlockedUser(email)
        if (blockedUser != null) {
            blockedDao.removeBlockedUser(blockedUser)
        }
    }

    fun getBlockedUsers(): Flow<List<BlockedUser>> {
        return blockedDao.getBlockedUsersFlow()
    }

    // Like & Dislike
    suspend fun likeComment(commentId: Int) {
        val user = _currentUser.value ?: return
        commentDao.getAllComments().firstOrNull()?.find { it.id == commentId }?.let { comment ->
            val likedList = comment.likedBy.split(",").filter { it.isNotEmpty() }.toMutableList()
            val dislikedList = comment.dislikedBy.split(",").filter { it.isNotEmpty() }.toMutableList()

            if (likedList.contains(user.email)) {
                likedList.remove(user.email)
            } else {
                likedList.add(user.email)
                dislikedList.remove(user.email)
            }

            commentDao.insertComment(
                comment.copy(
                    likes = likedList.size,
                    dislikes = dislikedList.size,
                    likedBy = likedList.joinToString(","),
                    dislikedBy = dislikedList.joinToString(",")
                )
            )
        }
    }

    suspend fun dislikeComment(commentId: Int) {
        val user = _currentUser.value ?: return
        commentDao.getAllComments().firstOrNull()?.find { it.id == commentId }?.let { comment ->
            val likedList = comment.likedBy.split(",").filter { it.isNotEmpty() }.toMutableList()
            val dislikedList = comment.dislikedBy.split(",").filter { it.isNotEmpty() }.toMutableList()

            if (dislikedList.contains(user.email)) {
                dislikedList.remove(user.email)
            } else {
                dislikedList.add(user.email)
                likedList.remove(user.email)
            }

            commentDao.insertComment(
                comment.copy(
                    likes = likedList.size,
                    dislikes = dislikedList.size,
                    likedBy = likedList.joinToString(","),
                    dislikedBy = dislikedList.joinToString(",")
                )
            )
        }
    }

    // Downloads
    fun getDownloads(): Flow<List<OfflineDownload>> {
        return downloadDao.getAllDownloads()
    }

    suspend fun startDownload(animeId: String, animeTitle: String, animeImage: String, episodeNumber: String, videoUrl: String) {
        val id = "${animeId}_$episodeNumber"
        val download = OfflineDownload(
            id = id,
            animeId = animeId,
            animeTitle = animeTitle,
            animeImage = animeImage,
            episodeNumber = episodeNumber,
            videoUrl = videoUrl,
            filePath = "/storage/emulated/0/Download/NihonHua_${animeId}_ep$episodeNumber.mp4",
            downloadProgress = 0.1f,
            fileSize = "180 MB"
        )
        downloadDao.insertDownload(download)

        repositoryScope.launch {
            kotlinx.coroutines.delay(2000)
            downloadDao.insertDownload(download.copy(downloadProgress = 0.5f))
            kotlinx.coroutines.delay(2000)
            downloadDao.insertDownload(download.copy(downloadProgress = 1.0f))
        }
    }

    suspend fun deleteDownload(id: String) {
        downloadDao.deleteDownload(id)
    }

    // Premium Codes
    fun getAllPremiumCodes(): Flow<List<PremiumGiftCode>> {
        return codeDao.getAllCodesFlow()
    }

    suspend fun generatePremiumCode(code: String, premiumType: String, maxClaims: Int) {
        val giftCode = PremiumGiftCode(
            code = code.uppercase().trim(),
            premiumType = premiumType,
            maxClaims = maxClaims,
            claimCount = 0,
            claimedBy = ""
        )
        codeDao.insertCode(giftCode)
    }

    suspend fun claimPremiumCode(codeStr: String): String {
        val user = _currentUser.value ?: return "Harus login terlebih dahulu!"
        val codeObj = codeDao.getCode(codeStr.uppercase().trim()) ?: return "Kode premium tidak valid!"

        if (codeObj.claimCount >= codeObj.maxClaims) {
            return "Kode ini sudah mencapai batas penukaran!"
        }

        val claimList = codeObj.claimedBy.split(",").filter { it.isNotEmpty() }
        if (claimList.contains(user.email)) {
            return "Anda sudah menukarkan kode ini!"
        }

        val addedTimeMs = when (codeObj.premiumType) {
            "1_DAY" -> 24L * 60 * 60 * 1000
            "5_DAYS" -> 5L * 24 * 60 * 60 * 1000
            "30_DAYS" -> 30L * 24 * 60 * 60 * 1000
            "1_YEAR" -> 365L * 24 * 60 * 60 * 1000
            else -> 0L
        }

        if (addedTimeMs == 0L) return "Tipe premium tidak valid!"

        val currentExp = if (user.isPremium && user.premiumExpiration > System.currentTimeMillis()) {
            user.premiumExpiration
        } else {
            System.currentTimeMillis()
        }

        val newExp = currentExp + addedTimeMs
        val updatedProfile = user.copy(isPremium = true, premiumExpiration = newExp)
        updateProfile(updatedProfile)

        val newClaimedBy = if (codeObj.claimedBy.isEmpty()) user.email else "${codeObj.claimedBy},${user.email}"
        codeDao.updateCode(
            codeObj.copy(
                claimCount = codeObj.claimCount + 1,
                claimedBy = newClaimedBy
            )
        )

        val readableType = when (codeObj.premiumType) {
            "1_DAY" -> "1 Hari"
            "5_DAYS" -> "5 Hari"
            "30_DAYS" -> "30 Hari"
            "1_YEAR" -> "1 Tahun"
            else -> codeObj.premiumType
        }
        return "Berhasil klaim Premium $readableType! Berlaku hingga ${
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(newExp))
        }"
    }

    suspend fun purchasePremium(tier: String): String {
        val user = _currentUser.value ?: return "Harus login terlebih dahulu!"
        val addedTimeMs = when (tier) {
            "1_DAY" -> 24L * 60 * 60 * 1000
            "5_DAYS" -> 5L * 24 * 60 * 60 * 1000
            "30_DAYS" -> 30L * 24 * 60 * 60 * 1000
            "1_YEAR" -> 365L * 24 * 60 * 60 * 1000
            else -> 0L
        }

        val currentExp = if (user.isPremium && user.premiumExpiration > System.currentTimeMillis()) {
            user.premiumExpiration
        } else {
            System.currentTimeMillis()
        }

        val newExp = currentExp + addedTimeMs
        val updatedProfile = user.copy(isPremium = true, premiumExpiration = newExp)
        updateProfile(updatedProfile)
        return "Berhasil membeli paket premium! Berlaku hingga ${
            java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault()).format(java.util.Date(newExp))
        }"
    }

    companion object {
        @Volatile
        private var INSTANCE: AnimeRepository? = null

        fun getInstance(context: Context): AnimeRepository {
            return INSTANCE ?: synchronized(this) {
                val instance = AnimeRepository(context)
                INSTANCE = instance
                instance
            }
        }
    }
}
