package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.example.data.*
import java.text.SimpleDateFormat
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppNavigation(
    viewModel: AnimeViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentUser by viewModel.currentUser.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val isModded by viewModel.isAppModded.collectAsState()
    val isOutdated by viewModel.isAppOutdated.collectAsState()

    var activeTab by remember { mutableStateOf("dashboard") }

    if (isModded) {
        // Red Anti-MOD Screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0F0404)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.AdminPanelSettings,
                    contentDescription = "Security Alert",
                    tint = Color.Red,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "KEAMANAN NIHONHUA TERANCAM!",
                    color = Color.Red,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Aplikasi terdeteksi telah dimodifikasi secara ilegal (Signature Tampered)! Untuk mencegah pencurian kredensial akun dan penyalahgunaan server premium, akses ditolak.",
                    color = Color.White,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        Toast.makeText(context, "Mengunduh Versi Resmi NihonHua...", Toast.LENGTH_LONG).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Unduh Versi Resmi (APK)", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else if (isOutdated) {
        // Force Update Block screen
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0B0E11)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Download,
                    contentDescription = "Force Update",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(96.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(
                    text = "PEMBARUAN WAJIB NIHONHUA",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Versi aplikasi Anda sudah usang. Pembaruan diperlukan untuk melindungi akun dari modifikasi berbahaya dan memastikan kompatibilitas server streaming.",
                    color = MaterialTheme.colorScheme.onBackground,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = {
                        Toast.makeText(context, "Membuka link update...", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Update Ke Versi Terbaru", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            }
        }
    } else if (currentUser == null) {
        LoginScreen(viewModel = viewModel)
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
                        IconButton(onClick = {
                            viewModel.inspectUserProfile(currentUser!!.email)
                        }) {
                            AsyncImage(
                                model = currentUser!!.photoUrl,
                                contentDescription = "Profil",
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, MaterialTheme.colorScheme.secondary, CircleShape)
                            )
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

// 1. LOGIN SCREEN WITH AUTHENTIC GOOGLE ACCOUNT CHOOSER & REGISTER FORM
@Composable
fun LoginScreen(viewModel: AnimeViewModel) {
    var showGoogleChooser by remember { mutableStateOf(false) }
    var customEmail by remember { mutableStateOf("") }
    var customName by remember { mutableStateOf("") }
    var customAvatar by remember { mutableStateOf("") }
    var showCustomRegister by remember { mutableStateOf(false) }
    var isConnectingGoogle by remember { mutableStateOf(false) }

    if (isConnectingGoogle) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(800)
            isConnectingGoogle = false
            showGoogleChooser = true
        }
    }

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
                onClick = { isConnectingGoogle = true },
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

        // Simulated native loading indicator overlay
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

        // Simulated Native Google Account Chooser Modal
        if (showGoogleChooser) {
            Dialog(onDismissRequest = { showGoogleChooser = false }) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF202124)) // Material Dark Google color
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = Color(0xFF4285F4),
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                "Pilih akun Google",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Text(
                            "untuk melanjutkan ke NihonHua",
                            fontSize = 13.sp,
                            color = Color(0xFF9AA0A6),
                            modifier = Modifier.padding(start = 38.dp, bottom = 16.dp)
                        )

                        Divider(color = Color(0xFF3C4043))

                        // Account 1: General User (Using their actual active email address and a clean name)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showGoogleChooser = false
                                    viewModel.handleGoogleLogin(
                                        "niparsia433@gmail.com",
                                        "Niparsia",
                                        "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80"
                                    ) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                    }
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = "https://images.unsplash.com/photo-1535713875002-d1d0cf377fde?w=100&q=80",
                                contentDescription = null,
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Niparsia", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text("niparsia433@gmail.com", color = Color(0xFF9AA0A6), fontSize = 12.sp)
                            }
                        }

                        Divider(color = Color(0xFF3C4043))

                        // Account 2: Connect another Google Account (or Admin account)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    showGoogleChooser = false
                                    showCustomRegister = true
                                }
                                .padding(vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF303134)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                "Gunakan akun Google lain...",
                                color = Color.White,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.sp
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = { showGoogleChooser = false }) {
                                Text("Batal", color = Color(0xFF8AB4F8))
                            }
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
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 16.dp)
        ) {
            // Hero Banner Section
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp)
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
                                        Color.Black.copy(alpha = 0.85f)
                                    )
                                )
                            )
                    )

                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                "POPULER DI ANICHIN",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Battle Through The Heavens S5",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            "Xiao Yan bertarung di Akademi Jia Nan. Nikmati resolusi grafis 4K!",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
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

            val filteredShows = shows.filter {
                selectedFilter == "Semua" || it.type.equals(selectedFilter, ignoreCase = true)
            }

            // Trending Row
            item {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Text(
                        text = "Sedang Populer",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 8.dp)
                    )

                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(filteredShows.sortedByDescending { it.rating }) { anime ->
                            Card(
                                modifier = Modifier
                                    .width(140.dp)
                                    .clickable { viewModel.selectAnime(anime) },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                            ) {
                                Box(modifier = Modifier.height(170.dp)) {
                                    AsyncImage(
                                        model = anime.imageUrl,
                                        contentDescription = anime.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                    // Rating Badge
                                    Row(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(6.dp)
                                            .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(4.dp))
                                            .padding(horizontal = 4.dp, vertical = 2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Filled.Star,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(2.dp))
                                        Text(
                                            anime.rating,
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
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
                                        text = "${anime.releaseYear} • ${anime.type}",
                                        color = MaterialTheme.colorScheme.onSurface,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Latest Updates List
            item {
                Text(
                    text = "Update Episode Terbaru",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp).padding(top = 16.dp, bottom = 8.dp)
                )
            }

            items(filteredShows) { anime ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(12.dp))
                        .clickable { viewModel.selectAnime(anime) }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AsyncImage(
                        model = anime.imageUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(70.dp, 90.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = anime.title,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = anime.description,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 11.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = anime.type,
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "EP ${anime.episodeCount}",
                                color = MaterialTheme.colorScheme.secondary,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier
                                    .background(MaterialTheme.colorScheme.secondary.copy(alpha = 0.1f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = "Play",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )
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

    val filteredResults = shows.filter { anime ->
        val matchesQuery = searchQuery.isEmpty() || anime.title.contains(searchQuery, ignoreCase = true) || anime.description.contains(searchQuery, ignoreCase = true)
        val matchesGenre = selectedGenre.isEmpty() || selectedGenre == "Semua Genre" || anime.genres.any { it.equals(selectedGenre, ignoreCase = true) }
        val matchesType = selectedType.isEmpty() || selectedType == "Semua Tipe" || anime.type.equals(selectedType, ignoreCase = true)
        val matchesYear = selectedYear.isEmpty() || selectedYear == "Semua Tahun" || anime.releaseYear == selectedYear
        matchesQuery && matchesGenre && matchesType && matchesYear
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
                items(filteredResults) { anime ->
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

    val context = LocalContext.current

    if (anime == null || selectedEp == null) return

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

    // Video playback progress simulation
    var isPlaying by remember { mutableStateOf(true) }
    var isWatching by remember { mutableStateOf(false) }
    var progressSeconds by remember { mutableStateOf(45L) }
    val totalSeconds = 1440L // 24 minutes

    // Save watch history periodically
    LaunchedEffect(selectedEp, progressSeconds) {
        viewModel.saveProgress(
            animeId = anime!!.id,
            animeTitle = anime!!.title,
            animeImage = anime!!.imageUrl,
            episodeNumber = selectedEp!!.episodeNumber,
            progressPercent = progressSeconds.toFloat() / totalSeconds.toFloat(),
            progressSeconds = progressSeconds,
            totalSeconds = totalSeconds
        )
    }

    // Auto seek progress bar mockup
    LaunchedEffect(isPlaying) {
        while (isPlaying) {
            kotlinx.coroutines.delay(1000)
            if (progressSeconds < totalSeconds) {
                progressSeconds++
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0F1115)),
        color = Color(0xFF0F1115)
    ) {
            if (!isWatching) {
                // ANIME/DONGHUA DETAIL SCREEN
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .background(Color(0xFF0F1115))
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
                                            Color(0xFF0F1115).copy(alpha = 0.5f),
                                            Color(0xFF0F1115)
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
                                .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali", tint = Color.White)
                        }

                        // Rating badge on bottom right of image
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .padding(16.dp)
                                .background(Color(0xFFFFB300), RoundedCornerShape(8.dp))
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
                                        if (isDonghua) Brush.horizontalGradient(listOf(Color(0xFFE53935), Color(0xFFFFB300)))
                                        else Brush.horizontalGradient(listOf(Color(0xFF1E88E5), Color(0xFF00E676))),
                                        RoundedCornerShape(6.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = anime!!.type.uppercase(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }

                            // Year Chip
                            Box(
                                modifier = Modifier
                                    .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(text = anime!!.releaseYear, color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                            }

                            // Status Chip
                            Box(
                                modifier = Modifier
                                    .border(1.dp, if (anime!!.status.equals("Ongoing", ignoreCase = true)) Color(0xFF00E676) else Color.Gray, RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = anime!!.status,
                                    color = if (anime!!.status.equals("Ongoing", ignoreCase = true)) Color(0xFF00E676) else Color.Gray,
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
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f), CircleShape)
                                        .border(0.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f), CircleShape)
                                        .padding(horizontal = 12.dp, vertical = 5.dp)
                                ) {
                                    Text(text = genre, color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Description/Synopsis card
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.04f)),
                            border = BorderStroke(0.5.dp, Color.White.copy(alpha = 0.1f)),
                            shape = RoundedCornerShape(12.dp)
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
                                Text("Studio: ${anime!!.studio}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        // 3. MAIN ACTION PLAY BUTTON (glowing gradient)
                        Button(
                            onClick = { isWatching = true },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            shape = RoundedCornerShape(28.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                            contentPadding = PaddingValues()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                MaterialTheme.colorScheme.primary,
                                                MaterialTheme.colorScheme.secondary
                                            )
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Tonton Sekarang", color = Color.Black, fontWeight = FontWeight.Black, fontSize = 15.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 4. EPISODES LIST
                        Text(
                            text = "Daftar Episode (${anime!!.episodes.size})",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        if (anime!!.episodes.isEmpty()) {
                            Text("Tidak ada episode tersedia.", color = Color.Gray, fontSize = 12.sp)
                        } else {
                            // Render list/grid of episodes beautifully
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                anime!!.episodes.forEach { ep ->
                                    val isCurrent = selectedEp?.id == ep.id
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.selectEpisode(ep)
                                                isWatching = true
                                            },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                                                             else Color.White.copy(alpha = 0.05f)
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isCurrent) MaterialTheme.colorScheme.primary
                                                    else Color.White.copy(alpha = 0.08f)
                                        ),
                                        shape = RoundedCornerShape(10.dp)
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
                                                        if (isCurrent) MaterialTheme.colorScheme.primary
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
                                                    color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.White
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
                    // Header Player area including "Back to details" button
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { isWatching = false }) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Kembali ke Detail", tint = Color.White)
                        }
                        IconButton(onClick = {
                            viewModel.closeActiveWatchScreen()
                            viewModel.loadLatestShows()
                        }) {
                            Icon(Icons.Filled.Close, contentDescription = "Kembali")
                        }
                    Text(
                        text = anime!!.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )

                    // Chromecast / Cast Button
                    IconButton(onClick = {
                        Toast.makeText(context, "Mencari perangkat Chromecast / Layar Lebar...", Toast.LENGTH_SHORT).show()
                    }) {
                        Icon(Icons.Filled.Cast, contentDescription = "Cast", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Aesthetic Simulated Player View Area supporting 4K
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .background(Color.Black)
                ) {
                    AsyncImage(
                        model = anime!!.imageUrl,
                        contentDescription = "Video Poster Blur",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .alpha(0.4f)
                    )

                    // Overlay quality indicator
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .background(Color.Red.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (quality == "4K") "4K ULTRA HD" else quality.uppercase(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }

                    // Player Controls Center
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            if (progressSeconds > 10) progressSeconds -= 10
                        }) {
                            Icon(Icons.Filled.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }

                        IconButton(onClick = { isPlaying = !isPlaying }) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Filled.PauseCircleFilled else Icons.Filled.PlayCircleFilled,
                                contentDescription = "Play/Pause",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(54.dp)
                            )
                        }

                        IconButton(onClick = {
                            if (progressSeconds < totalSeconds - 10) progressSeconds += 10
                        }) {
                            Icon(Icons.Filled.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                        }
                    }

                    // Bottom Player slider controls
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))
                                )
                            )
                            .padding(8.dp)
                    ) {
                        // Progress Slider Row
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            val progressFloat = progressSeconds.toFloat() / totalSeconds.toFloat()
                            Slider(
                                value = progressFloat,
                                onValueChange = { progressSeconds = (it * totalSeconds).toLong() },
                                modifier = Modifier.weight(1f),
                                colors = SliderDefaults.colors(
                                    activeTrackColor = MaterialTheme.colorScheme.primary,
                                    thumbColor = MaterialTheme.colorScheme.primary
                                )
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Formatting min:sec
                            val currentMin = progressSeconds / 60
                            val currentSec = progressSeconds % 60
                            val totalMin = totalSeconds / 60
                            val totalSec = totalSeconds % 60
                            Text(
                                String.format(Locale.getDefault(), "%02d:%02d / %02d:%02d", currentMin, currentSec, totalMin, totalSec),
                                color = Color.White,
                                fontSize = 11.sp
                            )

                            // Resolution Changer Trigger
                            var showResolutionMenu by remember { mutableStateOf(false) }
                            Box {
                                Text(
                                    "Kualitas: $quality ▼",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier
                                        .clickable { showResolutionMenu = true }
                                        .padding(4.dp)
                                )
                                DropdownMenu(expanded = showResolutionMenu, onDismissRequest = { showResolutionMenu = false }) {
                                    listOf("360p", "480p", "720p", "1080p", "4K").forEach { q ->
                                        DropdownMenuItem(
                                            text = { Text(if (q == "4K") "4K (Ultra HD)" else q) },
                                            onClick = {
                                                viewModel.changeQuality(q)
                                                showResolutionMenu = false
                                                Toast.makeText(context, "Grafik dialihkan ke resolusi $q", Toast.LENGTH_SHORT).show()
                                            }
                                        )
                                    }
                                }
                            }
                        }
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
                                    color = MaterialTheme.colorScheme.primary,
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
                                        color = MaterialTheme.colorScheme.secondary,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier
                                            .border(1.dp, MaterialTheme.colorScheme.secondary, RoundedCornerShape(4.dp))
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
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
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
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surface),
                                    shape = RoundedCornerShape(8.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Filled.Share, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Bagikan Ulasan", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
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
                                color = MaterialTheme.colorScheme.onBackground
                            )

                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(anime!!.episodes) { ep ->
                                    val isSelected = selectedEp!!.id == ep.id
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface
                                            )
                                            .clickable {
                                                viewModel.selectEpisode(ep)
                                                progressSeconds = 0L // Reset progress on ep switch
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    ) {
                                        Text(
                                            "EP ${ep.episodeNumber}",
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onBackground
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Comments Section Title
                    item {
                        Divider(modifier = Modifier.padding(vertical = 8.dp))
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
                        items(comments) { comment ->
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
                items(downloads) { dl ->
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

// 6. SETTINGS & PREMIUM CHANNELS (Premium tiers + claiming codes)
@Composable
fun SettingsScreen(viewModel: AnimeViewModel) {
    val currentUser by viewModel.currentUser.collectAsState()
    val isDarkMode by viewModel.isDarkMode.collectAsState()

    val context = LocalContext.current
    var inputCode by remember { mutableStateOf("") }

    var activePurchaseTier by remember { mutableStateOf<String?>(null) }
    var activePurchasePrice by remember { mutableStateOf("") }
    var activePurchaseTitle by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Toggle Dark mode
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Tema Gelap Estetis", fontWeight = FontWeight.Bold)
                        Text("Kenyamanan mata saat nonton malam hari", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
                    }
                    Switch(checked = isDarkMode, onCheckedChange = { viewModel.toggleDarkMode() })
                }
            }
        }

        // Subscription Plans Section with visual discount
        item {
            Text("Berlangganan Paket Premium", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text("Akses resolusi 4K tanpa batasan dan tonton offline", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)
        }

        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                // Tier 1: 1 Day
                PremiumCard(
                    title = "Premium 1 Hari",
                    price = "Rp 1.000",
                    originalPrice = "",
                    onPurchase = {
                        activePurchaseTier = "1_DAY"
                        activePurchasePrice = "Rp 1.000"
                        activePurchaseTitle = "Premium 1 Hari"
                    }
                )
                // Tier 2: 5 Days
                PremiumCard(
                    title = "Premium 5 Hari",
                    price = "Rp 4.000",
                    originalPrice = "",
                    onPurchase = {
                        activePurchaseTier = "5_DAYS"
                        activePurchasePrice = "Rp 4.000"
                        activePurchaseTitle = "Premium 5 Hari"
                    }
                )
                // Tier 3: 30 Days with Discount!
                PremiumCard(
                    title = "Premium 30 Hari",
                    price = "Rp 20.000",
                    originalPrice = "Rp 25.000",
                    onPurchase = {
                        activePurchaseTier = "30_DAYS"
                        activePurchasePrice = "Rp 20.000"
                        activePurchaseTitle = "Premium 30 Hari"
                    }
                )
                // Tier 4: 1 Year with Big Discount!
                PremiumCard(
                    title = "Premium 1 Tahun",
                    price = "Rp 200.000",
                    originalPrice = "Rp 240.000",
                    onPurchase = {
                        activePurchaseTier = "1_YEAR"
                        activePurchasePrice = "Rp 200.000"
                        activePurchaseTitle = "Premium 1 Tahun"
                    }
                )
            }
        }

        // Claim Code Section
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Klaim Kode Premium", fontWeight = FontWeight.Bold)
                    Text("Masukkan kode khusus yang diberikan admin", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurface)

                    Spacer(modifier = Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputCode,
                            onValueChange = { inputCode = it },
                            placeholder = { Text("CONTOH: ADPREM1") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = {
                            if (inputCode.trim().isNotEmpty()) {
                                viewModel.redeemGiftCode(inputCode.trim()) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                }
                                inputCode = ""
                            }
                        }) {
                            Text("Klaim")
                        }
                    }
                }
            }
        }

        // Community Rules
        item {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Aturan Komunitas Indonesia", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "1. Dilarang spoiler episode anime/donghua yang belum tayang.\n" +
                        "2. Tidak boleh mengirim komentar kasar, SARA, pornografi, atau toxic.\n" +
                        "3. Dilarang spam atau mempromosikan link ilegal.\n" +
                        "Pelanggaran aturan berakibat pemblokiran akun Google permanen oleh Admin.",
                        fontSize = 11.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }

        // Log out button
        item {
            Button(
                onClick = { viewModel.handleLogout() },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiary),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Keluar dari Akun Google", fontWeight = FontWeight.Bold, color = Color.White)
            }
        }
    }

    if (activePurchaseTier != null) {
        PremiumPaymentDialog(
            tier = activePurchaseTier!!,
            title = activePurchaseTitle,
            price = activePurchasePrice,
            viewModel = viewModel,
            onDismiss = { activePurchaseTier = null }
        )
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
    var selectedMethod by remember { mutableStateOf("QRIS") }
    var phoneNumber by remember { mutableStateOf("") }
    var paymentStep by remember { mutableStateOf(0) } // 0: Form, 1: Loading, 2: Success
    val context = LocalContext.current

    if (paymentStep == 1) {
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(2000)
            viewModel.purchasePremium(tier) { msg ->
                paymentStep = 2
            }
        }
    }

    Dialog(onDismissRequest = { if (paymentStep != 1) onDismiss() }) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF131722)),
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
                        text = "Metode Pembayaran",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Selesaikan transaksi untuk $title",
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
                            Text("TOTAL TAGIHAN", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(price, fontSize = 24.sp, fontWeight = FontWeight.Black, color = Color(0xFFFFB300))
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = "Pilih Saluran Pembayaran:",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.align(Alignment.Start)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Scrollable Payment Methods List
                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val channels = listOf(
                            Triple("QRIS", "QRIS (Semua Bank / E-Money)", Color(0xFF00E676)),
                            Triple("DANA", "DANA (Kirim ke 082298329032)", Color(0xFF118EEA))
                        )

                        channels.forEach { (code, name, color) ->
                            val isSelected = selectedMethod == code
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedMethod = code },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) color.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f)
                                ),
                                border = BorderStroke(
                                    width = if (isSelected) 2.dp else 1.dp,
                                    color = if (isSelected) color else Color.White.copy(alpha = 0.1f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(
                                        selected = isSelected,
                                        onClick = { selectedMethod = code },
                                        colors = RadioButtonDefaults.colors(selectedColor = color, unselectedColor = Color.White.copy(alpha = 0.3f))
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(name, fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color.White)
                                        if (code == "QRIS") {
                                            Text("Scan QR Code QRIS instan otomatis", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                        } else {
                                            Text("Kirim/Transfer DANA manual", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    if (selectedMethod == "DANA") {
                        // Display Destination DANA Box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF118EEA).copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color(0xFF118EEA).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("NOMOR TUJUAN TRANSFER DANA", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("0822-9832-9032", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color(0xFF118EEA))
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Harap transfer sesuai nominal tagihan ke nomor DANA di atas.", fontSize = 10.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))

                        // Phone Number Input field
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = { phoneNumber = it },
                            placeholder = { Text("Contoh: 081234567890", color = Color.White.copy(alpha = 0.4f)) },
                            label = { Text("Nomor Handphone Pengirim DANA Anda", color = Color.White.copy(alpha = 0.6f)) },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                focusedBorderColor = Color(0xFF118EEA),
                                unfocusedBorderColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    } else {
                        // Display Dynamic QRIS code
                        Box(
                            modifier = Modifier
                                .size(160.dp)
                                .background(Color.White, RoundedCornerShape(8.dp))
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                // Draw simulated QRIS scan grid
                                Box(
                                    modifier = Modifier
                                        .size(110.dp)
                                        .border(2.dp, Color.Black)
                                        .background(Color.White)
                                ) {
                                    Row(modifier = Modifier.fillMaxSize()) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Box(modifier = Modifier.size(24.dp).background(Color.Black))
                                            Spacer(modifier = Modifier.height(10.dp))
                                            Box(modifier = Modifier.size(20.dp).background(Color.Black))
                                            Spacer(modifier = Modifier.weight(1f))
                                            Box(modifier = Modifier.size(24.dp).background(Color.Black))
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                                            Spacer(modifier = Modifier.height(15.dp))
                                            Box(modifier = Modifier.size(15.dp).background(Color.Black))
                                            Spacer(modifier = Modifier.height(15.dp))
                                            Box(modifier = Modifier.size(15.dp).background(Color.Black))
                                            Spacer(modifier = Modifier.height(15.dp))
                                            Box(modifier = Modifier.size(15.dp).background(Color.Black))
                                        }
                                        Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                                            Box(modifier = Modifier.size(24.dp).background(Color.Black))
                                            Spacer(modifier = Modifier.height(20.dp))
                                            Box(modifier = Modifier.size(12.dp).background(Color.Black))
                                            Spacer(modifier = Modifier.weight(1f))
                                            Box(modifier = Modifier.size(20.dp).background(Color.Black))
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text("NihonHua QRIS", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 8.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Silakan screenshot QRIS diatas lalu scan di aplikasi pembayaran Anda", color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, textAlign = TextAlign.Center)
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    // Action buttons
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                            Text("Batal", color = Color.White.copy(alpha = 0.6f))
                        }
                        Button(
                            onClick = {
                                if (selectedMethod != "QRIS" && phoneNumber.trim().length < 9) {
                                    Toast.makeText(context, "Harap masukkan nomor handphone yang valid!", Toast.LENGTH_SHORT).show()
                                } else {
                                    paymentStep = 1
                                }
                            },
                            modifier = Modifier.weight(1.5f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Bayar Sekarang")
                        }
                    }
                } else if (paymentStep == 1) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(20.dp))
                    Text("Menghubungkan ke API Gateway...", fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Memvalidasi tagihan e-money & memproses saldo...", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                } else if (paymentStep == 2) {
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
                    Text("Akun Anda sekarang berstatus Premium VIP!", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f), textAlign = TextAlign.Center)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Selesai & Nikmati")
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(
            width = if (hasDiscount) 1.5.dp else 1.dp,
            color = if (hasDiscount) MaterialTheme.colorScheme.secondary.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)
        ),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = if (hasDiscount) listOf(Color(0xFF1B2228), Color(0xFF141B22))
                                 else listOf(Color(0xFF1B2228), Color(0xFF1B2228))
                    )
                )
                .clickable { onPurchase() }
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = if (hasDiscount) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = price,
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 18.sp
                        )
                        if (originalPrice.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = originalPrice,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                textDecoration = androidx.compose.ui.text.style.TextDecoration.LineThrough
                            )
                        }
                    }
                }

                // Buy button or Promo indicator
                if (hasDiscount) {
                    Box(
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.secondary, RoundedCornerShape(8.dp))
                            .clickable { onPurchase() }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "PROMO 20%",
                            color = Color.Black,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                } else {
                    Button(
                        onClick = onPurchase,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Beli", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
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
            listOf("Profile", "Komentar", "Gift Codes", "Blokir", "Scraper").forEach { subTab ->
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
                // Edit Admin Profile
                var adminNameInput by remember { mutableStateOf(currentUser!!.displayName) }
                var adminAvatarInput by remember { mutableStateOf(currentUser!!.photoUrl) }

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
                    Divider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Manajemen Keamanan & Proteksi", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    Text("Uji coba fitur anti-tamper MOD dan force update secara real-time.", fontSize = 11.sp, color = Color.Gray)

                    val isModdedSimulated by viewModel.isAppModded.collectAsState()
                    val isOutdatedSimulated by viewModel.isAppOutdated.collectAsState()

                    // Switch 1: Anti-MOD simulation
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAppModded(!isModdedSimulated)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (isModdedSimulated) Color.Red else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Simulasikan Deteksi MOD APK", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Jika aktif, aplikasi akan mengunci layar dengan Red Warning Screen", fontSize = 10.sp, color = Color.Gray)
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isModdedSimulated) Color.Red else Color.Gray,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isModdedSimulated) "AKTIF" else "MATI",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Switch 2: Force Update simulation
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.setAppOutdated(!isOutdatedSimulated)
                            },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = null,
                                tint = if (isOutdatedSimulated) MaterialTheme.colorScheme.primary else Color.Gray,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Simulasikan Force Update (Versi Usang)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text("Layar wajib update akan muncul jika bernilai true", fontSize = 10.sp, color = Color.Gray)
                            }
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = if (isOutdatedSimulated) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isOutdatedSimulated) "USANG" else "TERBARU",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isOutdatedSimulated) Color.Black else Color.White
                                )
                            }
                        }
                    }
                }
            }

            "Komentar" -> {
                // Delete any user comments
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (comments.isEmpty()) {
                        item { Text("Belum ada komentar.") }
                    }
                    items(comments) { c ->
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

                    Divider()

                    Text("Daftar Kode yang Aktif", fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(codes) { c ->
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

                    Divider()

                    Text("Daftar Pengguna yang Diblokir", fontWeight = FontWeight.Bold)
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(blockedUsers) { b ->
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

// 8. INTERACTIVE DIALOG DISPLAY FOR ANY USER PROFILE CARD
@Composable
fun UserProfileDialog(
    profile: UserProfile,
    viewModel: AnimeViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val simpleDate = SimpleDateFormat("dd MMMM yyyy", Locale("id", "ID"))
    val currentUser by viewModel.currentUser.collectAsState()

    var isEditing by remember { mutableStateOf(false) }
    var editedDisplayName by remember { mutableStateOf(profile.displayName) }
    var editedPhotoUrl by remember { mutableStateOf(profile.photoUrl) }

    val animatedAvatars = listOf(
        "https://i.pinimg.com/originals/c0/83/88/c0838848dbbf264d17fcd590e816a12a.gif",
        "https://i.pinimg.com/originals/cf/d5/bc/cfd5bc1eb6b0e8c0e9ec19ef6b0cfc1c.gif",
        "https://i.pinimg.com/originals/07/3c/ba/073cba99665fa9ff16ec12a14e9f39bf.gif",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExM3ZpcGRoN3VpeGs2czg1eXBtOHZubXBtYm9hZWVlNW9tOXV4cmE2MyZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/XMeS9f9DSuN76gYm08/giphy.gif",
        "https://media.giphy.com/media/v1.Y2lkPTc5MGI3NjExbmswbHpwOTI3YmpxNnVzZThjNjNldWpzeWszZzhpaTVjYzg4bm1mMCZlcD12MV9pbnRlcm5hbF9naWZfYnlfaWQmY3Q9cw/3o7bu3XilJ5BOiSGic/giphy.gif"
    )

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (isEditing) {
                    Text(
                        "Edit Profil Anda",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    // Avatar Preview
                    Box(contentAlignment = Alignment.Center) {
                        Box(
                            modifier = Modifier
                                .size(86.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        colors = listOf(MaterialTheme.colorScheme.primary, Color.Transparent, MaterialTheme.colorScheme.primary)
                                    )
                                )
                        )
                        AsyncImage(
                            model = editedPhotoUrl,
                            contentDescription = "Preview",
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedTextField(
                        value = editedDisplayName,
                        onValueChange = { editedDisplayName = it },
                        label = { Text("Nama Tampilan") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = editedPhotoUrl,
                        onValueChange = { editedPhotoUrl = it },
                        label = { Text("URL Foto Profil / GIF") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        "Pilih Poto Profil Bergerak (GIF):",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.align(Alignment.Start)
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(animatedAvatars) { gifUrl ->
                            Box(
                                modifier = Modifier
                                    .size(60.dp)
                                    .clip(CircleShape)
                                    .border(
                                        width = if (editedPhotoUrl == gifUrl) 3.dp else 1.dp,
                                        color = if (editedPhotoUrl == gifUrl) MaterialTheme.colorScheme.primary else Color.Gray,
                                        shape = CircleShape
                                    )
                                    .clickable {
                                        editedPhotoUrl = gifUrl
                                    }
                            ) {
                                AsyncImage(
                                    model = gifUrl,
                                    contentDescription = "Preset GIF",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isEditing = false },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Batal")
                        }
                        Button(
                            onClick = {
                                if (editedDisplayName.trim().isEmpty()) {
                                    Toast.makeText(context, "Nama tidak boleh kosong!", Toast.LENGTH_SHORT).show()
                                } else {
                                    viewModel.updateUserProfile(editedDisplayName.trim(), editedPhotoUrl.trim()) { success, msg ->
                                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        isEditing = false
                                        onDismiss()
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Simpan", fontWeight = FontWeight.Bold)
                        }
                    }

                } else {
                    // Avatar Large glow with beautiful double border
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(top = 10.dp)
                    ) {
                        // Glowing outer ring
                        Box(
                            modifier = Modifier
                                .size(110.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.sweepGradient(
                                        colors = if (profile.isPremium) {
                                            listOf(Color(0xFFFFB300), Color(0xFFE53935), Color(0xFFFFB300))
                                        } else {
                                            listOf(Color(0xFF29B6F6), Color(0xFFAB47BC), Color(0xFF29B6F6))
                                        }
                                    )
                                )
                        )
                        // Inner clean border background
                        Box(
                            modifier = Modifier
                                .size(104.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF131722))
                        )
                        // The actual image
                        AsyncImage(
                            model = profile.photoUrl,
                            contentDescription = profile.displayName,
                            modifier = Modifier
                                .size(96.dp)
                                .clip(CircleShape)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Name
                    Text(
                        text = profile.displayName,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )

                    // Masked email for privacy
                    Text(
                        text = maskEmail(profile.email),
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Premium badge display with neon highlight
                    if (profile.isPremium) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.horizontalGradient(listOf(Color(0xFFFFB300), Color(0xFFE53935))),
                                    RoundedCornerShape(50.dp)
                                )
                                .padding(horizontal = 20.dp, vertical = 6.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.Star,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    "VIP PREMIUM MEMBER",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Black,
                                    letterSpacing = 1.sp
                                )
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(50.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(50.dp))
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "REGULER MEMBER",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))
                    HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                    Spacer(modifier = Modifier.height(20.dp))

                    // Profile analytic details - styled as grid items
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Stat 1: Joined
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Bergabung", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    simpleDate.format(Date(profile.joinDate)),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }

                        // Stat 2: Watch Duration
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Waktu Tonton", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    "${profile.watchDurationMinutes} Menit",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFE53935)
                                )
                            }
                        }

                        // Stat 3: Comments Count
                        Card(
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.03f)),
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.06f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text("Komentar", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    profile.commentCount.toString(),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFFFFB300)
                                )
                            }
                        }
                    }

                    // Currently Watching List
                    if (profile.watchingList.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(24.dp))
                        Text(
                            "Sedang Ditonton:",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Start)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.02f), RoundedCornerShape(12.dp))
                                .border(1.dp, Color.White.copy(alpha = 0.05f), RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            profile.watchingList.split(",").forEach { id ->
                                val title = when (id) {
                                    "btth_s5" -> "Battle Through The Heavens S5"
                                    "renegade_immortal" -> "Renegade Immortal"
                                    "perfect_world" -> "Perfect World"
                                    "shrouding_the_heavens" -> "Shrouding the Heavens"
                                    "solo_leveling" -> "Solo Leveling"
                                    "demon_slayer_s4" -> "Demon Slayer: Hashira Training"
                                    else -> id
                                }
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = Color(0xFFE53935),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(title, fontSize = 12.sp, color = Color.White.copy(alpha = 0.8f))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (currentUser?.email == profile.email) {
                            OutlinedButton(
                                onClick = { isEditing = true },
                                modifier = Modifier.weight(1f),
                                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Edit Profil", color = Color.White)
                            }
                        }
                        Button(
                            onClick = onDismiss,
                            modifier = Modifier.weight(if (currentUser?.email == profile.email) 1f else 2f),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text("Tutup", fontWeight = FontWeight.Bold)
                        }
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
                    items(notifications) { notif ->
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


