package com.shyan.dreamin.viewmodel

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.service.MusicService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Encapsulates Media3 MediaController connection and playback operations.
 */
class PlaybackController(private val application: Application) {

    private var controllerFuture: ListenableFuture<MediaController>? = null
    var controller: MediaController? = null
        private set

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    fun connect(onConnected: (MediaController) -> Unit) {
        val sessionToken = SessionToken(application, ComponentName(application, MusicService::class.java))
        controllerFuture = MediaController.Builder(application, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                val c = controllerFuture?.get()
                controller = c
                _isConnected.value = true
                if (c != null) onConnected(c)
            } catch (_: Exception) {}
        }, MoreExecutors.directExecutor())
    }

    fun playSong(song: Song, streamUri: String, startPositionMs: Long = 0L) {
        val c = controller ?: return
        val metadata = MediaMetadata.Builder()
            .setTitle(song.displayTitle)
            .setArtist(song.artist)
            .setArtworkUri(Uri.parse(song.displayArtworkUrl))
            .build()

        val mediaItem = MediaItem.Builder()
            .setMediaId(song.id)
            .setUri(streamUri)
            .setMediaMetadata(metadata)
            .build()

        c.setMediaItem(mediaItem, startPositionMs)
        c.prepare()
        c.play()
    }

    fun pause() {
        controller?.pause()
    }

    fun resume() {
        controller?.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun release() {
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
        controller = null
        _isConnected.value = false
    }
}
