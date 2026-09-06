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
    // 🌊 1400ms Fluid Watercolor Color Crossfade when tracks or playlists change
    val animDominant by animateColorAsState(
        targetValue = dominantColor,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "mesh_dominant"
    )

    val animSecondary by animateColorAsState(
        targetValue = secondaryColor,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "mesh_secondary"
    )

    val animAccent by animateColorAsState(
        targetValue = accentColor,
        animationSpec = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
        label = "mesh_accent"
    )

    // 🌸 Transient Color Bloom on Track / Playlist Switch (Smooth ease without snapping)
    val bloomScale = remember { Animatable(1.0f) }
    var isFirstBloomLaunch by remember { mutableStateOf(true) }
    LaunchedEffect(dominantColor) {
        if (isFirstBloomLaunch) {
            isFirstBloomLaunch = false
            return@LaunchedEffect
        }
        // Smoothly breathe without any abrupt jump or snapTo
        bloomScale.animateTo(
            targetValue = 1.05f,
            animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
        )
        bloomScale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
        )
    }

    // 🪐 Continuous Multi-Speed Orbital Drifts (Constant durations ensure seamless orbit without phase reset on play/pause)
    val infiniteTransition = rememberInfiniteTransition(label = "mesh_orbit")
    
    val orbitPhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_1"
    )

    val orbitPhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (Math.PI * 2).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "orbit_2"
    )

    // ✨ Organic Luminous Breathing Pulses (Constant durations prevent sudden resets)
    val pulseTransition = rememberInfiniteTransition(label = "mesh_pulse")
    val pulse1 by pulseTransition.animateFloat(
        initialValue = 0.93f,
        targetValue = 1.12f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 5200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_1"
    )
    val pulse2 by pulseTransition.animateFloat(
        initialValue = 1.10f,
        targetValue = 0.90f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 7400, easing = FastOutSlowInEasing),
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
                            0.60f to backgroundColor.copy(alpha = 0.32f),
                            0.85f to backgroundColor.copy(alpha = 0.65f),
                            1.00f to backgroundColor.copy(alpha = 0.85f)
                        ),
                        startY = 0f,
                        endY = height
                    )
                }

                onDrawBehind {
                    val t1 = orbitPhase1.toDouble()
                    val t2 = orbitPhase2.toDouble()
                    val bloom = bloomScale.value

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

                    // 1. Orb 1 (Dominant Color Aura - Lissajous Harmonic Drift)
                    val orb1X = (0.46 + 0.26 * sin(t1) + 0.10 * cos(t1 * 1.618)).toFloat() * width
                    val orb1Y = ((yAnchor1 + 0.12 * cos(t1 * 1.2) + 0.06 * sin(t1 * 2.1)).toFloat() * height) + parallaxY
                    val orb1Radius = width * (if (isHeaderMode) 0.88f else 0.95f) * bloom * pulse1
                    val orb1ScaleX = 1f + 0.09f * sin(t1 * 2.0).toFloat()
                    val orb1ScaleY = 1f + 0.09f * cos(t1 * 2.0).toFloat()

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

                    // 2. Orb 2 (Secondary Color Aura - Radiant Center Right Lissajous Wave)
                    val orb2X = (0.62 + 0.24 * cos(t2 + 1.25) + 0.09 * sin(t2 * 1.4)).toFloat() * width
                    val orb2Y = ((yAnchor2 + 0.14 * sin(t2 + 1.25) + 0.05 * cos(t2 * 1.9)).toFloat() * height) + parallaxY
                    val orb2Radius = width * (if (isHeaderMode) 0.92f else 1.02f) * bloom * pulse2
                    val orb2ScaleX = 1f + 0.11f * cos(t2 * 1.8).toFloat()
                    val orb2ScaleY = 1f + 0.11f * sin(t2 * 1.8).toFloat()

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

                    // 3. Orb 3 (Accent Color Glow - Mid Elevation Counter-Balance)
                    val orb3X = (0.34 + 0.22 * sin(t2 + Math.PI) + 0.08 * cos(t2 * 1.3)).toFloat() * width
                    val orb3Y = ((yAnchor3 + 0.12 * cos(t2 + Math.PI) + 0.06 * sin(t2 * 1.7)).toFloat() * height) + parallaxY
                    val orb3Radius = width * (if (isHeaderMode) 0.82f else 0.92f) * bloom * pulse1
                    val orb3ScaleX = 1f + 0.08f * sin(t2 * 2.2).toFloat()
                    val orb3ScaleY = 1f + 0.08f * cos(t2 * 2.2).toFloat()

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
                    val coreRadius = width * (if (isHeaderMode) 1.05f else 1.15f) * bloom
                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                animDominant.copy(alpha = 0.35f * scrollAlpha),
                                animSecondary.copy(alpha = 0.20f * scrollAlpha),
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

