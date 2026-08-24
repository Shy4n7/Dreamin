package com.shyan.dreamin.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

val DeepBlack    = Color(0xFF07070A)
val SurfaceBlack = Color(0xFF0F0F16)
val CardBlack    = Color(0xFF14141E)
val CardActive   = Color(0xFF1C1C2A)
val CyanAccent   = Color(0xFF06B6D4)
val PurpleAccent = Color(0xFF8B5CF6)
val PinkAccent   = Color(0xFFEC4899)
val OnSurface    = Color.White.copy(alpha = 0.96f)
val OnSurfaceMed = Color.White.copy(alpha = 0.65f)
val OnSurfaceLow = Color.White.copy(alpha = 0.38f)
val GlassBorder  = Color.White.copy(alpha = 0.08f)

private val ResonanceDarkColors = darkColorScheme(
    primary          = PurpleAccent,
    onPrimary        = Color.White,
    secondary        = CyanAccent,
    onSecondary      = DeepBlack,
    tertiary         = PinkAccent,
    background       = DeepBlack,
    onBackground     = OnSurface,
    surface          = SurfaceBlack,
    onSurface        = OnSurface,
    surfaceVariant   = CardBlack,
    onSurfaceVariant = OnSurfaceMed,
    outline          = OnSurfaceLow,
    outlineVariant   = GlassBorder,
    error            = Color(0xFFF43F5E)
)

@Composable
fun ResonanceTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = DeepBlack.toArgb()
        window.navigationBarColor = DeepBlack.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }

    MaterialTheme(
        colorScheme = ResonanceDarkColors,
        typography = SonicNocturneTypography,
        content = content
    )
}
