package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val CinematicDarkScheme = darkColorScheme(
    // Primary – Electric Violet
    primary            = ElectricViolet,
    onPrimary          = VioletOnPrimary,
    primaryContainer   = VioletContainer,
    onPrimaryContainer = VioletOnContainer,
    inversePrimary     = VioletInverse,

    // Secondary – Neon Cyan
    secondary            = NeonCyan,
    onSecondary          = Color(0xFF003543).let { androidx.compose.ui.graphics.Color(0xFF003543) },
    secondaryContainer   = CyanContainer,
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF00566B),

    // Tertiary – Crimson Spark
    tertiary            = CrimsonSpark,
    onTertiary          = androidx.compose.ui.graphics.Color(0xFF67001F),
    tertiaryContainer   = CrimsonContainer,
    onTertiaryContainer = androidx.compose.ui.graphics.Color(0xFFFFDEE0),

    // Error
    error            = ErrorRed,
    onError          = androidx.compose.ui.graphics.Color(0xFF690005),
    errorContainer   = ErrorContainer,
    onErrorContainer = androidx.compose.ui.graphics.Color(0xFFFFDAD6),

    // Backgrounds & Surfaces
    background   = Obsidian,
    onBackground = TextPrimary,
    surface      = SurfaceMid,
    onSurface    = TextPrimary,

    surfaceVariant   = SurfaceHighest,
    onSurfaceVariant = TextSecondary,

    outline        = OutlineColor,
    outlineVariant = OutlineVariant,

    inverseSurface   = TextPrimary,
    inverseOnSurface = androidx.compose.ui.graphics.Color(0xFF283044),

    surfaceTint = ElectricViolet,
    scrim       = Obsidian,
)

private val CinematicLightScheme = lightColorScheme(
    primary            = VioletInverse,
    onPrimary          = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    primaryContainer   = VioletOnContainer,
    onPrimaryContainer = VioletOnPrimary,
    secondary          = androidx.compose.ui.graphics.Color(0xFF006781),
    onSecondary        = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    secondaryContainer = androidx.compose.ui.graphics.Color(0xFFB7EAFF),
    onSecondaryContainer = androidx.compose.ui.graphics.Color(0xFF001F28),
    tertiary            = androidx.compose.ui.graphics.Color(0xFF9E0038),
    onTertiary          = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    background          = androidx.compose.ui.graphics.Color(0xFFF8FAFC),
    onBackground        = androidx.compose.ui.graphics.Color(0xFF0B1326),
    surface             = androidx.compose.ui.graphics.Color(0xFFFFFFFF),
    onSurface           = androidx.compose.ui.graphics.Color(0xFF0B1326),
    outline             = androidx.compose.ui.graphics.Color(0xFF6B7280),
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,   // off — preserve Cinematic Pulse branding
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) CinematicDarkScheme else CinematicLightScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = Typography,
        content     = content,
    )
}

// ── Inline Color alias so composables can use Color(0xFF...) directly ──
private fun Color(value: Long) = androidx.compose.ui.graphics.Color(value.toULong())