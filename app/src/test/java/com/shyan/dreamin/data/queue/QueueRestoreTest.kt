package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.local.UserPreferencesDataStore.LastSession
import com.shyan.dreamin.data.local.UserPreferencesDataStore.SavedQueue
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.model.TrackRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueRestoreTest {

    private fun song(id: String) = Song(id = id, title = "Title $id", artist = "Artist")

    private val a = song("a")
    private val b = song("b")
    private val c = song("c")

    @Test
    fun `saving skips an empty queue or one without a current song`() {
        assertNull(QueueRestore.toSaved(QueueSnapshot()))
        assertNull(QueueRestore.toSaved(QueueSnapshot(items = listOf(a), currentIndex = -1)))
    }

    @Test
    fun `saving keeps items, index, playlist flag and user-queued ids`() {
        val saved = QueueRestore.toSaved(QueueSnapshot(listOf(a, b), 1, listOf("b"), playlistActive = true))
        assertEquals(SavedQueue(listOf(a, b), 1, true, listOf("b")), saved)
    }

    @Test
    fun `an in-memory queue wins over the saved one`() {
        val inMemory = QueueSnapshot(items = listOf(c), currentIndex = 0)
        val target = QueueRestore.resumeTarget(inMemory, SavedQueue(listOf(a, b), 0, false, emptyList()), LastSession(c, 42_000L))
        assertEquals(ResumeTarget(inMemory, 42_000L), target)
    }

    @Test
    fun `the saved queue resumes at the last session song and position`() {
        val inMemory = QueueSnapshot(repeatMode = TrackRepeatMode.ALL, isShuffle = true)
        val target = QueueRestore.resumeTarget(inMemory, SavedQueue(listOf(a, b, c), 0, true, listOf("c")), LastSession(b, 9_000L))
        val expected = QueueSnapshot(listOf(a, b, c), 1, listOf("c"), true, TrackRepeatMode.ALL, true)
        assertEquals(ResumeTarget(expected, 9_000L), target)
    }

    @Test
    fun `a session song missing from the saved queue falls back to the saved index from the start`() {
        val target = QueueRestore.resumeTarget(QueueSnapshot(), SavedQueue(listOf(a, b), 1, false, emptyList()), LastSession(c, 9_000L))
        assertEquals(ResumeTarget(QueueSnapshot(items = listOf(a, b), currentIndex = 1), 0L), target)
    }

    @Test
    fun `without a saved queue the last session song plays alone`() {
        val target = QueueRestore.resumeTarget(QueueSnapshot(), null, LastSession(a, 5_000L))
        assertEquals(ResumeTarget(QueueSnapshot(items = listOf(a), currentIndex = 0), 5_000L), target)
    }

    @Test
    fun `nothing to resume returns null`() {
        assertNull(QueueRestore.resumeTarget(QueueSnapshot(), null, null))
    }
}
