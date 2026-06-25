package com.example.ui

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AnimeRepository.getInstance(application)

    // Current logged-in user
    val currentUser: StateFlow<UserProfile?> = repo.currentUser

    // All available shows (ongoing + local + scraped)
    private val _shows = MutableStateFlow<List<AnimeVideo>>(emptyList())
    val shows: StateFlow<List<AnimeVideo>> = _shows.asStateFlow()

    // Loading status
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Selected Anime Detail
    private val _selectedAnime = MutableStateFlow<AnimeVideo?>(null)
    val selectedAnime: StateFlow<AnimeVideo?> = _selectedAnime.asStateFlow()

    // Selected Episode and quality
    private val _selectedEpisode = MutableStateFlow<Episode?>(null)
    val selectedEpisode: StateFlow<Episode?> = _selectedEpisode.asStateFlow()

    private val _selectedQuality = MutableStateFlow("1080p")
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    // Comments for selected episode
    val currentComments: StateFlow<List<Comment>> = _selectedAnime
        .combine(_selectedEpisode) { anime, episode ->
            Pair(anime?.id, episode?.episodeNumber)
        }
        .flatMapLatest { (animeId, epNum) ->
            if (animeId != null && epNum != null) {
                repo.getEpisodeComments(animeId, epNum)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Watch History
    val watchHistory: StateFlow<List<WatchHistory>> = currentUser
        .flatMapLatest { user ->
            if (user != null) {
                repo.getWatchHistory(user.email)
            } else {
                flowOf(emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Downloads
    val downloads: StateFlow<List<OfflineDownload>> = repo.getDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Dark Mode Theme State
    private val _isDarkMode = MutableStateFlow(true) // Estetis Dark Mode default
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    // Notification Simulation List
    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    // Admin Panel States (Only loaded/subscribed if admin is active)
    val allComments: StateFlow<List<Comment>> = repo.getAllComments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPremiumCodes: StateFlow<List<PremiumGiftCode>> = repo.getAllPremiumCodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedUsers: StateFlow<List<BlockedUser>> = repo.getBlockedUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Active Profile inspect popup
    private val _inspectedProfile = MutableStateFlow<UserProfile?>(null)
    val inspectedProfile: StateFlow<UserProfile?> = _inspectedProfile.asStateFlow()

    init {
        loadLatestShows()
        generateInitialNotifications()
    }

    fun loadLatestShows() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Try to fetch from backend API first
                val apiData = ApiService.fetchAllAnime()
                if (apiData.isNotEmpty()) {
                    _shows.value = apiData
                } else {
                    // Fallback to local scraper if API returns empty
                    val scraped = AnichinScraper.getLatestShows()
                    _shows.value = scraped
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Final fallback to local data
                _shows.value = AnichinScraper.LOCAL_ANIMES
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectAnime(anime: AnimeVideo) {
        _selectedAnime.value = anime
        _selectedEpisode.value = anime.episodes.firstOrNull()
    }

    fun closeActiveWatchScreen() {
        _selectedAnime.value = null
        _selectedEpisode.value = null
    }

    fun selectEpisode(episode: Episode) {
        _selectedEpisode.value = episode
    }

    fun changeQuality(quality: String) {
        _selectedQuality.value = quality
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    // Real Google Auth with Firebase
    fun signInWithGoogle(context: android.content.Context) {
        viewModelScope.launch {
            try {
                // TODO: Implement actual Firebase Auth
                // For now, fallback to manual login dialog
                // This will be replaced with real Firebase Auth implementation
                Toast.makeText(context, "Firebase Auth belum dikonfigurasi. Gunakan manual login.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Google Login Non-simulated (direct register and session sync)
    fun handleGoogleLogin(email: String, displayName: String, photoUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repo.loginWithGoogle(email, displayName, photoUrl)
            if (success) {
                onResult(true, "Selamat datang, $displayName!")
            } else {
                onResult(false, "Akun Anda telah diblokir karena melanggar aturan komunitas!")
            }
        }
    }

    fun handleLogout() {
        repo.logout()
    }

    // Save history progress
    fun saveProgress(animeId: String, animeTitle: String, animeImage: String, episodeNumber: String, progressPercent: Float, progressSeconds: Long, totalSeconds: Long) {
        viewModelScope.launch {
            repo.saveWatchHistory(animeId, animeTitle, animeImage, episodeNumber, progressPercent, progressSeconds, totalSeconds)
        }
    }

    // Ad System
    private val _shouldShowAd = MutableStateFlow(false)
    val shouldShowAd: StateFlow<Boolean> = _shouldShowAd.asStateFlow()

    private val _adReward = MutableStateFlow(0)
    val adReward: StateFlow<Int> = _adReward.asStateFlow()

    fun checkAdRequirement(): Boolean {
        val user = currentUser.value ?: return false
        if (user.isPremium) return false // Premium users don't see ads

        val fortyMinutesMs = 40 * 60 * 1000L
        val timeSinceLastAd = System.currentTimeMillis() - user.lastAdWatchTime
        return timeSinceLastAd >= fortyMinutesMs
    }

    fun triggerAd() {
        _shouldShowAd.value = true
    }

    fun onAdWatched() {
        viewModelScope.launch {
            val user = currentUser.value ?: return@launch
            val expReward = 50 // 50 EXP for watching ad
            val updatedProfile = user.copy(
                exp = user.exp + expReward,
                lastAdWatchTime = System.currentTimeMillis()
            )
            repo.updateProfile(updatedProfile)
            _adReward.value = expReward
            _shouldShowAd.value = false
        }
    }

    fun dismissAd() {
        _shouldShowAd.value = false
    }

    // Comments Actions
    fun postComment(animeId: String, animeTitle: String, episodeNumber: String, content: String) {
        viewModelScope.launch {
            repo.addComment(animeId, animeTitle, episodeNumber, content)
        }
    }

    fun updateComment(commentId: Int, content: String) {
        viewModelScope.launch {
            repo.editComment(commentId, content)
        }
    }

    fun deleteComment(commentId: Int) {
        viewModelScope.launch {
            repo.deleteComment(commentId)
        }
    }

    fun reportComment(commentId: Int, reason: String) {
        viewModelScope.launch {
            repo.reportComment(commentId, reason)
        }
    }

    fun likeComment(commentId: Int) {
        viewModelScope.launch {
            repo.likeComment(commentId)
        }
    }

    fun dislikeComment(commentId: Int) {
        viewModelScope.launch {
            repo.dislikeComment(commentId)
        }
    }

    // Downloads
    fun downloadEpisode(anime: AnimeVideo, episode: Episode) {
        viewModelScope.launch {
            repo.startDownload(anime.id, anime.title, anime.imageUrl, episode.episodeNumber, episode.videoUrl)
        }
    }

    fun deleteDownloadedEpisode(downloadId: String) {
        viewModelScope.launch {
            repo.deleteDownload(downloadId)
        }
    }

    // Profile Inspection popup
    fun inspectUserProfile(email: String) {
        viewModelScope.launch {
            _inspectedProfile.value = repo.getProfileSync(email)
        }
    }

    fun clearInspectedProfile() {
        _inspectedProfile.value = null
    }

    // Premium Subscription Purchasing
    fun purchasePremium(tier: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = repo.purchasePremium(tier)
            onResult(msg)
        }
    }

    // Update general user profile
    fun updateUserProfile(displayName: String, photoUrl: String, onResult: (Boolean, String) -> Unit) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(displayName = displayName, photoUrl = photoUrl)
            repo.updateProfile(updated)
            onResult(true, "Profil berhasil diperbarui!")
        }
    }

    // Gift Codes Code Claiming
    fun redeemGiftCode(code: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = repo.claimPremiumCode(code)
            onResult(msg)
        }
    }

    // Admin panel specific calls
    private fun isUserAdmin(email: String): Boolean {
        return email == "rayx445@gmail.com" || email == "niparsia433@gmail.com"
    }

    fun adminUpdateProfile(displayName: String, photoUrl: String) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch {
            repo.updateProfile(admin.copy(displayName = displayName, photoUrl = photoUrl))
        }
    }

    fun adminDeleteComment(commentId: Int) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch {
            repo.deleteComment(commentId)
        }
    }

    fun adminGeneratePremiumCode(code: String, premiumType: String, maxClaims: Int, onResult: (String) -> Unit) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) {
            onResult("Hanya admin yang bisa membuat code premium!")
            return
        }
        viewModelScope.launch {
            repo.generatePremiumCode(code, premiumType, maxClaims)
            onResult("Kode premium $code berhasil digenerate!")
        }
    }

    fun adminBlockUser(email: String, reason: String) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch {
            repo.blockUser(email, reason)
        }
    }

    fun adminUnblockUser(email: String) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch {
            repo.removeBlockedUser(email)
        }
    }

    fun adminUpdateUserLevelAndExp(email: String, level: Int, exp: Int) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch {
            repo.adminUpdateUserLevelAndExp(email, level, exp)
        }
    }

    private fun generateInitialNotifications() {
        _notifications.value = listOf(
            AppNotification(1, "Selamat Datang!", "Nikmati streaming anime dan donghua kualitas 4K.", System.currentTimeMillis()),
            AppNotification(2, "Update Premium", "Paket 30 hari sekarang diskon 20%!", System.currentTimeMillis() - 86400000)
        )
    }

    fun shareReviewToSocialMedia(animeTitle: String, rating: String, review: String, onResult: (String) -> Unit) {
        // Mock social sharing
        onResult("Berhasil dibagikan!")
    }
}
