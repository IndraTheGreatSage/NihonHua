package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val DarkColorScheme = darkColorScheme(
    primary = ElectricCyan,
    secondary = PremiumGold,
    tertiary = HotPink,
    background = SpaceBlack,
    surface = SlateCard,
    onBackground = SlateTextPrimary,
    onSurface = SlateTextSecondary,
    surfaceVariant = SoftGrayBorder,
    outline = SoftGrayBorder
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFF00ACC1),
    secondary = Color(0xFFF57C00),
    tertiary = Color(0xFFD81B60),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF475569),
    surfaceVariant = Color(0xFFE2E8F0),
    outline = Color(0xFFCBD5E1)
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force Dark Mode by default for cinema aesthetics
    dynamicColor: Boolean = false, // Set false to preserve our custom premium branding
    content: @Composable () -> Unit,
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
