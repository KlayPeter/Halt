package com.klaypeter.halt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF6C5CE7),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDEAFF),
    onPrimaryContainer = Color(0xFF2B2450),
    secondary = Color(0xFF5B8DEF),
    background = Color(0xFFF5F5F9),
    onBackground = Color(0xFF16161C),
    surface = Color.White,
    onSurface = Color(0xFF16161C),
    surfaceVariant = Color(0xFFEDEDF3),
    onSurfaceVariant = Color(0xFF8A8A96),
    outlineVariant = Color(0xFFEDEDF2),
    error = Color(0xFFFF5A5F),
    onError = Color.White,
    errorContainer = Color(0xFFFFECEC),
    onErrorContainer = Color(0xFF8C1D22)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9A8CFF),
    onPrimary = Color(0xFF1E1A38),
    primaryContainer = Color(0xFF2E2856),
    onPrimaryContainer = Color(0xFFE5E1FF),
    secondary = Color(0xFF7FA6FF),
    background = Color(0xFF0E0E13),
    onBackground = Color(0xFFF2F2F7),
    surface = Color(0xFF17171F),
    onSurface = Color(0xFFF2F2F7),
    surfaceVariant = Color(0xFF23232D),
    onSurfaceVariant = Color(0xFF9A9AA6),
    outlineVariant = Color(0xFF282833),
    error = Color(0xFFFF6B6F),
    onError = Color(0xFF3A0F11),
    errorContainer = Color(0xFF3A1B1D),
    onErrorContainer = Color(0xFFFFD9DA)
)

@Composable
fun ScreenTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}
