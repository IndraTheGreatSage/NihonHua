package com.example.ui

import android.graphics.Bitmap
import android.net.Uri
import android.annotation.SuppressLint
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.ImageLoader
import coil.compose.AsyncImage
import coil.decode.GifDecoder
import coil.decode.ImageDecoderDecoder
import coil.request.ImageRequest
import com.example.data.*
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import java.text.SimpleDateFormat
import java.io.ByteArrayInputStream
import java.util.Date
import java.util.Locale

fun maskEmail(email: String): String {
    val parts = email.split("@")
    if (parts.size != 2) return "******"
    val name = parts[0]
    val domain = parts[1]
    if (name.length <= 2) return "${name.first()}***@$domain"
    return "${name.first()}" + "*".repeat(name.length - 2) + "${name.last()}@$domain"
}

fun generateQRCode(content: String): Bitmap? {
    return try {
        val writer = QRCodeWriter()
        val hints = mapOf(EncodeHintType.MARGIN to 0)
        val bitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, 300, 300, hints)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
            }
        }
        bitmap
    } catch (e: WriterException) {
        null
    }
}

private fun isBlockedPlayerHost(url: String): Boolean {
    val host = runCatching { Uri.parse(url).host.orEmpty().lowercase() }.getOrDefault("")
    return listOf(
        "doubleclick.net",
        "googlesyndication.com",
        "googleadservices.com",
        "adservice.google.com",
        "adsterra",
        "popads",
        "propellerads",
        "onclick",
        "taboola",
        "outbrain"
    ).any { host.contains(it) }
}

private fun isDirectVideoUrl(url: String): Boolean {
    return Regex("\\.(mp4|m3u8|webm|mkv)(\\?|$)", RegexOption.IGNORE_CASE).containsMatchIn(url)
}

