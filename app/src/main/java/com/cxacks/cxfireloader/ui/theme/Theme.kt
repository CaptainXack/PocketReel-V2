package com.captainxack.pocketreel.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PocketReelScheme = darkColorScheme(
    primary = Color(0xFF43B5FF),
    onPrimary = Color(0xFF06111B),
    secondary = Color(0xFF8BCAFF),
    onSecondary = Color(0xFF06111B),
    background = Color(0xFF12151C),
    onBackground = Color(0xFFF3F7FF),
    surface = Color(0xFF1B2230),
    onSurface = Color(0xFFF3F7FF),
    surfaceVariant = Color(0xFF263247),
    onSurfaceVariant = Color(0xFFA4B1C9),
    error = Color(0xFFFF7C7C),
    onError = Color(0xFF2E0000),
)

@Composable
fun PocketReelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PocketReelScheme,
        content = content,
    )
}
