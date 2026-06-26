package com.example.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import android.util.Base64
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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

    private val _isResolvingEpisode = MutableStateFlow(false)
    val isResolvingEpisode: StateFlow<Boolean> = _isResolvingEpisode.asStateFlow()

    private val _selectedQuality = MutableStateFlow("1080p")
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

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

    // State Google Sign-In (ID Token dari Activity dikirim ke sini)
    private val _googleSignInError = MutableStateFlow<String?>(null)
    val googleSignInError: StateFlow<String?> = _googleSignInError.asStateFlow()

    // Ad Management for non-premium users
    private val _shouldShowAd = MutableStateFlow(false)
    val shouldShowAd: StateFlow<Boolean> = _shouldShowAd.asStateFlow()

    private var lastAdTime: Long = 0
    private val adInterval: Long = 300000 // 5 minutes

    init {
        loadLatestShows()
        generateInitialNotifications()
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
        val user = currentUser.value ?: return
        viewModelScope.launch {
            repo.addWatchMinutesAndSave(user.email, 60L)
        }
    }

    fun dismissAd() {
        _shouldShowAd.value = false
    }

    fun loadLatestShows() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Try to fetch from backend API first
                val apiShows = ApiService.fetchAllAnime()
                if (apiShows.isNotEmpty()) {
                    _shows.value = apiShows
                } else {
                    // Fallback to local scraper if API returns empty
                    val scraped = AnichinScraper.getLatestShows()
                    _shows.value = scraped
                }
            } catch (e: Exception) {
                // Fallback to local scraper on error
                try {
                    val scraped = AnichinScraper.getLatestShows()
                    _shows.value = scraped
                } catch (e2: Exception) {
                    _shows.value = emptyList()
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectAnime(anime: AnimeVideo) {
        _selectedAnime.value = anime
        anime.episodes.firstOrNull()?.let { selectEpisode(it) } ?: run {
            _selectedEpisode.value = null
        }
        if (anime.episodes.isEmpty()) {
            viewModelScope.launch {
                _isLoading.value = true
                try {
                    val detail = ApiService.fetchAnimeDetail(anime.id)
                    if (detail != null) {
                        _selectedAnime.value = detail
                        detail.episodes.firstOrNull()?.let { selectEpisode(it) } ?: run {
                            _selectedEpisode.value = null
                        }
                        _shows.value = _shows.value.map { if (it.id == detail.id) detail else it }
                    }
                } finally {
                    _isLoading.value = false
                }
            }
        }
    }

    fun closeActiveWatchScreen() {
        _selectedAnime.value = null
        _selectedEpisode.value = null
    }

    fun selectEpisode(episode: Episode) {
        _selectedEpisode.value = episode
        if (episode.videoUrl.startsWith("http", ignoreCase = true)) {
            viewModelScope.launch {
                _isResolvingEpisode.value = true
                try {
                    val resolvedUrl = ApiService.resolveEpisodeVideoUrl(episode.videoUrl)
                    if (resolvedUrl.isNotBlank() && resolvedUrl != episode.videoUrl) {
                        _selectedEpisode.value = episode.copy(videoUrl = resolvedUrl)
                    }
                } finally {
                    _isResolvingEpisode.value = false
                }
            }
        }
    }

    fun changeQuality(quality: String) {
        _selectedQuality.value = quality
    }

    fun toggleDarkMode() {
        _isDarkMode.value = !_isDarkMode.value
    }

    /**
     * Dipanggil dari Activity setelah Google Sign-In berhasil mendapat idToken.
     * Gunakan GoogleSignIn.getSignedInAccountFromIntent() di Activity,
     * lalu kirim account.idToken ke sini.
     */
    fun handleFirebaseGoogleLogin(idToken: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repo.loginWithFirebaseGoogle(idToken)
            if (success) {
                val user = repo.currentUser.value
                onResult(true, "Selamat datang, ${user?.displayName ?: ""}!")
                // Load anime data after successful login
                loadLatestShows()
            } else {
                onResult(false, "Akun Anda telah diblokir atau terjadi kesalahan autentikasi.")
            }
        }
    }

    // Real Google Sign-In with Firebase
    fun signInWithGoogle(context: android.content.Context) {
        viewModelScope.launch {
            try {
                val googleSignInOptions = com.google.android.gms.auth.api.signin.GoogleSignInOptions.Builder(
                    com.google.android.gms.auth.api.signin.GoogleSignInOptions.DEFAULT_SIGN_IN
                )
                    .requestIdToken("295876523736-8rj2t9m8q7k5l4n3p1o2i3u4y5t6r7e8.apps.googleusercontent.com")
                    .requestEmail()
                    .build()

                val googleSignInClient = com.google.android.gms.auth.api.signin.GoogleSignIn.getClient(context, googleSignInOptions)
                val signInIntent = googleSignInClient.signInIntent
                
                // This needs to be handled in Activity with startActivityForResult
                // For now, show a message to use the manual fallback
                android.widget.Toast.makeText(
                    context,
                    "Google Sign-In requires Activity integration. Use manual login for now.",
                    android.widget.Toast.LENGTH_LONG
                ).show()
            } catch (e: Exception) {
                android.widget.Toast.makeText(
                    context,
                    "Error: ${e.message}",
                    android.widget.Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    /**
     * Fallback: login manual via email (untuk development / akun custom).
     * Di production, sebaiknya dihapus dan hanya pakai Firebase.
     */
    fun handleGoogleLogin(email: String, displayName: String, photoUrl: String, onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val success = repo.loginWithGoogle(email, displayName, photoUrl)
            if (success) {
                onResult(true, "Selamat datang, $displayName!")
                // Load anime data after successful login
                loadLatestShows()
            } else {
                onResult(false, "Akun Anda telah diblokir karena melanggar aturan komunitas!")
            }
        }
    }

    fun handleLogout(context: android.content.Context) {
        repo.logout(context)
    }

    fun saveProgress(
        animeId: String, animeTitle: String, animeImage: String,
        episodeNumber: String, progressPercent: Float,
        progressSeconds: Long, totalSeconds: Long
    ) {
        viewModelScope.launch {
            repo.saveWatchHistory(animeId, animeTitle, animeImage, episodeNumber, progressPercent, progressSeconds, totalSeconds)
        }
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

    /**
     * Mengambil gambar dari URI galeri, mengkonversinya ke Base64,
     * lalu menyimpannya ke profil user sebagai data URI (mendukung GIF!).
     */
    fun updateUserProfileFromGallery(displayName: String, imageUri: Uri, onResult: (Boolean, String) -> Unit) {
        val user = currentUser.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>().applicationContext
                val contentResolver = context.contentResolver
                val mimeType = contentResolver.getType(imageUri) ?: "image/jpeg"
                val inputStream = contentResolver.openInputStream(imageUri)
                val bytes = inputStream?.readBytes()
                inputStream?.close()
                if (bytes == null || bytes.isEmpty()) {
                    withContext(Dispatchers.Main) { onResult(false, "Gagal membaca gambar!") }
                    return@launch
                }
                val base64 = Base64.encodeToString(bytes, Base64.DEFAULT)
                val dataUri = "data:$mimeType;base64,$base64"
                val updated = user.copy(displayName = displayName, photoUrl = dataUri)
                repo.updateProfile(updated)
                withContext(Dispatchers.Main) { onResult(true, "Foto profil berhasil diperbarui!") }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { onResult(false, "Error: ${e.message}") }
            }
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

    private val _isAppModded = MutableStateFlow(false)
    val isAppModded: StateFlow<Boolean> = _isAppModded.asStateFlow()

    private val _isAppOutdated = MutableStateFlow(false)
    val isAppOutdated: StateFlow<Boolean> = _isAppOutdated.asStateFlow()

    fun setAppModded(modded: Boolean) { _isAppModded.value = modded }
    fun setAppOutdated(outdated: Boolean) { _isAppOutdated.value = outdated }

    fun shareReviewToSocialMedia(animeTitle: String, rating: String, reviewText: String, onShareCompleted: (String) -> Unit) {
        val shareMsg = "Menonton $animeTitle dengan Rating $rating/10 di NihonHua! Ulasan: \"$reviewText\" #NihonHua #Anime #Donghua"
        onShareCompleted(shareMsg)
    }

    private fun generateInitialNotifications() {
        _notifications.value = listOf(
            AppNotification(1, "Rilis Episode Baru", "Episode terbaru dari sumber utama sudah masuk. Cek daftar rilis hari ini.", System.currentTimeMillis() - 3600000),
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
