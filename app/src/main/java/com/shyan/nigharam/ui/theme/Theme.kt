package com.shyan.nigharam.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

// ── Color Palette ─────────────────────────────────────────────────────────────

val DeepBlack    = Color(0xFF0A0A0F)
val SurfaceBlack = Color(0xFF12121A)
val CardBlack    = Color(0xFF1C1C28)
val CyanAccent   = Color(0xFF00E5FF)
val PurpleAccent = Color(0xFF7C4DFF)
val OnSurface    = Color(0xFFE8E8F0)
val OnSurfaceMed = Color(0xFF9090A8)
val OnSurfaceLow = Color(0xFF505068)

private val ResonanceDarkColors = darkColorScheme(
    primary         = CyanAccent,
    onPrimary       = DeepBlack,
    secondary       = PurpleAccent,
    onSecondary     = Color.White,
    background      = DeepBlack,
    onBackground    = OnSurface,
    surface         = SurfaceBlack,
    onSurface       = OnSurface,
    surfaceVariant  = CardBlack,
    onSurfaceVariant = OnSurfaceMed,
    outline         = OnSurfaceLow,
    error           = Color(0xFFFF6B6B)
)

@Composable
fun ResonanceTheme(content: @Composable () -> Unit) {
    val view = LocalView.current

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = DeepBlack.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
    }

    MaterialTheme(
        colorScheme = ResonanceDarkColors,
        content = content
    )
}
