package com.example.data

object ApiConfig {
    /**
     * Base URL backend.
     * Contoh: "https://server.fromscratch.web.id/server/43312601" (tanpa trailing slash)
     */
    private const val DEFAULT_BASE_URL = "https://server.fromscratch.web.id/server/43312601"

    // You can later replace this with BuildConfig field or environment-based config.
    val baseUrl: String
        get() = DEFAULT_BASE_URL
}