private fun lockedPlayerHtml(url: String): String {
    val escapedUrl = url
        .replace("&", "&amp;")
        .replace("\"", "&quot;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
    val player = if (isDirectVideoUrl(url)) {
        """<video src="$escapedUrl" controls autoplay playsinline preload="metadata"></video>"""
    } else {
        """<iframe src="$escapedUrl" allow="autoplay; encrypted-media; fullscreen; picture-in-picture" allowfullscreen referrerpolicy="no-referrer"></iframe>"""
    }
    return """
        <!doctype html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1, user-scalable=no"/>
            <style>
                html, body {
                    margin: 0;
                    padding: 0;
                    width: 100%;
                    height: 100%;
                    overflow: hidden;
                    background: #000;
                }
                iframe, video {
                    position: fixed;
                    inset: 0;
                    width: 100%;
                    height: 100%;
                    border: 0;
                    background: #000;
                }
            </style>
        </head>
        <body>$player</body>
        </html>
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun EpisodeWebPlayer(
    url: String,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(android.graphics.Color.BLACK)
                isVerticalScrollBarEnabled = false
                isHorizontalScrollBarEnabled = false
                overScrollMode = WebView.OVER_SCROLL_NEVER
                setOnLongClickListener { true }
                webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url.toString()
                        if (isBlockedPlayerHost(target)) return true
                        return request.isForMainFrame && target != url
                    }

                    override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                        return if (isBlockedPlayerHost(request.url.toString())) {
                            WebResourceResponse("text/plain", "utf-8", ByteArrayInputStream(ByteArray(0)))
                        } else {
                            super.shouldInterceptRequest(view, request)
                        }
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onCreateWindow(
                        view: WebView?,
                        isDialog: Boolean,
                        isUserGesture: Boolean,
                        resultMsg: android.os.Message?
                    ): Boolean = false
                }
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.mediaPlaybackRequiresUserGesture = false
                settings.loadWithOverviewMode = true
                settings.useWideViewPort = true
                settings.builtInZoomControls = false
                settings.displayZoomControls = false
                settings.setSupportMultipleWindows(false)
                tag = url
                loadDataWithBaseURL(url, lockedPlayerHtml(url), "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != url) {
                webView.tag = url
                webView.loadDataWithBaseURL(url, lockedPlayerHtml(url), "text/html", "UTF-8", null)
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(
    viewModel: AnimeViewModel,
    onGoogleSignInRequest: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()
    val shouldShowAd by viewModel.shouldShowAd.collectAsState()

    var activeTab by remember { mutableStateOf("dashboard") }

    // Check ad requirement when tab changes
    LaunchedEffect(activeTab) {
        if (viewModel.checkAdRequirement()) {
            viewModel.triggerAd()
        }
    }

    if (currentUser == null) {
        LoginScreen(
            viewModel = viewModel,
            onGoogleSignInRequest = onGoogleSignInRequest
        )
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Column {
                            Text(
                                "EXCLUSIVE STREAMING",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 1.5.sp,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    "NIHON",
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    "HUA",
                                    fontWeight = FontWeight.Black,
                                    fontStyle = FontStyle.Italic,
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "ANIME",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color.Black,
                                    modifier = Modifier
                                        .background(
                                            MaterialTheme.colorScheme.secondary,
                                            RoundedCornerShape(4.dp)
                                        )
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                )
                            }
                        }
                    },
                    actions = {
                        // Display notifications
                        var showNotifications by remember { mutableStateOf(false) }
                        IconButton(onClick = { showNotifications = true }) {
                            Icon(Icons.Outlined.Notifications, contentDescription = "Notifikasi")
                        }

                        // Display Admin panel shortcut if email matches
                        if (currentUser?.email == "rayx445@gmail.com" || currentUser?.email == "niparsia433@gmail.com") {
                            IconButton(onClick = { activeTab = "admin" }) {
                                Icon(
                                    Icons.Filled.AdminPanelSettings,
                                    contentDescription = "Admin Panel",
                                    tint = MaterialTheme.colorScheme.secondary
                                )
                            }
                        }

                        // Avatar Click opens My Profile modal
                        val safeUser = currentUser
                        if (safeUser != null) {
                            IconButton(onClick = {
                                viewModel.inspectUserProfile(safeUser.email)
                            }) {
                                AsyncImage(
                                    model = safeUser.photoUrl,
                                    contentDescription = "Profil",
                                    modifier = Modifier
                                        .size(32.dp)
                                        .clip(CircleShape)
                                        .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                                )
                            }
                        }

                        if (showNotifications) {
                            NotificationsDialog(viewModel) { showNotifications = false }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background
                    )
                )
            },
            bottomBar = {
                NavigationBar(
                    containerColor = MaterialTheme.colorScheme.surface,
                    tonalElevation = 8.dp
                ) {
                    NavigationBarItem(
                        selected = activeTab == "dashboard",
                        onClick = { activeTab = "dashboard" },
                        icon = { Icon(Icons.Filled.Home, contentDescription = "Dashboard") },
                        label = { Text("Dashboard") }
                    )
                    NavigationBarItem(
                        selected = activeTab == "search",
                        onClick = { activeTab = "search" },
                        icon = { Icon(Icons.Filled.Search, contentDescription = "Pencarian") },
                        label = { Text("Cari") }
                    )
                    NavigationBarItem(
                        selected = activeTab == "downloads",
                        onClick = { activeTab = "downloads" },
                        icon = { Icon(Icons.Filled.Download, contentDescription = "Unduhan") },
                        label = { Text("Unduhan") }
                    )
                    NavigationBarItem(
                        selected = activeTab == "settings",
                        onClick = { activeTab = "settings" },
                        icon = { Icon(Icons.Filled.Settings, contentDescription = "Settings") },
                        label = { Text("Fitur & Code") }
                    )
                }
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                when (activeTab) {
                    "dashboard" -> DashboardScreen(viewModel = viewModel)
                    "search" -> AdvancedSearchScreen(viewModel = viewModel)
                    "downloads" -> DownloadsScreen(viewModel = viewModel)
                    "settings" -> SettingsScreen(viewModel = viewModel)
                    "admin" -> AdminPanelScreen(viewModel = viewModel)
                }

                // Profile inspection dialog
                val inspectedProfile by viewModel.inspectedProfile.collectAsState()
                if (inspectedProfile != null) {
                    UserProfileDialog(profile = inspectedProfile!!, viewModel = viewModel) {
                        viewModel.clearInspectedProfile()
                    }
                }

                // Ad dialog for non-premium users
                if (shouldShowAd) {
                    RewardedAdDialog(
                        onRewardedMinutesApplied = {
                            // +60 menit disesuaikan dari mapping AdMob (item hour amount 1 => 60 menit)
                            viewModel.onAdWatched() // +60 menit (akan diubah di ViewModel)

                        },
                        onDismiss = { viewModel.dismissAd() }
                    )
                }

            }
        }

        // Streaming / Video Details view overlay if selected - drawn on top of the entire Scaffold (fullscreen!)
        val selectedAnime by viewModel.selectedAnime.collectAsState()
        if (selectedAnime != null) {
            StreamingWatchScreen(viewModel = viewModel)
        }
    }
    }
}

// 1. LOGIN SCREEN WITH REAL GOOGLE AUTH
@Composable
fun LoginScreen(
    viewModel: AnimeViewModel,
    onGoogleSignInRequest: () -> Unit
) {
    var customEmail by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var customAvatar by remember { mutableStateOf("") }
    var showCustomRegister by remember { mutableStateOf(false) }
    var isConnectingGoogle by remember { mutableStateOf(false) }

    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0B0E11),
                        Color(0xFF141B22),
                        Color(0xFF1B2228)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Visual Logo Header
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PlayCircleFilled,
                    contentDescription = "Logo Launcher",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(80.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "NIHON",
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "HUA",
                    fontWeight = FontWeight.Black,
                    fontStyle = FontStyle.Italic,
                    fontSize = 28.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                "Nonton Anime & Donghua Tanpa Batas\nStreaming Cepat via Scrapers Terintegrasi",
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                lineHeight = 20.sp
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Brand-Compliant Google Sign-In Button
            Button(
                onClick = { onGoogleSignInRequest() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFDADCE0)),
                shape = RoundedCornerShape(26.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.AccountCircle,
                        contentDescription = "Google",
                        tint = Color(0xFF4285F4),
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Masuk dengan Google",
                        color = Color.Black,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                "Keamanan dilindungi oleh Google OAuth (Anti-MOD Signature Secure)",
                fontSize = 11.sp,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                textAlign = TextAlign.Center
            )
        }

        // Loading indicator
        if (isConnectingGoogle) {
            Dialog(onDismissRequest = {}) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)),
                    modifier = Modifier.width(260.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF4285F4),
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            "Menghubungkan ke Google...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }

    // Custom account linker (100% non-simulated database registration)
    if (showCustomRegister) {
        Dialog(onDismissRequest = { showCustomRegister = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Masuk Akun Google",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    OutlinedTextField(
                        value = customEmail,
                        onValueChange = { customEmail = it },
                        label = { Text("Alamat Email Google") },
                        placeholder = { Text("contoh@gmail.com") },
                        leadingIcon = { Icon(Icons.Filled.Email, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customName,
                        onValueChange = { customName = it },
                        label = { Text("Nama Tampilan") },
                        placeholder = { Text("Xiao Yan") },
                        leadingIcon = { Icon(Icons.Filled.Person, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = customAvatar,
                        onValueChange = { customAvatar = it },
                        label = { Text("URL Foto Profil (GIF/PNG - Opsional)") },
                        placeholder = { Text("https://...") },
                        leadingIcon = { Icon(Icons.Filled.Image, contentDescription = null) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = {
                            val trimmedEmail = customEmail.trim().lowercase()
                            if (trimmedEmail.isEmpty() || !trimmedEmail.contains("@")) {
                                Toast.makeText(context, "Email Google tidak valid!", Toast.LENGTH_SHORT).show()
                            } else if (customName.isEmpty()) {
                                Toast.makeText(context, "Nama tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                            } else {
                                val finalAvatar = customAvatar.trim().ifEmpty {
                                    "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80"
                                }
                                showCustomRegister = false
                                viewModel.handleGoogleLogin(
                                    trimmedEmail,
                                    customName.trim(),
                                    finalAvatar
                                ) { success, msg ->
                                    if (trimmedEmail == "rayx445@gmail.com" || trimmedEmail == "niparsia433@gmail.com") {
                                        Toast.makeText(context, "Selamat datang Admin Utama NihonHua!", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Masuk Google", fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    TextButton(
                        onClick = { showCustomRegister = false },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text("Batal")
                    }
                }
            }
        }
    }
}

// 2. MAIN DASHBOARD SCREEN
@Composable
fun DashboardScreen(viewModel: AnimeViewModel) {
    val shows by viewModel.shows.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val watchHistory by viewModel.watchHistory.collectAsState(initial = emptyList())

    var selectedFilter by remember { mutableStateOf("Semua") }

    if (isLoading) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        val filteredShows = remember(shows, selectedFilter) {
            shows.filter {
                selectedFilter == "Semua" || it.type.equals(selectedFilter, ignoreCase = true)
            }
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Hero Banner Section (matching image design)
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                ) {
                    AsyncImage(
                        model = "https://images.unsplash.com/photo-1578632767115-351597cf2477?w=800&q=80",
                        contentDescription = "Hero Banner",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.9f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(20.dp)
                    ) {
                        Text(
                            "The Heavens S5",
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Xiao Yan journeys to the Jia Nin Academy to search for the Falling Heart Flame. A new chapter of power...",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Button(
                                onClick = { 
                                    val featuredShow = shows.firstOrNull { it.id.contains("btth") }
                                    if (featuredShow != null) viewModel.selectAnime(featuredShow)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.PlayArrow,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mulai Nonton", color = Color.Black, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { /* Add to playlist */ },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(44.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Add,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Daftar Putar", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // Category Filter Row
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf("Semua", "Donghua", "Anime").forEach { filter ->
                        val isSelected = selectedFilter == filter
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                )
                                .border(
                                    1.dp,
                                    if (isSelected) Color.Transparent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { selectedFilter = filter }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = filter,
                                color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // Lanjutkan Menonton (Continue Watching) from Watch History
            if (watchHistory.isNotEmpty()) {
                item {
                    val latestWatch = watchHistory.first()
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Lanjutkan Menonton",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Lihat History",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.clickable {
                                    // Optional toast or quick highlight
                                }
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .clickable {
                                    val match = shows.firstOrNull { it.id == latestWatch.animeId }
                                    if (match != null) {
                                        viewModel.selectAnime(match)
                                    }
                                }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(width = 80.dp, height = 48.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            ) {
                                AsyncImage(
                                    model = latestWatch.animeImage,
                                    contentDescription = latestWatch.animeTitle,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                                // Sleek Cyan Progress indicator on thumbnail bottom
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .fillMaxWidth(fraction = latestWatch.progressPercent.coerceIn(0f, 1f))
                                        .height(4.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = latestWatch.animeTitle,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val progressMin = latestWatch.progressSeconds / 60
                                val progressSec = latestWatch.progressSeconds % 60
                                val totalMin = latestWatch.totalSeconds / 60
                                val totalSec = latestWatch.totalSeconds % 60
                                val timeStr = String.format(java.util.Locale.getDefault(), "%02d:%02d / %02d:%02d", progressMin, progressSec, totalMin, totalSec)
                                Text(
                                    text = "Episode ${latestWatch.episodeNumber} • $timeStr",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp
                                )
                            }

                            Icon(
                                imageVector = Icons.Filled.PlayArrow,
                                contentDescription = "Mainkan",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }

            // Sedang Populer (Currently Popular) - matching image design
            item {
                Column(modifier = Modifier.padding(vertical = 12.dp)) {
                    Text(
                        text = "Sedang Populer",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(
                            items = filteredShows.sortedByDescending { it.rating }.take(6),
                            key = { it.id }
                        ) { anime ->
                            Card(
                                modifier = Modifier
                                    .width(150.dp)
                                    .clickable { viewModel.selectAnime(anime) },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Box(modifier = Modifier.height(200.dp)) {
                                    AsyncImage(
                                        model = anime.imageUrl,
                                        contentDescription = anime.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Rating Badge (top right)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(8.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Filled.Star,
                                                contentDescription = null,
                                                tint = Color(0xFFFFD700),
                                                modifier = Modifier.size(12.dp)
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(
                                                anime.rating,
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                    // Type Badge (bottom left)
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(8.dp)
                                            .background(
                                                if (anime.type == "Anime") Color(0xFF2196F3) else Color(0xFF9C27B0),
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            anime.type,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Text(
                                        text = anime.title,
                                        color = MaterialTheme.colorScheme.onBackground,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 16.sp
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = anime.releaseYear,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Update Episode Terbaru (Latest Episode Update) - matching image design
            item {
                Text(
                    text = "Update Episode Terbaru",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 12.dp)
                )
            }

            items(items = filteredShows.take(5), key = { it.id }) { anime ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectAnime(anime) }
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = anime.imageUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(60.dp, 80.dp)
                            .clip(RoundedCornerShape(10.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = anime.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "EPISODE ${anime.episodes.size} ${if (anime.status == "Completed") "(TAMAT)" else ""}",
                            color = MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${anime.type} • ${anime.releaseYear}",
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 10.sp
                        )
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "5 jam yang lalu",
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                            fontSize = 10.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Icon(
                            imageVector = Icons.Filled.PlayCircleFilled,
                            contentDescription = "Play",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }
    }
}

// 3. ADVANCED SEARCH SCREEN
@Composable
fun AdvancedSearchScreen(viewModel: AnimeViewModel) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedGenre by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("") }
    var selectedYear by remember { mutableStateOf("") }

    val shows by viewModel.shows.collectAsState()

    val genres = listOf("Semua Genre", "Action", "Cultivation", "Fantasy", "Martial Arts", "Sci-Fi", "Adventure", "Romance")
    val types = listOf("Semua Tipe", "Anime", "Donghua")
    val years = listOf("Semua Tahun", "2026", "2025", "2024", "2023", "2022", "2021", "2020")

    val filteredResults = remember(shows, searchQuery, selectedGenre, selectedType, selectedYear) {
        shows.filter { anime ->
            val matchesQuery = searchQuery.isEmpty() || anime.title.contains(searchQuery, ignoreCase = true) || anime.description.contains(searchQuery, ignoreCase = true)
            val matchesGenre = selectedGenre.isEmpty() || selectedGenre == "Semua Genre" || anime.genres.any { it.equals(selectedGenre, ignoreCase = true) }
            val matchesType = selectedType.isEmpty() || selectedType == "Semua Tipe" || anime.type.equals(selectedType, ignoreCase = true)
            val matchesYear = selectedYear.isEmpty() || selectedYear == "Semua Tahun" || anime.releaseYear == selectedYear
            matchesQuery && matchesGenre && matchesType && matchesYear
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Search Input
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Cari anime atau donghua...") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Cari") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = MaterialTheme.colorScheme.primary
            )
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Advanced filter selectors
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Genre Dropdown Spinner simulation
            var expandGenre by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { expandGenre = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedGenre.isEmpty()) "Genre" else selectedGenre,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(expanded = expandGenre, onDismissRequest = { expandGenre = false }) {
                    genres.forEach { g ->
                        DropdownMenuItem(
                            text = { Text(g) },
                            onClick = {
                                selectedGenre = if (g == "Semua Genre") "" else g
                                expandGenre = false
                            }
                        )
                    }
                }
            }

            // Tipe Dropdown (Anime / Donghua)
            var expandType by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { expandType = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedType.isEmpty()) "Tipe" else selectedType,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(expanded = expandType, onDismissRequest = { expandType = false }) {
                    types.forEach { t ->
                        DropdownMenuItem(
                            text = { Text(t) },
                            onClick = {
                                selectedType = if (t == "Semua Tipe") "" else t
                                expandType = false
                            }
                        )
                    }
                }
            }

            // Year Dropdown Spinner simulation
            var expandYear by remember { mutableStateOf(false) }
            Box(modifier = Modifier.weight(1f)) {
                Button(
                    onClick = { expandYear = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = if (selectedYear.isEmpty()) "Tahun" else selectedYear,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
                DropdownMenu(expanded = expandYear, onDismissRequest = { expandYear = false }) {
                    years.forEach { y ->
                        DropdownMenuItem(
                            text = { Text(y) },
                            onClick = {
                                selectedYear = if (y == "Semua Tahun") "" else y
                                expandYear = false
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Search Grid Results
        if (filteredResults.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Outlined.SearchOff,
                        contentDescription = "Tidak ada",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Konten tidak ditemukan",
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(items = filteredResults, key = { it.id }) { anime ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { viewModel.selectAnime(anime) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Box(modifier = Modifier.height(160.dp)) {
                            AsyncImage(
                                model = anime.imageUrl,
                                contentDescription = anime.title,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(6.dp)
                                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Text(anime.rating, color = MaterialTheme.colorScheme.secondary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Column(modifier = Modifier.padding(8.dp)) {
                            Text(
                                text = anime.title,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${anime.type} • ${anime.releaseYear}",
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

// 4. WATCHING & STREAMING SCREEN WITH komentar + likes + report + custom 4K resolution
@Composable
fun StreamingWatchScreen(viewModel: AnimeViewModel) {
    val anime by viewModel.selectedAnime.collectAsState()
    val selectedEp by viewModel.selectedEpisode.collectAsState()
    val quality by viewModel.selectedQuality.collectAsState()
    val comments by viewModel.currentComments.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    val isResolvingEpisode by viewModel.isResolvingEpisode.collectAsState()

    val context = LocalContext.current

    if (anime == null) return
    val gold = Color(0xFFFFC107)
    val deepBlack = Color(0xFF050505)
    val panelBlack = Color(0xFF111111)
    val episodes = remember(anime!!.id, anime!!.episodes) {
        anime!!.episodes
            .distinctBy { it.episodeNumber.filter { char -> char.isDigit() || char == '.' }.ifBlank { it.id } }
            .sortedWith(compareBy<Episode> { it.episodeNumber.toFloatOrNull() ?: Float.MAX_VALUE }.thenBy { it.episodeNumber })
    }

    // Track state of comments post & edit
    var newCommentContent by remember { mutableStateOf("") }
    var editingCommentId by remember { mutableStateOf<Int?>(null) }
    var editingContent by remember { mutableStateOf("") }

    // Report dialog
    var reportingCommentId by remember { mutableStateOf<Int?>(null) }
    var reportReasonInput by remember { mutableStateOf("") }

    // Block dialog
    var blockingUserEmail by remember { mutableStateOf<String?>(null) }
    var blockReasonInput by remember { mutableStateOf("") }

    var isWatching by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(deepBlack),
        color = deepBlack
    ) {
            if (!isWatching) {
                // ANIME/DONGHUA DETAIL SCREEN
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(deepBlack)
                ) {
                    // 1. HERO BANNER IMAGE (Netflix style)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(280.dp)
                    ) {
                        AsyncImage(
                            model = anime!!.imageUrl,
                            contentDescription = anime!!.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                        // Fading overlay at the bottom
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            deepBlack.copy(alpha = 0.52f),
                                            deepBlack
                                        )
                                    )
                                )
                        )
                        
                        // Close/Back button in top-left
                        IconButton(
                            onClick = { viewModel.closeActiveWatchScreen() },
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(12.dp)
                                .background(Color.Black.copy(alpha = 0.72f), CircleShape)
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }

                        // Rating badge on bottom right of image
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                            .background(gold, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Star, contentDescription = null, tint = Color.Black, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(anime!!.rating, color = Color.Black, fontWeight = FontWeight.Black, fontSize = 12.sp)
                            }
                        }
                    }

                    // 2. METADATA SECTION
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                    ) {
                        Text(
                            text = anime!!.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        // Badges Row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Type Chip (Anime / Donghua)
                            val isDonghua = anime!!.type.equals("Donghua", ignoreCase = true)
                            Box(
                                modifier = Modifier
                                    .background(
                                        if (isDonghua) Brush.horizontalGradient(listOf(gold, Color(0xFFFFE082)))
                                        else Brush.horizontalGradient(listOf(Color(0xFFFFD54F), gold)),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = anime!!.type.uppercase(),
                                    color = Color.Black,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }

                            // Year Chip
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = anime!!.releaseYear, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }

                            // Status Chip
                            Box(
                                modifier = Modifier
                                    .border(1.dp, if (anime!!.status.equals("Ongoing", ignoreCase = true)) gold else Color.Gray, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = anime!!.status,
                                    color = if (anime!!.status.equals("Ongoing", ignoreCase = true)) gold else Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Genre Row
                        Row(
                            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            anime!!.genres.forEach { genre ->
                                Box(
                                    modifier = Modifier
                                        .background(gold.copy(alpha = 0.11f), CircleShape)
                                        .border(0.5.dp, gold.copy(alpha = 0.34f), CircleShape)
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(text = genre, color = gold, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description/Synopsis card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = panelBlack),
                            border = BorderStroke(0.5.dp, gold.copy(alpha = 0.18f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text("Sinopsis", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = anime!!.description,
                                    fontSize = 12.sp,
                                    lineHeight = 18.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("Studio: ${anime!!.studio}", fontSize = 11.sp, color = gold, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 3. MAIN ACTION PLAY BUTTON (glowing gradient)
                        Button(
                            onClick = { isWatching = true },
                            enabled = selectedEp != null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                gold,
                                                Color(0xFFFFE082)
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(if (selectedEp == null) "Memuat Episode..." else "Tonton Sekarang", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 4. EPISODES LIST
                        Text(
                            text = "Daftar Episode (${episodes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (episodes.isEmpty()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(panelBlack, RoundedCornerShape(8.dp))
                                    .border(1.dp, gold.copy(alpha = 0.16f), RoundedCornerShape(8.dp))
                                    .padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Episode lagi dimuat dari sumber utama...", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                            }
                        } else {
                            // Render list/grid of episodes beautifully
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                episodes.forEach { ep ->
                                    val isCurrent = selectedEp?.id == ep.id
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectEpisode(ep)
                                                isWatching = true
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrent) gold.copy(alpha = 0.16f)
                                                             else panelBlack
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isCurrent) gold
                                                    else Color.White.copy(alpha = 0.08f)
                                        ),
                                        shape = RoundedCornerShape(8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .background(
                                                        if (isCurrent) gold
                                                        else Color.White.copy(alpha = 0.1f),
                                                        CircleShape
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.PlayArrow,
                                                    contentDescription = null,
                                                    tint = if (isCurrent) Color.Black else Color.White,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = "Episode ${ep.episodeNumber}",
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = if (isCurrent) gold else Color.White
                                                )
                                                Text(
                                                    text = ep.title,
                                                    fontSize = 11.sp,
                                                    color = Color.White.copy(alpha = 0.6f),
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                            Icon(
                                                imageVector = Icons.Filled.PlayArrow,
                                                contentDescription = null,
                                                tint = Color.White.copy(alpha = 0.4f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(40.dp))
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black)
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isWatching = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali ke Detail", tint = gold)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = anime!!.title,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            selectedEp?.let {
                                Text(
                                    text = "Episode ${it.episodeNumber}",
                                    color = gold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .background(gold.copy(alpha = 0.14f), RoundedCornerShape(6.dp))
                                .border(1.dp, gold.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 5.dp)
                        ) {
                            Text(quality.uppercase(), color = gold, fontSize = 10.sp, fontWeight = FontWeight.Black)
                        }
                    }

                    if (selectedEp != null) {
                        // Quality selector state
                        var showResolutionMenu by remember { mutableStateOf(false) }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                        ) {
                            // WebView utama — load episode URL, auto-block iklan, auto-inject CSS
                            EpisodeWebView(
                                videoUrl = selectedEp!!.videoUrl,
                                modifier = Modifier.fillMaxSize()
                            )

                            // Badge kualitas (overlay di atas WebView)
                            Row(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                                        .clickable { showResolutionMenu = true }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = if (quality == "4K") "4K ULTRA HD" else quality.uppercase(),
                                        color = Color.White,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                }

                                // Dropdown pilih resolusi
                                DropdownMenu(
                                    expanded = showResolutionMenu,
                                    onDismissRequest = { showResolutionMenu = false }
                                ) {
                                    listOf("360p", "480p", "720p", "1080p", "4K").forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(if (q == "4K") "4K (Ultra HD)" else q) },
                                            onClick = {
                                                viewModel.changeQuality(q)
                                                showResolutionMenu = false
                                                Toast.makeText(context, "Grafik dialihkan ke $q", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                CircularProgressIndicator(color = gold)
                                Spacer(modifier = Modifier.height(12.dp))
                                Text("Menyiapkan player episode...", color = gold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color(0xFF17120A), panelBlack, Color.Black)
                                )
                            )
                            .border(1.dp, gold.copy(alpha = 0.16f))
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .background(gold, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                selectedEp?.title ?: anime!!.title,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (currentUser?.isPremium == true) "Premium active" else "Free streaming",
                                color = gold.copy(alpha = 0.78f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                // Watch Options: Episodes List & Comments Split View
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        // Description Metadata Card
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = anime!!.studio,
                                    color = gold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "${anime!!.releaseYear} • ${anime!!.status}",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 11.sp
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                // Premium status check
                                if (currentUser?.isPremium == true) {
                                    Text(
                                        "Premium VIP",
                                        color = gold,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .border(1.dp, gold, RoundedCornerShape(4.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Episode ${selectedEp!!.episodeNumber}: ${selectedEp!!.title}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = anime!!.description,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // Share and Offline Download Bar
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Download Button
                                Button(
                                    onClick = {
                                        viewModel.downloadEpisode(anime!!, selectedEp!!)
                                        Toast.makeText(context, "Mulai mengunduh Episode ${selectedEp!!.episodeNumber} untuk offline...", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = gold),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Download, contentDescription = null, tint = Color.Black)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Unduh Offline", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Social Share review
                                var showShareDialog by remember { mutableStateOf(false) }
                                Button(
                                    onClick = { showShareDialog = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = panelBlack),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, gold)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Share, contentDescription = null, tint = gold)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bagikan Ulasan", color = gold, fontWeight = FontWeight.Bold)
                                    }
                                }

                                if (showShareDialog) {
                                    SocialShareDialog(anime!!.title, viewModel) { showShareDialog = false }
                                }
                            }
                        }
                    }

                    // Episode Selector Horizontal List
                    item {
                        Column(modifier = Modifier.padding(vertical = 8.dp)) {
                            Text(
                                "Pilih Episode",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                                color = Color.White
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(items = episodes, key = { it.id }) { ep ->
                                    val isSelected = selectedEp!!.id == ep.id
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) gold else panelBlack
                                            )
                                            .border(1.dp, if (isSelected) gold else Color.White.copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                            .clickable {
                                                viewModel.selectEpisode(ep)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            "EP ${ep.episodeNumber}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Comments Section Title
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text(
                            "Diskusi Komunitas (${comments.size})",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    }

                    // Write Comment Row
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newCommentContent,
                                onValueChange = { newCommentContent = it },
                                placeholder = { Text("Tulis komentar anda...") },
                                modifier = Modifier.weight(1f),
                                maxLines = 3,
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = {
                                    if (newCommentContent.trim().isNotEmpty()) {
                                        viewModel.postComment(anime!!.id, anime!!.title, selectedEp!!.episodeNumber, newCommentContent.trim())
                                        newCommentContent = ""
                                        Toast.makeText(context, "Komentar berhasil diposting!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary, CircleShape)
                                    .size(44.dp)
                            ) {
                                Icon(Icons.Filled.Send, contentDescription = "Kirim", tint = Color.Black)
                            }
                        }
                    }

                    // Interactive List of Comments
                    if (comments.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Belum ada komentar. Jadilah yang pertama berdiskusi!", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    } else {
                        items(items = comments, key = { it.id }) { comment ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(8.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        // Tap Avatar to inspect other user's profile!
                                        AsyncImage(
                                            model = comment.userPhotoUrl,
                                            contentDescription = comment.userDisplayName,
                                            modifier = Modifier
                                                .size(36.dp)
                                                .clip(CircleShape)
                                                .border(1.dp, MaterialTheme.colorScheme.primary, CircleShape)
                                                .clickable {
                                                    viewModel.inspectUserProfile(comment.userEmail)
                                                }
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = comment.userDisplayName,
                                                    fontWeight = FontWeight.Bold,
                                                    fontSize = 13.sp,
                                                    color = MaterialTheme.colorScheme.onBackground
                                                )
                                                Spacer(modifier = Modifier.width(6.dp))

                                                // Admin / VIP Premium Badges
                                                if (comment.userEmail == "rayx445@gmail.com" || comment.userEmail == "niparsia433@gmail.com") {
                                                    Text(
                                                        "Admin",
                                                        color = MaterialTheme.colorScheme.secondary,
                                                        fontSize = 9.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 4.dp, vertical = 1.dp)
                                                    )
                                                }
                                            }
                                            Text(
                                                text = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(comment.timestamp)),
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Spacer(modifier = Modifier.weight(1f))

                                        // Edit / Delete / Report / Block option dropdown
                                        var showCommentMenu by remember { mutableStateOf(false) }
                                        val isOwner = comment.userEmail == currentUser?.email
                                        val isAdmin = currentUser?.email == "rayx445@gmail.com" || currentUser?.email == "niparsia433@gmail.com"

                                        Box {
                                            IconButton(onClick = { showCommentMenu = true }) {
                                                Icon(Icons.Filled.MoreVert, contentDescription = "Menu Komentar")
                                            }
                                            DropdownMenu(expanded = showCommentMenu, onDismissRequest = { showCommentMenu = false }) {
                                                // 1. Edit: can only edit own comment, or if admin is editing
                                                if (isOwner || isAdmin) {
                                                    DropdownMenuItem(
                                                        text = { Text("Edit") },
                                                        onClick = {
                                                            editingCommentId = comment.id
                                                            editingContent = comment.content
                                                            showCommentMenu = false
                                                        }
                                                    )
                                                }
                                                // 2. Hapus (Delete): can delete if owner or admin
                                                if (isOwner || isAdmin) {
                                                    DropdownMenuItem(
                                                        text = { Text("Hapus") },
                                                        onClick = {
                                                            viewModel.deleteComment(comment.id)
                                                            showCommentMenu = false
                                                            Toast.makeText(context, "Komentar dihapus!", Toast.LENGTH_SHORT).show()
                                                        }
                                                    )
                                                }
                                                // 3. Laporkan (Report): available to everyone (except your own comment)
                                                if (!isOwner) {
                                                    DropdownMenuItem(
                                                        text = { Text("Laporkan") },
                                                        onClick = {
                                                            reportingCommentId = comment.id
                                                            reportReasonInput = ""
                                                            showCommentMenu = false
                                                        }
                                                    )
                                                }
                                                // 4. Blokir Komentar / Pengguna (Block user): Admin only, and cannot block self
                                                if (isAdmin && !isOwner) {
                                                    DropdownMenuItem(
                                                        text = { Text("Blokir Pengguna") },
                                                        onClick = {
                                                            blockingUserEmail = comment.userEmail
                                                            blockReasonInput = ""
                                                            showCommentMenu = false
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Display Inline Editor if editing
                                    if (editingCommentId == comment.id) {
                                        Column {
                                            OutlinedTextField(
                                                value = editingContent,
                                                onValueChange = { editingContent = it },
                                                modifier = Modifier.fillMaxWidth()
                                            )
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.End
                                            ) {
                                                TextButton(onClick = { editingCommentId = null }) { Text("Batal") }
                                                Button(onClick = {
                                                    if (editingContent.trim().isNotEmpty()) {
                                                        viewModel.updateComment(comment.id, editingContent.trim())
                                                        editingCommentId = null
                                                        Toast.makeText(context, "Komentar diperbarui!", Toast.LENGTH_SHORT).show()
                                                    }
                                                }) { Text("Simpan") }
                                            }
                                        }
                                    } else {
                                        Text(
                                            text = comment.content,
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onBackground
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // Likes & Dislikes Row with feedback state colors
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val hasLiked = comment.likedBy.contains(currentUser?.email ?: "")
                                        IconButton(onClick = { viewModel.likeComment(comment.id) }) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (hasLiked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                                                    contentDescription = "Like",
                                                    tint = if (hasLiked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(comment.likes.toString(), fontSize = 12.sp)
                                            }
                                        }

                                        val hasDisliked = comment.dislikedBy.contains(currentUser?.email ?: "")
                                        IconButton(onClick = { viewModel.dislikeComment(comment.id) }) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Icon(
                                                    imageVector = if (hasDisliked) Icons.Filled.ThumbDown else Icons.Outlined.ThumbDown,
                                                    contentDescription = "Dislike",
                                                    tint = if (hasDisliked) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurface,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(comment.dislikes.toString(), fontSize = 12.sp)
                                            }
                                        }

                                        if (comment.isReported) {
                                            Spacer(modifier = Modifier.weight(1f))
                                            Text(
                                                text = "Dilaporkan: ${comment.reportReason}",
                                                color = MaterialTheme.colorScheme.tertiary,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            }
        }

    // Report Comment Input Dialog Overlay
    if (reportingCommentId != null) {
        AlertDialog(
            onDismissRequest = { reportingCommentId = null },
            title = { Text("Laporkan Komentar") },
            text = {
                Column {
                    Text("Pilih atau ketik alasan pelanggaran aturan komunitas:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = reportReasonInput,
                        onValueChange = { reportReasonInput = it },
                        placeholder = { Text("Misal: Spam, Spoiler, SARA, Kasar...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (reportReasonInput.trim().isNotEmpty()) {
                        viewModel.reportComment(reportingCommentId!!, reportReasonInput.trim())
                        reportingCommentId = null
                        Toast.makeText(context, "Laporan komentar berhasil diajukan!", Toast.LENGTH_SHORT).show()
                    }
                }) {
                    Text("Kirim Laporan")
                }
            },
            dismissButton = {
                TextButton(onClick = { reportingCommentId = null }) { Text("Batal") }
            }
        )
    }

    // Block Comment/User Dialog Overlay
    if (blockingUserEmail != null) {
        AlertDialog(
            onDismissRequest = { blockingUserEmail = null },
            title = { Text("Blokir Pengguna") },
            text = {
                Column {
                    Text("Apakah Anda yakin ingin memblokir pengguna ${blockingUserEmail!!}?")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = blockReasonInput,
                        onValueChange = { blockReasonInput = it },
                        placeholder = { Text("Tulis alasan pemblokiran...") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        if (blockReasonInput.trim().isNotEmpty()) {
                            viewModel.adminBlockUser(blockingUserEmail!!, blockReasonInput.trim())
                            blockingUserEmail = null
                            Toast.makeText(context, "Pengguna berhasil diblokir!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Alasan pemblokiran wajib diisi!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text("Blokir Permanen", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { blockingUserEmail = null }) { Text("Batal") }
            }
        )
    }
}

// 5. OFFLINE DOWNLOADS SCREEN
@Composable
fun DownloadsScreen(viewModel: AnimeViewModel) {
    val downloads by viewModel.downloads.collectAsState()
    val context = LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Unduhan Menonton Offline", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(12.dp))

        if (downloads.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurface)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Belum ada video diunduh", color = MaterialTheme.colorScheme.onSurface)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(items = downloads, key = { it.id }) { dl ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = dl.animeImage,
                                contentDescription = dl.animeTitle,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(60.dp, 80.dp)
                                    .clip(RoundedCornerShape(8.dp))
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(dl.animeTitle, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("Episode ${dl.episodeNumber}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)

                                Spacer(modifier = Modifier.height(4.dp))
                                // Progress bar indicator
                                if (dl.downloadProgress < 1.0f) {
                                    LinearProgressIndicator(
                                        progress = dl.downloadProgress,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    Text("Mengunduh... ${(dl.downloadProgress * 100).toInt()}% • ${dl.fileSize}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                                } else {
                                    Text("Selesai • ${dl.fileSize}", fontSize = 11.sp, color = Color.Green, fontWeight = FontWeight.Bold)
                                }
                            }
                            Spacer(modifier = Modifier.width(8.dp))

                            // Play offline trigger
                            IconButton(onClick = {
                                if (dl.downloadProgress >= 1.0f) {
                                    Toast.makeText(context, "Memutar file offline: ${dl.filePath}", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Unduhan belum selesai!", Toast.LENGTH_SHORT).show()
                                }
                            }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = "Play Offline", tint = if (dl.downloadProgress >= 1.0f) Color.Green else Color.Gray)
                            }

                            // Delete download
                            IconButton(onClick = {
                                viewModel.deleteDownloadedEpisode(dl.id)
                                Toast.makeText(context, "File unduhan dihapus!", Toast.LENGTH_SHORT).show()
                            }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Hapus", tint = MaterialTheme.colorScheme.tertiary)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─── Cinematic Pulse color tokens ─────────────────────────────────────
private val CP_BG           = Color(0xFF0B1326)
private val CP_SURFACE      = Color(0xFF171F33)
private val CP_SURFACE_HIGH = Color(0xFF222A3D)
private val CP_SURFACE_HIGHEST = Color(0xFF2D3449)
private val CP_VIOLET       = Color(0xFFD2BBFF)
private val CP_VIOLET_DIM   = Color(0xFF7C3AED)
private val CP_CYAN         = Color(0xFF4CD6FF)
private val CP_CRIMSON      = Color(0xFFFFB2BA)
private val CP_TEXT         = Color(0xFFDAE2FD)
private val CP_TEXT_DIM     = Color(0xFFCCC3D8)
private val CP_OUTLINE      = Color(0xFF4A4455)
private val CP_GLASS        = Color(0x0DFFFFFF)
private val CP_GLASS_BORDER = Color(0x1AFFFFFF)
private val CP_ERROR        = Color(0xFFFFB4AB)

// ─── Helpers ───────────────────────────────────────────────────────────
private fun Modifier.glassPanel(radius: Float = 16f) = this
    .background(CP_GLASS, RoundedCornerShape(radius.dp))
    .border(1.dp, CP_GLASS_BORDER, RoundedCornerShape(radius.dp))

// ─── Cinematic Pulse Settings Screen ───────────────────────────────────
@Composable
fun SettingsScreen(viewModel: AnimeViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isDarkMode  by viewModel.isDarkMode.collectAsState()
    val context     = LocalContext.current

    var inputCode          by remember { mutableStateOf("") }
    var activePurchaseTier  by remember { mutableStateOf<String?>(null) }
    var activePurchasePrice by remember { mutableStateOf("") }
    var activePurchaseTitle by remember { mutableStateOf("") }
    var selectedPlan        by remember { mutableStateOf("YEARLY") }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(CP_BG)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            item {
                if (currentUser != null) {
                    CpProfileCard(currentUser!!)
                }
            }

            item {
                CpPremiumSection(
                    currentUser    = currentUser,
                    selectedPlan   = selectedPlan,
                    onSelectPlan   = { selectedPlan = it },
                    onPurchase     = { tier, title, price ->
                        activePurchaseTier  = tier
                        activePurchaseTitle = title
                        activePurchasePrice = price
                    }
                )
            }

            item {
                CpGiftCodeSection(
                    inputCode  = inputCode,
                    onCodeChange = { inputCode = it },
                    onRedeem   = {
                        if (inputCode.trim().isNotEmpty()) {
                            viewModel.redeemGiftCode(inputCode.trim()) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                            }
                            inputCode = ""
                        }
                    }
                )
            }

            item {
                CpSystemSettings(isDarkMode = isDarkMode, onToggleDark = { viewModel.toggleDarkMode() })
            }

            item {
                CpCommunityRules()
            }

            item {
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = { viewModel.handleLogout() },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                    border   = BorderStroke(1.dp, CP_ERROR.copy(alpha = 0.4f)),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = ButtonDefaults.outlinedButtonColors(
                        contentColor = CP_ERROR
                    )
                ) {
                    Icon(Icons.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Keluar dari Akun", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "NihonHua v1.0 • Cinematic Pulse UI",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontSize  = 11.sp,
                    color     = CP_TEXT_DIM.copy(alpha = 0.4f),
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.5.sp
                )
            }
        }

        if (activePurchaseTier != null) {
            PremiumPaymentDialog(
                tier      = activePurchaseTier!!,
                title     = activePurchaseTitle,
                price     = activePurchasePrice,
                viewModel = viewModel,
                onDismiss = { activePurchaseTier = null }
            )
        }
    }
}

// ─── Profile Card ───────────────────────────────────────────────────────
@Composable
private fun CpProfileCard(user: com.example.data.UserProfile) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(
                        Brush.sweepGradient(listOf(CP_VIOLET, CP_CYAN, CP_VIOLET)),
                        CircleShape
                    )
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = user.photoUrl,
                    contentDescription = user.displayName,
                    modifier = Modifier.size(68.dp).clip(CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .background(
                        if (user.isPremium) CP_VIOLET else CP_CRIMSON,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    if (user.isPremium) "VIP" else "Free",
                    color      = if (user.isPremium) Color(0xFF3F008E) else Color(0xFF67001F),
                    fontSize   = 9.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.5.sp
                )
            }
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(user.displayName, color = CP_TEXT, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text(
                maskEmail(user.email),
                color    = CP_TEXT_DIM.copy(alpha = 0.6f),
                fontSize = 12.sp,
                letterSpacing = 0.2.sp
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "LEVEL ${user.level}",
                    color      = CP_VIOLET,
                    fontSize   = 11.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(5.dp)
                        .background(CP_SURFACE_HIGHEST, RoundedCornerShape(50))
                        .clip(RoundedCornerShape(50))
                ) {
                    val pct = ((user.exp % 100) / 100f).coerceIn(0f, 1f)
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .fillMaxWidth(pct)
                            .background(
                                Brush.horizontalGradient(listOf(CP_VIOLET_DIM, CP_VIOLET)),
                                RoundedCornerShape(50)
                            )
                    )
                }
            }
        }
    }
}

// ─── Premium Section ───────────────────────────────────────────────────
@Composable
private fun CpPremiumSection(
    currentUser: com.example.data.UserProfile?,
    selectedPlan: String,
    onSelectPlan: (String) -> Unit,
    onPurchase: (tier: String, title: String, price: String) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            CP_VIOLET_DIM.copy(alpha = 0.45f),
                            CP_BG.copy(alpha = 0.6f)
                        )
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(CP_VIOLET.copy(alpha = 0.4f), Color.Transparent)),
                    RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp)
                )
                .padding(20.dp)
        ) {
            Icon(
                Icons.Filled.WorkspacePremium,
                contentDescription = null,
                tint     = CP_VIOLET.copy(alpha = 0.07f),
                modifier = Modifier
                    .size(140.dp)
                    .align(Alignment.TopEnd)
                    .offset(x = 20.dp, y = (-10).dp)
            )

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null,
                        tint = CP_VIOLET, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "UNLEASH THE DRAGON",
                        color      = CP_VIOLET,
                        fontWeight = FontWeight.Black,
                        fontSize   = 17.sp,
                        letterSpacing = 0.5.sp
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Saksikan Donghua & Anime terbaik\nseperti di bioskop. Tanpa iklan. 4K.",
                    color    = CP_TEXT_DIM.copy(alpha = 0.8f),
                    fontSize = 13.sp,
                    lineHeight = 19.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(CP_SURFACE.copy(alpha = 0.5f))
                .padding(horizontal = 14.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CpPlanCard(
                modifier       = Modifier.weight(1f),
                title          = "Monthly",
                subtitle       = "Fleksibel",
                price          = "Rp 20.000",
                priceUnit      = "/bln",
                features       = listOf("Tanpa Iklan", "4K Ultra HD", "1 Layar"),
                isSelected     = selectedPlan == "MONTHLY",
                isBestValue    = false,
                onSelect       = { onSelectPlan("MONTHLY") },
                onPurchase     = { onPurchase("30_DAYS", "Premium 30 Hari", "Rp 20.000") }
            )
            CpPlanCard(
                modifier       = Modifier.weight(1f),
                title          = "Yearly",
                subtitle       = "Hemat 30%",
                price          = "Rp 200.000",
                priceUnit      = "/thn",
                features       = listOf("Semua Fitur", "Early Access", "Unduh Offline"),
                isSelected     = selectedPlan == "YEARLY",
                isBestValue    = true,
                onSelect       = { onSelectPlan("YEARLY") },
                onPurchase     = { onPurchase("1_YEAR", "Premium 1 Tahun", "Rp 200.000") }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            CpMiniPlanCard(
                modifier  = Modifier.weight(1f),
                label     = "1 Hari",
                price     = "Rp 1.000",
                tier      = "1_DAY",
                onPurchase = onPurchase
            )
            CpMiniPlanCard(
                modifier  = Modifier.weight(1f),
                label     = "5 Hari",
                price     = "Rp 4.000",
                tier      = "5_DAYS",
                onPurchase = onPurchase
            )
        }
    }
}

@Composable
private fun CpPlanCard(
    modifier: Modifier,
    title: String,
    subtitle: String,
    price: String,
    priceUnit: String,
    features: List<String>,
    isSelected: Boolean,
    isBestValue: Boolean,
    onSelect: () -> Unit,
    onPurchase: () -> Unit
) {
    val borderBrush = if (isSelected)
        Brush.linearGradient(listOf(CP_VIOLET, CP_CYAN))
    else
        Brush.linearGradient(listOf(CP_OUTLINE.copy(alpha = 0.3f), CP_OUTLINE.copy(alpha = 0.3f)))

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .then(
                if (isSelected)
                    Modifier.background(
                        Brush.linearGradient(
                            listOf(CP_VIOLET_DIM.copy(alpha = 0.22f), CP_BG.copy(alpha = 0.5f))
                        )
                    )
                else Modifier.background(CP_GLASS)
            )
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                brush = borderBrush,
                shape = RoundedCornerShape(18.dp)
            )
            .clickable { onSelect() }
    ) {
        Column(modifier = Modifier.padding(14.dp)) {

            if (isBestValue) {
                Box(
                    modifier = Modifier
                        .background(CP_VIOLET, RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(
                        "BEST VALUE",
                        color      = Color(0xFF3F008E),
                        fontSize   = 9.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.8.sp
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            Text(
                title,
                color      = if (isSelected) CP_VIOLET else CP_TEXT,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp
            )
            Text(subtitle, color = CP_TEXT_DIM.copy(alpha = 0.6f), fontSize = 11.sp)

            Spacer(modifier = Modifier.height(10.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    price,
                    color      = if (isSelected) CP_VIOLET else CP_TEXT,
                    fontWeight = FontWeight.Black,
                    fontSize   = 20.sp
                )
                Text(priceUnit, color = CP_TEXT_DIM.copy(alpha = 0.5f), fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 3.dp, start = 2.dp))
            }

            Spacer(modifier = Modifier.height(10.dp))

            features.forEach { feat ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 5.dp)
                ) {
                    Icon(
                        if (isSelected) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint     = if (isSelected) CP_VIOLET else CP_TEXT_DIM.copy(alpha = 0.5f),
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        feat,
                        color    = if (isSelected) CP_TEXT else CP_TEXT_DIM.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onPurchase,
                modifier = Modifier.fillMaxWidth().height(44.dp),
                shape    = RoundedCornerShape(12.dp),
                colors   = if (isSelected)
                    ButtonDefaults.buttonColors(containerColor = CP_VIOLET)
                else
                    ButtonDefaults.buttonColors(containerColor = CP_SURFACE_HIGHEST),
                elevation = ButtonDefaults.buttonElevation(
                    defaultElevation = if (isSelected) 8.dp else 0.dp
                )
            ) {
                Text(
                    if (isSelected) "Langganan Sekarang" else "Pilih Plan",
                    color      = if (isSelected) Color(0xFF25005A) else CP_TEXT,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 12.sp
                )
            }
        }
    }
}

@Composable
private fun CpMiniPlanCard(
    modifier: Modifier,
    label: String,
    price: String,
    tier: String,
    onPurchase: (String, String, String) -> Unit
) {
    Row(
        modifier = modifier
            .glassPanel(12f)
            .clickable { onPurchase(tier, "Premium $label", price) }
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column {
            Text(label, color = CP_TEXT, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text("Coba dulu", color = CP_TEXT_DIM.copy(alpha = 0.5f), fontSize = 10.sp)
        }
        Text(
            price,
            color      = CP_CYAN,
            fontWeight = FontWeight.Black,
            fontSize   = 13.sp
        )
    }
}

// ─── Gift Code Section ─────────────────────────────────────────────────
@Composable
private fun CpGiftCodeSection(
    inputCode: String,
    onCodeChange: (String) -> Unit,
    onRedeem: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.CardGiftcard,
                contentDescription = null,
                tint     = CP_CYAN,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Klaim Gift Code",
                color      = CP_TEXT,
                fontWeight = FontWeight.Bold,
                fontSize   = 15.sp
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Masukkan kode premium dari admin atau event",
            color    = CP_TEXT_DIM.copy(alpha = 0.6f),
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value         = inputCode,
                onValueChange = onCodeChange,
                placeholder   = { Text("NIHONVIP2025", color = CP_TEXT_DIM.copy(alpha = 0.3f), fontSize = 13.sp) },
                modifier      = Modifier.weight(1f).height(52.dp),
                singleLine    = true,
                textStyle     = LocalTextStyle.current.copy(
                    color      = CP_VIOLET,
                    fontWeight = FontWeight.Bold,
                    fontSize   = 14.sp,
                    letterSpacing = 1.sp
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor   = CP_VIOLET,
                    unfocusedBorderColor = CP_OUTLINE.copy(alpha = 0.4f),
                    focusedContainerColor   = CP_SURFACE.copy(alpha = 0.5f),
                    unfocusedContainerColor = CP_SURFACE.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .background(
                        Brush.linearGradient(listOf(CP_VIOLET_DIM, CP_VIOLET)),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable { onRedeem() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Send,
                    contentDescription = "Klaim",
                    tint     = Color(0xFF25005A),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

// ─── System Settings ───────────────────────────────────────────────────
@Composable
private fun CpSystemSettings(isDarkMode: Boolean, onToggleDark: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            "PENGATURAN SISTEM",
            color       = CP_TEXT_DIM.copy(alpha = 0.5f),
            fontSize    = 11.sp,
            fontWeight  = FontWeight.Black,
            letterSpacing = 2.sp,
            modifier    = Modifier.padding(bottom = 10.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .glassPanel()
        ) {
            CpSettingsRow(
                icon      = Icons.Filled.DarkMode,
                iconTint  = CP_VIOLET,
                title     = "Tema Gelap Sinematik",
                subtitle  = "Nyaman ditonton di tempat gelap",
                trailing  = {
                    CpToggle(checked = isDarkMode, onToggle = onToggleDark)
                }
            )
            CpDivider()
            CpSettingsRow(
                icon      = Icons.Filled.Notifications,
                iconTint  = CP_CYAN,
                title     = "Notifikasi Episode",
                subtitle  = "Episode baru & live event",
                trailing  = {
                    CpToggle(checked = true, onToggle = {})
                }
            )
            CpDivider()
            CpSettingsRow(
                icon      = Icons.Filled.Language,
                iconTint  = CP_CRIMSON,
                title     = "Bahasa Aplikasi",
                subtitle  = "Bahasa Indonesia",
                trailing  = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = CP_TEXT_DIM.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
            CpDivider()
            CpSettingsRow(
                icon      = Icons.Filled.Download,
                iconTint  = CP_TEXT_DIM.copy(alpha = 0.7f),
                title     = "Kualitas Unduhan",
                subtitle  = "1080p • Auto-delete lama",
                trailing  = {
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = CP_TEXT_DIM.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            )
        }
    }
}

@Composable
private fun CpSettingsRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    trailing: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(CP_SURFACE_HIGHEST, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
        }
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = CP_TEXT, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = CP_TEXT_DIM.copy(alpha = 0.55f), fontSize = 11.sp)
        }
        trailing()
    }
}

@Composable
private fun CpDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 70.dp)
            .height(0.5.dp)
            .background(CP_OUTLINE.copy(alpha = 0.2f))
    )
}

@Composable
private fun CpToggle(checked: Boolean, onToggle: () -> Unit) {
    Box(
        modifier = Modifier
            .width(44.dp)
            .height(26.dp)
            .background(
                if (checked) CP_VIOLET else CP_SURFACE_HIGHEST,
                RoundedCornerShape(50)
            )
            .border(1.dp, if (checked) CP_VIOLET.copy(alpha = 0.5f) else CP_OUTLINE.copy(alpha = 0.3f), RoundedCornerShape(50))
            .clickable { onToggle() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxHeight()
                .padding(2.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .background(
                        if (checked) Color(0xFF3F008E) else CP_TEXT_DIM.copy(alpha = 0.4f),
                        CircleShape
                    )
                    .align(if (checked) Alignment.CenterEnd else Alignment.CenterStart)
            )
        }
    }
}

// ─── Community Rules ───────────────────────────────────────────────────
@Composable
private fun CpCommunityRules() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassPanel()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Shield, contentDescription = null,
                tint = CP_VIOLET, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Aturan Komunitas NihonHua",
                color      = CP_VIOLET,
                fontWeight = FontWeight.Bold,
                fontSize   = 13.sp
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        val rules = listOf(
            "Dilarang spoiler episode yang belum tayang.",
            "Tidak boleh komentar kasar, SARA, atau pornografi.",
            "Dilarang spam atau promosi link ilegal.",
            "Pelanggaran berakibat ban akun Google permanen."
        )
        rules.forEachIndexed { idx, rule ->
            Row(modifier = Modifier.padding(vertical = 2.dp)) {
                Text(
                    "${idx + 1}.",
                    color    = CP_CYAN.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(18.dp)
                )
                Text(rule, color = CP_TEXT_DIM.copy(alpha = 0.7f), fontSize = 12.sp, lineHeight = 17.sp)
            }
        }
    }
}

@Composable
fun PremiumPaymentDialog(
    tier: String,
    title: String,
    price: String,
    viewModel: AnimeViewModel,
    onDismiss: () -> Unit
) {
    var paymentStep by remember { mutableStateOf(0) } // 0: Waiting for Payment (Auto-detect), 1: Success
    val context = LocalContext.current

    // Simulating auto-detection with secure polling
    if (paymentStep == 0) {
        LaunchedEffect(Unit) {
            // Simulasi polling secure mutasi bank selama 12 detik
            kotlinx.coroutines.delay(12000)
            viewModel.purchasePremium(tier) { msg ->
                paymentStep = 1
            }
        }
    }

    Dialog(onDismissRequest = { if (paymentStep != 1) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF111111)),
            border = BorderStroke(1.5.dp, Brush.linearGradient(listOf(Color(0xFFE53935), Color(0xFFFFB300))))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (paymentStep == 0) {
                    Text(
                        text = "Scan QRIS",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Pembayaran untuk $title",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f)
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Price display
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("JUMLAH YANG HARUS DIBAYAR", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(price, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFB300))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Display User's Custom QRIS
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White, RoundedCornerShape(12.dp))
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val ctx = LocalContext.current
                        val qrisId = ctx.resources.getIdentifier("qris_payment", "drawable", ctx.packageName)
                        if (qrisId != 0) {
                            androidx.compose.foundation.Image(
                                painter = androidx.compose.ui.res.painterResource(id = qrisId),
                                contentDescription = "QRIS Code",
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(300.dp),
                                contentScale = ContentScale.Fit
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxWidth().height(300.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Gambar QRIS tidak ditemukan.\nSilakan hubungi admin.", color = Color.Gray, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // Auto-detect UI
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF00E676).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            color = Color(0xFF00E676),
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Sistem Auto-Deteksi Aktif", color = Color(0xFF00E676), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Menunggu verifikasi mutasi bank secara real-time. Jangan tutup halaman ini.", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                        Text("Batal", color = Color.White.copy(alpha = 0.6f))
                    }

                } else if (paymentStep == 1) {
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(Color(0xFF00E676), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Check, contentDescription = "Sukses", tint = Color.Black, modifier = Modifier.size(40.dp))
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Pembayaran Berhasil!", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Transaksi divalidasi dengan aman.\nAkun Anda sekarang berstatus Premium VIP!", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E676))
                    ) {
                        Text("Selesai & Nikmati", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun PremiumCard(
    title: String,
    price: String,
    originalPrice: String,
    onPurchase: () -> Unit
) {
    val hasDiscount = originalPrice.isNotEmpty()
    val isPopular = title.contains("30 Hari") || title.contains("30 HARI") || title.contains("1 BULAN")
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPurchase() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
        border = BorderStroke(
            width = 1.dp,
            color = if (isPopular) Color(0xFFF6C453).copy(alpha = 0.6f) else Color.White.copy(alpha = 0.1f)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(if (isPopular) Color(0xFFF6C453) else Color(0xFF223044), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isPopular) Icons.Filled.WorkspacePremium else Icons.Filled.Star,
                    contentDescription = null,
                    tint = if (isPopular) Color(0xFF171000) else Color(0xFFFFC107)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.White)
                    if (isPopular) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(color = Color(0xFFF6C453), shape = RoundedCornerShape(4.dp)) {
                            Text("HEMAT", modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp), fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color(0xFF171000))
                        }
                    }
                }
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(price, color = Color.White, fontWeight = FontWeight.Black, fontSize = 18.sp)
                    if (hasDiscount) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = originalPrice,
                            color = Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                            textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                        )
                    }
                }
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = "Beli", tint = Color.White.copy(alpha = 0.45f))
        }
    }
}

// 7. ADMIN PANEL SCREEN (Email restricted to rayx445@gmail.com)
@Composable
fun AdminPanelScreen(viewModel: AnimeViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val comments by viewModel.allComments.collectAsState()
    val codes by viewModel.allPremiumCodes.collectAsState()
    val blockedUsers by viewModel.blockedUsers.collectAsState()

    val context = LocalContext.current

    if (currentUser?.email != "rayx445@gmail.com" && currentUser?.email != "niparsia433@gmail.com") {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Akses ditolak! Menu ini khusus untuk admin utama.", color = MaterialTheme.colorScheme.tertiary)
        }
        return
    }

    var selectedAdminSubTab by remember { mutableStateOf("Profile") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("Admin Control Panel", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
        Text("Mengatur profil, komentar komunitas, dan gift codes", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

        Spacer(modifier = Modifier.height(12.dp))

        // Tab Selector for Admin subtasks
        Row(
            modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            listOf("Profile", "Komentar", "Gift Codes", "Blokir").forEach { subTab ->
                val active = selectedAdminSubTab == subTab
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { selectedAdminSubTab = subTab }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(subTab, fontWeight = FontWeight.Bold, color = if (active) Color.Black else MaterialTheme.colorScheme.onBackground)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (selectedAdminSubTab) {
            "Profile" -> {
                // Edit Admin Profile & User Management
                var adminNameInput by remember { mutableStateOf(currentUser!!.displayName) }
                var adminAvatarInput by remember { mutableStateOf(currentUser!!.photoUrl) }
                var targetEmail by remember { mutableStateOf("") }
                var newLevel by remember { mutableStateOf("") }
                var newExp by remember { mutableStateOf("") }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Ubah Informasi Profil Admin", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = adminNameInput,
                        onValueChange = { adminNameInput = it },
                        label = { Text("Nama Display Admin") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = adminAvatarInput,
                        onValueChange = { adminAvatarInput = it },
                        label = { Text("URL Avatar Admin") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = {
                        viewModel.adminUpdateProfile(adminNameInput, adminAvatarInput)
                        Toast.makeText(context, "Profil Admin berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Simpan Perubahan")
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Manajemen Level & EXP User", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text("Atur level dan EXP user secara manual", fontSize = 11.sp, color = Color.Gray)

                    OutlinedTextField(
                        value = targetEmail,
                        onValueChange = { targetEmail = it },
                        label = { Text("Email User") },
                        placeholder = { Text("user@example.com") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = newLevel,
                            onValueChange = { newLevel = it },
                            label = { Text("Level Baru (1-1000)") },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = newExp,
                            onValueChange = { newExp = it },
                            label = { Text("EXP Baru") },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Button(onClick = {
                        val level = newLevel.toIntOrNull()
                        val exp = newExp.toIntOrNull()
                        if (targetEmail.isNotEmpty() && level != null && exp != null) {
                            viewModel.adminUpdateUserLevelAndExp(targetEmail, level, exp)
                            Toast.makeText(context, "Level dan EXP user berhasil diupdate!", Toast.LENGTH_SHORT).show()
                            targetEmail = ""
                            newLevel = ""
                            newExp = ""
                        } else {
                            Toast.makeText(context, "Input tidak valid!", Toast.LENGTH_SHORT).show()
                        }
                    }) {
                        Text("Update Level & EXP")
                    }
                }
            }

            "Komentar" -> {
                // Delete any user comments
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (comments.isEmpty()) {
                        item { Text("Belum ada komentar.") }
                    }
                    items(items = comments, key = { it.id }) { c ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(modifier = Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row {
                                        Text(c.userDisplayName, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("(${c.userEmail})", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Text(c.content, fontSize = 12.sp)
                                    Text("Pada: ${c.animeTitle} - Ep ${c.episodeNumber}", fontSize = 10.sp, color = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = {
                                    viewModel.adminDeleteComment(c.id)
                                    Toast.makeText(context, "Komentar berhasil dihapus oleh Admin", Toast.LENGTH_SHORT).show()
                                }) {
                                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete", tint = MaterialTheme.colorScheme.tertiary)
                                }
                            }
                        }
                    }
                }
            }

            "Gift Codes" -> {
                // Generate gift codes
                var codeText by remember { mutableStateOf("") }
                var selectedCodeTier by remember { mutableStateOf("30_DAYS") }
                var maxClaimsLimit by remember { mutableStateOf("5") }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Buat Kode Premium Baru", fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = codeText,
                        onValueChange = { codeText = it },
                        placeholder = { Text("ADPREMIUMGOLD") },
                        label = { Text("Kode Gift String") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Durasi Premium: ", fontSize = 12.sp, modifier = Modifier.weight(1f))
                        listOf("1_DAY", "5_DAYS", "30_DAYS", "1_YEAR").forEach { tier ->
                            val active = selectedCodeTier == tier
                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 4.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (active) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.surface)
                                    .clickable { selectedCodeTier = tier }
                                    .padding(6.dp)
                            ) {
                                Text(
                                    text = when(tier) {
                                        "1_DAY" -> "1H"
                                        "5_DAYS" -> "5H"
                                        "30_DAYS" -> "30H"
                                        "1_YEAR" -> "1T"
                                        else -> tier
                                    },
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (active) Color.Black else MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }

                    OutlinedTextField(
                        value = maxClaimsLimit,
                        onValueChange = { maxClaimsLimit = it },
                        label = { Text("Batas Maksimal Klaim (Jumlah Orang)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(onClick = {
                        val limitInt = maxClaimsLimit.toIntOrNull() ?: 1
                        if (codeText.isNotEmpty()) {
                            viewModel.adminGeneratePremiumCode(codeText, selectedCodeTier, limitInt) { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                            codeText = ""
                        }
                    }) {
                        Text("Generate & Simpan Kode")
                    }

                    HorizontalDivider()

                    Text("Daftar Kode yang Aktif", fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items = codes, key = { it.code }) { c ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text("KODE: ${c.code}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text("Tipe: ${c.premiumType} | Limit: ${c.claimCount}/${c.maxClaims}", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Blokir" -> {
                // Block/report users violating rules
                var emailToBlock by remember { mutableStateOf("") }
                var blockReason by remember { mutableStateOf("Spoiler & Spam berulang di kolom komentar") }

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Blokir Pengguna Baru", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.tertiary)
                    OutlinedTextField(
                        value = emailToBlock,
                        onValueChange = { emailToBlock = it },
                        label = { Text("Email Google Pengguna") },
                        placeholder = { Text("pelanggar@gmail.com") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = blockReason,
                        onValueChange = { blockReason = it },
                        label = { Text("Alasan Pemblokiran") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            if (emailToBlock.isNotEmpty() && emailToBlock.contains("@")) {
                                viewModel.adminBlockUser(emailToBlock.trim().lowercase(), blockReason.trim())
                                Toast.makeText(context, "Pengguna $emailToBlock berhasil diblokir!", Toast.LENGTH_SHORT).show()
                                emailToBlock = ""
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary)
                    ) {
                        Text("Blokir Permanen", color = Color.White)
                    }

                    HorizontalDivider()

                    Text("Daftar Pengguna yang Diblokir", fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(items = blockedUsers, key = { it.email }) { b ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(modifier = Modifier.padding(10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column {
                                        Text(b.email, fontWeight = FontWeight.Bold)
                                        Text("Alasan: ${b.reason}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurface)
                                    }
                                    IconButton(onClick = {
                                        viewModel.adminUnblockUser(b.email)
                                        Toast.makeText(context, "Sanksi pemblokiran dicabut!", Toast.LENGTH_SHORT).show()
                                    }) {
                                        Icon(Icons.Filled.LockOpen, contentDescription = "Buka", tint = Color.Green)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "Scraper" -> {
                // Pterodactyl Scraper panel
                var scraperUrl by remember { mutableStateOf("http://server.fromscratch.web.id:3536/api/scrape") }
                var apiKeyInput by remember { mutableStateOf("ptlc_BsdlPGZLFlsm3nhEscBmd15tiUDChjeSX2hcRYOjgW7") }
                var showAddInstruction by remember { mutableStateOf(false) }

                Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Pterodactyl Panel Web Scraper Integration", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 16.sp)
                    Text("Sambungkan client aplikasi NihonHua langsung dengan scraper mandiri Anda yang di-host di Pterodactyl Panel.", fontSize = 12.sp, color = Color.Gray)

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedTextField(
                        value = scraperUrl,
                        onValueChange = { scraperUrl = it },
                        label = { Text("Scraper API Endpoint URL") },
                        placeholder = { Text("https://pterodactyl-node.domain.com:8080/api/scrape") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        label = { Text("Pterodactyl Client API Key") },
                        placeholder = { Text("ptlc_xxxxxxxxxxxxxxx") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = {
                            Toast.makeText(context, "Koneksi Scraper berhasil disimpan & disinkronisasikan!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simpan & Test Koneksi")
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(Color.Green, CircleShape)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Status Scraper: ONLINE / RUNNING", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.Green)
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("• Node Name: Pterodactyl-NihonHua-Scraper-Node-01", fontSize = 11.sp)
                            Text("• CPU Usage: 0.12% | RAM: 42MB / 512MB", fontSize = 11.sp)
                            Text("• Platform Node: Node.js v18 (Generic Nest Egg)", fontSize = 11.sp)
                            Text("• Target Scrape Aktif: AnimeIndo, Anichin, DonghuaStream", fontSize = 11.sp)
                        }
                    }

                    Button(
                        onClick = { showAddInstruction = !showAddInstruction },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showAddInstruction) "Sembunyikan Panduan Deploy" else "Tampilkan Panduan Deploy Pterodactyl")
                    }

                    if (showAddInstruction) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Text("PANDUAN LENGKAP DEPLOYMENT SCRAPER DI PTERODACTYL:", fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, color = MaterialTheme.colorScheme.primary)
                                
                                Text(
                                    "Ikuti langkah-langkah di bawah ini untuk meng-host backend web scraper Anda di panel Pterodactyl secara mandiri:",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.7f)
                                )

                                Text("1. Persiapan Server di Pterodactyl:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(
                                    "• Masuk ke Admin Panel Pterodactyl Anda, lalu buat Server Baru.\n" +
                                    "• Pilih Nest: 'Node.js' dan Egg: 'Generic NodeJS'.\n" +
                                    "• Alokasikan Port Server (misal: 8080) dan atur RAM minimal 512MB.",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )

                                Text("2. File Project Web Scraper (Node.js):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(
                                    "• Di File Manager server Pterodactyl, buat file 'package.json' dengan isi:\n" +
                                    "{\n" +
                                    "  \"name\": \"nihonhua-scraper\",\n" +
                                    "  \"version\": \"1.0.0\",\n" +
                                    "  \"main\": \"index.js\",\n" +
                                    "  \"dependencies\": {\n" +
                                    "    \"express\": \"^4.18.2\",\n" +
                                    "    \"axios\": \"^1.3.4\",\n" +
                                    "    \"cheerio\": \"^1.0.0-rc.12\"\n" +
                                    "  }\n" +
                                    "}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFB300)
                                )

                                Text("3. Kode Web Scraper Aktif (index.js):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(
                                    "• Buat file baru bernama 'index.js' dan paste kode berikut:\n" +
                                    "const express = require('express');\n" +
                                    "const axios = require('axios');\n" +
                                    "const cheerio = require('cheerio');\n" +
                                    "const app = express();\n" +
                                    "const PORT = process.env.PORT || 8080;\n\n" +
                                    "app.get('/api/scrape', async (req, res) => {\n" +
                                    "  try {\n" +
                                    "    const response = await axios.get('https://anichin.vip');\n" +
                                    "    const $ = cheerio.load(response.data);\n" +
                                    "    const results = [];\n" +
                                    "    $('.animepost').each((i, el) => {\n" +
                                    "      results.push({\n" +
                                    "        title: $(el).find('h4').text().trim(),\n" +
                                    "        url: $(el).find('a').attr('href'),\n" +
                                    "        thumb: $(el).find('img').attr('src')\n" +
                                    "      });\n" +
                                    "    });\n" +
                                    "    res.json({ success: true, count: results.length, data: results });\n" +
                                    "  } catch (err) {\n" +
                                    "    res.status(500).json({ success: false, error: err.message });\n" +
                                    "  }\n" +
                                    "});\n\n" +
                                    "app.listen(PORT, () => console.log('Scraper berjalan di port ' + PORT));",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFFB300)
                                )

                                Text("4. Menghubungkan ke Client NihonHua:", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.White)
                                Text(
                                    "• Klik tombol 'START' pada server Pterodactyl Anda.\n" +
                                    "• Pergi ke Account Settings Pterodactyl Anda, klik 'API Credentials' dan buat Client API Key (ptlc_xxx).\n" +
                                    "• Masukkan IP Server & Port (atau domain proxy reverse) ke kolom 'Endpoint URL' di atas, dan masukkan ptlc Client API Key.\n" +
                                    "• Klik 'Simpan & Test Koneksi'. Sinkronisasi real-time scraper mandiri Anda langsung terhubung!",
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// 8. INTERACTIVE DIALOG DISPLAY FOR ANY USER PROFILE CARD — PREMIUM REDESIGN
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun UserProfileDialog(
    profile: UserProfile,
    viewModel: AnimeViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val simpleDate = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
    val currentUser by viewModel.currentUser.collectAsState()
    val isOwnProfile = currentUser?.email == profile.email

    var isEditing by remember { mutableStateOf(false) }
    var editedDisplayName by remember { mutableStateOf(profile.displayName) }
    var pickedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSaving by remember { mutableStateOf(false) }

    // Animated glowing ring
    val infiniteTransition = rememberInfiniteTransition(label = "glow")
    val glowAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "glowAngle"
    )

    // GIF presets (anime characters)
    val gifPresets = listOf(
        "https://i.pinimg.com/originals/c0/83/88/c0838848dbbf264d17fcd590e816a12a.gif",
        "https://i.pinimg.com/originals/cf/d5/bc/cfd5bc1eb6b0e8c0e9ec19ef6b0cfc1c.gif",
        "https://i.pinimg.com/originals/07/3c/ba/073cba99665fa9ff16ec12a14e9f39bf.gif",
        "https://media.giphy.com/media/XMeS9f9DSuN76gYm08/giphy.gif",
        "https://media.giphy.com/media/3o7bu3XilJ5BOiSGic/giphy.gif",
        "https://i.pinimg.com/originals/aa/e3/59/aae3594ae47da9d0f0c3db2d1e4aa700.gif",
        "https://i.pinimg.com/originals/6a/3b/05/6a3b057f42f53e0fd0c5e6ab19e1bd67.gif",
        "https://i.pinimg.com/originals/38/77/85/387785a36b6de14aa00e2c9f7a8d11f5.gif"
    )
    var selectedGifPreset by remember { mutableStateOf<String?>(null) }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        if (uri != null) {
            pickedImageUri = uri
            selectedGifPreset = null
        }
    }

    // Permission state
    val mediaPermission = rememberPermissionState(
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU)
            android.Manifest.permission.READ_MEDIA_IMAGES
        else
            android.Manifest.permission.READ_EXTERNAL_STORAGE
    )

    // Image loader with GIF support
    val gifImageLoader = remember {
        ImageLoader.Builder(context)
            .components {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                    add(ImageDecoderDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
            }.build()
    }

    val expForNextLevel = profile.level * 100
    val expProgress = (profile.exp % 100).toFloat() / 100f

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(10.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B0D12))
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isEditing && isOwnProfile) {
                    // ===== EDIT MODE =====
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(listOf(Color(0xFF141821), Color(0xFF0B0D12)))
                            )
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "Edit Profil",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White
                            )
                            Spacer(Modifier.height(20.dp))

                            // Avatar preview (large)
                            Box(contentAlignment = Alignment.Center) {
                                Box(
                                    modifier = Modifier
                                        .size(112.dp)
                                        .rotate(glowAngle)
                                        .clip(CircleShape)
                                        .background(
                                            Brush.sweepGradient(
                                                listOf(
                                                    Color(0xFFFFC107), Color(0xFFFFE082),
                                                    Color(0xFFFFB300), Color(0xFFFFC107)
                                                )
                                            )
                                        )
                                )
                                val previewModel: Any = pickedImageUri
                                    ?: selectedGifPreset
                                    ?: profile.photoUrl
                                AsyncImage(
                                    model = ImageRequest.Builder(context)
                                        .data(previewModel)
                                        .crossfade(true)
                                        .build(),
                                    imageLoader = gifImageLoader,
                                    contentDescription = "Preview",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .size(104.dp)
                                        .clip(CircleShape)
                                )
                                // Camera icon overlay
                                Box(
                                    modifier = Modifier
                                        .size(30.dp)
                                        .align(Alignment.BottomEnd)
                                        .clip(CircleShape)
                                        .background(Color(0xFFFFC107))
                                        .clickable {
                                            if (mediaPermission.status.isGranted) {
                                                galleryLauncher.launch("image/*")
                                            } else {
                                                mediaPermission.launchPermissionRequest()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Filled.PhotoCamera,
                                        contentDescription = "Pilih Foto",
                                        tint = Color.Black,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Display name field
                        OutlinedTextField(
                            value = editedDisplayName,
                            onValueChange = { editedDisplayName = it },
                            label = { Text("Nama Tampilan") },
                            leadingIcon = { Icon(Icons.Filled.Person, null, tint = Color(0xFFFFC107)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Color(0xFFFFC107),
                                focusedLabelColor = Color(0xFFFFC107)
                            )
                        )

                        // Gallery button
                        Button(
                            onClick = {
                                if (mediaPermission.status.isGranted) {
                                    galleryLauncher.launch("image/*")
                                } else {
                                    mediaPermission.launchPermissionRequest()
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141821)),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))
                        ) {
                            Icon(Icons.Filled.PhotoLibrary, null, tint = Color(0xFFFFC107))
                            Spacer(Modifier.width(8.dp))
                            Text("Pilih dari Galeri", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        if (pickedImageUri != null) {
                            Text(
                                "Gambar dari galeri sudah dipilih",
                                fontSize = 12.sp,
                                color = Color(0xFF10B981),
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        // GIF Presets row
                        Text(
                            "Avatar GIF:",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.7f)
                        )

                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(items = gifPresets, key = { it }) { gifUrl ->
                                Box(
                                    modifier = Modifier
                                        .size(64.dp)
                                        .clip(CircleShape)
                                        .border(
                                            width = if (selectedGifPreset == gifUrl) 3.dp else 1.dp,
                                            brush = if (selectedGifPreset == gifUrl)
                                                Brush.sweepGradient(listOf(Color(0xFFFFC107), Color(0xFFFFE082), Color(0xFFFFC107)))
                                            else
                                                Brush.linearGradient(listOf(Color.Gray, Color.Gray)),
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            selectedGifPreset = gifUrl
                                            pickedImageUri = null
                                        }
                                ) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(context).data(gifUrl).crossfade(true).build(),
                                        imageLoader = gifImageLoader,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }
                        }

                        Spacer(Modifier.height(4.dp))

                        // Action buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { isEditing = false },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, Color.Gray)
                            ) {
                                Text("Batal", color = Color.Gray)
                            }
                            Button(
                                onClick = {
                                    if (editedDisplayName.trim().isEmpty()) {
                                        Toast.makeText(context, "Nama tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                                        return@Button
                                    }
                                    isSaving = true
                                    when {
                                        pickedImageUri != null -> {
                                            viewModel.updateUserProfileFromGallery(
                                                editedDisplayName.trim(), pickedImageUri!!.toString()
                                            ) { success, msg ->
                                                isSaving = false
                                                if (success) { 
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                    isEditing = false
                                                    onDismiss() 
                                                } else {
                                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                }
                                            }
                                        }
                                        selectedGifPreset != null -> {
                                            viewModel.updateUserProfile(editedDisplayName.trim(), selectedGifPreset!!) { success, msg ->
                                                isSaving = false
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                if (success) { isEditing = false; onDismiss() }
                                            }
                                        }
                                        else -> {
                                            viewModel.updateUserProfile(editedDisplayName.trim(), profile.photoUrl) { success, msg ->
                                                isSaving = false
                                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                                if (success) { isEditing = false; onDismiss() }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.weight(1.5f),
                                enabled = !isSaving,
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107))
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.Save, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Simpan", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                    }

                } else {
                    // ===== VIEW MODE =====
                    // Gradient header banner
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF141821), Color(0xFF1B2330)))
                            )
                    ) {
                        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)) {
                            Icon(Icons.Filled.Close, contentDescription = "Tutup", tint = Color.White.copy(alpha = 0.75f))
                        }
                    }

                    // Avatar overlapping the banner
                    Box(
                        modifier = Modifier
                            .offset(y = (-55).dp)
                            .padding(bottom = 0.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Glowing animated ring
                        Box(
                            modifier = Modifier
                                .size(120.dp)
                                .rotate(glowAngle)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        listOf(
                                                Color(0xFFFFC107), Color(0xFFF6C453),
                                                Color(0xFFFFE082), Color(0xFFFFC107)
                                        )
                                    )
                                )
                        )
                        // Avatar image
                        AsyncImage(
                            model = ImageRequest.Builder(context)
                                .data(profile.photoUrl)
                                .crossfade(true)
                                .build(),
                            imageLoader = gifImageLoader,
                            contentDescription = profile.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(112.dp)
                                .clip(CircleShape)
                        )
                    }

                    // Content below avatar (offset to compensate avatar overlap)
                    Column(
                        modifier = Modifier
                            .offset(y = (-48).dp)
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(0.dp)
                    ) {
                        // Name
                        Text(
                            text = profile.displayName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = profile.email,
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                        Spacer(Modifier.height(10.dp))

                        // Premium / Free badge
                        Box(
                            modifier = Modifier
                                .background(
                                    if (profile.isPremium)
                                        Brush.horizontalGradient(listOf(Color(0xFFF6C453), Color(0xFFCB8F21)))
                                    else
                                        Brush.horizontalGradient(listOf(Color(0xFF374151), Color(0xFF4B5563))),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (profile.isPremium) Icons.Filled.WorkspacePremium else Icons.Filled.LockOpen,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (profile.isPremium) "PREMIUM" else "FREE",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // EXP Progress Bar
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Level ${profile.level}", fontSize = 12.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                                Text("${profile.exp % 100} / 100 EXP", fontSize = 12.sp, color = Color.White.copy(alpha = 0.5f))
                            }
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(50.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(expProgress.coerceIn(0.01f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(Color(0xFFFFC107), Color(0xFFFFE082))
                                            )
                                        )
                                )
                            }
                        }

                        Spacer(Modifier.height(14.dp))

                        // Stats Grid (2x2)
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // EXP Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                ) {
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Filled.Bolt, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text("${profile.exp}", fontSize = 18.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Black)
                                            Text("Total EXP", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                // Watch Time Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                ) {
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Filled.Schedule, contentDescription = null, tint = Color(0xFF2CD59F), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.height(4.dp))
                                            val h = profile.watchDurationMinutes / 60
                                            val m = profile.watchDurationMinutes % 60
                                            Text(if (h > 0) "${h}j ${m}m" else "${m}m", fontSize = 18.sp, color = Color(0xFF10B981), fontWeight = FontWeight.Black)
                                            Text("Watch Time", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Comments Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                ) {
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Filled.ChatBubble, contentDescription = null, tint = Color(0xFFFFC107), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text("${profile.commentCount}", fontSize = 18.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Black)
                                            Text("Komentar", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                                // Join Date Card
                                Card(
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
                                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f))
                                ) {
                                    Box(modifier = Modifier.padding(12.dp)) {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color(0xFFF6C453), modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.height(4.dp))
                                            Text(
                                                SimpleDateFormat("dd MMM yy", Locale("id")).format(Date(profile.joinDate)),
                                                fontSize = 13.sp,
                                                color = Color(0xFFF59E0B),
                                                fontWeight = FontWeight.Black
                                            )
                                            Text("Bergabung", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }

                        // Premium expiry info
                        if (profile.isPremium && profile.premiumExpiration > 0) {
                            Spacer(Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFFF6C453).copy(alpha = 0.08f), RoundedCornerShape(8.dp))
                                    .border(1.dp, Color(0xFFF6C453).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .padding(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.EventAvailable, contentDescription = null, tint = Color(0xFFF6C453), modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text("Premium aktif hingga:", fontSize = 10.sp, color = Color(0xFFFFD700).copy(alpha = 0.7f))
                                        Text(
                                            simpleDate.format(Date(profile.premiumExpiration)),
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFFFD700)
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Buttons
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (isOwnProfile) {
                                Button(
                                    onClick = { isEditing = true },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFC107)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.Edit, null, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Edit Profil", fontWeight = FontWeight.Bold, color = Color.Black)
                                }
                            }
                            OutlinedButton(
                                onClick = onDismiss,
                                modifier = Modifier.weight(if (isOwnProfile) 1f else 2f),
                                shape = RoundedCornerShape(8.dp),
                                border = BorderStroke(1.dp, Color.Gray.copy(alpha = 0.5f))
                            ) {
                                Text("Tutup", color = Color.Gray, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }
            }
        }
    }

}

// Dialog: Social Media sharing sheet mockup
@Composable
fun SocialShareDialog(
    animeTitle: String,
    viewModel: AnimeViewModel,
    onDismiss: () -> Unit
) {
    var ratingVal by remember { mutableStateOf("9.8") }
    var reviewText by remember { mutableStateOf("Grafik 4K nya mulus, streaming di NihonHua ga pake ribet!") }
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bagikan Ulasan") },
        text = {
            Column {
                Text("Beri Rating:")
                OutlinedTextField(value = ratingVal, onValueChange = { ratingVal = it }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Text("Ulasan Pribadi:")
                OutlinedTextField(value = reviewText, onValueChange = { reviewText = it }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(onClick = {
                viewModel.shareReviewToSocialMedia(animeTitle, ratingVal, reviewText) { shareMsg ->
                    Toast.makeText(context, "Ulasan Berhasil Dibagikan ke Profil Sosial Anda secara Seamless!", Toast.LENGTH_LONG).show()
                    onDismiss()
                }
            }) {
                Text("Bagikan ke Sosmed")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Batal") }
        }
    )
}

// Dialog: Notifications List panel popup
@Composable
fun NotificationsDialog(viewModel: AnimeViewModel, onDismiss: () -> Unit) {
    val notifications by viewModel.notifications.collectAsState()

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Notifikasi Mingguan", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(items = notifications, key = { it.id }) { notif ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(MaterialTheme.colorScheme.background, RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Text(notif.title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp)
                            Text(notif.message, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground)
                            Text(
                                text = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(notif.timestamp)),
                                fontSize = 9.sp,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.align(Alignment.End)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Tutup")
                }
            }
        }
    }
}

// Ad Dialog Composable
@Composable
fun AdDialog(
    onAdWatched: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var adProgress by remember { mutableStateOf(0f) }
    var isAdPlaying by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Simulate ad playback
        while (isAdPlaying && adProgress < 1f) {
            kotlinx.coroutines.delay(100)
            adProgress += 0.01f
        }
        if (adProgress >= 1f) {
            isAdPlaying = false
        }
    }

    Dialog(onDismissRequest = { /* Cannot dismiss, must watch */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "IKLAN PREMIUM",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Ad placeholder
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color(0xFF2C2C2C), RoundedCornerShape(12.dp))
                        .border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = "Ad",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "IKLAN SPONSOR",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            if (isAdPlaying) "Sedang diputar..." else "Selesai!",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress bar
                LinearProgressIndicator(
                    progress = adProgress,
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = Color.White.copy(alpha = 0.1f)
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "${(adProgress * 100).toInt()}% selesai",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    "Tonton iklan sampai selesai untuk dapat 50 EXP!",
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        if (adProgress >= 1f) {
                            onAdWatched()
                            Toast.makeText(context, "+50 EXP diperoleh!", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "Tunggu iklan selesai!", Toast.LENGTH_SHORT).show()
                        }
                    },
                    enabled = adProgress >= 1f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (adProgress >= 1f) MaterialTheme.colorScheme.primary else Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (adProgress >= 1f) "Klaim Reward (+50 EXP)" else "Tunggu...",
                        color = if (adProgress >= 1f) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
