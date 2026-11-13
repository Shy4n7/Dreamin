package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.model.TrackRepeatMode

sealed interface QueueAdvance {
    data object ReplayCurrent : QueueAdvance
    data class PlayIndex(val index: Int, val reshuffledItems: List<Song>? = null) : QueueAdvance
    data object EndOfQueue : QueueAdvance
}

data class QueuePosition(val items: List<Song>, val currentIndex: Int)

object QueueOps {

    private const val RESTART_THRESHOLD_MS = 3_000L

    fun resolveCurrentIndex(items: List<Song>, currentSongId: String?, hint: Int): Int {
        if (currentSongId == null) return -1
        if (hint in items.indices && items[hint].id == currentSongId) return hint
        return items.indexOfFirst { it.id == currentSongId }
    }

    fun next(
        items: List<Song>,
        currentIndex: Int,
        repeatMode: TrackRepeatMode,
        loopsQueue: Boolean,
        isShuffle: Boolean,
        shuffler: (List<Song>) -> List<Song> = { it.shuffled() }
    ): QueueAdvance {
        if (repeatMode == TrackRepeatMode.ONE) return QueueAdvance.ReplayCurrent
        if (currentIndex >= 0 && currentIndex + 1 < items.size) return QueueAdvance.PlayIndex(currentIndex + 1)
        val wraps = repeatMode == TrackRepeatMode.ALL || loopsQueue
        if (items.size <= 1 && wraps) return QueueAdvance.ReplayCurrent
        if (!wraps || items.isEmpty()) return QueueAdvance.EndOfQueue
        if (isShuffle && currentIndex in items.indices) {
            val current = items[currentIndex]
            val others = items.filterIndexed { index, _ -> index != currentIndex }
            return QueueAdvance.PlayIndex(1, listOf(current) + shuffler(others))
        }
        return QueueAdvance.PlayIndex(0)
    }

    fun previous(
        items: List<Song>,
        currentIndex: Int,
        positionMs: Long,
        repeatMode: TrackRepeatMode,
        loopsQueue: Boolean
    ): QueueAdvance {
        if (positionMs > RESTART_THRESHOLD_MS) return QueueAdvance.ReplayCurrent
        if (currentIndex > 0 && currentIndex < items.size) return QueueAdvance.PlayIndex(currentIndex - 1)
        val wraps = repeatMode == TrackRepeatMode.ALL || loopsQueue
        if (wraps && items.isNotEmpty()) return QueueAdvance.PlayIndex(items.lastIndex)
        return QueueAdvance.ReplayCurrent
    }

    fun upcomingIndex(
        items: List<Song>,
        currentIndex: Int,
        repeatMode: TrackRepeatMode,
        loopsQueue: Boolean,
        isShuffle: Boolean = false
    ): Int? {
        if (repeatMode == TrackRepeatMode.ONE) return null
        if (currentIndex >= 0 && currentIndex + 1 < items.size) return currentIndex + 1
        val wraps = repeatMode == TrackRepeatMode.ALL || loopsQueue
        if (isShuffle && items.size > 1) return null
        return if (wraps && items.isNotEmpty()) 0 else null
    }

    fun removeById(items: List<Song>, currentIndex: Int, songId: String): QueuePosition {
        val removedBefore = items.take(currentIndex.coerceAtLeast(0)).count { it.id == songId }
        val remaining = items.filter { it.id != songId }
        val newIndex = if (currentIndex < 0) -1 else (currentIndex - removedBefore).coerceAtMost(remaining.lastIndex)
        return QueuePosition(remaining, newIndex)
    }

    fun removeAt(items: List<Song>, index: Int): List<Song> =
        if (index !in items.indices) items else items.filterIndexed { i, _ -> i != index }

    fun move(items: List<Song>, currentIndex: Int, from: Int, to: Int): QueuePosition {
        if (from == to || from !in items.indices || to !in items.indices) return QueuePosition(items, currentIndex)
        val mutable = items.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        val newIndex = when {
            currentIndex == from -> to
            from < currentIndex && to >= currentIndex -> currentIndex - 1
            from > currentIndex && to <= currentIndex -> currentIndex + 1
            else -> currentIndex
        }
        return QueuePosition(mutable.toList(), newIndex)
    }

    fun playNext(items: List<Song>, currentIndex: Int, current: Song?, song: Song): QueuePosition {
        if (song.id == current?.id) return QueuePosition(items, currentIndex)
        val base = ensureCurrent(items, currentIndex, current)
        val stripped = removeById(base.items, base.currentIndex, song.id)
        val insertAt = (stripped.currentIndex + 1).coerceIn(0, stripped.items.size)
        val result = stripped.items.toMutableList().apply { add(insertAt, song) }
        return QueuePosition(result.toList(), stripped.currentIndex)
    }

    fun addToQueue(
        items: List<Song>,
        currentIndex: Int,
        current: Song?,
        song: Song,
        userQueuedIds: List<String>
    ): QueuePosition {
        if (song.id == current?.id) return QueuePosition(items, currentIndex)
        val base = ensureCurrent(items, currentIndex, current)
        val stripped = removeById(base.items, base.currentIndex, song.id)
        val anchor = stripped.currentIndex.coerceAtLeast(0)
        var insertAt = anchor + 1
        while (insertAt < stripped.items.size && userQueuedIds.contains(stripped.items[insertAt].id)) insertAt++
        val result = stripped.items.toMutableList().apply { add(insertAt.coerceAtMost(size), song) }
        return QueuePosition(result.toList(), stripped.currentIndex)
    }

    fun positionForPlay(items: List<Song>, currentIndex: Int, song: Song, preferredIndex: Int?): QueuePosition {
        if (preferredIndex != null && preferredIndex in items.indices && items[preferredIndex].id == song.id) {
            return QueuePosition(items, preferredIndex)
        }
        val existing = items.indexOfFirst { it.id == song.id }
        if (existing >= 0) return QueuePosition(items, existing)
        val insertAt = if (currentIndex >= 0) (currentIndex + 1).coerceAtMost(items.size) else items.size
        val result = items.toMutableList().apply { add(insertAt, song) }
        return QueuePosition(result.toList(), insertAt)
    }

    fun shuffleUpcoming(
        items: List<Song>,
        currentIndex: Int,
        shuffler: (List<Song>) -> List<Song> = { it.shuffled() }
    ): List<Song> {
        if (currentIndex < 0 || currentIndex + 1 >= items.size) return items
        return items.take(currentIndex + 1) + shuffler(items.drop(currentIndex + 1))
    }

    fun upcoming(items: List<Song>, currentIndex: Int): List<Song> =
        if (currentIndex < 0) items else items.drop(currentIndex + 1)

    fun remainingDurationMs(items: List<Song>, currentIndex: Int, positionMs: Long): Long {
        val currentRemaining = items.getOrNull(currentIndex)?.let { (it.duration - positionMs).coerceAtLeast(0L) } ?: 0L
        return currentRemaining + upcoming(items, currentIndex).sumOf { it.duration.coerceAtLeast(0L) }
    }

    private fun ensureCurrent(items: List<Song>, currentIndex: Int, current: Song?): QueuePosition {
        if (current == null) return QueuePosition(items, currentIndex)
        val resolved = resolveCurrentIndex(items, current.id, currentIndex)
        if (resolved >= 0) return QueuePosition(items, resolved)
        return QueuePosition(listOf(current) + items, 0)
    }
}
