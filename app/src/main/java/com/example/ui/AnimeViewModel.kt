package com.example.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class AnimeViewModel(application: Application) : AndroidViewModel(application) {
    private val repo = AnimeRepository.getInstance(application)

    val currentUser: StateFlow<UserProfile?> = repo.currentUser

    private val _shows = MutableStateFlow<List<AnimeVideo>>(emptyList())
    val shows: StateFlow<List<AnimeVideo>> = _shows.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _selectedAnime = MutableStateFlow<AnimeVideo?>(null)
    val selectedAnime: StateFlow<AnimeVideo?> = _selectedAnime.asStateFlow()

    private val _selectedEpisode = MutableStateFlow<Episode?>(null)
    val selectedEpisode: StateFlow<Episode?> = _selectedEpisode.asStateFlow()

    private val _selectedQuality = MutableStateFlow("1080p")
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    private val _isResolvingEpisode = MutableStateFlow(false)
    val isResolvingEpisode: StateFlow<Boolean> = _isResolvingEpisode.asStateFlow()

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

    val watchHistory: StateFlow<List<WatchHistory>> = currentUser
        .flatMapLatest { user ->
            if (user != null) repo.getWatchHistory(user.email) else flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun getAllCommentsForUser(email: String): StateFlow<List<Comment>> {
        return repo.getCommentsForUser(email)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    val downloads: StateFlow<List<OfflineDownload>> = repo.getDownloads()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isDarkMode = MutableStateFlow(true)
    val isDarkMode: StateFlow<Boolean> = _isDarkMode.asStateFlow()

    private val _notifications = MutableStateFlow<List<AppNotification>>(emptyList())
    val notifications: StateFlow<List<AppNotification>> = _notifications.asStateFlow()

    val allComments: StateFlow<List<Comment>> = repo.getAllComments()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPremiumCodes: StateFlow<List<PremiumGiftCode>> = repo.getAllPremiumCodes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val blockedUsers: StateFlow<List<BlockedUser>> = repo.getBlockedUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _inspectedProfile = MutableStateFlow<UserProfile?>(null)
    val inspectedProfile: StateFlow<UserProfile?> = _inspectedProfile.asStateFlow()

    // Ad Management for non-premium users
    private val _shouldShowAd = MutableStateFlow(false)
    val shouldShowAd: StateFlow<Boolean> = _shouldShowAd.asStateFlow()

    private var lastAdTime: Long = 0
    private val adInterval: Long = 300000 // 5 minutes

    init {
        loadLatestShows()
        generateInitialNotifications()
    }

    // ── FIX: dedup shows by id, dedup episodes by episodeNumber ──────────────
    private fun deduplicateShows(shows: List<AnimeVideo>): List<AnimeVideo> {
        return shows.distinctBy { it.id }.map { anime ->
            anime.copy(
                episodes = anime.episodes.distinctBy { it.episodeNumber }
            )
        }
    }

    fun checkAdRequirement(): Boolean {
        val user = currentUser.value
        if (user?.isPremium == true) return false
        val currentTime = System.currentTimeMillis()
        return (currentTime - lastAdTime) > adInterval
    }

    fun triggerAd() {
        _shouldShowAd.value = true
    }

    fun onAdWatched() {
        _shouldShowAd.value = false
        lastAdTime = System.currentTimeMillis()
    }

    fun dismissAd() {
        _shouldShowAd.value = false
    }

    fun loadLatestShows() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val apiShows = ApiService.fetchAllAnime()
                if (apiShows.isNotEmpty()) {
                    _shows.value = deduplicateShows(apiShows)  // FIX: dedup applied
                } else {
                    val scraped = AnichinScraper.getLatestShows()
                    _shows.value = deduplicateShows(scraped)   // FIX: dedup applied
                }
            } catch (_: Exception) {
                try {
                    val scraped = AnichinScraper.getLatestShows()
                    _shows.value = deduplicateShows(scraped)   // FIX: dedup applied
                } catch (_: Exception) {
                    _shows.value = emptyList()
                }
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

    fun handleFirebaseGoogleLogin(idToken: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repo.loginWithFirebaseGoogle(idToken)
            if (success) {
                val user = repo.currentUser.value
                onResult(true, "Selamat datang, ${user?.displayName ?: ""}!")
                loadLatestShows()
            } else {
                onResult(false, "Akun Anda telah diblokir atau terjadi kesalahan autentikasi.")
            }
        }
    }

    fun handleGoogleLogin(email: String, displayName: String, photoUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repo.loginWithGoogle(email, displayName, photoUrl)
            if (success) {
                onResult(true, "Selamat datang, $displayName!")
                loadLatestShows()
            } else {
                onResult(false, "Akun Anda telah diblokir karena melanggar aturan komunitas!")
            }
        }
    }

    fun handleLogout() {
        repo.logout()
    }

    fun postComment(animeId: String, animeTitle: String, episodeNumber: String, content: String) {
        viewModelScope.launch { repo.addComment(animeId, animeTitle, episodeNumber, content) }
    }

    fun updateComment(commentId: Int, content: String) {
        viewModelScope.launch { repo.editComment(commentId, content) }
    }

    fun deleteComment(commentId: Int) {
        viewModelScope.launch { repo.deleteComment(commentId) }
    }

    fun reportComment(commentId: Int, reason: String) {
        viewModelScope.launch { repo.reportComment(commentId, reason) }
    }

    fun likeComment(commentId: Int) {
        viewModelScope.launch { repo.likeComment(commentId) }
    }

    fun dislikeComment(commentId: Int) {
        viewModelScope.launch { repo.dislikeComment(commentId) }
    }

    fun downloadEpisode(anime: AnimeVideo, episode: Episode) {
        viewModelScope.launch {
            repo.startDownload(anime.id, anime.title, anime.imageUrl, episode.episodeNumber, episode.videoUrl)
        }
    }

    fun deleteDownloadedEpisode(downloadId: String) {
        viewModelScope.launch { repo.deleteDownload(downloadId) }
    }

    fun inspectUserProfile(email: String) {
        viewModelScope.launch {
            _inspectedProfile.value = repo.getProfileSync(email)
        }
    }

    fun clearInspectedProfile() {
        _inspectedProfile.value = null
    }

    fun purchasePremium(tier: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = repo.purchasePremium(tier)
            onResult(msg)
        }
    }

    fun updateUserProfile(displayName: String, photoUrl: String, onResult: (Boolean, String) -> Unit) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(displayName = displayName, photoUrl = photoUrl)
            repo.updateProfile(updated)
            onResult(true, "Profil berhasil diperbarui!")
        }
    }

    fun updateUserProfileFromGallery(displayName: String, imageUri: String, onResult: (Boolean, String) -> Unit) {
        val user = currentUser.value ?: return
        viewModelScope.launch {
            val updated = user.copy(displayName = displayName, photoUrl = imageUri)
            repo.updateProfile(updated)
            onResult(true, "Profil berhasil diperbarui!")
        }
    }

    fun redeemGiftCode(code: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val msg = repo.claimPremiumCode(code)
            onResult(msg)
        }
    }

    private fun isUserAdmin(email: String) =
        email == "rayx445@gmail.com" || email == "niparsia433@gmail.com"

    fun adminUpdateProfile(displayName: String, photoUrl: String) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch {
            repo.updateProfile(admin.copy(displayName = displayName, photoUrl = photoUrl))
        }
    }

    fun adminUpdateUserLevelAndExp(email: String, level: Int, exp: Int) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch {
            val user = repo.getProfileSync(email)
            if (user != null) {
                repo.updateProfile(user.copy(level = level, exp = exp))
            }
        }
    }

    fun adminDeleteComment(commentId: Int) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch { repo.deleteComment(commentId) }
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
        viewModelScope.launch { repo.blockUser(email, reason) }
    }

    fun adminUnblockUser(email: String) {
        val admin = currentUser.value ?: return
        if (!isUserAdmin(admin.email)) return
        viewModelScope.launch { repo.removeBlockedUser(email) }
    }

    fun shareReviewToSocialMedia(animeTitle: String, rating: String, reviewText: String, onShareCompleted: (String) -> Unit) {
        val shareMsg = "Menonton $animeTitle dengan Rating $rating/10 di NihonHua! Ulasan: \"$reviewText\" #NihonHua #Anime #Donghua"
        onShareCompleted(shareMsg)
    }

    private fun generateInitialNotifications() {
        _notifications.value = listOf(
            AppNotification(1, "Rilis Episode Baru", "Battle Through The Heavens S5 Episode 101 telah rilis! Nonton sekarang dengan grafis 4K.", System.currentTimeMillis() - 3600000),
            AppNotification(2, "Rilis Episode Baru", "Renegade Immortal Episode 38 sudah tayang. Ikuti perjalanan kejam Wang Lin!", System.currentTimeMillis() - 7200000),
            AppNotification(3, "Komunitas Aktif", "Seseorang menyukai komentar Anda di Perfect World Episode 165.", System.currentTimeMillis() - 14400000)
        )
    }
}

data class AppNotification(
    val id: Int,
    val title: String,
    val message: String,
    val timestamp: Long
)