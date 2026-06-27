package com.example.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.view.WindowInsets
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

// Domain-domain iklan yang di-block di WebView
private val AD_DOMAINS = setOf(
    "doubleclick.net",
    "googlesyndication.com",
    "googleadservices.com",
    "adnxs.com",
    "adsrvr.org",
    "adtechus.com",
    "advertising.com",
    "adbrite.com",
    "popads.net",
    "popcash.net",
    "propellerads.com",
    "exoclick.com",
    "hilltopads.net",
    "adsterra.com",
    "trafficjunky.net",
    "juicyads.com",
    "trafficfactory.biz",
    "clickadu.com",
    "ero-advertising.com",
    "ad.fly",
    "adf.ly",
    "linkvertise.com",
    "shorte.st",
    "ouo.io",
    "bc.vc",
    "gratispremium.net",
    "gogoadstats.com",
    "ads.js",
    "pop.js",
    "pagead",
    "adservice",
    "analytics.google",
    "hotjar.com",
    "taboola.com",
    "outbrain.com",
    "criteo.com",
    "rubiconproject.com",
    "openx.net",
    "pubmatic.com",
    "appnexus.com"
)

// CSS yang di-inject untuk menyembunyikan iklan dan elemen non-player dari website
private const val HIDE_ADS_CSS = """
    /* === BLOCK ADS & POPUPS === */
    .banner, .ad-banner, .advertisement, .ads, .ad-container, .ad-wrapper,
    .ad-block, .ad-area, .ad-box, .ad-slot, .ad-unit, .ad-frame,
    [id*='ad-'], [id*='ads-'], [id*='banner'], [id*='popup'], [id*='pop-'],
    [class*='ad-'], [class*='ads-'], [class*='banner-'], [class*='sponsor'],
    [class*='promo'], [class*='popup'], [class*='popunder'], [class*='overlay-ad'],
    [class*='adsbygoogle'], [class*='advertisement'],
    div[data-ad], div[data-ads], div[data-banner],
    ins.adsbygoogle, .google-ad, .google-ads,
    iframe[src*='doubleclick'], iframe[src*='googlesyndication'],
    iframe[src*='adnxs'], iframe[src*='exoclick'],
    /* === BLOCK WEBSITE UI (header, nav, footer, sidebar) === */
    header, .header, #header, .site-header, .top-bar, .topbar,
    nav, .navbar, .navigation, .nav-bar, #nav, .menu, .main-menu,
    footer, .footer, #footer, .site-footer, .bottom-bar,
    .sidebar, #sidebar, .side-bar, .widget-area, .related, .related-posts,
    .breadcrumb, .breadcrumbs, .page-title, .entry-title,
    .comment-section, .comments, #comments, .comment-area,
    .social-share, .share-buttons, .tags, .categories,
    .pagination, .pager, .next-prev,
    .download-links, .download-button,
    .title-anime, .anime-info, .anime-detail,
    /* === BLOCK POPUPS & OVERLAYS === */
    .popup, .modal, .overlay, .lightbox, .dialog,
    [class*='popup'], [class*='modal'], [class*='overlay'],
    [style*='z-index: 9999'], [style*='z-index:9999'],
    [style*='z-index: 99999'], [style*='z-index:99999'],
    /* === BLOCK NOTIFICATION BARS === */
    .notification, .alert-bar, .info-bar, .cookie-notice, .gdpr-notice,
    { 
        display: none !important; 
        visibility: hidden !important;
        opacity: 0 !important;
        pointer-events: none !important;
    }
    
    /* === FORCE VIDEO PLAYER FULLSCREEN === */
    html, body {
        margin: 0 !important;
        padding: 0 !important;
        background: #000 !important;
        overflow: hidden !important;
        width: 100% !important;
        height: 100% !important;
    }
    
    /* === MAKE VIDEO/PLAYER FILL SCREEN === */
    video, 
    .video-js, .jw-video, .plyr, .flowplayer,
    .player, .player-container, #player, .video-player,
    .video-wrap, .video-container, .embed-responsive,
    iframe[src*='player'], iframe[src*='embed'],
    iframe[allow*='autoplay'], iframe[allowfullscreen],
    .stream-container, #stream, .watch-video {
        position: fixed !important;
        top: 0 !important;
        left: 0 !important;
        width: 100vw !important;
        height: 100vh !important;
        max-width: 100% !important;
        max-height: 100% !important;
        z-index: 1 !important;
        background: #000 !important;
    }
    
    /* Pastikan kontainer utama tidak blocking player */
    .content, .main-content, #content, .wrap, .container, main {
        padding: 0 !important;
        margin: 0 !important;
    }
"""

