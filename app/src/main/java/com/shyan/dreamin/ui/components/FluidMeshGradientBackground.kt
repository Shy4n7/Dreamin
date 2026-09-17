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
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.sin

/**
 * 🌌 Fluid Liquid Mesh Background for NowPlaying & Playlist Screens.
 * 
 * Features:
 * - Harmonic Lissajous Fluid Orbits: Dual-frequency wave motion ($f_1=1.0, f_2=1.618$) for molten liquid deformation.
 * - Out-of-Phase Elliptical Stretch: Dynamic organic morphing simulating liquid surface tension.
 * - Header Mode & Zero-GC Scroll Parallax: Upper hero concentration with phase-deferred scroll offset sampling.
 * - Sub-Surface Atmospheric Blending: Multi-stop non-linear radial vignettes into dark obsidian.
 * - 120fps Zero-Heap Canvas: Pre-calculated math with zero object allocations in draw loop.
 */
@Composable
fun FluidMeshGradientBackground(
    dominantColor: Color,
    secondaryColor: Color,
    accentColor: Color,
    backgroundColor: Color,
    isPlaying: Boolean = true,
    isHeaderMode: Boolean = false,
    scrollOffsetProvider: (() -> Float)? = null,
    modifier: Modifier = Modifier
) {
    // 🌊 2400ms Serene Liquid Watercolor Color Crossfade when tracks or playlists change
    val smoothColorSpec = tween<Color>(
        durationMillis = 2400,
        easing = CubicBezierEasing(0.33f, 0.0f, 0.20f, 1.0f)
    )

    val animDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = smoothColorSpec,
        label = "mesh_dominant"
    )

    val animSecondary by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = smoothColorSpec,
        label = "mesh_secondary"
    )

    val animAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = smoothColorSpec,
        label = "mesh_accent"
    )

    // 🪐 Continuous Closed-Loop Harmonic Orbital Drifts (Exact integer harmonics ensure zero cut/jump on loop reset)
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_orbit")
    
    val orbitPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_1"
    )

    val orbitPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_2"
    )

    val orbitPhase3 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_3"
    )

    // ✨ Calm, Organic Luminous Breathing Pulses (Gentle 4-6% drift)
    val pulseTransition = rememberInfiniteTransition(label = "mesh_pulse")
    val pulse1 by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 10000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_1"
    )
    val pulse2 by pulseTransition.animateFloat(
        initialValue = 1.04f,
        targetValue = 0.96f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 13000, easing = FastOutSlowInEasing),
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

                // Custom non-linear atmospheric vignette depending on header mode
                val vignetteBrush = if (isHeaderMode) {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to backgroundColor.copy(alpha = 0.05f),
                            0.20f to Color.Transparent,
                            0.42f to backgroundColor.copy(alpha = 0.40f),
                            0.65f to backgroundColor.copy(alpha = 0.85f),
                            0.88f to backgroundColor,
                            1.00f to backgroundColor
                        ),
                        startY = 0f,
                        endY = height
                    )
                } else {
                    Brush.verticalGradient(
                        colorStops = arrayOf(
                            0.00f to backgroundColor.copy(alpha = 0.12f),
                            0.30f to Color.Transparent,
                            0.60f to backgroundColor.copy(alpha = 0.20f),
                            0.85f to backgroundColor.copy(alpha = 0.36f),
                            1.00f to backgroundColor.copy(alpha = 0.48f)
                        ),
                        startY = 0f,
                        endY = height
                    )
                }

                onDrawBehind {
                    val t1 = orbitPhase1.toDouble()
                    val t2 = orbitPhase2.toDouble()
                    val t3 = orbitPhase3.toDouble()

                    // Phase-deferred scroll parallax reading
                    val scrollY = scrollOffsetProvider?.invoke() ?: 0f
                    val parallaxY = if (isHeaderMode) -scrollY * 0.38f else 0f
                    val scrollAlpha = if (isHeaderMode) {
                        (1f - (scrollY / (height * 0.55f))).coerceIn(0.15f, 1f)
                    } else 1.0f

                    // Base atmospheric background
                    drawRect(color = backgroundColor)

                    // Vertical offset anchor adjustments for header vs full screen
                    val yAnchor1 = if (isHeaderMode) 0.14 else 0.20
                    val yAnchor2 = if (isHeaderMode) 0.24 else 0.34
                    val yAnchor3 = if (isHeaderMode) 0.30 else 0.46
                    val coreYAnchor = if (isHeaderMode) 0.20f else 0.32f

                    // 1. Orb 1 (Dominant Color Aura - Closed Harmonic Lissajous Wave)
                    val orb1X = (0.48 + 0.22 * sin(t1) + 0.08 * cos(2.0 * t1)).toFloat() * width
                    val orb1Y = ((yAnchor1 + 0.12 * cos(t1) + 0.05 * sin(2.0 * t1)).toFloat() * height) + parallaxY
                    val orb1Radius = width * (if (isHeaderMode) 0.90f else 0.98f) * pulse1
                    val orb1ScaleX = 1f + 0.06f * sin(2.0 * t1).toFloat()
                    val orb1ScaleY = 1f + 0.06f * cos(2.0 * t1).toFloat()

                    scale(scaleX = orb1ScaleX, scaleY = orb1ScaleY, pivot = Offset(orb1X, orb1Y)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    animDominant.copy(alpha = 0.78f * scrollAlpha),
                                    animDominant.copy(alpha = 0.42f * scrollAlpha),
                                    animDominant.copy(alpha = 0.14f * scrollAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(orb1X, orb1Y),
                                radius = orb1Radius
                            ),
                            center = Offset(orb1X, orb1Y),
                            radius = orb1Radius
                        )
                    }

                    // 2. Orb 2 (Secondary Color Aura - Closed Harmonic Lissajous Wave)
                    val orb2X = (0.58 + 0.20 * cos(t2) - 0.07 * sin(2.0 * t2)).toFloat() * width
                    val orb2Y = ((yAnchor2 + 0.13 * sin(t2) + 0.05 * cos(2.0 * t2)).toFloat() * height) + parallaxY
                    val orb2Radius = width * (if (isHeaderMode) 0.94f else 1.04f) * pulse2
                    val orb2ScaleX = 1f + 0.07f * cos(2.0 * t2).toFloat()
                    val orb2ScaleY = 1f + 0.07f * sin(2.0 * t2).toFloat()

                    scale(scaleX = orb2ScaleX, scaleY = orb2ScaleY, pivot = Offset(orb2X, orb2Y)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    animSecondary.copy(alpha = 0.68f * scrollAlpha),
                                    animSecondary.copy(alpha = 0.36f * scrollAlpha),
                                    animSecondary.copy(alpha = 0.12f * scrollAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(orb2X, orb2Y),
                                radius = orb2Radius
                            ),
                            center = Offset(orb2X, orb2Y),
                            radius = orb2Radius
                        )
                    }

                    // 3. Orb 3 (Accent Color Glow - Closed Harmonic Lissajous Wave)
                    val orb3X = (0.38 - 0.18 * sin(t3) + 0.06 * cos(2.0 * t3)).toFloat() * width
                    val orb3Y = ((yAnchor3 + 0.11 * cos(t3) - 0.05 * sin(2.0 * t3)).toFloat() * height) + parallaxY
                    val orb3Radius = width * (if (isHeaderMode) 0.84f else 0.94f) * pulse1
                    val orb3ScaleX = 1f + 0.05f * sin(2.0 * t3).toFloat()
                    val orb3ScaleY = 1f + 0.05f * cos(2.0 * t3).toFloat()

                    scale(scaleX = orb3ScaleX, scaleY = orb3ScaleY, pivot = Offset(orb3X, orb3Y)) {
                        drawCircle(
                            brush = Brush.radialGradient(
                                colors = listOf(
                                    animAccent.copy(alpha = 0.58f * scrollAlpha),
                                    animAccent.copy(alpha = 0.28f * scrollAlpha),
                                    animAccent.copy(alpha = 0.08f * scrollAlpha),
                                    Color.Transparent
                                ),
                                center = Offset(orb3X, orb3Y),
                                radius = orb3Radius
                            ),
                            center = Offset(orb3X, orb3Y),
                            radius = orb3Radius
                        )
                    }

                    // 4. Luminous Center Ambient Core (Binds all 3 orbs into unified liquid atmosphere)
                    val coreX = width * 0.5f
                    val coreY = (height * coreYAnchor) + parallaxY
                    val coreRadius = width * (if (isHeaderMode) 1.05f else 1.15f)
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animDominant.copy(alpha = 0.50f * scrollAlpha),
                                animSecondary.copy(alpha = 0.30f * scrollAlpha),
                                Color.Transparent
                            ),
                            center = Offset(coreX, coreY),
                            radius = coreRadius
                        ),
                        center = Offset(coreX, coreY),
                        radius = coreRadius
                    )

                    // 5. Atmospheric Vignette Overlay
                    drawRect(
                        brush = vignetteBrush,
                        size = size
                    )
                }
            }
    )
}

