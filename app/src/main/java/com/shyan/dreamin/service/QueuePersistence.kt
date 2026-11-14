package com.shyan.dreamin.service

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.shyan.dreamin.data.local.UserPreferencesDataStore
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.queue.PlaybackQueueStore
import com.shyan.dreamin.data.queue.QueueRestore
import com.shyan.dreamin.data.queue.QueueSnapshot
import com.shyan.dreamin.data.queue.ResumeTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class QueuePersistence(
    private val player: Player,
    private val prefs: UserPreferencesDataStore,
    private val scope: CoroutineScope,
    private val store: PlaybackQueueStore = PlaybackQueueStore
) : Player.Listener {

    private data class Checkpoint(val sequence: Long, val snapshot: QueueSnapshot, val song: Song, val positionMs: Long)

    private val writeLock = Mutex()
    private val nextSequence = AtomicLong()
    private var lastWrittenSequence = -1L

    private var queueJob: Job? = null
    private var tickerJob: Job? = null

    @OptIn(FlowPreview::class)
    fun start() {
        player.addListener(this)
        queueJob = scope.launch {
            store.state
                .drop(1)
                .debounce(QUEUE_SAVE_DEBOUNCE_MS)
                .collect { saveNow() }
        }
    }

    fun release() {
        player.removeListener(this)
        queueJob?.cancel()
        tickerJob?.cancel()
    }

    suspend fun loadResumeTarget(): ResumeTarget? {
        val saved = withContext(Dispatchers.IO) { prefs.loadSavedQueue() }
        val session = withContext(Dispatchers.IO) { prefs.lastSession.first() }
        return QueueRestore.resumeTarget(store.state.value, saved, session)
    }

    fun saveNow() {
        val checkpoint = capture() ?: return
        scope.launch { persist(checkpoint) }
    }

    fun saveBlocking() {
        val checkpoint = capture() ?: return
        runBlocking { withTimeoutOrNull(BLOCKING_SAVE_TIMEOUT_MS) { persist(checkpoint) } }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        tickerJob?.cancel()
        if (!isPlaying) {
            saveNow()
            return
        }
        tickerJob = scope.launch {
            while (isActive) {
                delay(POSITION_SAVE_INTERVAL_MS)
                saveNow()
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        saveNow()
    }

    private fun capture(): Checkpoint? {
        val snapshot = store.state.value
        val song = snapshot.currentSong ?: return null
        if (player.currentMediaItem?.mediaId != song.id) return null
        val position = if (player.playbackState == Player.STATE_ENDED) 0L else player.currentPosition.coerceAtLeast(0L)
        return Checkpoint(nextSequence.getAndIncrement(), snapshot, song, position)
    }

    private suspend fun persist(checkpoint: Checkpoint) {
        try {
            withContext(Dispatchers.IO) {
                writeLock.withLock {
                    if (checkpoint.sequence <= lastWrittenSequence) return@withLock
                    QueueRestore.toSaved(checkpoint.snapshot)?.let { prefs.saveQueue(it) }
                    prefs.saveLastSession(checkpoint.song, checkpoint.positionMs)
                    lastWrittenSequence = checkpoint.sequence
                }
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Could not save playback session: ${e.message}")
        }
    }

    private companion object {
        const val TAG = "QueuePersistence"
        const val QUEUE_SAVE_DEBOUNCE_MS = 2_000L
        const val POSITION_SAVE_INTERVAL_MS = 15_000L
        const val BLOCKING_SAVE_TIMEOUT_MS = 1_500L
    }
}
