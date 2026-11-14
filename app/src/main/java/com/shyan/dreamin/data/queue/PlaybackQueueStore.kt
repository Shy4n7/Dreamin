package com.shyan.dreamin.data.queue

import com.shyan.dreamin.data.model.PlayerUiState
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.model.TrackRepeatMode
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class QueueSnapshot(
    val items: List<Song> = emptyList(),
    val currentIndex: Int = -1,
    val userQueuedIds: List<String> = emptyList(),
    val playlistActive: Boolean = false,
    val repeatMode: TrackRepeatMode = TrackRepeatMode.OFF,
    val isShuffle: Boolean = false,
    val originalOrder: List<Song> = emptyList()
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

fun PlayerUiState.withQueue(snapshot: QueueSnapshot): PlayerUiState = copy(
    currentSong = snapshot.currentSong ?: currentSong,
    queue = snapshot.items,
    currentQueueIndex = snapshot.currentIndex,
    userQueuedSongIds = snapshot.userQueuedIds,
    playlistQueueActive = snapshot.playlistActive,
    repeatMode = snapshot.repeatMode,
    isShuffle = snapshot.isShuffle
)

private const val SKIP_EVENT_BUFFER = 8

object PlaybackQueueStore {
    private val _state = MutableStateFlow(QueueSnapshot())
    val state: StateFlow<QueueSnapshot> = _state.asStateFlow()

    private val _isExtending = MutableStateFlow(false)
    val isExtending: StateFlow<Boolean> = _isExtending.asStateFlow()

    fun publish(snapshot: QueueSnapshot) {
        _state.value = snapshot
    }

    fun update(transform: (QueueSnapshot) -> QueueSnapshot) {
        _state.update(transform)
    }

    private val _haltMessage = MutableStateFlow<String?>(null)
    val haltMessage: StateFlow<String?> = _haltMessage.asStateFlow()

    private val _skipEvents = MutableSharedFlow<SkipEvent>(
        extraBufferCapacity = SKIP_EVENT_BUFFER,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val skipEvents: SharedFlow<SkipEvent> = _skipEvents.asSharedFlow()

    fun emitSkip(event: SkipEvent) {
        _skipEvents.tryEmit(event)
    }

    fun setExtending(extending: Boolean) {
        _isExtending.value = extending
    }

    fun halt(message: String) {
        _haltMessage.value = message
    }

    fun clearHalt() {
        _haltMessage.value = null
    }

    fun reset() {
        _state.value = QueueSnapshot()
        _isExtending.value = false
        _haltMessage.value = null
    }
}
