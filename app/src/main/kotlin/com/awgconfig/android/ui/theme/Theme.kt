package com.awgconfig.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Неоновый тёмный стиль в духе awgconfig.com. */
object NeonPalette {
    val background = Color(0xFF0A0E14)
    val surface = Color(0xFF0D1117)
    val surfaceVariant = Color(0xFF121926)
    val outline = Color(0xFF1F2A37)
    val accent = Color(0xFFA78BFA)   // фиолетовый
    val accent2 = Color(0xFF34D399)  // зелёный
    val accent3 = Color(0xFF60A5FA)  // голубой (WARP)
    val accent4 = Color(0xFF2DD4BF)  // бирюза (MASQUE)
    val amber = Color(0xFFFBBF24)
    val red = Color(0xFFF87171)
    val textPrimary = Color(0xFFE6EDF3)
    val textSecondary = Color(0xFF8B949E)
    val mono = Color(0xFF7EE787)
}

private val DarkScheme = darkColorScheme(
    primary = NeonPalette.accent,
    onPrimary = Color(0xFF0A0E14),
    secondary = NeonPalette.accent2,
    onSecondary = Color(0xFF0A0E14),
    tertiary = NeonPalette.accent3,
    onTertiary = Color(0xFF0A0E14),
    background = NeonPalette.background,
    onBackground = NeonPalette.textPrimary,
    surface = NeonPalette.surface,
    onSurface = NeonPalette.textPrimary,
    surfaceVariant = NeonPalette.surfaceVariant,
    onSurfaceVariant = NeonPalette.textSecondary,
    outline = NeonPalette.outline,
    error = NeonPalette.red,
    onError = Color(0xFF0A0E14),
)

@Composable
fun AwgConfigTheme(content: @Composable () -> Unit) {
    // приложение осознанно всегда тёмное неоновое
    MaterialTheme(
        colorScheme = DarkScheme,
        content = content,
    )
}
