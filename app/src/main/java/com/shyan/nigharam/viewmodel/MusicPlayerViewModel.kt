package com.shyan.nigharam.viewmodel

import android.app.Application
import android.content.ComponentName
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.shyan.nigharam.data.model.*
import com.shyan.nigharam.data.network.NetworkService
import com.shyan.nigharam.service.MusicService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val api = NetworkService.api

    // Track which song was playing before current (for recommendation transitions)
    private var previousSongId: String? = null

    // ── Init ──────────────────────────────────────────────────────────────────

    init {
        connectToService()
        loadChart()
    }

    // Connect ViewModel to the MusicService via MediaController
    private fun connectToService() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MusicService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            controller = controllerFuture?.get()
            controller?.addListener(playerListener)
            startPositionPoller()
        }, MoreExecutors.directExecutor())
    }

    // ── Player Listener — syncs ExoPlayer state → UI ─────────────────────────

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) {
            val playbackState = when {
                state == Player.STATE_BUFFERING -> PlaybackState.Loading
                state == Player.STATE_READY && controller?.isPlaying == true -> PlaybackState.Playing
                state == Player.STATE_READY -> PlaybackState.Paused
                state == Player.STATE_ENDED -> {
                    playNext()
                    PlaybackState.Idle
                }
                else -> PlaybackState.Idle
            }
            _uiState.update { it.copy(playbackState = playbackState) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _uiState.update {
                it.copy(
                    playbackState = if (isPlaying) PlaybackState.Playing else PlaybackState.Paused
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            // Keep UI in sync if ExoPlayer auto-advances
            _uiState.update { it.copy(durationMs = controller?.duration?.coerceAtLeast(0) ?: 0L) }
        }
    }

    // Poll position every 500ms while playing
    private fun startPositionPoller() {
        viewModelScope.launch {
            while (true) {
                delay(500)
                val pos = controller?.currentPosition?.coerceAtLeast(0) ?: 0L
                val dur = controller?.duration?.coerceAtLeast(0) ?: 0L
                _uiState.update { it.copy(currentPositionMs = pos, durationMs = dur) }
            }
        }
    }

    // ── Playback Commands ─────────────────────────────────────────────────────

    fun playSong(song: Song) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    currentSong = song,
                    playbackState = PlaybackState.Loading
                )
            }

            try {
                // Ask server for stream URL (same as Daydreamin's /api/mobile/play)
                val response = api.getStreamUrl(
                    id = song.id,
                    artist = song.artist,
                    title = song.title,
                    previousSongId = previousSongId
                )

                val streamUrl = response.proxyUrl ?: response.streamUrl
                previousSongId = song.id

                // Build MediaItem with metadata (shows in lock screen notification)
                val mediaItem = MediaItem.Builder()
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(android.net.Uri.parse(song.artworkUrl))
                            .build()
                    )
                    .build()

                controller?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                }

                // Fetch Up Next queue for this song
                fetchUpNext(song.id)
                fetchRecommendations(song.id)

            } catch (e: Exception) {
                _uiState.update {
                    it.copy(playbackState = PlaybackState.Error("Failed to load: ${e.message}"))
                }
            }
        }
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun playNext() {
        val state = _uiState.value
        val queue = state.queue
        if (queue.isEmpty()) return
        val current = state.currentSong
        val nextIndex = if (current == null) 0
        else {
            val idx = queue.indexOfFirst { it.id == current.id }
            if (idx >= 0 && idx < queue.size - 1) idx + 1 else 0
        }
        val nextSong = queue.getOrNull(nextIndex) ?: queue.first()
        playSong(nextSong)
    }

    fun playPrevious() {
        val state = _uiState.value
        // If > 3 seconds in, restart. Otherwise go to previous.
        if ((controller?.currentPosition ?: 0L) > 3000L) {
            controller?.seekTo(0L)
            return
        }
        val queue = state.queue
        val current = state.currentSong
        if (queue.isEmpty() || current == null) return
        val idx = queue.indexOfFirst { it.id == current.id }
        val prevSong = if (idx > 0) queue[idx - 1] else queue.last()
        playSong(prevSong)
    }

    fun addToQueue(song: Song) {
        _uiState.update { it.copy(queue = it.queue + song) }
    }

    fun removeFromQueue(song: Song) {
        _uiState.update { it.copy(queue = it.queue.filter { s -> s.id != song.id }) }
    }

    fun toggleQueue() {
        _uiState.update { it.copy(isQueueVisible = !it.isQueueVisible) }
    }

    // ── Search ────────────────────────────────────────────────────────────────

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query, isSearchActive = query.isNotEmpty(), searchError = null) }
        if (query.length >= 2) {
            viewModelScope.launch {
                try {
                    val results = api.search(query)
                    _uiState.update { it.copy(searchResults = results.results, searchError = if (results.results.isEmpty()) "No results for \"$query\"" else null) }
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Search failed: ${e.javaClass.simpleName}: ${e.message}")
                    _uiState.update { it.copy(searchResults = emptyList(), searchError = "Search error: ${e.message}") }
                }
            }
        } else {
            _uiState.update { it.copy(searchResults = emptyList(), searchError = null) }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", isSearchActive = false, searchResults = emptyList(), searchError = null) }
    }

    // ── Data Fetching ─────────────────────────────────────────────────────────

    private fun loadChart() {
        viewModelScope.launch {
            try {
                val chart = api.getChart()
                _uiState.update { it.copy(trendingCharts = chart.songs) }
            } catch (_: Exception) { }
        }
    }

    private fun fetchUpNext(songId: String) {
        viewModelScope.launch {
            try {
                val upNext = api.getUpNext(songId)
                _uiState.update { it.copy(queue = upNext.songs) }
            } catch (_: Exception) { }
        }
    }

    private fun fetchRecommendations(songId: String) {
        viewModelScope.launch {
            try {
                val recs = api.getRecommendations(songId)
                _uiState.update { it.copy(recommendations = recs.recommendations) }
            } catch (_: Exception) { }
        }
    }

    // ── Color update from palette ─────────────────────────────────────────────

    fun updateDominantColor(color: Int) {
        _uiState.update { it.copy(dominantColor = color) }
    }

    // ── Cleanup ───────────────────────────────────────────────────────────────

    override fun onCleared() {
        controller?.removeListener(playerListener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
