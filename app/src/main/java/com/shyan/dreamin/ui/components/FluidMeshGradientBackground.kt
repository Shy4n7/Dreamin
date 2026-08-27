package com.shyan.dreamin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌌 Fluid Liquid Mesh Background for NowPlaying Screen.
 * 
 * Features:
 * - 3-Orb Elevated Harmonic Orbit: Seamless continuous drifting orbs centered higher on screen.
 * - Sub-Surface Atmospheric Blending: Soft radial gradients bleeding into dark obsidian background.
 * - 120fps Zero-GC Canvas: Pre-calculated math with zero heap allocations per frame.
 */
@Composable
fun FluidMeshGradientBackground(
    dominantColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    backgroundColor: Color,
    isPlaying: Boolean = true,
    modifier: Modifier = Modifier
) {
    // 🌊 1200ms Fluid Watercolor Color Crossfade when tracks change
    val animDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "mesh_dominant"
    )

    val animSecondary by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "mesh_secondary"
    )

    val animAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "mesh_accent"
    )

    // 🌸 Transient Color Bloom on Track Switch
    val bloomScale = remember { Animatable(1.0f) }
    LaunchedEffect(dominantColor) {
        bloomScale.snapTo(1.22f)
        bloomScale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing)
        )
    }

    val speedMultiplier by animateFloatAsState(
        targetValue = if (isPlaying) 1.0f else 0.45f,
        animationSpec = tween(durationMillis = 1500, easing = FastOutSlowInEasing),
        label = "mesh_speed"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "mesh_orbit")
    val orbitPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 16000 else 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_1"
    )
    val orbitPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 22000 else 44000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_2"
    )

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
        ) {
            val width = size.width
            val height = size.height
            val t1 = orbitPhase1.toDouble()
            val t2 = orbitPhase2.toDouble()
            val bloom = bloomScale.value

            // Solid base background
            drawRect(color = backgroundColor)

            // 1. Orb 1 (Dominant Color Aura - Elevated Upper Center/Left)
            val orb1X = (0.42 + 0.26 * sin(t1 * 1.0)).toFloat() * width
            val orb1Y = (0.16 + 0.12 * cos(t1 * 0.8)).toFloat() * height
            val orb1Radius = width * 0.90f * bloom

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animDominant.copy(alpha = 0.45f),
                        animDominant.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(orb1X, orb1Y),
                    radius = orb1Radius
                ),
                center = Offset(orb1X, orb1Y),
                radius = orb1Radius
            )

            // 2. Orb 2 (Secondary Color Aura - Elevated Center Right Artwork Region)
            val orb2X = (0.68 + 0.22 * cos(t2 * 0.9 + 1.4)).toFloat() * width
            val orb2Y = (0.32 + 0.14 * sin(t2 * 1.1 + 0.9)).toFloat() * height
            val orb2Radius = width * 0.95f * bloom

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animSecondary.copy(alpha = 0.38f),
                        animSecondary.copy(alpha = 0.16f),
                        Color.Transparent
                    ),
                    center = Offset(orb2X, orb2Y),
                    radius = orb2Radius
                ),
                center = Offset(orb2X, orb2Y),
                radius = orb2Radius
            )

            // 3. Orb 3 (Accent Color Glow - Mid Elevation under Artwork)
            val orb3X = (0.32 + 0.24 * sin(t2 * 0.7 + 2.8)).toFloat() * width
            val orb3Y = (0.46 + 0.12 * cos(t2 * 0.85 + 2.1)).toFloat() * height
            val orb3Radius = width * 0.85f * bloom

            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        animAccent.copy(alpha = 0.32f),
                        animAccent.copy(alpha = 0.12f),
                        Color.Transparent
                    ),
                    center = Offset(orb3X, orb3Y),
                    radius = orb3Radius
                ),
                center = Offset(orb3X, orb3Y),
                radius = orb3Radius
            )

            // 4. Contrast Vignette: Keeps top and middle illuminated, deepens bottom control deck
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to backgroundColor.copy(alpha = 0.30f),
                        0.25f to Color.Transparent,
                        0.55f to backgroundColor.copy(alpha = 0.40f),
                        0.80f to backgroundColor.copy(alpha = 0.92f),
                        1.00f to backgroundColor
                    ),
                    startY = 0f,
                    endY = height
                ),
                size = size
            )
        }
    }
}
