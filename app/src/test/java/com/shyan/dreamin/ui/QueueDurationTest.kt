package com.shyan.dreamin.ui

import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueDurationTest {

    @Test
    fun testTotalQueueDurationFormattingMilliseconds() {
        // 23 songs of ~214 seconds each (4,922,000 ms total = 1 hr 22 min 2 sec)
        val songs = (1..23).map { index ->
            Song(
                id = index.toString(),
                title = "Song $index",
                artist = "Artist",
                duration = 214000L // 214 seconds in milliseconds
            )
        }

        val totalSecs = songs.sumOf { song: Song ->
            if (song.duration > 10_000L) song.duration / 1000L else song.duration
        }
        val hours = totalSecs / 3600
        val minutes = (totalSecs % 3600) / 60
        val seconds = totalSecs % 60
        val formatted = if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }

        assertEquals("1:22:02", formatted)
    }

    @Test
    fun testPerTrackDurationFormattingMilliseconds() {
        val durationMs = 214000L // 3 minutes 34 seconds
        val sec = if (durationMs > 10_000L) durationMs / 1000L else durationMs
        val mins = sec / 60
        val secs = sec % 60
        val formatted = String.format("%d:%02d", mins, secs)

        assertEquals("3:34", formatted)
    }
}
