package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.model.Song
/**
 * Tracks playback milestones and scrobbling criteria to update analytics and play history.
 */
class PlaybackAnalyticsTracker(
    private val historyRepo: PlayHistoryRepository,
    private val statsRepo: StatsRepository
) {

    /**
     * Records track completion for scrobbling.
     * Songs played for >= 30 seconds or >= 50% duration are counted as completed scrobbles.
     */
    suspend fun recordTrackEnd(song: Song, positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return

        // Persist to local play history & statistics if scrobble threshold met
        val isScrobbled = positionMs >= 30_000L || (durationMs > 0L && positionMs >= durationMs / 2L)
        if (isScrobbled) {
            runCatching {
                historyRepo.recordPlay(song)
            }
        }
    }
}
