package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueSectionsTest {

    private fun song(id: String, duration: Long = 180_000L) = Song(id = id, title = "Title $id", artist = "Artist", duration = duration)

    private val a = song("a")
    private val b = song("b")
    private val c = song("c")
    private val d = song("d")
    private val e = song("e")

    @Test
    fun `splits history, current, user-queued and up next in play order`() {
        val sections = QueueSections.build(listOf(a, b, c, d, e), currentIndex = 1, userQueuedIds = listOf("c", "d"))
        assertEquals(listOf(a), sections.history.map { it.song })
        assertEquals(b, sections.current?.song)
        assertEquals(listOf(c, d), sections.fromYou.map { it.song })
        assertEquals(listOf(e), sections.nextUp.map { it.song })
    }

    @Test
    fun `a user-queued song after a regular one stays in up next`() {
        val sections = QueueSections.build(listOf(a, b, c), currentIndex = 0, userQueuedIds = listOf("c"))
        assertEquals(emptyList<Song>(), sections.fromYou.map { it.song })
        assertEquals(listOf(b, c), sections.nextUp.map { it.song })
    }

    @Test
    fun `rows keep their queue index and duplicates get distinct keys`() {
        val sections = QueueSections.build(listOf(a, b, a), currentIndex = 0, userQueuedIds = emptyList())
        assertEquals(listOf(1, 2), sections.nextUp.map { it.index })
        assertEquals(listOf("a", "b", "a#1"), (listOfNotNull(sections.current) + sections.nextUp).map { it.key })
    }

    @Test
    fun `without a current song everything is up next`() {
        val sections = QueueSections.build(listOf(a, b), currentIndex = -1, userQueuedIds = listOf("a"))
        assertNull(sections.current)
        assertEquals(listOf(a, b), sections.nextUp.map { it.song })
    }

    @Test
    fun `remaining duration excludes history and the played part of the current song`() {
        val items = listOf(song("p", 100_000L), song("q", 200_000L), song("r", 300_000L))
        val sections = QueueSections.build(items, currentIndex = 1, userQueuedIds = emptyList())
        assertEquals(150_000L + 300_000L, QueueSections.remainingDurationMs(sections, positionMs = 50_000L))
    }

    @Test
    fun `durations stored in seconds are normalized to milliseconds`() {
        assertEquals(214_000L, QueueSections.normalizedDurationMs(214L))
        assertEquals(214_000L, QueueSections.normalizedDurationMs(214_000L))
        assertEquals(0L, QueueSections.normalizedDurationMs(-1L))
    }

    @Test
    fun `song count label is pluralized`() {
        assertEquals("1 song", QueueSections.songCountLabel(1))
        assertEquals("0 songs", QueueSections.songCountLabel(0))
        assertEquals("12 songs", QueueSections.songCountLabel(12))
    }
}
