package com.shyan.dreamin.data.recommendation

import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.ConcurrentHashMap

object FeedbackEngine {

    // Suppressed track/artist IDs for current playback session (if skipped < 15s)
    private val sessionSuppressedIds = ConcurrentHashMap.newKeySet<String>()
    private val sessionSuppressedArtists = ConcurrentHashMap.newKeySet<String>()

    // High-affinity boosted artists (completed >80% or hearted)
    private val _artistAffinityWeights = ConcurrentHashMap<String, Int>()

    /**
     * Records a track transition event to learn user preference.
     * @param playbackDurationMs how long user listened before skipping or finishing
     * @param totalTrackDurationMs the full track duration
     */
    fun recordTrackEvent(song: Song, playbackDurationMs: Long, totalTrackDurationMs: Long, isFavorite: Boolean = false) {
        val primaryArtist = song.artist.split(",").firstOrNull()?.trim() ?: song.artist

        if (isFavorite) {
            val cur = _artistAffinityWeights[primaryArtist] ?: 0
            _artistAffinityWeights[primaryArtist] = cur + 5
            return
        }

        val playedSeconds = playbackDurationMs / 1000L
        val totalSeconds = if (totalTrackDurationMs > 0) totalTrackDurationMs / 1000L else 180L
        val percent = if (totalSeconds > 0) (playedSeconds.toFloat() / totalSeconds.toFloat()) else 0f

        if (playedSeconds < 15L && percent < 0.20f) {
            // Immediate skip -> suppress track for current session
            sessionSuppressedIds.add(song.id)
            val currentPenalty = _artistAffinityWeights[primaryArtist] ?: 0
            if (currentPenalty > -5) {
                _artistAffinityWeights[primaryArtist] = currentPenalty - 1
            }
            android.util.Log.d("FeedbackEngine", "Track ${song.displayTitle} skipped quickly (<15s). Suppressed for session.")
        } else if (percent >= 0.80f || playedSeconds >= 120L) {
            // Complete listen -> boost affinity
            val cur = _artistAffinityWeights[primaryArtist] ?: 0
            _artistAffinityWeights[primaryArtist] = (cur + 2).coerceAtMost(20)
            android.util.Log.d("FeedbackEngine", "Track ${song.displayTitle} completed (>80%). Affinity for $primaryArtist boosted to ${_artistAffinityWeights[primaryArtist]}.")
        }
    }

    fun isSuppressed(song: Song): Boolean {
        if (sessionSuppressedIds.contains(song.id)) return true
        val primaryArtist = song.artist.split(",").firstOrNull()?.trim() ?: ""
        return sessionSuppressedArtists.contains(primaryArtist)
    }

    fun getTopAffinityArtists(): List<String> {
        return _artistAffinityWeights.entries
            .filter { it.value > 0 }
            .sortedByDescending { it.value }
            .map { it.key }
            .take(5)
    }
}
