@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.shyan.dreamin.ui.components

import com.shyan.dreamin.ui.theme.DreaminMotion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.FloatState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sign

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val MIN_FRAME_SECONDS = 0.001f
private const val MAX_FRAME_SECONDS = 0.05f
private const val IDLE_POLL_MS = 200L
private const val STOPPED_SPEED = 0.001f
private const val RUBBER_BAND_RESISTANCE = 0.35f

@Stable
class PlaybackClock internal constructor(
    private val elapsed: FloatState,
    private val speed: State<Float>
) {
    val seconds: Float get() = elapsed.floatValue
    val intensity: Float get() = speed.value

    fun wave(periodSeconds: Float): Float = 0.5f - 0.5f * cos(2f * PI.toFloat() * seconds / periodSeconds)

    fun phase(periodSeconds: Float): Float = 2f * PI.toFloat() * seconds / periodSeconds
}

@Composable
fun rememberPlaybackClock(isPlaying: Boolean, rampMillis: Int = 1200): PlaybackClock {
    val playing by rememberUpdatedState(isPlaying)
    val speed = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = tween(durationMillis = rampMillis, easing = FastOutSlowInEasing),
        label = "playback_clock_speed"
    )
    val elapsed = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var lastFrameNanos = 0L
        while (true) {
            if (!playing && speed.value <= STOPPED_SPEED) {
                delay(IDLE_POLL_MS)
                lastFrameNanos = 0L
                continue
            }
            withFrameNanos { frameNanos ->
                if (lastFrameNanos != 0L) {
                    val dt = ((frameNanos - lastFrameNanos) / NANOS_PER_SECOND).coerceIn(MIN_FRAME_SECONDS, MAX_FRAME_SECONDS)
                    elapsed.floatValue += dt * speed.value
                }
                lastFrameNanos = frameNanos
            }
        }
    }
    return remember(elapsed, speed) { PlaybackClock(elapsed, speed) }
}

fun rubberBand(raw: Float, limit: Float): Float {
    if (abs(raw) <= limit) return raw
    return sign(raw) * (limit + (abs(raw) - limit) * RUBBER_BAND_RESISTANCE)
}

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalArtworkVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

@Composable
fun Modifier.sharedArtwork(songId: String?): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val visibility = LocalArtworkVisibilityScope.current ?: return this
    if (songId == null) return this
    return with(shared) {
        this@sharedArtwork.sharedElement(
            state = rememberSharedContentState(key = "artwork-$songId"),
            animatedVisibilityScope = visibility,
            boundsTransform = { _, _ -> DreaminMotion.standard() }
        )
    }
}
