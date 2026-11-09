package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = PrimaryIndigo,
    onPrimary = Color.White,
    primaryContainer = PrimaryViolet,
    onPrimaryContainer = Color.White,
    secondary = AccentEmerald,
    onSecondary = Color.Black,
    secondaryContainer = SurfaceElevated,
    onSecondaryContainer = AccentEmerald,
    tertiary = AccentCyan,
    onTertiary = Color.Black,
    error = AlertCoral,
    onError = Color.White,
    background = MidnightDark,
    onBackground = TextPrimary,
    surface = SurfaceCard,
    onSurface = TextPrimary,
    surfaceVariant = SurfaceElevated,
    onSurfaceVariant = TextSecondary,
    outline = BorderSubtle
)

@Composable
fun SyncAlarmTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // SyncAlarm is designed with Midnight Precision dark palette
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}
