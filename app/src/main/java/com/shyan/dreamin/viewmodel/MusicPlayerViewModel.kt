package com.shyan.dreamin.viewmodel

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
import com.shyan.dreamin.data.local.AppDatabase
import com.shyan.dreamin.data.local.FavoritesRepository
import com.shyan.dreamin.data.local.PlayHistoryRepository
import com.shyan.dreamin.data.local.StatsRepository
import com.shyan.dreamin.data.model.DreaminTheme
import com.shyan.dreamin.data.model.*
import com.shyan.dreamin.data.network.NetworkService
import com.shyan.dreamin.service.MusicService
import androidx.media3.common.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicPlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    // Position/duration update every 100ms — kept separate so HomeScreen never recomposes for it
    private val _progress = MutableStateFlow(PlaybackProgress())
    val progressFlow: StateFlow<PlaybackProgress> = _progress.asStateFlow()

    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null

    private val api = NetworkService.api
    private val userPrefs = com.shyan.dreamin.data.local.UserPreferencesDataStore(getApplication<Application>())
    private val db = AppDatabase.getInstance(getApplication<Application>())
    private val historyRepo = PlayHistoryRepository(db.playHistoryDao())
    private val favoritesRepo = FavoritesRepository(db.favoriteDao())
    private val statsRepo = StatsRepository(db.playHistoryDao())
    private val playlistRepo = com.shyan.dreamin.data.local.PlaylistRepository(db.playlistDao())

    private var isListenerAttached = false
    private var searchJob: Job? = null
    private var sleepTimerJob: Job? = null


    init {
        // Critical path: userName + theme must resolve before the app shows content.
        // Read both with first() on IO so the gate clears in <100ms, then keep collecting.
        viewModelScope.launch(Dispatchers.IO) {
            val name = userPrefs.userName.first()
            val theme = userPrefs.selectedTheme.first()
            val session = userPrefs.lastSession.first()
            val searches = userPrefs.recentSearches.first()
            _uiState.update {
                it.copy(
                    userName = name,
                    selectedTheme = theme,
                    lastSession = session,
                    recentSearches = searches
                )
            }
            // Continue collecting for changes
            launch { userPrefs.userName.collect { n -> _uiState.update { it.copy(userName = n) } } }
            launch { userPrefs.selectedTheme.collect { t -> _uiState.update { it.copy(selectedTheme = t) } } }
            launch { userPrefs.lastSession.collect { s -> if (_uiState.value.currentSong == null) _uiState.update { it.copy(lastSession = s) } } }
            launch { userPrefs.recentSearches.collect { r -> _uiState.update { it.copy(recentSearches = r) } } }
        }

        connectToService()
        loadChart()
        loadRecentlyPlayed()
        loadTopSongs()
        loadFavorites()
        loadStats()
        loadPlaylists()
    }

    private fun loadUserName() {
        // No-op: handled in init's critical-path block above
    }

    fun saveUserName(name: String) {
        viewModelScope.launch {
            userPrefs.saveUserName(name.trim())
        }
    }

    private fun loadRecentlyPlayed() {
        viewModelScope.launch {
            historyRepo.getRecentlyPlayed().collect { songs ->
                _uiState.update { it.copy(recentlyPlayed = songs) }
            }
        }
    }

    private fun loadTopSongs() {
        viewModelScope.launch {
            historyRepo.getTopSongs().collect { songs ->
                _uiState.update { it.copy(topSongs = songs) }
            }
        }
    }

    private fun loadFavorites() {
        viewModelScope.launch {
            favoritesRepo.observeAll().collect { songs ->
                _uiState.update { state ->
                    state.copy(
                        favorites = songs,
                        currentSongIsFavorite = state.currentSong?.id?.let { id ->
                            songs.any { s -> s.id == id }
                        } ?: false
                    )
                }
            }
        }
    }

    fun toggleFavorite() {
        val song = _uiState.value.currentSong ?: return
        viewModelScope.launch {
            if (_uiState.value.currentSongIsFavorite) {
                favoritesRepo.removeFavorite(song.id)
            } else {
                favoritesRepo.addFavorite(song)
            }
        }
    }

    private fun loadStats() {
        // Throttle: only recompute stats when the count actually changes, not on every row insert
        viewModelScope.launch {
            statsRepo.weeklyStatsFlow()
                .distinctUntilChanged()
                .collect { stats -> _uiState.update { it.copy(listeningStats = stats) } }
        }
    }

    private fun loadPlaylists() {
        viewModelScope.launch {
            playlistRepo.observePlaylists().collect { lists ->
                // Fetch artworks in parallel on IO, then update state once
                val artworks = withContext(Dispatchers.IO) {
                    lists.associate { playlist ->
                        playlist.id to playlistRepo.getFirstFourArtworks(playlist.id)
                    }
                }
                _uiState.update { it.copy(playlists = lists, playlistArtworks = artworks) }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch { playlistRepo.createPlaylist(name.trim()) }
    }

    fun saveQueueAsPlaylist(name: String) {
        val songs = _uiState.value.queue
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistRepo.createPlaylist(name.trim())
            songs.forEachIndexed { index, song -> playlistRepo.addSong(playlistId, song, index) }
        }
    }

    fun deletePlaylist(playlistId: Long) {
        viewModelScope.launch {
            playlistRepo.deletePlaylist(playlistId)
            if (_uiState.value.openPlaylistId == playlistId) {
                _uiState.update { it.copy(openPlaylistId = null, openPlaylistSongs = emptyList()) }
            }
        }
    }

    fun renamePlaylist(playlistId: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { playlistRepo.renamePlaylist(playlistId, name.trim()) }
    }

    fun openPlaylist(playlistId: Long) {
        _uiState.update { it.copy(openPlaylistId = playlistId) }
        viewModelScope.launch {
            playlistRepo.observeSongs(playlistId).collect { songs ->
                _uiState.update { it.copy(openPlaylistSongs = songs) }
            }
        }
    }

    fun closePlaylist() {
        _uiState.update { it.copy(openPlaylistId = null, openPlaylistSongs = emptyList()) }
    }

    fun addSongToPlaylist(playlistId: Long, song: Song) {
        viewModelScope.launch {
            val existing = playlistRepo.getSongs(playlistId)
            if (existing.any { it.id == song.id }) return@launch
            playlistRepo.addSong(playlistId, song, existing.size)
        }
    }

    fun removeSongFromPlaylist(playlistId: Long, songId: String) {
        viewModelScope.launch { playlistRepo.removeSong(playlistId, songId) }
    }

    fun playSongsFromPlaylist(playlistId: Long) {
        viewModelScope.launch {
            val songs = playlistRepo.getSongs(playlistId)
            if (songs.isEmpty()) return@launch
            _uiState.update { it.copy(queue = songs, playlistQueueActive = true) }
            playSong(songs.first(), fromPlaylist = true)
        }
    }

    fun playSongFromPlaylist(song: Song, playlistSongs: List<Song>) {
        _uiState.update { it.copy(queue = playlistSongs, playlistQueueActive = true) }
        playSong(song, fromPlaylist = true)
    }

    fun shuffleAndPlayPlaylist(playlistSongs: List<Song>) {
        if (playlistSongs.isEmpty()) return
        val shuffled = playlistSongs.shuffled()
        _uiState.update { it.copy(queue = shuffled, playlistQueueActive = true) }
        playSong(shuffled.first(), fromPlaylist = true)
    }

    // Play a song from a non-playlist list (home/trending)
    // Seeds the queue with songs after the tapped one; fetchUpNext appends more
    fun playSongFromList(song: Song, songs: List<Song>) {
        val idx = songs.indexOfFirst { it.id == song.id }
        val tail = if (idx >= 0) songs.drop(idx + 1) else emptyList()
        _uiState.update { it.copy(queue = tail, playlistQueueActive = false) }
        playSong(song)
    }

    fun shuffleAndPlayList(songs: List<Song>) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        _uiState.update { it.copy(queue = shuffled.drop(1), playlistQueueActive = false) }
        playSong(shuffled.first())
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val endMs = System.currentTimeMillis() + minutes * 60_000L
        _uiState.update { it.copy(sleepTimerEndMs = endMs) }
        sleepTimerJob = viewModelScope.launch {
            delay(minutes * 60_000L)
            controller?.pause()
            _uiState.update { it.copy(sleepTimerEndMs = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.update { it.copy(sleepTimerEndMs = null) }
    }

    private fun connectToService() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(context, ComponentName(context, MusicService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        // Use main-thread executor so listener body safely touches UI state and starts coroutines
        controllerFuture?.addListener({
            val c = runCatching { controllerFuture?.get() }.getOrNull() ?: return@addListener
            controller = c
            if (!isListenerAttached) {
                c.addListener(playerListener)
                isListenerAttached = true
            }
            syncStateFromController(c)
            startPositionPoller()
        }, context.mainExecutor)
    }

    private fun syncStateFromController(c: MediaController) {
        val mediaItem = c.currentMediaItem ?: return
        val meta = mediaItem.mediaMetadata
        val id = mediaItem.mediaId.takeIf { it.isNotBlank() } ?: return
        val title = meta.title?.toString()?.takeIf { it.isNotBlank() } ?: return
        val song = Song(
            id = id,
            title = title,
            artist = meta.artist?.toString() ?: "",
            artworkUrl = meta.artworkUri?.toString() ?: ""
        )
        val playbackState = when {
            c.isPlaying -> PlaybackState.Playing
            c.playbackState == Player.STATE_BUFFERING -> PlaybackState.Loading
            c.playbackState == Player.STATE_READY -> PlaybackState.Paused
            else -> return // Not playing anything meaningful
        }
        val pos = c.currentPosition.coerceAtLeast(0L)
        val duration = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
        _uiState.update { it.copy(currentSong = song, playbackState = playbackState) }
        _progress.value = PlaybackProgress(pos, duration)
    }

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
            val meta = mediaItem?.mediaMetadata
            val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
            val title = meta?.title?.toString()?.takeIf { it.isNotBlank() }
            val duration = controller?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            _uiState.update { state ->
                state.copy(
                    currentSong = if (id != null && title != null) {
                        Song(
                            id = id,
                            title = title,
                            artist = meta?.artist?.toString() ?: "",
                            artworkUrl = meta?.artworkUri?.toString() ?: ""
                        )
                    } else state.currentSong,
                    durationMs = duration
                )
            }
        }
    }

    private fun startPositionPoller() {
        viewModelScope.launch {
            var saveCounter = 0
            while (isActive) {
                delay(100)
                val c = controller ?: continue
                if (!c.isPlaying) continue
                val pos = c.currentPosition.coerceAtLeast(0L)
                val dur = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                val current = _progress.value
                if (pos != current.currentPositionMs || dur != current.durationMs) {
                    _progress.value = PlaybackProgress(pos, dur)
                }
                saveCounter++
                if (saveCounter >= 100) {
                    saveCounter = 0
                    _uiState.value.currentSong?.let { song ->
                        userPrefs.saveLastSession(song, pos)
                    }
                }
            }
        }
    }

    fun playSong(song: Song, fromPlaylist: Boolean = false) {
        // Optimistic UI: update song + state immediately so artwork/title swap is instant
        _uiState.update {
            it.copy(
                currentSong = song,
                playbackState = PlaybackState.Loading,
                currentSongIsFavorite = it.favorites.any { s -> s.id == song.id },
                playlistQueueActive = if (fromPlaylist) it.playlistQueueActive else false
            )
        }
        _progress.value = PlaybackProgress(0L, 0L)

        viewModelScope.launch {
            try {
                val response = api.getStreamUrl(
                    id = song.id,
                    artist = song.artist,
                    title = song.title,
                )

                val streamUrl = response.proxyUrl ?: response.streamUrl
                    ?: throw IllegalStateException("No stream URL for '${song.title}'")

                historyRepo.recordPlay(song)

                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(streamUrl)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(
                                song.artworkUrl.takeIf { it.isNotBlank() }
                                    ?.let { android.net.Uri.parse(it) }
                            )
                            .build()
                    )
                    .build()

                controller?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                }

                if (!_uiState.value.playlistQueueActive) {
                    fetchUpNext(song.id)
                    fetchRecommendations(song.id)
                }

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
        val idx = queue.indexOfFirst { it.id == current?.id }
        if (state.playlistQueueActive) {
            if (idx < 0 || idx >= queue.size - 1) {
                controller?.pause()
                _uiState.update { it.copy(playbackState = PlaybackState.Paused) }
                return
            }
            playSong(queue[idx + 1], fromPlaylist = true)
        } else {
            val nextIndex = if (idx >= 0 && idx < queue.size - 1) idx + 1 else 0
            playSong(queue.getOrNull(nextIndex) ?: queue.first(), fromPlaylist = false)
        }
    }

    fun playPrevious() {
        val state = _uiState.value
        if ((controller?.currentPosition ?: 0L) > 3000L) {
            controller?.seekTo(0L)
            return
        }
        val queue = state.queue
        val current = state.currentSong
        if (queue.isEmpty() || current == null) return
        val idx = queue.indexOfFirst { it.id == current.id }
        val prevSong = if (idx > 0) queue[idx - 1] else queue.last()
        playSong(prevSong, fromPlaylist = state.playlistQueueActive)
    }

    fun addToQueue(song: Song) {
        _uiState.update { it.copy(queue = it.queue + song) }
    }

    fun removeFromQueue(song: Song) {
        _uiState.update { it.copy(queue = it.queue.filter { s -> s.id != song.id }) }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        val queue = _uiState.value.queue.toMutableList()
        if (fromIndex !in queue.indices || toIndex !in queue.indices) return
        val item = queue.removeAt(fromIndex)
        queue.add(toIndex, item)
        _uiState.update { it.copy(queue = queue) }
    }

    fun toggleQueue() {
        _uiState.update { it.copy(isQueueVisible = !it.isQueueVisible) }
    }

    private fun loadRecentSearches() { /* handled in init critical-path block */ }

    fun clearRecentSearches() {
        viewModelScope.launch { userPrefs.clearRecentSearches() }
    }

    private fun loadLastSession() { /* handled in init critical-path block */ }

    fun resumeLastSession() {
        val session = _uiState.value.lastSession ?: return
        viewModelScope.launch {
            playSong(session.song)
            val resumePos = session.positionMs
            if (resumePos > 0L) {
                kotlinx.coroutines.delay(1500)
                controller?.seekTo(resumePos)
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchActive = query.isNotEmpty(),
                searchPage = 1,
                hasMoreSearchResults = false,
                searchResults = emptyList()
            )
        }
        searchJob?.cancel()
        if (query.length >= 2) {
            searchJob = viewModelScope.launch {
                delay(300)
                try {
                    val results = api.search(query, page = 1)
                    userPrefs.addRecentSearch(query.trim())
                    _uiState.update {
                        it.copy(
                            searchResults = results.results,
                            hasMoreSearchResults = results.results.size >= 15
                        )
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Search failed: ${e.javaClass.simpleName}: ${e.message}")
                    _uiState.update { it.copy(searchResults = emptyList()) }
                }
            }
        }
    }

    fun loadMoreSearchResults() {
        val state = _uiState.value
        if (!state.hasMoreSearchResults || state.isLoadingMoreSearch || state.searchQuery.length < 2) return
        val nextPage = state.searchPage + 1
        _uiState.update { it.copy(isLoadingMoreSearch = true) }
        viewModelScope.launch {
            try {
                val results = api.search(state.searchQuery, page = nextPage)
                _uiState.update {
                    it.copy(
                        searchResults = it.searchResults + results.results,
                        searchPage = nextPage,
                        isLoadingMoreSearch = false,
                        hasMoreSearchResults = results.results.size >= 15
                    )
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "loadMore failed: ${e.message}")
                _uiState.update { it.copy(isLoadingMoreSearch = false) }
            }
        }
    }

    fun clearSearch() {
        _uiState.update { it.copy(searchQuery = "", isSearchActive = false, searchResults = emptyList()) }
    }

    fun refreshData() {
        android.util.Log.d("MusicVM", "Refreshing data...")
        loadChart()
    }

    private fun loadChart() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingChart = true) }
            try {
                val chart = api.getChart(language = "tamil")
                android.util.Log.d("MusicVM", "Loaded ${chart.songs.size} trending songs")
                _uiState.update { it.copy(trendingCharts = chart.songs, isLoadingChart = false) }
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Failed to load chart: ${e.javaClass.simpleName}: ${e.message}")
                _uiState.update { it.copy(isLoadingChart = false) }
            }
        }
    }

    private fun fetchUpNext(songId: String) {
        viewModelScope.launch {
            try {
                val currentQueue = _uiState.value.queue
                val excludeIds = (currentQueue.map { it.id } + songId)
                    .distinct()
                    .joinToString(",")
                val upNext = api.getUpNext(songId, exclude = excludeIds)
                val newSongs = upNext.songs.filter { s -> currentQueue.none { it.id == s.id } && s.id != songId }
                _uiState.update { it.copy(queue = currentQueue + newSongs) }
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "fetchUpNext failed: ${e.message}")
            }
        }
    }

    private fun fetchRecommendations(songId: String) {
        val seedTitle = _uiState.value.currentSong?.artist
        viewModelScope.launch {
            try {
                val recs = api.getRecommendations(songId)
                _uiState.update { it.copy(recommendations = recs.recommendations, recommendationSeedTitle = seedTitle) }
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "fetchRecommendations failed: ${e.message}")
            }
        }
    }

    fun updateDominantColor(color: Int) {
        _uiState.update { it.copy(dominantColor = color) }
    }

    fun toggleShuffle() {
        val newShuffle = !_uiState.value.isShuffle
        _uiState.update { it.copy(isShuffle = newShuffle) }
        controller?.shuffleModeEnabled = newShuffle
    }

    fun toggleRepeat() {
        val next = when (_uiState.value.repeatMode) {
            TrackRepeatMode.OFF -> TrackRepeatMode.ONE
            TrackRepeatMode.ONE -> TrackRepeatMode.ALL
            TrackRepeatMode.ALL -> TrackRepeatMode.OFF
        }
        _uiState.update { it.copy(repeatMode = next) }
        controller?.repeatMode = when (next) {
            TrackRepeatMode.OFF -> Player.REPEAT_MODE_OFF
            TrackRepeatMode.ONE -> Player.REPEAT_MODE_ONE
            TrackRepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    private fun loadTheme() { /* handled in init critical-path block */
    }

    fun toggleTheme() {
        val themes = DreaminTheme.entries
        val next = themes[(themes.indexOf(_uiState.value.selectedTheme) + 1) % themes.size]
        _uiState.update { it.copy(selectedTheme = next) }
        viewModelScope.launch { userPrefs.saveTheme(next) }
    }

    fun extractColorsFromArtwork(artworkUrl: String) {
        if (artworkUrl.isBlank()) return
        val appContext = getApplication<Application>()
        viewModelScope.launch {
            try {
                val request = coil.request.ImageRequest.Builder(appContext)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()
                val result = coil.Coil.imageLoader(appContext).execute(request)
                val bitmap = (result as? coil.request.SuccessResult)
                    ?.drawable
                    ?.let { (it as? android.graphics.drawable.BitmapDrawable)?.bitmap }
                bitmap?.let { bmp ->
                    val palette = androidx.palette.graphics.Palette.from(bmp).generate()
                    updateDominantColor(palette.getDominantColor(0xFF1A1A2E.toInt()))
                } ?: updateDominantColor(0xFF1A1A2E.toInt())
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Color extraction failed: ${e.message}")
                updateDominantColor(0xFF1A1A2E.toInt())
            }
        }
    }

    override fun onCleared() {
        val song = _uiState.value.currentSong
        val pos = controller?.currentPosition?.coerceAtLeast(0L) ?: 0L
        if (song != null) {
            viewModelScope.launch { userPrefs.saveLastSession(song, pos) }
        }
        controller?.removeListener(playerListener)
        isListenerAttached = false
        sleepTimerJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        super.onCleared()
    }
}
