package com.shyan.dreamin.viewmodel

import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * State holder for player queue, reordering, history stack, and shuffle ordering.
 */
class QueueManager {

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _history = MutableStateFlow<List<Song>>(emptyList())
    val history: StateFlow<List<Song>> = _history.asStateFlow()

    private var originalQueue: List<Song> = emptyList()
    var isShuffle: Boolean = false
        private set

    fun setQueue(songs: List<Song>, startIndex: Int = 0) {
        originalQueue = songs
        if (isShuffle) {
            val current = songs.getOrNull(startIndex)
            val remaining = songs.filterIndexed { index, _ -> index != startIndex }.shuffled()
            _queue.value = if (current != null) listOf(current) + remaining else remaining
        } else {
            _queue.value = songs
        }
    }

    fun toggleShuffle(currentSong: Song?): Boolean {
        isShuffle = !isShuffle
        if (isShuffle) {
            val currentList = _queue.value
            if (currentList.isNotEmpty()) {
                val remaining = currentList.filter { it.id != currentSong?.id }.shuffled()
                _queue.value = if (currentSong != null) listOf(currentSong) + remaining else remaining
            }
        } else {
            if (originalQueue.isNotEmpty()) {
                _queue.value = originalQueue
            }
        }
        return isShuffle
    }

    fun pushHistory(song: Song) {
        _history.update { (listOf(song) + it).take(50) }
    }

    fun popHistory(): Song? {
        val hist = _history.value
        if (hist.isEmpty()) return null
        val prev = hist.first()
        _history.value = hist.drop(1)
        return prev
    }

    fun addToQueue(song: Song) {
        _queue.update { it + song }
        originalQueue = originalQueue + song
    }

    fun addToQueueNext(song: Song, currentSongId: String?) {
        val current = _queue.value
        val index = current.indexOfFirst { it.id == currentSongId }
        val insertAt = if (index >= 0) index + 1 else 0
        val updated = current.toMutableList().apply { add(insertAt.coerceIn(0, size), song) }
        _queue.value = updated
    }

    fun removeFromQueue(songId: String) {
        _queue.update { it.filter { song -> song.id != songId } }
        originalQueue = originalQueue.filter { it.id != songId }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val list = _queue.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            _queue.value = list
        }
    }

    fun clearQueue() {
        _queue.value = emptyList()
        originalQueue = emptyList()
        _history.value = emptyList()
    }
}
