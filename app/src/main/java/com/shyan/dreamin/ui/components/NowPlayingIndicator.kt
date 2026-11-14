package com.shyan.dreamin.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shyan.dreamin.ui.theme.LocalDreaminColors

val LocalPlaybackActive = compositionLocalOf { false }

private val BAR_PERIODS_SECONDS = floatArrayOf(0.62f, 0.48f, 0.71f, 0.55f)
private val BAR_REST_HEIGHTS = floatArrayOf(0.45f, 0.75f, 0.55f, 0.35f)
private const val BAR_SWING = 0.55f
private const val BAR_MIN_HEIGHT = 0.2f

@Composable
fun NowPlayingIndicator(
    modifier: Modifier = Modifier,
    color: Color = LocalDreaminColors.current.primary,
    width: Dp = 18.dp,
    height: Dp = 14.dp
) {
    val isActive = LocalPlaybackActive.current
    val clock = rememberPlaybackClock(isActive, rampMillis = 600)
    Canvas(
        modifier = modifier
            .size(width = width, height = height)
            .semantics { contentDescription = if (isActive) "Now playing" else "Paused" }
    ) {
        val barCount = BAR_PERIODS_SECONDS.size
        val gap = size.width * 0.12f
        val barWidth = (size.width - gap * (barCount - 1)) / barCount
        val corner = CornerRadius(barWidth / 2f, barWidth / 2f)
        for (i in 0 until barCount) {
            val swing = (clock.wave(BAR_PERIODS_SECONDS[i]) - 0.5f) * BAR_SWING * clock.intensity
            val fraction = (BAR_REST_HEIGHTS[i] + swing).coerceIn(BAR_MIN_HEIGHT, 1f)
            val barHeight = size.height * fraction
            drawRoundRect(
                color = color,
                topLeft = Offset(i * (barWidth + gap), size.height - barHeight),
                size = Size(barWidth, barHeight),
                cornerRadius = corner
            )
        }
    }
}
