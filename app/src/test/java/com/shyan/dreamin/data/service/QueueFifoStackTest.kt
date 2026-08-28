package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.PlayerUiState
import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueFifoStackTest {

    private fun createSong(id: String, title: String) = Song(
        id = id,
        title = title,
        artist = "Artist $id",
        duration = 180L,
        artworkUrl = "https://example.com/$id.jpg"
    )

    @Test
    fun `addToQueue stacks songs in FIFO priority order immediately after current track`() {
        val baseTrack0 = createSong("0", "Current Song 0")
        val baseTrack1 = createSong("1", "Album Track 1")
        val baseTrack2 = createSong("2", "Album Track 2")

        var state = PlayerUiState(
            currentSong = baseTrack0,
            queue = listOf(baseTrack0, baseTrack1, baseTrack2)
        )

        val songA = createSong("A", "User Queued A")
        val songB = createSong("B", "User Queued B")
        val songC = createSong("C", "User Queued C")

        // Helper function mimicking VM addToQueue logic
        fun addToQueue(currentState: PlayerUiState, song: Song): PlayerUiState {
            val queue = currentState.queue.toMutableList()
            if (currentState.currentSong != null && queue.none { it.id == currentState.currentSong.id }) {
                queue.add(0, currentState.currentSong)
            }
            val currentIdx = queue.indexOfFirst { it.id == currentState.currentSong?.id }.coerceAtLeast(0)

            var userQueueCount = 0
            for (i in (currentIdx + 1) until queue.size) {
                if (currentState.userQueuedSongIds.contains(queue[i].id)) {
                    userQueueCount++
                } else {
                    break
                }
            }

            val priorIdx = queue.indexOfFirst { it.id == song.id }
            if (priorIdx in (currentIdx + 1)..(currentIdx + userQueueCount)) {
                userQueueCount--
            }
            queue.removeAll { it.id == song.id }

            val insertAt = (currentIdx + 1 + userQueueCount).coerceIn(0, queue.size)
            queue.add(insertAt, song)

            val updatedUserQueued = (currentState.userQueuedSongIds.filter { it != song.id } + song.id)
            return currentState.copy(
                queue = queue.toList(),
                userQueuedSongIds = updatedUserQueued
            )
        }

        // 1. User adds Song A -> should be immediately after Track 0
        state = addToQueue(state, songA)
        assertEquals(listOf("0", "A", "1", "2"), state.queue.map { it.id })
        assertEquals(listOf("A"), state.userQueuedSongIds)

        // 2. User adds Song B -> should be after Song A (before Track 1)
        state = addToQueue(state, songB)
        assertEquals(listOf("0", "A", "B", "1", "2"), state.queue.map { it.id })
        assertEquals(listOf("A", "B"), state.userQueuedSongIds)

        // 3. User adds Song C -> should be after Song B (before Track 1)
        state = addToQueue(state, songC)
        assertEquals(listOf("0", "A", "B", "C", "1", "2"), state.queue.map { it.id })
        assertEquals(listOf("A", "B", "C"), state.userQueuedSongIds)

        // 4. Now advance playback to Song A (Track 0 ends)
        state = state.copy(currentSong = songA)
        val songD = createSong("D", "User Queued D")

        // 5. User adds Song D -> should stack after Song C (before Track 1)
        state = addToQueue(state, songD)
        assertEquals(listOf("0", "A", "B", "C", "D", "1", "2"), state.queue.map { it.id })
        assertEquals(listOf("A", "B", "C", "D"), state.userQueuedSongIds)
    }
}
