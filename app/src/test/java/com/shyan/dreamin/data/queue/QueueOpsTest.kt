package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.model.TrackRepeatMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class QueueOpsTest {

    private fun song(id: String, durationMs: Long = 180_000L) =
        Song(id = id, title = "Title $id", artist = "Artist", duration = durationMs)

    private val a = song("a")
    private val b = song("b")
    private val c = song("c")
    private val d = song("d")
    private val abcd = listOf(a, b, c, d)
    private val reverse: (List<Song>) -> List<Song> = { it.reversed() }

    @Test
    fun `resolveCurrentIndex prefers hint when it matches`() {
        val items = listOf(a, b, a, c)
        assertEquals(2, QueueOps.resolveCurrentIndex(items, "a", 2))
    }

    @Test
    fun `resolveCurrentIndex falls back to first match when hint is stale`() {
        assertEquals(1, QueueOps.resolveCurrentIndex(abcd, "b", 3))
        assertEquals(-1, QueueOps.resolveCurrentIndex(abcd, "z", 0))
        assertEquals(-1, QueueOps.resolveCurrentIndex(abcd, null, 0))
    }

    @Test
    fun `next moves to following index in the middle`() {
        assertEquals(QueueAdvance.PlayIndex(2), QueueOps.next(abcd, 1, TrackRepeatMode.OFF, isShuffle = false))
    }

    @Test
    fun `next with duplicates uses index not id`() {
        val items = listOf(a, b, a, c)
        assertEquals(QueueAdvance.PlayIndex(3), QueueOps.next(items, 2, TrackRepeatMode.OFF, isShuffle = false))
    }

    @Test
    fun `next at end without repeat ends queue`() {
        assertEquals(QueueAdvance.EndOfQueue, QueueOps.next(abcd, 3, TrackRepeatMode.OFF, isShuffle = false))
    }

    @Test
    fun `next at end wraps only for repeat all`() {
        assertEquals(QueueAdvance.PlayIndex(0), QueueOps.next(abcd, 3, TrackRepeatMode.ALL, isShuffle = false))
        assertEquals(QueueAdvance.EndOfQueue, QueueOps.next(abcd, 3, TrackRepeatMode.ONE, isShuffle = false, isManual = true))
    }

    @Test
    fun `next at end with shuffle reshuffles keeping current first`() {
        val result = QueueOps.next(abcd, 3, TrackRepeatMode.ALL, isShuffle = true, shuffler = reverse)
        assertEquals(QueueAdvance.PlayIndex(1, listOf(d, c, b, a)), result)
    }

    @Test
    fun `natural end with repeat one replays`() {
        assertEquals(QueueAdvance.ReplayCurrent, QueueOps.next(abcd, 1, TrackRepeatMode.ONE, isShuffle = false))
    }

    @Test
    fun `manual next with repeat one changes track`() {
        assertEquals(QueueAdvance.PlayIndex(2), QueueOps.next(abcd, 1, TrackRepeatMode.ONE, isShuffle = false, isManual = true))
    }

    @Test
    fun `next on single item with repeat all replays`() {
        assertEquals(QueueAdvance.ReplayCurrent, QueueOps.next(listOf(a), 0, TrackRepeatMode.ALL, isShuffle = false))
    }

    @Test
    fun `previous restarts after three seconds`() {
        assertEquals(QueueAdvance.ReplayCurrent, QueueOps.previous(abcd, 2, 3_500L, TrackRepeatMode.OFF))
    }

    @Test
    fun `previous goes back one index early in the song`() {
        assertEquals(QueueAdvance.PlayIndex(1), QueueOps.previous(abcd, 2, 1_000L, TrackRepeatMode.OFF))
    }

    @Test
    fun `previous at start restarts unless repeat all`() {
        assertEquals(QueueAdvance.ReplayCurrent, QueueOps.previous(abcd, 0, 0L, TrackRepeatMode.OFF))
        assertEquals(QueueAdvance.ReplayCurrent, QueueOps.previous(abcd, 0, 0L, TrackRepeatMode.ONE))
        assertEquals(QueueAdvance.PlayIndex(3), QueueOps.previous(abcd, 0, 0L, TrackRepeatMode.ALL))
    }

    @Test
    fun `upcomingIndex preloads the next song even with repeat one`() {
        assertEquals(2, QueueOps.upcomingIndex(abcd, 1, TrackRepeatMode.ONE))
        assertEquals(2, QueueOps.upcomingIndex(abcd, 1, TrackRepeatMode.OFF))
        assertNull(QueueOps.upcomingIndex(abcd, 3, TrackRepeatMode.OFF))
        assertNull(QueueOps.upcomingIndex(abcd, 3, TrackRepeatMode.ONE))
        assertEquals(0, QueueOps.upcomingIndex(abcd, 3, TrackRepeatMode.ALL))
    }

    @Test
    fun `upcomingIndex does not preload a shuffled wrap so the queue can reshuffle`() {
        assertNull(QueueOps.upcomingIndex(abcd, 3, TrackRepeatMode.ALL, isShuffle = true))
        assertEquals(2, QueueOps.upcomingIndex(abcd, 1, TrackRepeatMode.ALL, isShuffle = true))
    }

    @Test
    fun `removeById before current shifts current index`() {
        val result = QueueOps.removeById(abcd, 2, "a")
        assertEquals(QueuePosition(listOf(b, c, d), 1), result)
    }

    @Test
    fun `removeById after current keeps current index`() {
        assertEquals(QueuePosition(listOf(a, b, d), 1), QueueOps.removeById(abcd, 1, "c"))
    }

    @Test
    fun `removeAt current leaves following song at same index`() {
        val result = QueueOps.removeAt(abcd, 1)
        assertEquals(listOf(a, c, d), result)
        assertEquals(c, result[1])
    }

    @Test
    fun `move keeps current pointer on the playing song`() {
        assertEquals(QueuePosition(listOf(b, c, a, d), 1), QueueOps.move(abcd, 2, 0, 2))
        assertEquals(QueuePosition(listOf(a, d, b, c), 2), QueueOps.move(abcd, 1, 3, 1))
        assertEquals(QueuePosition(listOf(a, c, d, b), 3), QueueOps.move(abcd, 1, 1, 3))
        assertEquals(QueuePosition(listOf(a, b, d, c), 0), QueueOps.move(abcd, 0, 2, 3))
    }

    @Test
    fun `playNext inserts directly after current`() {
        val x = song("x")
        val result = QueueOps.playNext(abcd, 1, b, x)
        assertEquals(QueuePosition(listOf(a, b, x, c, d), 1), result)
    }

    @Test
    fun `playNext moves an existing upcoming song`() {
        val result = QueueOps.playNext(abcd, 0, a, d)
        assertEquals(QueuePosition(listOf(a, d, b, c), 0), result)
    }

    @Test
    fun `playNext ignores the current song`() {
        assertEquals(QueuePosition(abcd, 1), QueueOps.playNext(abcd, 1, b, b))
    }

    @Test
    fun `addToQueue appends after the user queued block in FIFO order`() {
        val x = song("x")
        val y = song("y")
        val first = QueueOps.addToQueue(abcd, 0, a, x, userQueuedIds = emptyList())
        val second = QueueOps.addToQueue(first.items, first.currentIndex, a, y, userQueuedIds = listOf("x"))
        assertEquals(QueuePosition(listOf(a, x, y, b, c, d), 0), second)
    }

    @Test
    fun `addToQueue inserts current when queue does not contain it`() {
        val x = song("x")
        val result = QueueOps.addToQueue(listOf(b, c), -1, a, x, userQueuedIds = emptyList())
        assertEquals(QueuePosition(listOf(a, x, b, c), 0), result)
    }

    @Test
    fun `positionForPlay reuses existing index or inserts after current`() {
        assertEquals(QueuePosition(abcd, 2), QueueOps.positionForPlay(abcd, 0, c, preferredIndex = null))
        val x = song("x")
        assertEquals(QueuePosition(listOf(a, b, x, c, d), 2), QueueOps.positionForPlay(abcd, 1, x, preferredIndex = null))
    }

    @Test
    fun `positionForPlay honours a matching preferred index for duplicates`() {
        val items = listOf(a, b, a, c)
        assertEquals(QueuePosition(items, 2), QueueOps.positionForPlay(items, 1, a, preferredIndex = 2))
        assertEquals(QueuePosition(items, 0), QueueOps.positionForPlay(items, 1, a, preferredIndex = 3))
    }

    @Test
    fun `shuffleUpcoming only reorders songs after current`() {
        assertEquals(listOf(a, b, d, c), QueueOps.shuffleUpcoming(abcd, 1, shuffler = reverse))
        assertEquals(abcd, QueueOps.shuffleUpcoming(abcd, 3, shuffler = reverse))
    }

    @Test
    fun `shuffleUpcoming keeps user-queued songs pinned right after current`() {
        val e = song("e")
        val items = listOf(a, b, c, d, e)
        assertEquals(listOf(a, c, e, d, b), QueueOps.shuffleUpcoming(items, 0, pinnedIds = listOf("c"), shuffler = reverse))
    }

    @Test
    fun `unshuffleUpcoming restores original order with pinned first and new songs last`() {
        val x = song("x")
        val shuffled = listOf(c, a, d, x, b)
        val restored = QueueOps.unshuffleUpcoming(shuffled, 0, originalOrder = abcd, pinnedIds = listOf("d"))
        assertEquals(listOf(c, d, a, b, x), restored)
    }

    @Test
    fun `unshuffleUpcoming restores each duplicate to its own original slot`() {
        val x = song("x")
        val restored = QueueOps.unshuffleUpcoming(listOf(x, c, a, b, a), 0, originalOrder = listOf(a, b, a, c))
        assertEquals(listOf(x, a, b, a, c), restored)
    }

    @Test
    fun `pinning moves only one copy of a duplicated user-queued song`() {
        val restored = QueueOps.unshuffleUpcoming(listOf(d, b, a, c, a), 0, originalOrder = listOf(a, b, c, a), pinnedIds = listOf("a"))
        assertEquals(listOf(d, a, b, c, a), restored)
    }

    @Test
    fun `unshuffleUpcoming leaves history and current untouched`() {
        assertEquals(listOf(d, c, a, b), QueueOps.unshuffleUpcoming(listOf(d, c, b, a), 1, originalOrder = abcd))
    }

    @Test
    fun `remainingDurationMs counts rest of current plus upcoming`() {
        val items = listOf(song("p", 100_000L), song("q", 200_000L), song("r", 300_000L))
        assertEquals(150_000L + 300_000L, QueueOps.remainingDurationMs(items, 1, 50_000L))
        assertEquals(listOf(items[2]), QueueOps.upcoming(items, 1))
    }
}
