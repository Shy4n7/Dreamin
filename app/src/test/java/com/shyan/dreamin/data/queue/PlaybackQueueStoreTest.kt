package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.PlayerUiState
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.model.TrackRepeatMode
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackQueueStoreTest {

    private fun song(id: String) = Song(id = id, title = "Title $id", artist = "Artist")

    @After
    fun tearDown() = PlaybackQueueStore.reset()

    @Test
    fun `snapshot maps queue state and resolves a stale index by id`() {
        val a = song("a")
        val b = song("b")
        val state = PlayerUiState(
            currentSong = b,
            queue = listOf(a, b),
            currentQueueIndex = 0,
            userQueuedSongIds = listOf("b"),
            playlistQueueActive = true,
            repeatMode = TrackRepeatMode.ALL,
            isShuffle = true
        )

        val snapshot = state.toQueueSnapshot()

        assertEquals(QueueSnapshot(listOf(a, b), 1, listOf("b"), true, TrackRepeatMode.ALL, true), snapshot)
        assertEquals(b, snapshot.currentSong)
    }

    @Test
    fun `publish replaces the shared state and reset clears it`() {
        val snapshot = QueueSnapshot(items = listOf(song("x")), currentIndex = 0)

        PlaybackQueueStore.publish(snapshot)
        assertEquals(snapshot, PlaybackQueueStore.state.value)

        PlaybackQueueStore.reset()
        assertEquals(QueueSnapshot(), PlaybackQueueStore.state.value)
    }
}
