package com.shyan.dreamin.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Real-Time Bass Beat Drop Ripple & Ambient Artwork Halo.
 * 
 * Features:
 * - ⚡ Strict Bass Drop Response: Concentric ripples & thumps ONLY trigger on actual bass kicks and 808 drops.
 * - 🌙 Calm Ambient Resting Glow: Soft, soothing halo when there is no bass transient.
 */
@Composable
fun PulsingArtworkHalo(
    dominantColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
    bassEnergy: Float = 0f,
    baseSize: Dp = 290.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "halo_ambient_drift")

    // Gentle ambient resting glow (slow, calm breathing)
    val ambientScale by infiniteTransition.animateFloat(
        initialValue = 0.98f,
        targetValue = if (isPlaying) 1.03f else 1.00f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isPlaying) 3200 else 4500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_scale"
    )

    val ambientAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = if (isPlaying) 0.50f else 0.30f,
        animationSpec = infiniteRepeatable(
            animation = tween(
                durationMillis = if (isPlaying) 3200 else 4500,
                easing = FastOutSlowInEasing
            ),
            repeatMode = RepeatMode.Reverse
        ),
        label = "ambient_alpha"
    )

    // Dynamic Bass-Driven Shockwave (Strictly 0 alpha when no bass hit)
    val bassImpactScale = 1.0f + (bassEnergy * 0.45f)
    val bassImpactAlpha = (bassEnergy * 0.75f).coerceIn(0f, 1f)

    val subBassRingScale = 1.05f + (bassEnergy * 0.65f)
    val subBassRingAlpha = (bassEnergy * 0.45f).coerceIn(0f, 1f)

    val coreGlowScale = ambientScale + (bassEnergy * 0.12f)
    val coreGlowAlpha = (ambientAlpha + (bassEnergy * 0.40f)).coerceIn(0f, 1f)

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        // Outer Secondary Sub-Bass Shockwave (Only visible on heavy bass hit)
        if (subBassRingAlpha > 0.02f) {
            Box(
                modifier = Modifier
                    .size(baseSize)
                    .scale(subBassRingScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                dominantColor.copy(alpha = subBassRingAlpha * 0.7f),
                                dominantColor.copy(alpha = subBassRingAlpha * 0.2f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Primary Bass Drop Shockwave Ripple (Only visible on kick/bass drop)
        if (bassImpactAlpha > 0.02f) {
            Box(
                modifier = Modifier
                    .size(baseSize)
                    .scale(bassImpactScale)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                dominantColor.copy(alpha = bassImpactAlpha * 0.9f),
                                dominantColor.copy(alpha = bassImpactAlpha * 0.4f),
                                Color.Transparent
                            )
                        )
                    )
            )
        }

        // Core Ambient Halo directly behind artwork
        Box(
            modifier = Modifier
                .size(baseSize)
                .scale(coreGlowScale)
                .clip(CircleShape)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            dominantColor.copy(alpha = coreGlowAlpha),
                            dominantColor.copy(alpha = coreGlowAlpha * 0.50f),
                            dominantColor.copy(alpha = coreGlowAlpha * 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )
    }
}
