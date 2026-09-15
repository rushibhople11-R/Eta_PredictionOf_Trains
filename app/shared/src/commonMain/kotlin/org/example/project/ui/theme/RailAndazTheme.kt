package org.example.project.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val RailDark = Color(0xFF0B0F19)
val RailCard = Color(0xFF131A2A)
val RailCardBorder = Color(0xFF1E293B)
val RailCyan = Color(0xFF00E5FF)
val RailAmber = Color(0xFFFF9100)
val RailGreen = Color(0xFF00E676)
val RailRed = Color(0xFFFF5252)
val RailTextPrimary = Color(0xFFF1F5F9)
val RailTextSecondary = Color(0xFF94A3B8)
val RailMuted = Color(0xFF64748B)

private val DarkColorScheme = darkColorScheme(
    primary = RailCyan,
    onPrimary = Color(0xFF00363D),
    secondary = RailAmber,
    onSecondary = Color(0xFF421C00),
    background = RailDark,
    onBackground = RailTextPrimary,
    surface = RailCard,
    onSurface = RailTextPrimary,
    surfaceVariant = RailCardBorder,
    onSurfaceVariant = RailTextSecondary,
    outline = RailCardBorder
)

@Composable
fun RailAndazTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
