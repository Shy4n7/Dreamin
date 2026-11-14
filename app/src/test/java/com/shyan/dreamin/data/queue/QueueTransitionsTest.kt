package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.model.TrackRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueTransitionsTest {

    private fun song(id: String) = Song(id = id, title = "Title $id", artist = "Artist")

    private val a = song("a")
    private val b = song("b")
    private val c = song("c")
    private val d = song("d")

    private fun snap(
        vararg items: Song,
        index: Int = 0,
        playlist: Boolean = false,
        repeat: TrackRepeatMode = TrackRepeatMode.OFF,
        shuffle: Boolean = false,
        userQueued: List<String> = emptyList()
    ) = QueueSnapshot(items.toList(), index, userQueued, playlist, repeat, shuffle)

    @Test
    fun `transition to the next item advances the index and clears its user-queued flag`() {
        val result = QueueTransitions.onItemTransition(snap(a, b, c, userQueued = listOf("b")), "b")
        assertEquals(1, result.currentIndex)
        assertEquals(emptyList<String>(), result.userQueuedIds)
    }

    @Test
    fun `transition prefers the next slot over an earlier duplicate`() {
        val result = QueueTransitions.onItemTransition(snap(b, a, b, index = 1), "b")
        assertEquals(2, result.currentIndex)
    }

    @Test
    fun `transition to the current item returns the same snapshot`() {
        val start = snap(a, b, index = 0)
        assertSame(start, QueueTransitions.onItemTransition(start, "a"))
    }

    @Test
    fun `transition to an unknown item leaves the queue untouched`() {
        val start = snap(a, b)
        assertSame(start, QueueTransitions.onItemTransition(start, "zzz"))
    }

    @Test
    fun `transition to a distant item jumps to it`() {
        assertEquals(3, QueueTransitions.onItemTransition(snap(a, b, c, d), "d").currentIndex)
    }

    @Test
    fun `auto end in the middle plays the following item`() {
        val action = QueueTransitions.onAutoEnd(snap(a, b, c, index = 0))
        assertEquals(AutoEndAction.Play(snap(a, b, c, index = 1), b), action)
    }

    @Test
    fun `auto end at the end of a radio queue continues with radio`() {
        val start = snap(a, b, index = 1)
        assertEquals(AutoEndAction.ContinueWithRadio(b, start, 2), QueueTransitions.onAutoEnd(start))
    }

    @Test
    fun `failed song in the middle is removed and the following song plays`() {
        val action = QueueTransitions.skipFailed(snap(a, b, c, index = 1, userQueued = listOf("b")), "b")
        assertEquals(AutoEndAction.Play(snap(a, c, index = 1), c), action)
    }

    @Test
    fun `failed last song of a radio queue continues with radio from its slot`() {
        val action = QueueTransitions.skipFailed(snap(a, b, index = 1), "b")
        assertEquals(AutoEndAction.ContinueWithRadio(b, snap(a, index = -1), 1), action)
    }

    @Test
    fun `replacing the queue without shuffle starts at the chosen song and clears the original order`() {
        val start = snap(a, index = 0, shuffle = false).copy(originalOrder = listOf(d))
        val replaced = QueueTransitions.replaceQueue(start, listOf(b, c, d), playlistActive = true, start = c)
        assertEquals(listOf(b, c, d), replaced.items)
        assertEquals(1, replaced.currentIndex)
        assertTrue(replaced.playlistActive)
        assertEquals(emptyList<Song>(), replaced.originalOrder)
    }

    @Test
    fun `replacing the queue while shuffled shuffles behind the chosen song and remembers the new order`() {
        val start = snap(a, index = 0, shuffle = true).copy(originalOrder = listOf(a, d))
        val replaced = QueueTransitions.replaceQueue(start, listOf(b, c, d), playlistActive = false, start = c, shuffler = { it.reversed() })
        assertEquals(listOf(c, d, b), replaced.items)
        assertEquals(0, replaced.currentIndex)
        assertEquals(listOf(b, c, d), replaced.originalOrder)
    }

    @Test
    fun `failed last song of a playlist stops without repeat`() {
        assertEquals(AutoEndAction.Stop, QueueTransitions.skipFailed(snap(a, b, index = 1, playlist = true), "b"))
    }

    @Test
    fun `previous early in a song plays the item before it`() {
        val action = QueueTransitions.onUserPrevious(snap(a, b, c, index = 2), positionMs = 1_000L)
        assertEquals(AutoEndAction.Play(snap(a, b, c, index = 1), b), action)
    }

    @Test
    fun `previous after three seconds restarts the current song`() {
        assertEquals(AutoEndAction.ReplayCurrent, QueueTransitions.onUserPrevious(snap(a, b, index = 1), positionMs = 5_000L))
    }

    @Test
    fun `previous on the first song of a radio queue restarts it`() {
        assertEquals(AutoEndAction.ReplayCurrent, QueueTransitions.onUserPrevious(snap(a, b, index = 0), positionMs = 0L))
    }

    @Test
    fun `previous on the first song of a playlist restarts it`() {
        assertEquals(AutoEndAction.ReplayCurrent, QueueTransitions.onUserPrevious(snap(a, b, c, index = 0, playlist = true), positionMs = 0L))
    }

    @Test
    fun `previous on the first song with repeat all wraps to the last`() {
        val action = QueueTransitions.onUserPrevious(snap(a, b, c, index = 0, repeat = TrackRepeatMode.ALL), positionMs = 0L)
        assertEquals(AutoEndAction.Play(snap(a, b, c, index = 2, repeat = TrackRepeatMode.ALL), c), action)
    }

    @Test
    fun `failure for a song that is no longer current is ignored`() {
        assertEquals(AutoEndAction.Stop, QueueTransitions.skipFailed(snap(a, b, index = 0), "b"))
    }

    @Test
    fun `auto end at the end of a playlist without repeat stops`() {
        assertEquals(AutoEndAction.Stop, QueueTransitions.onAutoEnd(snap(a, b, index = 1, playlist = true)))
    }

    @Test
    fun `auto end at the end of a playlist with repeat all wraps`() {
        val action = QueueTransitions.onAutoEnd(snap(a, b, index = 1, playlist = true, repeat = TrackRepeatMode.ALL))
        assertEquals(AutoEndAction.Play(snap(a, b, index = 0, playlist = true, repeat = TrackRepeatMode.ALL), a), action)
    }

    @Test
    fun `user next with repeat one moves to the following song`() {
        val action = QueueTransitions.onUserNext(snap(a, b, index = 0, repeat = TrackRepeatMode.ONE))
        assertEquals(AutoEndAction.Play(snap(a, b, index = 1, repeat = TrackRepeatMode.ONE), b), action)
    }

    @Test
    fun `shuffle on pins user-queued songs and remembers the original order`() {
        val start = snap(a, b, c, d, index = 0, userQueued = listOf("b"))
        val shuffled = QueueTransitions.toggleShuffle(start, shuffler = { it.reversed() })
        assertEquals(listOf(a, b, d, c), shuffled.items)
        assertTrue(shuffled.isShuffle)
        assertEquals(listOf(a, b, c, d), shuffled.originalOrder)
    }

    @Test
    fun `shuffle off restores the original upcoming order`() {
        val start = snap(a, b, c, d, index = 0)
        val shuffled = QueueTransitions.toggleShuffle(start, shuffler = { it.reversed() })
        val advanced = shuffled.copy(currentIndex = 1)
        val restored = QueueTransitions.toggleShuffle(advanced)
        assertEquals(listOf(a, d, b, c), restored.items)
        assertFalse(restored.isShuffle)
        assertEquals(emptyList<Song>(), restored.originalOrder)
    }

    @Test
    fun `auto end with repeat all wraps to the start`() {
        val action = QueueTransitions.onAutoEnd(snap(a, b, c, index = 2, repeat = TrackRepeatMode.ALL))
        assertEquals(AutoEndAction.Play(snap(a, b, c, index = 0, repeat = TrackRepeatMode.ALL), a), action)
    }

    @Test
    fun `auto end with shuffle wrap reshuffles behind the current song`() {
        val action = QueueTransitions.onAutoEnd(
            snap(a, b, c, index = 2, repeat = TrackRepeatMode.ALL, shuffle = true),
            shuffler = { it.reversed() }
        )
        val expected = snap(c, b, a, index = 1, repeat = TrackRepeatMode.ALL, shuffle = true)
        assertEquals(AutoEndAction.Play(expected, b), action)
    }

    @Test
    fun `auto end with repeat one replays`() {
        assertEquals(AutoEndAction.ReplayCurrent, QueueTransitions.onAutoEnd(snap(a, b, repeat = TrackRepeatMode.ONE)))
    }

    @Test
    fun `auto end without a current song stops`() {
        assertEquals(AutoEndAction.Stop, QueueTransitions.onAutoEnd(snap(a, b, index = -1)))
    }

    @Test
    fun `upcoming song follows the current index`() {
        assertEquals(b, QueueTransitions.upcomingSong(snap(a, b, c, index = 0)))
        assertNull(QueueTransitions.upcomingSong(snap(a, b, index = 1)))
    }

    @Test
    fun `extension is needed only for radio queues with two or fewer songs left`() {
        assertTrue(QueueTransitions.needsExtension(snap(a, b, c, index = 0), isExtending = false))
        assertFalse(QueueTransitions.needsExtension(snap(a, b, c, d, index = 0), isExtending = false))
        assertFalse(QueueTransitions.needsExtension(snap(a, b, index = 0), isExtending = true))
        assertFalse(QueueTransitions.needsExtension(snap(a, b, index = 0, playlist = true), isExtending = false))
        assertFalse(QueueTransitions.needsExtension(snap(a, index = -1), isExtending = false))
    }

    @Test
    fun `append keeps existing items and skips duplicates`() {
        val result = QueueTransitions.appendUnique(snap(a, b), listOf(b, c, c, d))
        assertEquals(listOf(a, b, c, d), result.items)
        assertEquals(0, result.currentIndex)
    }

    @Test
    fun `append is ignored while a playlist is active`() {
        val start = snap(a, playlist = true)
        assertSame(start, QueueTransitions.appendUnique(start, listOf(b)))
    }

    @Test
    fun `play after current inserts the radio pick and makes it current`() {
        val result = QueueTransitions.playAfterCurrent(snap(a, b, index = 1), c)
        assertEquals(listOf(a, b, c), result.items)
        assertEquals(2, result.currentIndex)
    }
}
