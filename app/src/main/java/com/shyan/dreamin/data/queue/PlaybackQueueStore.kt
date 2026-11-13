package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.PlayerUiState
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.model.TrackRepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class QueueSnapshot(
    val items: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val userQueuedIds: List<String> = emptyList(),
    val playlistActive: Boolean = false,
    val repeatMode: TrackRepeatMode = TrackRepeatMode.OFF,
    val isShuffle: Boolean = false
) {
    val currentSong: Song? get() = items.getOrNull(currentIndex)
}

fun PlayerUiState.toQueueSnapshot(): QueueSnapshot = QueueSnapshot(
    items = queue,
    currentIndex = QueueOps.resolveCurrentIndex(queue, currentSong?.id, currentQueueIndex),
    userQueuedIds = userQueuedSongIds,
    playlistActive = playlistQueueActive,
    repeatMode = repeatMode,
    isShuffle = isShuffle
)

object PlaybackQueueStore {
    private val _state = MutableStateFlow(QueueSnapshot())
    val state: StateFlow<QueueSnapshot> = _state.asStateFlow()

    fun publish(snapshot: QueueSnapshot) {
        _state.value = snapshot
    }

    fun reset() {
        _state.value = QueueSnapshot()
    }
}
