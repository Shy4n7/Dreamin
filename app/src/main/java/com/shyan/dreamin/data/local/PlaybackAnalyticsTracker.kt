package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.recommendation.FeedbackEngine

/**
 * Tracks playback milestones, scrobbling criteria, and user skips to update analytics and feedback engines.
 */
class PlaybackAnalyticsTracker(
    private val historyRepo: PlayHistoryRepository,
    private val statsRepo: StatsRepository
) {

    /**
     * Records track completion or skip state.
     * Songs played for >= 30 seconds or >= 50% duration are counted as completed scrobbles.
     * Skips under 10 seconds incur skip penalties in the recommendation feedback matrix.
     */
    suspend fun recordTrackEnd(song: Song, positionMs: Long, durationMs: Long) {
        if (positionMs <= 0L) return

        // Dispatch to recommendation feedback engine for personalization scoring
        FeedbackEngine.recordTrackEvent(song, positionMs, durationMs)

        // Persist to local play history & statistics if scrobble threshold met
        val isScrobbled = positionMs >= 30_000L || (durationMs > 0L && positionMs >= durationMs / 2L)
        if (isScrobbled) {
            runCatching {
                historyRepo.recordPlay(song)
            }
        }
    }
}