// JS yang di-inject setelah halaman load
private const val INJECT_JS = """
(function() {
    // 1. Inject CSS anti-iklan
    var style = document.createElement('style');
    style.innerHTML = `$HIDE_ADS_CSS`;
    document.head.appendChild(style);
    
    // 2. Block window.open (popup iklan)
    window.open = function() { return null; };
    
    // 3. Block alert/confirm popup iklan
    window.alert = function() {};
    window.confirm = function() { return false; };
    
    // 4. Cari dan play video otomatis
    function tryAutoplay() {
        var videos = document.querySelectorAll('video');
        videos.forEach(function(v) {
            v.muted = false;
            v.play().catch(function() {});
        });
        
        // Cari iframe player dan scroll ke sana
        var playerFrame = document.querySelector(
            'iframe[src*="player"], iframe[src*="embed"], iframe[allowfullscreen], .video-player iframe'
        );
        if (playerFrame) {
            playerFrame.scrollIntoView({ behavior: 'smooth', block: 'center' });
        }
    }
    
    // 5. Remove elemen iklan yang muncul dinamis (MutationObserver)
    var adSelectors = [
        '.banner', '.advertisement', '.ads', '.popup', '.modal', 
        '.overlay', '[class*="ad-"]', '[id*="ad-"]', '[class*="banner"]',
        'header', 'footer', '.navbar', '.sidebar', '.comments'
    ];
    
    var observer = new MutationObserver(function(mutations) {
        adSelectors.forEach(function(selector) {
            document.querySelectorAll(selector).forEach(function(el) {
                el.style.display = 'none';
                el.style.visibility = 'hidden';
            });
        });
    });
    
    observer.observe(document.body, { 
        childList: true, 
        subtree: true 
    });
    
    // 6. Auto-play setelah load
    setTimeout(tryAutoplay, 1000);
    setTimeout(tryAutoplay, 3000);
    
    // 7. Hapus class yang menyembunyikan player di beberapa site
    document.querySelectorAll('.player-container, .video-wrap, #player').forEach(function(el) {
        el.style.display = 'block';
        el.style.visibility = 'visible';
        el.style.opacity = '1';
    });
})();
"""

private fun isAdUrl(url: String): Boolean {
    return AD_DOMAINS.any { domain -> url.contains(domain, ignoreCase = true) }
}

