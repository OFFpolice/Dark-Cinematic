package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val CyberColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonPurple,
    tertiary = NeonPink,
    background = CyberBackground,
    surface = CyberCard,
    onBackground = CyberOnCard,
    onSurface = CyberOnCard,
    surfaceVariant = CyberBorder,
    onSurfaceVariant = CyberMuted,
    primaryContainer = CyberActiveBg,
    onPrimaryContainer = Color.White,
    secondaryContainer = NeonPurple.copy(alpha = 0.2f),
    onSecondaryContainer = NeonCyan,
    error = Color(0xFFEF4444),
    onError = Color.White
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = true, // Force dark theme for cinematic vibe
    dynamicColor: Boolean = false, // Use our custom handcrafted colors
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = CyberColorScheme,
        typography = Typography,
        content = content
    )
}
