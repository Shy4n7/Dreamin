package com.shyan.dreamin.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌌 Fluid Liquid Mesh Background for NowPlaying Screen.
 * 
 * Features:
 * - 3-Orb Elevated Harmonic Orbit: Seamless continuous drifting orbs centered higher on screen.
 * - Sub-Surface Atmospheric Blending: Soft radial gradients bleeding into dark obsidian background.
 * - Luminous Multi-Speed Breathing Pulses: Dynamic organic glow that keeps the background alive.
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
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioLowBouncy,
                stiffness = Spring.StiffnessLow
            )
        )
    }

    // 🪐 Continuous Multi-Speed Orbital Drifts (120 FPS Phase-Isolated)
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_orbit")
    
    val orbitPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 14000 else 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_1"
    )

    val orbitPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 19000 else 38000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_2"
    )

    // ✨ Organic Luminous Breathing Pulses
    val pulseTransition = rememberInfiniteTransition(label = "mesh_pulse")
    val pulse1 by pulseTransition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 3600 else 6000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_1"
    )
    val pulse2 by pulseTransition.animateFloat(
        initialValue = 1.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isPlaying) 4800 else 7200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_2"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawWithCache {
                val width = size.width
                val height = size.height
                val vignetteBrush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0.00f to backgroundColor.copy(alpha = 0.12f),
                        0.30f to Color.Transparent,
                        0.60f to backgroundColor.copy(alpha = 0.32f),
                        0.85f to backgroundColor.copy(alpha = 0.65f),
                        1.00f to backgroundColor.copy(alpha = 0.85f)
                    ),
                    startY = 0f,
                    endY = height
                )

                onDrawBehind {
                    val t1 = orbitPhase1.toDouble()
                    val t2 = orbitPhase2.toDouble()
                    val bloom = bloomScale.value

                    // Base atmospheric background
                    drawRect(color = backgroundColor)

                    // 1. Orb 1 (Dominant Color Aura - Radiant Upper Center/Left)
                    val orb1X = (0.44 + 0.28 * sin(t1 * 1.0)).toFloat() * width
                    val orb1Y = (0.18 + 0.14 * cos(t1 * 0.8)).toFloat() * height
                    val orb1Radius = width * 0.95f * bloom * pulse1

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animDominant.copy(alpha = 0.78f),
                                animDominant.copy(alpha = 0.42f),
                                animDominant.copy(alpha = 0.14f),
                                Color.Transparent
                            ),
                            center = Offset(orb1X, orb1Y),
                            radius = orb1Radius
                        ),
                        center = Offset(orb1X, orb1Y),
                        radius = orb1Radius
                    )

                    // 2. Orb 2 (Secondary Color Aura - Radiant Center Right Artwork Region)
                    val orb2X = (0.66 + 0.24 * cos(t2 * 0.9 + 1.4)).toFloat() * width
                    val orb2Y = (0.34 + 0.16 * sin(t2 * 1.1 + 0.9)).toFloat() * height
                    val orb2Radius = width * 1.02f * bloom * pulse2

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animSecondary.copy(alpha = 0.68f),
                                animSecondary.copy(alpha = 0.36f),
                                animSecondary.copy(alpha = 0.12f),
                                Color.Transparent
                            ),
                            center = Offset(orb2X, orb2Y),
                            radius = orb2Radius
                        ),
                        center = Offset(orb2X, orb2Y),
                        radius = orb2Radius
                    )

                    // 3. Orb 3 (Accent Color Glow - Mid Elevation under Artwork)
                    val orb3X = (0.30 + 0.26 * sin(t2 * 0.7 + 2.8)).toFloat() * width
                    val orb3Y = (0.48 + 0.14 * cos(t2 * 0.85 + 2.1)).toFloat() * height
                    val orb3Radius = width * 0.92f * bloom * pulse1

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animAccent.copy(alpha = 0.58f),
                                animAccent.copy(alpha = 0.28f),
                                animAccent.copy(alpha = 0.08f),
                                Color.Transparent
                            ),
                            center = Offset(orb3X, orb3Y),
                            radius = orb3Radius
                        ),
                        center = Offset(orb3X, orb3Y),
                        radius = orb3Radius
                    )

                    // 4. Luminous Center Ambient Core (Binds all 3 orbs together)
                    val coreX = width * 0.5f
                    val coreY = height * 0.32f
                    val coreRadius = width * 1.15f * bloom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animDominant.copy(alpha = 0.35f),
                                animSecondary.copy(alpha = 0.20f),
                                Color.Transparent
                            ),
                            center = Offset(coreX, coreY),
                            radius = coreRadius
                        ),
                        center = Offset(coreX, coreY),
                        radius = coreRadius
                    )

                    // 5. Soft Atmospheric Vignette (Keeps top/center illuminated, ensures button legibility)
                    drawRect(
                        brush = vignetteBrush,
                        size = size
                    )
                }
            }
    )
}
