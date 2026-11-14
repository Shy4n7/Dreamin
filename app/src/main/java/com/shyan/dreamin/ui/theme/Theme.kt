package com.shyan.dreamin.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private fun DreaminColors.toMaterialScheme() = darkColorScheme(
    primary = primary,
    onPrimary = onPrimary,
    secondary = secondary,
    onSecondary = background,
    tertiary = tertiary,
    background = background,
    onBackground = onSurface,
    surface = surfaceContainer,
    onSurface = onSurface,
    surfaceVariant = surfaceHigh,
    onSurfaceVariant = onSurfaceVariant,
    outline = onSurfaceLow,
    outlineVariant = outlineVariant,
    error = error
)

@Composable
fun ResonanceTheme(
    colors: DreaminColors = SonicNocturneColors,
    content: @Composable () -> Unit
) {
    val view = LocalView.current

    SideEffect {
        val window = (view.context as Activity).window
        window.statusBarColor = colors.background.toArgb()
        window.navigationBarColor = colors.background.toArgb()
        WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        WindowCompat.getInsetsController(window, view).isAppearanceLightNavigationBars = false
    }

    CompositionLocalProvider(LocalDreaminColors provides colors) {
        MaterialTheme(
            colorScheme = colors.toMaterialScheme(),
            typography = SonicNocturneTypography,
            shapes = DreaminShapes.material,
            content = content
        )
    }
}
