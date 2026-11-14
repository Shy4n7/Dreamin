package com.shyan.dreamin.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.queue.AutoEndAction
import com.shyan.dreamin.data.queue.PlaybackQueueStore
import com.shyan.dreamin.data.queue.QueueTransitions
import com.shyan.dreamin.data.repository.CatalogRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class QueueController(
    private val context: Context,
    private val player: Player,
    private val scope: CoroutineScope,
    private val streamResolver: StreamResolver,
    private val catalog: CatalogRepository,
    private val store: PlaybackQueueStore = PlaybackQueueStore
) : Player.Listener {

    private var advanceJob: Job? = null
    private var preloadJob: Job? = null
    private var extendJob: Job? = null
    private var storeJob: Job? = null
    private var consecutiveFailures = 0
    private var pendingPreloadId: String? = null

    fun start() {
        player.addListener(this)
        storeJob = scope.launch {
            store.state
                .map { it.currentSong?.id to QueueTransitions.upcomingSong(it)?.id }
                .distinctUntilChanged()
                .drop(1)
                .collect { syncPreload(delayMs = 0L) }
        }
    }

    fun release() {
        player.removeListener(this)
        listOf(storeJob, advanceJob, preloadJob, extendJob).forEach { it?.cancel() }
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        if (playbackState == Player.STATE_ENDED) {
            perform(QueueTransitions.onAutoEnd(store.state.value))
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        pruneItemsBeforeCurrent()
        val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        store.update { QueueTransitions.onItemTransition(it, id) }
        maybeExtend()
        syncPreload(delayMs = PRELOAD_SETTLE_MS)
    }

    private fun perform(action: AutoEndAction) {
        when (action) {
            AutoEndAction.ReplayCurrent -> {
                player.seekTo(0L)
                player.play()
            }
            is AutoEndAction.Play -> {
                store.publish(action.snapshot)
                play(action.song)
            }
            is AutoEndAction.ContinueWithRadio -> continueWithRadio(action)
            AutoEndAction.Stop -> Unit
        }
    }

    private fun play(song: Song) {
        advanceJob?.cancel()
        advanceJob = scope.launch {
            try {
                val url = streamResolver.resolve(song)
                if (store.state.value.currentSong?.id != song.id) return@launch
                store.clearHalt()
                player.setMediaItem(MediaItemFactory.build(song, url))
                player.prepare()
                player.play()
                consecutiveFailures = 0
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Could not start '${song.title}': ${e.message}")
                skipUnplayable(song)
            }
        }
    }

    private fun skipUnplayable(song: Song) {
        consecutiveFailures++
        if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
            consecutiveFailures = 0
            android.util.Log.w(TAG, "Giving up after $MAX_CONSECUTIVE_FAILURES unplayable songs in a row")
            store.halt(HALT_UNPLAYABLE)
            return
        }
        perform(QueueTransitions.skipFailed(store.state.value, song.id))
    }

    private fun continueWithRadio(action: AutoEndAction.ContinueWithRadio) {
        store.publish(action.snapshot)
        advanceJob?.cancel()
        advanceJob = scope.launch {
            try {
                if (store.isExtending.value) {
                    withTimeoutOrNull(EXTENSION_WAIT_MS) { store.isExtending.first { !it } }
                }
                val afterExtension = store.state.value
                val queued = afterExtension.items.getOrNull(action.slot)
                if (queued != null) {
                    store.publish(afterExtension.copy(currentIndex = action.slot))
                    play(queued)
                    return@launch
                }
                val pick = catalog.findRadioContinuation(action.seed)
                if (pick != null) {
                    store.update { QueueTransitions.playAfterCurrent(it, pick) }
                    play(pick)
                } else {
                    replayCurrentIfAny()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Radio continuation failed: ${e.message}")
                replayCurrentIfAny()
            }
        }
    }

    private fun replayCurrentIfAny() {
        val current = store.state.value.currentSong
        if (current == null || player.currentMediaItem?.mediaId != current.id) {
            store.halt(HALT_NO_RADIO)
            return
        }
        player.seekTo(0L)
        player.play()
    }

    private fun maybeExtend() {
        val snapshot = store.state.value
        if (!QueueTransitions.needsExtension(snapshot, store.isExtending.value)) return
        val seed = snapshot.currentSong ?: return
        store.setExtending(true)
        extendJob?.cancel()
        extendJob = scope.launch {
            try {
                val items = store.state.value.items
                val tracks = catalog.buildUpNext(seed, items, relatedSeedId = items.lastOrNull()?.id ?: seed.id)
                store.update { QueueTransitions.appendUnique(it, tracks) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Queue extension failed: ${e.message}")
            } finally {
                store.setExtending(false)
            }
        }
    }

    private fun syncPreload(delayMs: Long) {
        val snapshot = store.state.value
        val current = snapshot.currentSong
        if (current == null || player.currentMediaItem?.mediaId != current.id) {
            preloadJob?.cancel()
            return
        }
        val next = QueueTransitions.upcomingSong(snapshot)
        if (preloadJob?.isActive == true && pendingPreloadId == next?.id) return
        preloadJob?.cancel()
        val preloadedId = preloadedIdAfterCurrent()
        if (preloadedId == next?.id) return
        if (preloadedId != null) clearItemsAfterCurrent()
        if (next == null) return
        pendingPreloadId = next.id
        preloadJob = scope.launch {
            try {
                if (delayMs > 0L) delay(delayMs)
                val url = streamResolver.resolve(next)
                if (url.isBlank()) return@launch
                val latest = store.state.value
                if (latest.currentSong?.id != current.id) return@launch
                if (QueueTransitions.upcomingSong(latest)?.id != next.id) return@launch
                if (player.currentMediaItem?.mediaId != current.id) return@launch
                if (preloadedIdAfterCurrent() == next.id) return@launch
                clearItemsAfterCurrent()
                player.addMediaItem(MediaItemFactory.build(next, url))
                PreBufferManager.preBufferUpcomingStream(context, url, scope)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Failed to preload '${next.title}': ${e.message}")
            }
        }
    }

    private fun pruneItemsBeforeCurrent() {
        val index = player.currentMediaItemIndex
        if (index > 0) player.removeMediaItems(0, index)
    }

    private fun preloadedIdAfterCurrent(): String? {
        val nextIndex = player.currentMediaItemIndex + 1
        if (nextIndex >= player.mediaItemCount) return null
        return player.getMediaItemAt(nextIndex).mediaId
    }

    private fun clearItemsAfterCurrent() {
        val nextIndex = player.currentMediaItemIndex + 1
        if (nextIndex < player.mediaItemCount) player.removeMediaItems(nextIndex, player.mediaItemCount)
    }

    private companion object {
        const val TAG = "QueueController"
        const val PRELOAD_SETTLE_MS = 1_200L
        const val EXTENSION_WAIT_MS = 2_500L
        const val MAX_CONSECUTIVE_FAILURES = 3
        const val HALT_UNPLAYABLE = "Couldn't play the next songs"
        const val HALT_NO_RADIO = "Couldn't find another song to play"
    }
}