private fun isWebPageUrl(url: String): Boolean {
    return url.startsWith("http") && !url.endsWith(".mp4") &&
           !url.endsWith(".m3u8") && !url.endsWith(".mkv") &&
           !url.endsWith(".webm") && !url.endsWith(".avi")
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun EpisodeWebView(
    videoUrl: String,
    modifier: Modifier = Modifier
) {
    var isLoading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf(false) }

    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { context ->
                WebView(context).apply {
                    // Settings WebView
                    settings.apply {
                        javaScriptEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        loadWithOverviewMode = true
                        useWideViewPort = true
                        builtInZoomControls = false
                        displayZoomControls = false
                        domStorageEnabled = true
                        allowFileAccess = true
                        allowContentAccess = true
                        mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        cacheMode = WebSettings.LOAD_DEFAULT
                        userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) " +
                            "AppleWebKit/537.36 (KHTML, like Gecko) " +
                            "Chrome/120.0.0.0 Mobile Safari/537.36"
                    }

                    setBackgroundColor(android.graphics.Color.BLACK)

                    // WebViewClient: intercept request, inject JS
                    webViewClient = object : WebViewClient() {

                        override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            isLoading = true
                            loadError = false
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            super.onPageFinished(view, url)
                            isLoading = false
                            // Inject CSS + JS hanya untuk halaman web (bukan video langsung)
                            if (isWebPageUrl(url)) {
                                view.evaluateJavascript(INJECT_JS, null)
                            }
                        }

                        override fun onReceivedError(
                            view: WebView?,
                            request: WebResourceRequest?,
                            error: WebResourceError?
                        ) {
                            super.onReceivedError(view, request, error)
                            val failingUrl = request?.url?.toString() ?: ""
                            if (!isAdUrl(failingUrl)) {
                                loadError = true
                                isLoading = false
                            }
                        }

                        @Deprecated("Deprecated in Java", ReplaceWith("onReceivedError(view, request, error)"))
                        @Suppress("DEPRECATION")
                        override fun onReceivedError(
                            view: WebView,
                            errorCode: Int,
                            description: String,
                            failingUrl: String
                        ) {
                            super.onReceivedError(view, errorCode, description, failingUrl)
                            // Jangan tampilkan error untuk iklan yang diblock
                            if (!isAdUrl(failingUrl)) {
                                loadError = true
                                isLoading = false
                            }
                        }

                        // Block iklan di level request
                        override fun shouldInterceptRequest(
                            view: WebView,
                            request: WebResourceRequest
                        ): WebResourceResponse? {
                            val url = request.url.toString()
                            if (isAdUrl(url)) {
                                // Return response kosong = iklan di-block
                                return WebResourceResponse(
                                    "text/plain",
                                    "utf-8",
                                    java.io.ByteArrayInputStream("".toByteArray())
                                )
                            }
                            return super.shouldInterceptRequest(view, request)
                        }

                        // Cegah navigasi keluar dari domain episode
                        override fun shouldOverrideUrlLoading(
                            view: WebView,
                            request: WebResourceRequest
                        ): Boolean {
                            val url = request.url.toString()
                            // Block domain iklan
                            if (isAdUrl(url)) return true
                            // Block navigasi ke domain luar yang tidak relevan
                            val blockedNavDomains = listOf(
                                "facebook.com", "twitter.com", "instagram.com",
                                "t.me", "telegram.org", "whatsapp.com",
                                "tiktok.com", "youtube.com"
                            )
                            return blockedNavDomains.any { url.contains(it) }
                        }
                    }

                    // Chrome client: izinkan fullscreen video
                    webChromeClient = object : WebChromeClient() {
                        private var customView: android.view.View? = null
                        private var customViewCallback: CustomViewCallback? = null

                        override fun onShowCustomView(view: android.view.View, callback: CustomViewCallback) {
                            customView = view
                            customViewCallback = callback
                            // Tambahkan view custom (fullscreen player)
                            (context as? android.app.Activity)?.window?.let { window ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                                } else {
                                    @Suppress("DEPRECATION")
                                    window.addFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                                }
                                val decorView = window.decorView as android.widget.FrameLayout
                                decorView.addView(view, android.widget.FrameLayout.LayoutParams(
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                                    android.widget.FrameLayout.LayoutParams.MATCH_PARENT
                                ))
                            }
                        }

                        override fun onHideCustomView() {
                            (context as? android.app.Activity)?.window?.let { window ->
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    window.insetsController?.show(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                                } else {
                                    @Suppress("DEPRECATION")
                                    window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN)
                                }
                                val decorView = window.decorView as android.widget.FrameLayout
                                customView?.let { decorView.removeView(it) }
                            }
                            customView = null
                            customViewCallback?.onCustomViewHidden()
                        }
                    }

                    loadUrl(videoUrl)
                }
            },
            update = { webView ->
                // Reload kalau URL berubah (ganti episode)
                if (webView.url != videoUrl) {
                    webView.loadUrl(videoUrl)
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Loading indicator
        if (isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "Memuat player...",
                        color = Color.White.copy(alpha = 0.7f),
                        fontSize = 12.sp
                    )
                }
            }
        }

        // Error state
        if (loadError) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "Gagal memuat video.\nPeriksa koneksi internet.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}