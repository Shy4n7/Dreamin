package com.shyan.dreamin.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class DreaminColors(
    val background: Color,
    val surfaceContainer: Color,
    val surfaceHigh: Color,
    val surfaceHighest: Color,
    val surfaceBright: Color,
    val primary: Color,
    val primaryDim: Color,
    val primaryDeep: Color,
    val secondary: Color,
    val tertiary: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val onSurfaceLow: Color,
    val outlineVariant: Color,
    val error: Color,
    val onErrorContainer: Color,
    val success: Color,
    val onPrimary: Color,
    val scrim: Color,
    val sheenWarm: Color,
    val sheenCool: Color,
    val meshDominant: Color,
    val meshSecondary: Color,
    val meshAccent: Color,
    val brandYoutube: Color
)

val SonicNocturneColors = DreaminColors(
    background = Color(0xFF07070A),
    surfaceContainer = Color(0xFF0F0F16),
    surfaceHigh = Color(0xFF14141E),
    surfaceHighest = Color(0xFF1C1C2A),
    surfaceBright = Color(0xFF242436),
    primary = Color(0xFF8B5CF6),
    primaryDim = Color(0xFF6D28D9),
    primaryDeep = Color(0xFF2E1065),
    secondary = Color(0xFF06B6D4),
    tertiary = Color(0xFFEC4899),
    onSurface = Color.White.copy(alpha = 0.96f),
    onSurfaceVariant = Color.White.copy(alpha = 0.65f),
    onSurfaceLow = Color.White.copy(alpha = 0.38f),
    outlineVariant = Color.White.copy(alpha = 0.08f),
    error = Color(0xFFF43F5E),
    onErrorContainer = Color(0xFFFF8A80),
    success = Color(0xFF00E676),
    onPrimary = Color.White,
    scrim = Color(0xFF080511),
    sheenWarm = Color(0xFFFF80BF),
    sheenCool = Color(0xFF80D8FF),
    meshDominant = Color(0xFF6C5CE7),
    meshSecondary = Color(0xFF8E44AD),
    meshAccent = Color(0xFF4A235A),
    brandYoutube = Color(0xFFFF0000)
)

val LocalDreaminColors = compositionLocalOf { SonicNocturneColors }
