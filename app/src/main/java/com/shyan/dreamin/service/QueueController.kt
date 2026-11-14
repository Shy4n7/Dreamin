package com.shyan.dreamin.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import com.shyan.dreamin.data.local.DownloadRepository
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.queue.AutoEndAction
import com.shyan.dreamin.data.queue.PlaybackQueueStore
import com.shyan.dreamin.data.queue.QueueTransitions
import com.shyan.dreamin.data.queue.SkipDirection
import com.shyan.dreamin.data.queue.SkipEvent
import com.shyan.dreamin.data.repository.CatalogRepository
import com.shyan.dreamin.data.service.AudioStreamResolver
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
    private val downloadRepo: DownloadRepository,
    private val store: PlaybackQueueStore = PlaybackQueueStore,
    private val clock: () -> Long = android.os.SystemClock::elapsedRealtime
) : Player.Listener {

    private var advanceJob: Job? = null
    private var preloadJob: Job? = null
    private var extendJob: Job? = null
    private var storeJob: Job? = null
    private var consecutiveFailures = 0
    private var pendingPreloadId: String? = null
    private var retriedSongId: String? = null
    private var isRetryReload = false
    private var lastSkipAtMs = 0L
    var shouldHoldAtEnd: () -> Boolean = { false }
    var onHeldAtEnd: (() -> Unit)? = null

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

    fun skipToNext() {
        if (!acceptSkip()) {
            store.emitSkip(SkipEvent(SkipDirection.NEXT, changedTrack = false))
            return
        }
        consecutiveFailures = 0
        val snapshot = store.state.value
        val action = QueueTransitions.onUserNext(snapshot)
        store.emitSkip(SkipEvent(SkipDirection.NEXT, changesTrack(action)))
        val isPreloaded = action is AutoEndAction.Play &&
            action.snapshot.items == snapshot.items &&
            preloadedIdAfterCurrent() == action.song.id
        if (isPreloaded) {
            advanceJob?.cancel()
            player.seekToNextMediaItem()
            player.play()
            return
        }
        perform(action)
    }

    fun skipToPrevious() {
        if (!acceptSkip()) {
            store.emitSkip(SkipEvent(SkipDirection.PREVIOUS, changedTrack = false))
            return
        }
        consecutiveFailures = 0
        val action = QueueTransitions.onUserPrevious(store.state.value, player.currentPosition)
        store.emitSkip(SkipEvent(SkipDirection.PREVIOUS, changesTrack(action)))
        perform(action)
    }

    private fun changesTrack(action: AutoEndAction): Boolean = when (action) {
        is AutoEndAction.Play, is AutoEndAction.ContinueWithRadio -> true
        AutoEndAction.ReplayCurrent, AutoEndAction.Stop -> false
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_ENDED -> {
                consecutiveFailures = 0
                val hold = shouldHoldAtEnd()
                perform(QueueTransitions.onAutoEnd(store.state.value), autoplay = !hold)
                if (hold) onHeldAtEnd?.invoke()
            }
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        android.util.Log.e(TAG, "Playback error: ${error.message}", error)
        val current = store.state.value.currentSong ?: return
        if (player.currentMediaItem?.mediaId != current.id) return
        AudioStreamResolver.removeCachedStreamUrl(current.id)
        if (retriedSongId == current.id) {
            skipUnplayable(current)
            return
        }
        retriedSongId = current.id
        advanceJob?.cancel()
        advanceJob = scope.launch {
            try {
                downloadRepo.deletePrefetch(current.id)
                val url = streamResolver.resolve(current)
                if (store.state.value.currentSong?.id != current.id) return@launch
                isRetryReload = true
                player.setMediaItem(MediaItemFactory.build(current, url))
                player.prepare()
                player.play()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Re-resolve failed for '${current.title}': ${e.message}")
                skipUnplayable(current)
            }
        }
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
        pruneItemsBeforeCurrent()
        val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() } ?: return
        val isFreshLoad = reason == Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED && !isRetryReload
        if (id != retriedSongId || isFreshLoad) retriedSongId = null
        isRetryReload = false
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) consecutiveFailures = 0
        store.update { QueueTransitions.onItemTransition(it, id) }
        maybeExtend()
        syncPreload(delayMs = PRELOAD_SETTLE_MS)
    }

    private fun perform(action: AutoEndAction, autoplay: Boolean = true) {
        when (action) {
            AutoEndAction.ReplayCurrent -> {
                player.seekTo(0L)
                if (autoplay) player.play() else player.pause()
            }
            is AutoEndAction.Play -> {
                store.publish(action.snapshot)
                play(action.song, autoplay)
            }
            is AutoEndAction.ContinueWithRadio -> continueWithRadio(action, autoplay)
            AutoEndAction.Stop -> Unit
        }
    }

    private fun play(song: Song, autoplay: Boolean = true) {
        advanceJob?.cancel()
        advanceJob = scope.launch {
            try {
                val url = streamResolver.resolve(song)
                if (store.state.value.currentSong?.id != song.id) return@launch
                store.clearHalt()
                player.playWhenReady = autoplay
                player.setMediaItem(MediaItemFactory.build(song, url))
                player.prepare()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w(TAG, "Could not start '${song.title}': ${e.message}")
                skipUnplayable(song)
            }
        }
    }

    private fun acceptSkip(): Boolean {
        val now = clock()
        if (now - lastSkipAtMs < SKIP_DEBOUNCE_MS) return false
        lastSkipAtMs = now
        return true
    }

    private fun skipUnplayable(song: Song) {
        consecutiveFailures++
        if (consecutiveFailures > MAX_CONSECUTIVE_FAILURES) {
            consecutiveFailures = 0
            android.util.Log.w(TAG, "Giving up after $MAX_CONSECUTIVE_FAILURES unplayable songs in a row")
            store.halt(HALT_UNPLAYABLE)
            return
        }
        val snapshot = store.state.value
        val action = QueueTransitions.skipFailed(snapshot, song.id)
        if (action == AutoEndAction.Stop && snapshot.currentSong?.id == song.id) {
            store.halt(HALT_UNPLAYABLE)
            return
        }
        perform(action)
    }

    private fun continueWithRadio(action: AutoEndAction.ContinueWithRadio, autoplay: Boolean = true) {
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
                    play(queued, autoplay)
                    return@launch
                }
                val pick = catalog.findRadioContinuation(action.seed)
                if (pick != null) {
                    store.update { QueueTransitions.playAfterCurrent(it, pick) }
                    play(pick, autoplay)
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
        const val SKIP_DEBOUNCE_MS = 350L
        const val HALT_UNPLAYABLE = "Couldn't play the next songs"
        const val HALT_NO_RADIO = "Couldn't find another song to play"
    }
}
