package com.shyan.dreamin.service

import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.pow

class SleepTimerController(
    private val player: ExoPlayer,
    private val scope: CoroutineScope,
    private val queueController: () -> QueueController?,
    private val publishExtras: (Bundle) -> Unit
) : Player.Listener {

    private var timerJob: Job? = null
    private var isEndOfSong = false
    private var holdUntilResume = false

    fun start() {
        player.addListener(this)
        queueController()?.let { controller ->
            controller.shouldHoldAtEnd = { isEndOfSong || holdUntilResume }
            controller.onHeldAtEnd = { finishEndOfSong() }
        }
    }

    fun release() {
        player.removeListener(this)
        timerJob?.cancel()
    }

    fun setEndTime(endEpochMs: Long) {
        clear()
        val remainingMs = endEpochMs - System.currentTimeMillis()
        if (remainingMs <= 0L) {
            publishExtras(Bundle.EMPTY)
            return
        }
        publishExtras(Bundle().apply { putLong(MusicService.EXTRA_SLEEP_TIMER_END_MS, endEpochMs) })
        timerJob = scope.launch {
            delay((remainingMs - FADE_DURATION_MS).coerceAtLeast(0L))
            fadeOutAndPause(fadeMs = remainingMs.coerceAtMost(FADE_DURATION_MS))
            cancel()
        }
    }

    fun setEndOfSong() {
        clear()
        isEndOfSong = true
        player.pauseAtEndOfMediaItems = true
        publishExtras(Bundle().apply { putBoolean(MusicService.EXTRA_SLEEP_TIMER_END_OF_SONG, true) })
    }

    fun cancel() {
        clear()
        publishExtras(Bundle.EMPTY)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            holdUntilResume = false
            return
        }
        if (isEndOfSong && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM) {
            holdUntilResume = true
            finishEndOfSong()
        }
    }

    private fun finishEndOfSong() {
        if (!isEndOfSong) return
        isEndOfSong = false
        player.pauseAtEndOfMediaItems = false
        publishExtras(Bundle.EMPTY)
    }

    private suspend fun fadeOutAndPause(fadeMs: Long) {
        val baseVolume = player.volume
        val stepMs = (fadeMs / FADE_STEPS).coerceAtLeast(1L)
        for (step in 1..FADE_STEPS) {
            val progress = step.toFloat() / FADE_STEPS
            player.volume = baseVolume * FADE_FLOOR.pow(progress)
            delay(stepMs)
        }
        player.pause()
        player.volume = baseVolume
    }

    private fun clear() {
        timerJob?.cancel()
        timerJob = null
        if (isEndOfSong) player.pauseAtEndOfMediaItems = false
        isEndOfSong = false
        holdUntilResume = false
        player.volume = FULL_VOLUME
    }

    private companion object {
        const val FADE_DURATION_MS = 10_000L
        const val FADE_STEPS = 40
        const val FADE_FLOOR = 0.01f
        const val FULL_VOLUME = 1f
    }
}
