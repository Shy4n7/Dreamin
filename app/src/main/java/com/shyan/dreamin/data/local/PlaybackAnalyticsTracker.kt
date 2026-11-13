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
     * Records a play once the listener has actually heard >= 30 seconds or >= 50% of the track.
     */
    suspend fun recordTrackEnd(song: Song, listenedMs: Long, durationMs: Long) {
        if (listenedMs <= 0L) return

        val isScrobbled = listenedMs >= 30_000L || (durationMs > 0L && listenedMs >= durationMs / 2L)
        if (isScrobbled) {
            runCatching {
                historyRepo.recordPlay(song)
            }
        }
    }
}
