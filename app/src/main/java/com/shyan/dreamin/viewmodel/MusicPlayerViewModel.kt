package com.shyan.dreamin.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.shyan.dreamin.data.local.AppDatabase
import com.shyan.dreamin.data.local.DownloadRepository
import com.shyan.dreamin.data.local.FavoritesRepository
import com.shyan.dreamin.data.local.PlayHistoryRepository
import com.shyan.dreamin.data.local.StatsRepository
import com.shyan.dreamin.data.model.*
import com.shyan.dreamin.data.network.LyricsService
import com.shyan.dreamin.data.network.NetworkService
import com.shyan.dreamin.service.MusicService
import androidx.media3.common.C
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import com.shyan.dreamin.data.recommendation.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

@OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
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
    val downloadRepo = DownloadRepository(getApplication(), db.downloadDao())
    private val importMatchDao = db.importMatchDao()
    private val analyticsTracker = com.shyan.dreamin.data.local.PlaybackAnalyticsTracker(historyRepo, statsRepo)

    private var isListenerAttached = false
    private var searchJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var openPlaylistJob: Job? = null
    private var colorExtractJob: Job? = null
    private var prefetchJob: Job? = null
    private var lyricsJob: Job? = null
    private var artistJob: Job? = null
    @Volatile
    private var isScreenInteractive: Boolean = true

    private val mediaActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                MusicService.ACTION_PLAY_NEXT -> playNext()
                MusicService.ACTION_PLAY_PREVIOUS -> playPrevious()
                MusicService.ACTION_TOGGLE_FAVORITE -> _uiState.value.currentSong?.let { toggleFavoriteFor(it) }
                MusicService.ACTION_TOGGLE_SHUFFLE -> toggleShuffle()
                android.content.Intent.ACTION_SCREEN_OFF -> isScreenInteractive = false
                android.content.Intent.ACTION_SCREEN_ON -> isScreenInteractive = true
            }
        }
    }

    init {
        clearAllCaches()
        // Fire-and-forget warmup so the server is ready before onboarding completes
        viewModelScope.launch(Dispatchers.IO) { runCatching { api.health() } }

        // Critical path: userName + theme must resolve before the app shows content.
        // Read both with first() on IO so the gate clears in <100ms, then keep collecting.
        viewModelScope.launch(Dispatchers.IO) {
            val name = userPrefs.userName.first()
            val session = userPrefs.lastSession.first()
            val searches = userPrefs.recentSearches.first()
            _uiState.update {
                it.copy(
                    userName = name,
                    lastSession = session,
                    recentSearches = searches
                )
            }
            // Continue collecting for changes
            launch { userPrefs.userName.distinctUntilChanged().collect { n -> _uiState.update { it.copy(userName = n) } } }
            launch { userPrefs.lastSession.distinctUntilChanged().collect { s -> if (_uiState.value.currentSong == null) _uiState.update { it.copy(lastSession = s) } } }
            launch { userPrefs.recentSearches.distinctUntilChanged().collect { r -> _uiState.update { it.copy(recentSearches = r) } } }
        }

        connectToService()
        loadChart()

        // Priority 2: Run all database warmups in parallel child coroutines on IO
        viewModelScope.launch(Dispatchers.IO) {
            delay(120) // Brief delay to yield main thread for 120 FPS first frame render
            launch { loadRecentlyPlayed() }
            launch { loadTopSongs() }
            launch { loadFavorites() }
            launch { loadStats() }
            launch { loadPlaylists() }
            launch { loadDownloads() }
        }

        val context = getApplication<Application>()
        val filter = android.content.IntentFilter().apply {
            addAction(MusicService.ACTION_PLAY_NEXT)
            addAction(MusicService.ACTION_PLAY_PREVIOUS)
            addAction(MusicService.ACTION_TOGGLE_FAVORITE)
            addAction(MusicService.ACTION_TOGGLE_SHUFFLE)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_SCREEN_ON)
        }
        androidx.core.content.ContextCompat.registerReceiver(
            context,
            mediaActionReceiver,
            filter,
            androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    private fun loadUserName() {
        // No-op: handled in init's critical-path block above
    }

    fun saveUserName(name: String) {
        val trimmed = name.trim()
        viewModelScope.launch {
            userPrefs.saveUserName(trimmed)
            runCatching {
                val deviceId = android.provider.Settings.Secure.getString(
                    getApplication<android.app.Application>().contentResolver,
                    android.provider.Settings.Secure.ANDROID_ID
                ) ?: ""
                api.registerUser(com.shyan.dreamin.data.model.RegisterRequest(name = trimmed, device_id = deviceId))
            }
        }
    }

    private fun loadRecentlyPlayed() {
        viewModelScope.launch {
            historyRepo.getRecentlyPlayed().distinctUntilChanged().collect { songs ->
                _uiState.update { it.copy(recentlyPlayed = songs) }
                songs.forEach { song ->
                    launch(Dispatchers.IO) {
                        try {
                            val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                            if (!poster.isNullOrBlank()) {
                                updateSongArtworkAcrossApp(song.id, poster)
                                historyRepo.updateArtwork(song.id, poster)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    private fun loadTopSongs() {
        viewModelScope.launch {
            historyRepo.getTopSongs().distinctUntilChanged().collect { songs ->
                _uiState.update { it.copy(topSongs = songs) }
                songs.forEach { song ->
                    launch(Dispatchers.IO) {
                        try {
                            val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                            if (!poster.isNullOrBlank()) {
                                updateSongArtworkAcrossApp(song.id, poster)
                                historyRepo.updateArtwork(song.id, poster)
                            }
                        } catch (_: Exception) {}
                    }
                }
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
                com.shyan.dreamin.data.service.OfficialArtworkService.prefetchSongListArtworks(songs, viewModelScope) { id, poster ->
                    updateSongArtworkAcrossApp(id, poster)
                }
            }
        }
    }

    fun toggleFavorite() {
        val song = _uiState.value.currentSong ?: return
        toggleFavoriteFor(song)
    }

    fun toggleFavoriteFor(song: Song) {
        val isFav = _uiState.value.favorites.any { it.id == song.id }
        viewModelScope.launch {
            if (isFav) favoritesRepo.removeFavorite(song.id)
            else {
                favoritesRepo.addFavorite(song)
                FeedbackEngine.recordTrackEvent(song, 0L, 0L, isFavorite = true)
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
                checkSpotifyPlaylistsForUpdates(lists)
            }
        }
    }

    private suspend fun saveImageToInternalStorage(uri: android.net.Uri): String = withContext(Dispatchers.IO) {
        try {
            val app = getApplication<Application>()
            val coversDir = java.io.File(app.filesDir, "playlist_covers")
            if (!coversDir.exists()) coversDir.mkdirs()
            val file = java.io.File(coversDir, "cover_${System.currentTimeMillis()}_${java.util.UUID.randomUUID().toString().take(6)}.jpg")
            app.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            file.absolutePath
        } catch (e: Exception) {
            android.util.Log.e("MusicVM", "Failed to save cover image: ${e.message}")
            uri.toString()
        }
    }

    fun createPlaylist(name: String, coverUri: android.net.Uri? = null) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val savedCover = if (coverUri != null) saveImageToInternalStorage(coverUri) else null
            playlistRepo.createPlaylist(name.trim(), coverUrl = savedCover)
        }
    }

    fun updatePlaylistCover(playlistId: Long, coverUri: android.net.Uri?) {
        viewModelScope.launch {
            val savedCover = if (coverUri != null) saveImageToInternalStorage(coverUri) else null
            playlistRepo.updatePlaylistCover(playlistId, savedCover)
        }
    }

    fun saveQueueAsPlaylist(name: String) {
        val songs = _uiState.value.queue
        if (songs.isEmpty()) return
        viewModelScope.launch {
            val playlistId = playlistRepo.createPlaylist(name.trim())
            playlistRepo.addSongs(playlistId, songs)
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
        openPlaylistJob?.cancel()
        openPlaylistJob = viewModelScope.launch {
            playlistRepo.observeSongs(playlistId).collect { songs ->
                _uiState.update { it.copy(openPlaylistSongs = songs) }
                com.shyan.dreamin.data.service.OfficialArtworkService.prefetchSongListArtworks(songs, viewModelScope) { id, poster ->
                    updateSongArtworkAcrossApp(id, poster)
                }
            }
        }
    }

    fun closePlaylist() {
        _uiState.update { it.copy(openPlaylistId = null, openPlaylistSongs = emptyList()) }
    }

    fun resetSpotifyImportState() {
        _uiState.update { it.copy(spotifyImportState = SpotifyImportState.Idle) }
    }

    fun checkClipboardForSpotifyLink(context: Context) {
        try {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager ?: return
            val clip = clipboard.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0)?.text?.toString()?.trim()
                if (!text.isNullOrBlank() && (text.contains("open.spotify.com/playlist/") || text.contains("spotify.link/") || text.contains("spoti.fi/"))) {
                    if (text != _uiState.value.detectedSpotifyClipboardUrl) {
                        _uiState.update { it.copy(detectedSpotifyClipboardUrl = text) }
                    }
                }
            }
        } catch (_: Exception) {}
    }

    fun dismissDetectedSpotifyLink() {
        _uiState.update { it.copy(detectedSpotifyClipboardUrl = null) }
    }

    fun importSpotifyPlaylist(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(spotifyImportState = SpotifyImportState.FetchingMetadata(url), detectedSpotifyClipboardUrl = null) }

            val playlistId = com.shyan.dreamin.data.service.SpotifyImportService.resolvePlaylistId(url)
            if (playlistId == null) {
                _uiState.update {
                    it.copy(spotifyImportState = SpotifyImportState.Error("Invalid Spotify playlist URL. Please check the link."))
                }
                return@launch
            }

            val details = com.shyan.dreamin.data.service.SpotifyImportService.fetchPlaylistDetails(playlistId, getApplication())
            if (details == null || details.tracks.isEmpty()) {
                _uiState.update {
                    it.copy(spotifyImportState = SpotifyImportState.Error("Could not fetch playlist tracks. Make sure the Spotify playlist is public."))
                }
                return@launch
            }

            processTrackMatching(details.title, details.coverUrl, details.tracks, spotifyPlaylistId = playlistId)
        }
    }

    private suspend fun processTrackMatching(
        title: String,
        coverUrl: String,
        tracks: List<com.shyan.dreamin.data.service.SpotifyImportedTrack>,
        spotifyPlaylistId: String? = null
    ) {
        val playlistLang = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.detectDominantPlaylistLanguage(tracks, title)
        android.util.Log.d("MusicVM", "Inferred dominant playlist language for '$title': $playlistLang")

        val total = tracks.size
        val matchedArray = arrayOfNulls<Song>(total)
        val suggestedArray = arrayOfNulls<Song>(total)
        val progressCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val matchedCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val semaphore = Semaphore(5)
        val queryCache = java.util.concurrent.ConcurrentHashMap<String, List<Song>>()

        kotlinx.coroutines.coroutineScope {
            tracks.forEachIndexed { index, track ->
                launch(Dispatchers.IO) {
                    try {
                        semaphore.withPermit {
                            val (matched, suggestion) = matchSpotifyTrack(track, playlistLang, queryCache)
                            if (matched != null) {
                                val finalArtwork = when {
                                    track.artworkUrl.isNotBlank() -> track.artworkUrl
                                    matched.artworkUrl.isNotBlank() -> matched.artworkUrl
                                    else -> ""
                                }
                                val songWithArt = matched.copy(artworkUrl = finalArtwork)
                                matchedArray[index] = songWithArt
                                matchedCounter.incrementAndGet()
                            } else if (suggestion != null) {
                                suggestedArray[index] = suggestion
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MusicVM", "Failed to match track: ${track.title} - ${e.message}")
                    } finally {
                        val currDone = progressCounter.incrementAndGet()
                        val currMatched = matchedCounter.get()
                        if (currDone % 3 == 0 || currDone == total) {
                            _uiState.update {
                                it.copy(
                                    spotifyImportState = SpotifyImportState.MatchingTracks(
                                        playlistTitle = title,
                                        coverUrl = coverUrl,
                                        currentTrackIndex = currDone,
                                        totalTracks = total,
                                        matchedCount = currMatched,
                                        currentTrackName = track.title,
                                        currentTrackArtist = track.artist,
                                        currentArtworkUrl = track.artworkUrl
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        val matchedSongs = matchedArray.filterNotNull()
        val unmatchedTracks = tracks.mapIndexedNotNull { index, originalTrack ->
            if (matchedArray[index] == null) {
                originalTrack.copy(suggestedCandidate = suggestedArray[index])
            } else null
        }

        if (matchedSongs.isEmpty()) {
            _uiState.update {
                it.copy(spotifyImportState = SpotifyImportState.Error("No matching tracks could be found in the catalog."))
            }
            return
        }

        val newPlaylistId = playlistRepo.createPlaylist(
            name = title,
            coverUrl = coverUrl.takeIf { it.isNotBlank() },
            spotifyPlaylistId = spotifyPlaylistId
        )
        playlistRepo.addSongs(newPlaylistId, matchedSongs)

        // Preload first batch of high-resolution cinematic covers in background
        val appCtx = getApplication<Application>()
        viewModelScope.launch(Dispatchers.IO) {
            matchedSongs.take(30).forEach { s ->
                if (s.displayArtworkUrl.isNotBlank()) {
                    try {
                        val req = coil.request.ImageRequest.Builder(appCtx)
                            .data(s.displayArtworkUrl)
                            .build()
                        coil.Coil.imageLoader(appCtx).enqueue(req)
                    } catch (_: Exception) {}
                }
            }
        }

        _uiState.update {
            it.copy(
                spotifyImportState = SpotifyImportState.Success(
                    playlistId = newPlaylistId,
                    playlistTitle = title,
                    matchedCount = matchedSongs.size,
                    totalTracks = total,
                    coverUrl = coverUrl,
                    matchedSongs = matchedSongs,
                    unmatchedTracks = unmatchedTracks,
                    spotifyPlaylistId = spotifyPlaylistId
                )
            )
        }
    }

    fun downloadPlaylist(songs: List<Song>) {
        if (songs.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) {
            for (song in songs) {
                downloadSong(song)
            }
        }
    }

    fun resolveAndAddUnmatchedTrack(
        playlistId: Long,
        unmatchedTrack: com.shyan.dreamin.data.service.SpotifyImportedTrack,
        replacementSong: Song
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val existing = playlistRepo.getSongs(playlistId)
            val nextPosition = existing.size
            playlistRepo.addSong(playlistId, replacementSong, nextPosition)

            _uiState.update { state ->
                val success = state.spotifyImportState as? SpotifyImportState.Success ?: return@update state
                val updatedUnmatched = success.unmatchedTracks.filterNot { it.title == unmatchedTrack.title && it.artist == unmatchedTrack.artist }
                val updatedMatched = success.matchedSongs + replacementSong
                state.copy(
                    spotifyImportState = success.copy(
                        matchedCount = success.matchedCount + 1,
                        matchedSongs = updatedMatched,
                        unmatchedTracks = updatedUnmatched
                    ),
                    openPlaylistSongs = if (state.openPlaylistId == playlistId) updatedMatched else state.openPlaylistSongs
                )
            }
        }
    }

    fun dismissSpotifySyncAlert(playlistId: Long) {
        _uiState.update { state ->
            state.copy(spotifySyncAlerts = state.spotifySyncAlerts.filterNot { it.playlistId == playlistId })
        }
    }

    fun unlinkSpotifyPlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepo.updateSpotifyPlaylistId(playlistId, null)
            dismissSpotifySyncAlert(playlistId)
        }
    }

    fun checkSpotifyPlaylistsForUpdates(playlists: List<com.shyan.dreamin.data.local.Playlist>? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val targetPlaylists = playlists ?: _uiState.value.playlists
                val spotifyPlaylists = targetPlaylists.filter { !it.spotifyPlaylistId.isNullOrBlank() }
                if (spotifyPlaylists.isEmpty()) return@launch

                val alerts = mutableListOf<SpotifySyncAlert>()
                for (pl in spotifyPlaylists) {
                    val spotifyId = pl.spotifyPlaylistId ?: continue
                    try {
                        val details = com.shyan.dreamin.data.service.SpotifyImportService.fetchPlaylistDetails(spotifyId, getApplication())
                        if (details == null) {
                            alerts.add(SpotifySyncAlert(playlistId = pl.id, playlistName = pl.name, newTrackCount = 0, isUnavailable = true))
                        } else {
                            val existingSongs = playlistRepo.getSongs(pl.id)
                            val existingKeys = existingSongs.map { com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(it.displayTitle) }.toSet()
                            val newTracks = details.tracks.filterNot { t ->
                                val key = com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(t.title)
                                existingKeys.contains(key)
                            }
                            if (newTracks.isNotEmpty()) {
                                alerts.add(SpotifySyncAlert(playlistId = pl.id, playlistName = pl.name, newTrackCount = newTracks.size, isUnavailable = false))
                            }
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MusicVM", "checkSpotifyPlaylistsForUpdates error on ${pl.name}: ${e.message}")
                    }
                }
                _uiState.update { state ->
                    state.copy(spotifySyncAlerts = alerts)
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "checkSpotifyPlaylistsForUpdates failed: ${e.message}")
            }
        }
    }

    fun syncSpotifyPlaylist(playlistId: Long) {
        if (_uiState.value.isSyncingSpotifyPlaylist) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isSyncingSpotifyPlaylist = true) }
            try {
                val spotifyId = playlistRepo.getSpotifyPlaylistId(playlistId)
                if (spotifyId.isNullOrBlank()) {
                    _uiState.update { it.copy(isSyncingSpotifyPlaylist = false) }
                    return@launch
                }
                val details = com.shyan.dreamin.data.service.SpotifyImportService.fetchPlaylistDetails(spotifyId, getApplication())
                if (details == null || details.tracks.isEmpty()) {
                    _uiState.update { it.copy(isSyncingSpotifyPlaylist = false) }
                    return@launch
                }
                val existingSongs = playlistRepo.getSongs(playlistId)
                val existingKeys = existingSongs.map { com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(it.displayTitle) }.toSet()

                val newTracks = details.tracks.filterNot { t ->
                    val key = com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(t.title)
                    existingKeys.contains(key)
                }

                if (newTracks.isNotEmpty()) {
                    val playlistLang = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.detectDominantPlaylistLanguage(details.tracks, details.title)
                    val newlyMatched = mutableListOf<Song>()
                    for (track in newTracks) {
                        val (matched, _) = matchSpotifyTrack(track, playlistLang)
                        if (matched != null) {
                            val art = if (track.artworkUrl.isNotBlank()) track.artworkUrl else matched.artworkUrl
                            newlyMatched.add(matched.copy(artworkUrl = art))
                        }
                    }
                    if (newlyMatched.isNotEmpty()) {
                        playlistRepo.addSongs(playlistId, newlyMatched, startPosition = existingSongs.size)
                        val updatedAll = existingSongs + newlyMatched
                        if (_uiState.value.openPlaylistId == playlistId) {
                            _uiState.update { it.copy(openPlaylistSongs = updatedAll) }
                        }
                    }
                }
                _uiState.update { state ->
                    state.copy(spotifySyncAlerts = state.spotifySyncAlerts.filterNot { it.playlistId == playlistId })
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "syncSpotifyPlaylist failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(isSyncingSpotifyPlaylist = false) }
            }
        }
    }

    private suspend fun matchSpotifyTrack(
        track: com.shyan.dreamin.data.service.SpotifyImportedTrack,
        playlistLanguage: String,
        queryCache: java.util.concurrent.ConcurrentHashMap<String, List<Song>>? = null
    ): Pair<Song?, Song?> = withContext(Dispatchers.IO) {
        val rawTitle = track.title
        val rawArtist = track.artist

        // 1. Language Detection from track title
        val langRegex = Regex("(?i)\\b(tamil|telugu|hindi|malayalam|kannada|punjabi|english)\\b")
        val trackLangMatch = langRegex.find(rawTitle)
        val targetLang = trackLangMatch?.value?.lowercase() ?: playlistLanguage.ifBlank { "tamil" }

        val (cleanBaseTitle, fullClean) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(rawTitle)
        val primaryArtist = rawArtist.split(",", "&", "feat.", "ft.", "/", ";").firstOrNull()?.trim() ?: ""
        val signature = "${cleanBaseTitle.lowercase().trim()}|${primaryArtist.lowercase().trim()}"

        // Tier 0: Learning Match Memory (Room DB Cache hit in 0ms)
        try {
            val cached = importMatchDao.getMatch(signature)
            if (cached != null) {
                val cachedSong = Song(
                    id = cached.songId,
                    title = cached.songTitle,
                    artist = cached.songArtist,
                    artworkUrl = cached.artworkUrl,
                    duration = cached.duration
                )
                return@withContext Pair(cachedSong, null)
            }
        } catch (_: Exception) {}

        // Tier 1 & 2: Multi-Pass Search with Session Query Cache
        val allCandidates = mutableListOf<Song>()

        // Pass 1: Direct Clean Base Title + Primary Artist (Most accurate for Spotify tracks)
        val q1 = if (primaryArtist.isNotBlank()) "$cleanBaseTitle $primaryArtist" else cleanBaseTitle
        val p1 = if (queryCache != null) {
            queryCache.getOrPut(q1) { searchOnDevice(q1, limit = 8, targetLanguage = targetLang, rejectHindi = false) }
        } else {
            searchOnDevice(q1, limit = 8, targetLanguage = targetLang, rejectHindi = false)
        }
        allCandidates.addAll(p1)

        // Pass 2: Base Title alone (if combined query had zero results)
        if (allCandidates.isEmpty() && primaryArtist.isNotBlank()) {
            val p2 = if (queryCache != null) {
                queryCache.getOrPut(cleanBaseTitle) { searchOnDevice(cleanBaseTitle, limit = 8, targetLanguage = targetLang, rejectHindi = false) }
            } else {
                searchOnDevice(cleanBaseTitle, limit = 8, targetLanguage = targetLang, rejectHindi = false)
            }
            allCandidates.addAll(p2)
        }

        // Pass 3: Full Clean Title
        if (allCandidates.isEmpty() && fullClean.length > cleanBaseTitle.length) {
            val p3 = if (queryCache != null) {
                queryCache.getOrPut(fullClean) { searchOnDevice(fullClean, limit = 8, targetLanguage = targetLang, rejectHindi = false) }
            } else {
                searchOnDevice(fullClean, limit = 8, targetLanguage = targetLang, rejectHindi = false)
            }
            allCandidates.addAll(p3)
        }

        if (allCandidates.isEmpty()) return@withContext Pair(null, null)

        // Tier 3: IntelliMatch Fuzzy Scoring & Confidence Classifier
        val matchResult = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.findBestMatch(
            targetTitle = rawTitle,
            targetArtist = rawArtist,
            targetDurationMs = track.durationMs,
            candidates = allCandidates.distinctBy { it.id },
            targetLanguage = targetLang
        )

        if (matchResult != null) {
            val matchedSong = if (matchResult.song.artworkUrl.isNotBlank()) matchResult.song
                              else matchResult.song.copy(artworkUrl = track.artworkUrl)
            if (matchResult.confidence == com.shyan.dreamin.data.recommendation.IntelliMatchEngine.MatchConfidence.HIGH ||
                matchResult.confidence == com.shyan.dreamin.data.recommendation.IntelliMatchEngine.MatchConfidence.MEDIUM
            ) {
                val finalMatchedSong = if (track.artworkUrl.isNotBlank()) {
                    matchedSong.copy(artworkUrl = track.artworkUrl)
                } else {
                    matchedSong
                }
                // Cache confirmed match to Room DB learning memory
                try {
                    importMatchDao.insertMatch(
                        com.shyan.dreamin.data.local.entity.ImportMatchEntity(
                            spotifySignature = signature,
                            songId = finalMatchedSong.id,
                            songTitle = finalMatchedSong.title,
                            songArtist = finalMatchedSong.artist,
                            artworkUrl = finalMatchedSong.artworkUrl,
                            duration = finalMatchedSong.duration,
                            confidenceScore = matchResult.score
                        )
                    )
                } catch (_: Exception) {}
                return@withContext Pair(finalMatchedSong, null)
            } else {
                // Low confidence: offer as intelligent suggestion
                val suggestedSong = if (track.artworkUrl.isNotBlank()) {
                    matchedSong.copy(artworkUrl = track.artworkUrl)
                } else {
                    matchedSong
                }
                return@withContext Pair(null, suggestedSong)
            }
        }

        return@withContext Pair(null, null)
    }

    fun addSuggestedTrackToPlaylist(playlistId: Long, song: Song, originalTrackTitle: String) {
        viewModelScope.launch(Dispatchers.IO) {
            addSongToPlaylist(playlistId, song)
            val (cleanBase, _) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(originalTrackTitle)
            val signature = "${cleanBase.lowercase().trim()}|${song.artist.lowercase().trim()}"
            try {
                importMatchDao.insertMatch(
                    com.shyan.dreamin.data.local.entity.ImportMatchEntity(
                        spotifySignature = signature,
                        songId = song.id,
                        songTitle = song.title,
                        songArtist = song.artist,
                        artworkUrl = song.artworkUrl,
                        duration = song.duration,
                        confidenceScore = 2000
                    )
                )
            } catch (_: Exception) {}
        }
    }


    fun downloadAllSongsInPlaylist(songs: List<Song>) {
        songs.forEach { song ->
            downloadSong(song)
        }
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
            playSong(songs.first(), fromPlaylist = true, preserveQueue = true)
        }
    }

    fun playSongFromPlaylist(song: Song, playlistSongs: List<Song>) {
        _uiState.update { it.copy(queue = playlistSongs, playlistQueueActive = true) }
        playSong(song, fromPlaylist = true, preserveQueue = true)
    }

    fun shuffleAndPlayPlaylist(playlistSongs: List<Song>) {
        if (playlistSongs.isEmpty()) return
        val shuffled = playlistSongs.shuffled()
        _uiState.update { it.copy(queue = shuffled, playlistQueueActive = true) }
        playSong(shuffled.first(), fromPlaylist = true, preserveQueue = true)
    }

    fun playSongFromList(song: Song, songs: List<Song>) {
        if (songs.isNotEmpty()) {
            _uiState.update { it.copy(queue = songs, playlistQueueActive = false) }
            playSong(song, fromPlaylist = false, preserveQueue = true)
        } else {
            playSong(song)
        }
    }

    fun shuffleAndPlayList(songs: List<Song>) {
        if (songs.isEmpty()) return
        val shuffled = songs.shuffled()
        _uiState.update { it.copy(queue = shuffled, playlistQueueActive = false) }
        playSong(shuffled.first(), fromPlaylist = false, preserveQueue = true)
    }

    fun setSleepTimer(minutes: Int) {
        sleepTimerJob?.cancel()
        val totalMs = minutes * 60_000L
        val endMs = System.currentTimeMillis() + totalMs
        _uiState.update { it.copy(sleepTimerEndMs = endMs) }
        sleepTimerJob = viewModelScope.launch {
            if (totalMs > 10_000L) {
                delay(totalMs - 10_000L)
                // Gentle audio fade out over 10 seconds
                for (i in 10 downTo 1) {
                    controller?.volume = (i / 10f).coerceIn(0f, 1f)
                    delay(1000)
                }
            } else {
                delay(totalMs)
            }
            controller?.pause()
            controller?.volume = 1f // Reset volume back to full for next session
            _uiState.update { it.copy(sleepTimerEndMs = null) }
        }
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        controller?.volume = 1f
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
        }, ContextCompat.getMainExecutor(context))
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
            if (state == Player.STATE_READY && pendingResumePositionMs > 0L) {
                val pos = pendingResumePositionMs
                pendingResumePositionMs = 0L
                controller?.seekTo(pos)
            }
            val c = controller
            val isPlaying = c?.isPlaying == true
            val playWhenReady = c?.playWhenReady == true
            val playbackState = when {
                state == Player.STATE_BUFFERING -> PlaybackState.Loading
                state == Player.STATE_READY -> if (playWhenReady || isPlaying) PlaybackState.Playing else PlaybackState.Paused
                state == Player.STATE_ENDED -> {
                    viewModelScope.launch {
                        delay(1500L) // 1.5s natural breathing gap between songs
                        playNext(isAutoEnd = true)
                    }
                    PlaybackState.Idle
                }
                else -> PlaybackState.Idle
            }
            _uiState.update { it.copy(playbackState = playbackState) }
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            val c = controller
            val playWhenReady = c?.playWhenReady == true
            val pState = c?.playbackState
            _uiState.update {
                it.copy(
                    playbackState = when {
                        isPlaying -> PlaybackState.Playing
                        pState == Player.STATE_BUFFERING -> PlaybackState.Loading
                        playWhenReady -> PlaybackState.Playing
                        else -> PlaybackState.Paused
                    }
                )
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val meta = mediaItem?.mediaMetadata
            val id = mediaItem?.mediaId?.takeIf { it.isNotBlank() }
            val duration = controller?.duration?.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
            if (id != null) {
                _uiState.update { state ->
                    val matching = state.queue.find { it.id == id }
                    if (matching != null) {
                        state.copy(currentSong = matching)
                    } else {
                        val title = meta?.title?.toString()?.takeIf { it.isNotBlank() }
                        if (title != null) {
                            state.copy(
                                currentSong = Song(
                                    id = id,
                                    title = title,
                                    artist = meta.artist?.toString() ?: "",
                                    artworkUrl = meta.artworkUri?.toString() ?: ""
                                )
                            )
                        } else state
                    }
                }
            }
            _progress.value = PlaybackProgress(0L, duration)
            _uiState.value.currentSong?.let { activeSong ->
                if (!activeSong.isSpotifyArtwork) {
                    prefetchQueueArtworks(_uiState.value.queue, activeSong.id)
                    viewModelScope.launch(Dispatchers.IO) {
                        try {
                            val official = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(activeSong)
                            if (!official.isNullOrBlank() && official != activeSong.artworkUrl) {
                                updateSongArtworkAcrossApp(activeSong.id, official)
                            }
                        } catch (_: Exception) {}
                    }
                }
            }
        }

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("MusicVM", "ExoPlayer error: ${error.message}", error)
            val current = _uiState.value.currentSong ?: return
            com.shyan.dreamin.data.service.AudioStreamResolver.removeCachedStreamUrl(current.id)
            viewModelScope.launch {
                downloadRepo.deletePrefetch(current.id)
                try {
                    val fallbackUrl = resolveStreamUrl(current)
                    val uri = parseAudioUri(fallbackUrl)
                    val mediaItem = MediaItem.Builder()
                        .setMediaId(current.id)
                        .setUri(uri)
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(current.title)
                                .setArtist(current.artist)
                                .setArtworkUri(
                                    current.displayArtworkUrl.takeIf { it.isNotBlank() }
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
                } catch (e: Exception) {
                    _uiState.update { it.copy(playbackState = PlaybackState.Error(e.message ?: "Playback failed")) }
                }
            }
        }
    }

    private val backgroundSyncDispatcher = Dispatchers.IO.limitedParallelism(2)
    private var lastPrefetchedTrackId: String? = null

    private fun startPositionPoller() {
        viewModelScope.launch {
            var saveCounter = 0
            while (isActive) {
                val pollDelay = if (isScreenInteractive) 100L else 2000L
                delay(pollDelay)
                val c = controller ?: continue
                if (!c.isPlaying) continue
                val pos = c.currentPosition.coerceAtLeast(0L)
                val dur = c.duration.takeIf { it > 0 && it != C.TIME_UNSET } ?: 0L
                val current = _progress.value
                if (pos != current.currentPositionMs || dur != current.durationMs) {
                    _progress.value = PlaybackProgress(pos, dur)
                }

                // Predictive stream resolution for the upcoming track at 75% progress or 25s remaining
                if (dur > 0 && (pos.toFloat() / dur.toFloat() > 0.75f || dur - pos < 25_000L)) {
                    val currentSongId = _uiState.value.currentSong?.id
                    if (currentSongId != null && lastPrefetchedTrackId != currentSongId) {
                        lastPrefetchedTrackId = currentSongId
                        prefetchQueueArtworks(_uiState.value.queue, currentSongId)
                    }
                }

                saveCounter += if (isScreenInteractive) 1 else 20
                if (saveCounter >= 100) {
                    saveCounter = 0
                    _uiState.value.currentSong?.let { song ->
                        userPrefs.saveLastSession(song, pos)
                    }
                }
            }
        }
    }

    fun prefetchQueueArtworks(queue: List<Song>, currentSongId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val currentIndex = queue.indexOfFirst { it.id == currentSongId }
            val nextSongs = if (currentIndex >= 0) {
                queue.drop(currentIndex + 1).take(5)
            } else {
                queue.take(5)
            }
            nextSongs.forEach { song ->
                launch(Dispatchers.IO) {
                    try {
                        val finalUrl = if (song.isSpotifyArtwork) {
                            song.artworkUrl
                        } else {
                            val official = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                            if (!official.isNullOrBlank() && official != song.artworkUrl) {
                                updateSongArtworkAcrossApp(song.id, official)
                            }
                            official ?: song.displayArtworkUrl
                        }
                        if (finalUrl.isNotBlank()) {
                            val req = coil.request.ImageRequest.Builder(context)
                                .data(finalUrl)
                                .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                                .build()
                            coil.Coil.imageLoader(context).enqueue(req)
                        }
                    } catch (_: Exception) {}
                }
            }

            // Also pre-resolve and pre-buffer stream URL for the upcoming song in parallel
            nextSongs.firstOrNull()?.let { upcomingSong ->
                launch(Dispatchers.IO) {
                    try {
                        val stream = resolveStreamUrl(upcomingSong)
                        if (stream.isNotBlank()) {
                            com.shyan.dreamin.service.PreBufferManager.preBufferUpcomingStream(context, stream, viewModelScope)
                        }
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun prefetchTopTrendingArtworks(songs: List<Song>) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            songs.take(5).forEach { song ->
                val url = song.displayArtworkUrl
                if (url.isNotBlank()) {
                    val req = coil.request.ImageRequest.Builder(context)
                        .data(url)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                    coil.Coil.imageLoader(context).enqueue(req)
                }
            }
        }
    }

    private val noiseRegex = Regex("(?i)\\b(remix|8d|8d audio|bass boosted|slowed|reverb|cover|lofi|status|dialogue|teaser|trailer|jukebox|mashup|bgm|ringtone|sped up)\\b")
    private val compilationRegex = com.shyan.dreamin.data.service.OfficialArtworkService.COMPILATION_REGEX

    private fun cleanArtworkUrl(url: String): String {
        if (url.isBlank()) return ""
        val highRes = url.replace(Regex("_\\d+x\\d+\\."), "_500x500.")
            .replace("50x50", "500x500")
            .replace("150x150", "500x500")
        return if (highRes.startsWith("http://")) "https://" + highRes.substring(7) else highRes
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
    }

    suspend fun searchSongsDirect(query: String): List<Song> = withContext(Dispatchers.IO) {
        if (query.isBlank()) return@withContext emptyList()
        try {
            searchOnDevice(query.trim(), limit = 20)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private suspend fun searchOnDevice(
        query: String, 
        limit: Int = 15, 
        page: Int = 1,
        targetLanguage: String = "",
        rejectHindi: Boolean = false
    ): List<Song> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=$limit&p=$page"
        
        for (attempt in 0..1) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Origin", "https://www.jiosaavn.com")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()
                val resp = NetworkService.httpClient.newCall(req).execute()
                val text = resp.body?.string().orEmpty().trim()
                if (text.startsWith("<") || !text.startsWith("{")) {
                    if (attempt == 0) kotlinx.coroutines.delay(200L)
                    continue
                }
                val root = org.json.JSONObject(text)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val allItems = mutableListOf<org.json.JSONObject>()
                    val langItems = mutableListOf<org.json.JSONObject>()
                    for (i in 0 until results.length()) {
                        val it = results.getJSONObject(i)
                        allItems.add(it)
                        val itemLang = it.optString("language").ifBlank { it.optJSONObject("more_info")?.optString("language") ?: "" }.lowercase().trim()
                        if (targetLanguage.isBlank() || itemLang.isBlank() || itemLang.equals(targetLanguage, ignoreCase = true)) {
                            langItems.add(it)
                        }
                    }
                    val rawItems = if (langItems.isNotEmpty()) langItems else allItems

                    val grouped = rawItems.groupBy {
                        it.optString("title").replace(Regex("(?i)\\s*\\(?\\s*from\\s+[\"\'\u201c\u2018].*?[\"\'\u201d\u2019]?\\s*\\)?$"), "")
                            .replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                    }
                    val songs = mutableListOf<Song>()
                    for ((_, items) in grouped) {
                        // 1. Detect movie from title tags
                        var movieDetected = ""
                        for (it in items) {
                            val m = Regex("(?i)\\(?\\s*(?:from|movie)\\s+[\"\'\u201c\u2018]?(.*?)[\"\'\u201d\u2019]?\\s*\\)?").find(it.optString("title"))
                            if (m != null) {
                                val cand = m.groupValues[1].replace(Regex("[\"\'\u201c\u2018\u201d\u2019]"), "").trim().lowercase()
                                if (cand.isNotBlank() && cand.length >= 2) {
                                    movieDetected = cand
                                    break
                                }
                            }
                        }

                        // 2. Select best candidate release from the provider
                        val bestItem = items.minByOrNull { item ->
                            val alb = unescapeHtml(item.optJSONObject("more_info")?.optString("album", "")?.trim() ?: "").lowercase()
                            val itemImage = item.optString("image", "").lowercase()
                            val isEditorialOrPlaylist = itemImage.contains("/editorial/") || itemImage.contains("/playlist/") || itemImage.contains("/818/") || itemImage.contains("/888/") || itemImage.contains("compilation")

                            var score = 1000
                            val isComp = compilationRegex.containsMatchIn(alb)
                            if (isComp) {
                                score += 8000
                            } else {
                                score -= 400
                                if (alb.contains("soundtrack") || alb.contains("original motion picture")) {
                                    score -= 600
                                }
                            }
                            if (movieDetected.isNotBlank() && (alb == movieDetected || alb.contains(movieDetected) || movieDetected.contains(alb))) {
                                score -= 1000
                            }
                            if (!isEditorialOrPlaylist) {
                                score -= 200
                            }
                            score
                        } ?: items.first()

                        val rawTitle = unescapeHtml(bestItem.optString("title"))
                        if (noiseRegex.containsMatchIn(rawTitle)) continue
                        val more = bestItem.optJSONObject("more_info") ?: org.json.JSONObject()
                        val durationSec = more.optString("duration").toLongOrNull() ?: 0L
                        if (durationSec in 1..49 || durationSec > 600) continue

                        val image = cleanArtworkUrl(bestItem.optString("image"))
                        val playCount = bestItem.optString("play_count").toLongOrNull()
                            ?: (more.optString("play_count").toLongOrNull() ?: 0L)

                        val artistMap = more.optJSONObject("artistMap")
                        val primaryArr = artistMap?.optJSONArray("primary_artists")
                        var artist = if (primaryArr != null && primaryArr.length() > 0) {
                            (0 until primaryArr.length()).joinToString(", ") { unescapeHtml(primaryArr.getJSONObject(it).optString("name")) }
                        } else unescapeHtml(more.optString("singers").ifBlank { "" })

                        if (artist.isBlank()) {
                            val music = unescapeHtml(more.optString("music"))
                            val subtitle = unescapeHtml(bestItem.optString("subtitle"))
                            val album = unescapeHtml(more.optString("album"))
                            artist = when {
                                music.isNotBlank() -> music
                                subtitle.isNotBlank() -> subtitle
                                album.isNotBlank() -> album
                                else -> "Original Soundtrack"
                            }
                        }

                        val songItem = Song(
                            id = bestItem.optString("id"),
                            title = rawTitle,
                            artist = artist,
                            artworkUrl = image,
                            duration = durationSec * 1000L,
                            playCount = playCount
                        )
                        if (OfficialSongFilter.isOfficial(songItem, rejectHindi = rejectHindi)) {
                            songs.add(songItem)
                        }
                    }
                    if (songs.isNotEmpty()) {
                        val ranked = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.fuzzyRankSearchResults(query, songs)
                        return@withContext ranked.take(limit)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "searchOnDevice attempt $attempt failed: ${e.message}")
                if (attempt == 0) {
                    kotlinx.coroutines.delay(200L)
                }
            }
        }
        // Seamless fallback to server search
        try {
            val serverResp = api.search(query, page = page, limit = limit)
            com.shyan.dreamin.data.recommendation.IntelliMatchEngine.fuzzyRankSearchResults(query, serverResp.results).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractDominantColor(artworkUrl: String) {
        if (artworkUrl.isBlank()) return

        // 1. Instant 0ms RAM cache hit
        com.shyan.dreamin.data.service.PaletteMemoryCache.getCachedTriad(artworkUrl)?.let { triad ->
            _uiState.update {
                it.copy(
                    dominantColor = triad.dominant,
                    secondaryColor = triad.secondary,
                    accentColor = triad.accent
                )
            }
            return
        }

        colorExtractJob?.cancel()
        colorExtractJob = viewModelScope.launch(Dispatchers.IO) {
            val triad = com.shyan.dreamin.data.service.PaletteMemoryCache.extractPaletteTriad(getApplication(), artworkUrl)
            _uiState.update {
                it.copy(
                    dominantColor = triad.dominant,
                    secondaryColor = triad.secondary,
                    accentColor = triad.accent
                )
            }
        }
    }

    private fun triggerSmartQueuePrefetch(currentSongId: String?) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(2500)
                val queue = _uiState.value.queue
                val idx = if (currentSongId != null) queue.indexOfFirst { it.id == currentSongId } else -1
                val upNextTracks = if (idx >= 0) queue.drop(idx + 1).take(1) else queue.take(1)

                for (song in upNextTracks) {
                    val cachedPath = downloadRepo.getPrefetchedOrDownloadedPath(song.id)
                    if (cachedPath == null) {
                        val streamUrl = resolveStreamUrl(song)
                        if (!streamUrl.startsWith("file://") && !streamUrl.startsWith("/")) {
                            downloadRepo.prefetchSong(song, streamUrl)
                            val appCtx = getApplication<Application>()
                            com.shyan.dreamin.service.PreBufferManager.preBufferUpcomingStream(appCtx, streamUrl, viewModelScope)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun prefetchNextSongStream(nextSong: Song?) {
        triggerSmartQueuePrefetch(nextSong?.id)
    }

    private suspend fun resolveStreamUrl(song: Song): String {
        return com.shyan.dreamin.data.service.AudioStreamResolver.resolveStreamUrl(
            song = song,
            downloadRepo = downloadRepo,
            apiService = api
        )
    }

    private fun parseAudioUri(streamUrl: String): android.net.Uri {
        val trimmed = streamUrl.trim()
        return when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> {
                val sanitized = trimmed.replace("\\/", "/").replace(" ", "%20")
                android.net.Uri.parse(sanitized)
            }
            trimmed.startsWith("file://") -> android.net.Uri.parse(trimmed)
            else -> {
                val file = java.io.File(trimmed)
                if (file.exists() && file.length() > 0) {
                    android.net.Uri.fromFile(file)
                } else {
                    android.net.Uri.parse(trimmed)
                }
            }
        }
    }

    fun playSong(song: Song, fromPlaylist: Boolean = false, preserveQueue: Boolean = false) {
        val isAlreadyInQueue = _uiState.value.queue.any { it.id == song.id }
        val isPlaylistActive = fromPlaylist || _uiState.value.playlistQueueActive

        // Optimistic UI: update song + state immediately so artwork/title swap is instant
        _uiState.update {
            val newQueue = when {
                preserveQueue || isAlreadyInQueue || isPlaylistActive -> {
                    if (it.queue.none { s -> s.id == song.id }) {
                        val currentIdx = it.queue.indexOfFirst { s -> s.id == it.currentSong?.id }
                        val insertAt = if (currentIdx >= 0) (currentIdx + 1).coerceAtMost(it.queue.size) else it.queue.size
                        it.queue.toMutableList().apply { add(insertAt, song) }
                    } else {
                        it.queue
                    }
                }
                fromPlaylist -> {
                    if (it.queue.none { s -> s.id == song.id }) listOf(song) + it.queue else it.queue
                }
                it.queue.isNotEmpty() -> {
                    val currentIdx = it.queue.indexOfFirst { s -> s.id == it.currentSong?.id }
                    val mutable = it.queue.toMutableList()
                    mutable.removeAll { s -> s.id == song.id }
                    val insertAt = if (currentIdx >= 0) (currentIdx + 1).coerceAtMost(mutable.size) else 0
                    mutable.add(insertAt, song)
                    mutable.toList()
                }
                else -> {
                    listOf(song)
                }
            }
            it.copy(
                currentSong = song,
                queue = newQueue,
                playbackState = PlaybackState.Loading,
                currentSongIsFavorite = it.favorites.any { s -> s.id == song.id },
                playlistQueueActive = isPlaylistActive
            )
        }
        _progress.value = PlaybackProgress(0L, 0L)
        extractDominantColor(song.displayArtworkUrl)
        loadLyricsForCurrentSong()

        // Resolve 100% official original movie soundtrack artwork in background (for non-Spotify tracks)
        if (!song.isSpotifyArtwork) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val official = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                    if (!official.isNullOrBlank() && official != song.artworkUrl) {
                        updateSongArtworkAcrossApp(song.id, official)
                    }
                } catch (_: Exception) {}
            }
        }

        viewModelScope.launch {
            try {
                val streamUrl = resolveStreamUrl(song)

                historyRepo.recordPlay(song)

                val uri = parseAudioUri(streamUrl)
                val mediaItem = MediaItem.Builder()
                    .setMediaId(song.id)
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(song.title)
                            .setArtist(song.artist)
                            .setArtworkUri(
                                song.displayArtworkUrl.takeIf { it.isNotBlank() }
                                    ?.let { android.net.Uri.parse(it) }
                            )
                            .build()
                    )
                    .build()

                var activeController = controller
                if (activeController == null) {
                    kotlinx.coroutines.withTimeoutOrNull(3000L) {
                        while (isActive && controller == null) {
                            val futureVal = runCatching { controllerFuture?.get() }.getOrNull()
                            if (futureVal != null) {
                                controller = futureVal
                                if (!isListenerAttached) {
                                    futureVal.addListener(playerListener)
                                    isListenerAttached = true
                                }
                                break
                            }
                            delay(50)
                        }
                    }
                    activeController = controller
                }

                activeController?.apply {
                    playWhenReady = true
                    setMediaItem(mediaItem)
                    prepare()
                    play()
                } ?: run {
                    android.util.Log.e("MusicVM", "MediaController not ready after timeout for ${song.title}")
                    _uiState.update { it.copy(playbackState = PlaybackState.Error("Player connecting... Please tap again.")) }
                }

                if (!isPlaylistActive) {
                    val currentQueue = _uiState.value.queue
                    val currentIdx = currentQueue.indexOfFirst { it.id == song.id }
                    val remaining = if (currentIdx >= 0) currentQueue.size - (currentIdx + 1) else 0
                    if (remaining <= 2 && !_uiState.value.isFetchingUpNext) {
                        val seedSong = currentQueue.lastOrNull() ?: song
                        fetchUpNext(seedSong.id)
                    }
                }
                fetchRecommendations(song.id)
                triggerSmartQueuePrefetch(song.id)

                // Real-time live official movie poster resolution (for non-Spotify tracks)
                if (!song.isSpotifyArtwork) {
                    launch(Dispatchers.IO) {
                        try {
                            val officialPoster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                            if (!officialPoster.isNullOrBlank()) {
                                updateSongArtworkAcrossApp(song.id, officialPoster)
                                extractDominantColor(officialPoster)
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "playSong failed: ${e.message}")
                _uiState.update { it.copy(playbackState = PlaybackState.Error(e.message ?: "Failed to load song")) }
            }
        }
    }

    fun updateSongArtworkAcrossApp(songId: String, officialPoster: String) {
        if (officialPoster.isBlank()) return
        val current = _uiState.value.currentSong
        if (current?.id == songId && current.isSpotifyArtwork) return

        _uiState.update { state ->
            fun shouldUpdate(s: Song?): Boolean = s != null && s.id == songId && !s.isSpotifyArtwork

            val newCurrentSong = if (shouldUpdate(state.currentSong)) {
                state.currentSong!!.copy(artworkUrl = officialPoster)
            } else state.currentSong

            val newQueue = state.queue.map { if (shouldUpdate(it)) it.copy(artworkUrl = officialPoster) else it }
            val newTrending = state.trendingCharts.map { if (shouldUpdate(it)) it.copy(artworkUrl = officialPoster) else it }
            val newRecs = state.recommendations.map { if (shouldUpdate(it)) it.copy(artworkUrl = officialPoster) else it }
            val newSearchResults = state.searchResults.map { if (shouldUpdate(it)) it.copy(artworkUrl = officialPoster) else it }
            val newRecentlyPlayed = state.recentlyPlayed.map { if (shouldUpdate(it)) it.copy(artworkUrl = officialPoster) else it }
            val newTopSongs = state.topSongs.map { if (shouldUpdate(it)) it.copy(artworkUrl = officialPoster) else it }
            val newOpenPlaylistSongs = state.openPlaylistSongs.map { if (shouldUpdate(it)) it.copy(artworkUrl = officialPoster) else it }
            val newFavorites = state.favorites.map { if (shouldUpdate(it)) it.copy(artworkUrl = officialPoster) else it }

            state.copy(
                currentSong = newCurrentSong,
                queue = newQueue,
                trendingCharts = newTrending,
                recommendations = newRecs,
                searchResults = newSearchResults,
                recentlyPlayed = newRecentlyPlayed,
                topSongs = newTopSongs,
                openPlaylistSongs = newOpenPlaylistSongs,
                favorites = newFavorites
            )
        }

        if (_uiState.value.currentSong?.id == songId && !_uiState.value.currentSong!!.isSpotifyArtwork) {
            extractDominantColor(officialPoster)
        }

        // Persist to Room database so playlists and offline library reflect updated posters!
        viewModelScope.launch(Dispatchers.IO) {
            try {
                playlistRepo.updateSongArtwork(songId, officialPoster)
                downloadRepo.updateArtwork(songId, officialPoster)
            } catch (_: Exception) {}
        }
    }

    private fun loadDownloads() {
        viewModelScope.launch {
            downloadRepo.observeAllDownloads().collect { list ->
                _uiState.update { it.copy(downloadedSongs = list) }
            }
        }
    }

    fun toggleLyricsView() {
        val willOpen = !_uiState.value.isLyricsViewOpen
        _uiState.update { it.copy(isLyricsViewOpen = willOpen) }
        if (willOpen && _uiState.value.lyricsState is LyricsState.Idle) {
            loadLyricsForCurrentSong()
        }
    }

    fun loadLyricsForCurrentSong() {
        val song = _uiState.value.currentSong ?: return
        lyricsJob?.cancel()
        lyricsJob = viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(lyricsState = LyricsState.Loading) }
            val res = LyricsService.fetchLyrics(
                trackName = song.title,
                artistName = song.artist,
                durationSec = song.duration / 1000L
            )
            _uiState.update { it.copy(lyricsState = res) }
        }
    }

    fun downloadSong(song: Song) {
        if (_uiState.value.downloadingSongIds.contains(song.id)) return
        viewModelScope.launch(Dispatchers.IO) {
            val (cleanTitle, cleanArtist) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.cleanTrackMetadata(song.title, song.artist)
            val cleanSong = song.copy(title = cleanTitle, artist = cleanArtist)
            _uiState.update { it.copy(downloadingSongIds = it.downloadingSongIds + song.id) }
            try {
                val streamUrl = resolveStreamUrl(cleanSong)
                val result = downloadRepo.downloadSong(cleanSong, streamUrl)
                if (result.isFailure) {
                    android.util.Log.e("MusicVM", "Download failed for ${song.title}")
                }
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "Download failed: ${e.message}")
            } finally {
                _uiState.update { it.copy(downloadingSongIds = it.downloadingSongIds - song.id) }
            }
        }
    }

    fun deleteDownload(songId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            downloadRepo.deleteDownload(songId)
        }
    }

    fun openArtistProfile(artistName: String) {
        val cleanArtist = artistName.split(",").firstOrNull()?.trim() ?: artistName
        if (cleanArtist.isBlank()) return

        _uiState.update {
            it.copy(
                selectedArtistProfile = ArtistProfile(
                    name = cleanArtist,
                    isLoading = true
                )
            )
        }

        artistJob?.cancel()
        artistJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val songs = searchOnDevice("$cleanArtist hits", limit = 20)
                val artwork = songs.firstOrNull()?.artworkUrl ?: ""
                _uiState.update {
                    it.copy(
                        selectedArtistProfile = ArtistProfile(
                            name = cleanArtist,
                            artworkUrl = artwork,
                            topSongs = songs,
                            isLoading = false
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "Error loading artist profile: ${e.message}")
                _uiState.update {
                    it.copy(
                        selectedArtistProfile = it.selectedArtistProfile?.copy(isLoading = false)
                    )
                }
            }
        }
    }

    fun closeArtistProfile() {
        _uiState.update { it.copy(selectedArtistProfile = null) }
    }

    fun togglePlayPause() {
        controller?.let {
            if (it.isPlaying) it.pause()
            else it.play()
        }
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    private var lastSkipTimestampMs: Long = 0L

    fun playNext(isAutoEnd: Boolean = false) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (!isAutoEnd && now - lastSkipTimestampMs < 350L) return
        lastSkipTimestampMs = now

        val state = _uiState.value
        val queue = state.queue
        val current = state.currentSong ?: return
        val idx = queue.indexOfFirst { it.id == current.id }

        // Track playback completion / skip in PlaybackAnalyticsTracker & FeedbackEngine
        val pos = controller?.currentPosition ?: 0L
        val dur = controller?.duration ?: 0L
        viewModelScope.launch(Dispatchers.IO) {
            analyticsTracker.recordTrackEnd(current, pos, dur)
        }

        // 1. Repeat ONE: replay current track from start
        if (state.repeatMode == TrackRepeatMode.ONE) {
            controller?.seekTo(0L)
            controller?.play()
            return
        }

        // 2. Single song playlist or queue: loop smoothly without dropping playback
        if (queue.size <= 1) {
            if (state.playlistQueueActive || state.repeatMode != TrackRepeatMode.OFF) {
                controller?.seekTo(0L)
                controller?.play()
                return
            }
        }

        // 3. Next song in active queue (respects shuffled queue order without repeats)
        if (idx >= 0 && idx + 1 < queue.size) {
            val nextSong = queue[idx + 1]
            playSong(nextSong, fromPlaylist = state.playlistQueueActive, preserveQueue = true)
            // Auto-extend queue when reaching the last 2 songs
            val remaining = queue.size - (idx + 2)
            if (com.shyan.dreamin.data.recommendation.SmartQueueEngine.shouldAutoExtendQueue(remaining, state.playlistQueueActive, _uiState.value.isFetchingUpNext)) {
                val seedSong = queue.lastOrNull() ?: nextSong
                fetchUpNext(seedSong.id)
            }
            return
        }

        // 4. Repeat ALL mode or Playlist Loop (loops playlist so playback never dies in background)
        if ((state.repeatMode == TrackRepeatMode.ALL || state.playlistQueueActive) && queue.isNotEmpty()) {
            val nextQueue = if (state.isShuffle && queue.size > 1) {
                val unplayed = queue.filter { it.id != current.id }.shuffled()
                listOf(current) + unplayed
            } else {
                queue
            }
            if (state.isShuffle && queue.size > 1) {
                _uiState.update { it.copy(queue = nextQueue) }
            }
            val nextSong = if (state.isShuffle && queue.size > 1) nextQueue.getOrNull(1) ?: nextQueue.first() else nextQueue.first()
            playSong(nextSong, fromPlaylist = state.playlistQueueActive, preserveQueue = true)
            return
        }

        // 5. Seamless Continuous Autoplay (Infinite Radio) when queue ends
        if (!state.playlistQueueActive) {
            viewModelScope.launch(Dispatchers.IO) {
                try {
                    val pairs = com.shyan.dreamin.data.service.YouTubeRadioService.fetchRadioRecommendations(current)
                    for ((t, a) in pairs.take(8)) {
                        val cleanT = t.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
                        val firstArtist = a.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
                        val cand = searchOnDevice("$cleanT $firstArtist", limit = 1).firstOrNull()
                            ?: searchOnDevice(cleanT, limit = 1).firstOrNull()
                        if (cand != null && cand.id != current.id && OfficialSongFilter.isOfficial(cand, rejectHindi = true)) {
                            withContext(Dispatchers.Main) {
                                playSong(cand, fromPlaylist = false, preserveQueue = true)
                            }
                            return@launch
                        }
                    }

                    // Fallback to related / artist hits if YouTube Radio hits were exhausted
                    val primaryArtist = current.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
                    if (primaryArtist.isNotBlank()) {
                        val artistHits = searchOnDevice("$primaryArtist hits", limit = 6).filter { it.id != current.id }
                        if (artistHits.isNotEmpty()) {
                            withContext(Dispatchers.Main) {
                                playSong(artistHits.first(), fromPlaylist = false, preserveQueue = true)
                            }
                            return@launch
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Continuous autoplay radio error: ${e.message}")
                }
            }
        }
    }

    fun playPrevious() {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastSkipTimestampMs < 350L) return
        lastSkipTimestampMs = now

        val state = _uiState.value
        val current = state.currentSong ?: return
        val queue = state.queue
        val idx = queue.indexOfFirst { it.id == current.id }

        // If played more than 3 seconds, seek to start of current song
        val pos = controller?.currentPosition ?: 0L
        if (pos > 3000L) {
            controller?.seekTo(0L)
            return
        }

        val prevSong = if (idx > 0) {
            queue[idx - 1]
        } else if ((state.repeatMode == TrackRepeatMode.ALL || state.playlistQueueActive) && queue.isNotEmpty()) {
            queue.last()
        } else {
            null
        }

        if (prevSong != null) {
            playSong(prevSong, fromPlaylist = state.playlistQueueActive, preserveQueue = true)
        } else {
            controller?.seekTo(0L)
        }
    }

    fun addToQueue(song: Song) {
        _uiState.update { state ->
            val queue = state.queue.toMutableList()
            if (state.currentSong != null && queue.none { it.id == state.currentSong.id }) {
                queue.add(0, state.currentSong)
            }
            val currentIdx = queue.indexOfFirst { it.id == state.currentSong?.id }.coerceAtLeast(0)

            // Count consecutive user-queued songs directly following the currently playing track
            var userQueueCount = 0
            for (i in (currentIdx + 1) until queue.size) {
                if (state.userQueuedSongIds.contains(queue[i].id)) {
                    userQueueCount++
                } else {
                    break
                }
            }

            // Adjust count if song was already within the user queue section
            val priorIdx = queue.indexOfFirst { it.id == song.id }
            if (priorIdx in (currentIdx + 1)..(currentIdx + userQueueCount)) {
                userQueueCount--
            }
            queue.removeAll { it.id == song.id }

            val insertAt = (currentIdx + 1 + userQueueCount).coerceIn(0, queue.size)
            queue.add(insertAt, song)

            val updatedUserQueued = (state.userQueuedSongIds.filter { it != song.id } + song.id)
            state.copy(
                queue = queue.toList(),
                userQueuedSongIds = updatedUserQueued
            )
        }
    }

    fun playNext(song: Song) {
        _uiState.update { state ->
            val queue = state.queue.toMutableList()
            if (state.currentSong != null && queue.none { it.id == state.currentSong.id }) {
                queue.add(0, state.currentSong)
            }
            val currentIdx = queue.indexOfFirst { it.id == state.currentSong?.id }.coerceAtLeast(0)
            val insertAt = (currentIdx + 1).coerceIn(0, queue.size)
            queue.removeAll { it.id == song.id }
            queue.add(insertAt, song)

            val updatedUserQueued = listOf(song.id) + state.userQueuedSongIds.filter { it != song.id }
            state.copy(
                queue = queue.toList(),
                userQueuedSongIds = updatedUserQueued
            )
        }
    }

    fun removeFromQueue(song: Song) {
        _uiState.update { state ->
            state.copy(
                queue = state.queue.filter { s -> s.id != song.id },
                userQueuedSongIds = state.userQueuedSongIds.filter { id -> id != song.id }
            )
        }
    }

    fun restoreToQueue(song: Song, index: Int) {
        _uiState.update { state ->
            val queue = state.queue.toMutableList()
            if (queue.none { it.id == song.id }) {
                val insertIdx = index.coerceIn(0, queue.size)
                queue.add(insertIdx, song)
                state.copy(
                    queue = queue.toList(),
                    userQueuedSongIds = state.userQueuedSongIds + song.id
                )
            } else {
                state
            }
        }
    }

    fun clearQueue() {
        _uiState.update { state ->
            val current = state.currentSong
            state.copy(
                queue = if (current != null) listOf(current) else emptyList(),
                userQueuedSongIds = emptyList()
            )
        }
    }

    fun reorderQueue(fromIndex: Int, toIndex: Int) {
        _uiState.update { state ->
            val queue = state.queue
            if (fromIndex !in queue.indices || toIndex !in queue.indices) return@update state
            val mutable = queue.toMutableList()
            val item = mutable.removeAt(fromIndex)
            mutable.add(toIndex, item)
            state.copy(queue = mutable.toList())
        }
    }

    fun toggleQueue() {
        _uiState.update { it.copy(isQueueVisible = !it.isQueueVisible) }
    }

    private fun loadRecentSearches() { /* handled in init critical-path block */ }

    fun clearRecentSearches() {
        viewModelScope.launch { userPrefs.clearRecentSearches() }
    }

    private fun loadLastSession() { /* handled in init critical-path block */ }

    private var pendingResumePositionMs: Long = 0L

    fun resumeLastSession() {
        val session = _uiState.value.lastSession ?: return
        pendingResumePositionMs = session.positionMs
        val recent = _uiState.value.recentlyPlayed
        if (recent.any { it.id == session.song.id }) {
            playSongFromList(session.song, recent)
        } else {
            _uiState.update { it.copy(queue = listOf(session.song)) }
            playSong(session.song, preserveQueue = true)
        }
    }

    fun activateSearch() {
        _uiState.update { it.copy(isSearchActive = true) }
    }

    fun setSearchQuery(query: String) {
        val trimmed = query.trim()
        val didYouMean = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.generatePhoneticSuggestions(trimmed)
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchActive = true,
                searchPage = 1,
                hasMoreSearchResults = false,
                searchError = null,
                didYouMeanQuery = didYouMean,
                isSearching = trimmed.isNotEmpty(),
                searchResults = if (trimmed.isEmpty()) emptyList() else it.searchResults
            )
        }
        searchJob?.cancel()
        if (trimmed.isNotEmpty()) {
            searchJob = viewModelScope.launch {
                delay(220)
                try {
                    var songs = searchOnDevice(trimmed, page = 1)
                    if (songs.isEmpty() && didYouMean != null) {
                        val altSongs = searchOnDevice(didYouMean, page = 1)
                        if (altSongs.isNotEmpty()) {
                            songs = altSongs
                        }
                    }
                    val rankedSongs = withContext(Dispatchers.Default) {
                        com.shyan.dreamin.data.recommendation.IntelliMatchEngine.fuzzyRankSearchResults(trimmed, songs)
                    }
                    if (trimmed.length >= 2) userPrefs.addRecentSearch(trimmed)
                    _uiState.update {
                        it.copy(
                            searchResults = rankedSongs,
                            hasMoreSearchResults = rankedSongs.size >= 15,
                            isSearching = false
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Search failed: ${e.javaClass.simpleName}: ${e.message}")
                    _uiState.update { it.copy(searchResults = emptyList(), isSearching = false, searchError = "Couldn't reach search. Check your connection.") }
                }
            }
        } else {
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList(), didYouMeanQuery = null) }
        }
    }

    fun loadMoreSearchResults() {
        val state = _uiState.value
        if (!state.hasMoreSearchResults || state.isLoadingMoreSearch || state.searchQuery.length < 2) return
        val nextPage = state.searchPage + 1
        _uiState.update { it.copy(isLoadingMoreSearch = true) }
        viewModelScope.launch {
            try {
                val songs = searchOnDevice(state.searchQuery, page = nextPage)
                val combined = state.searchResults + songs
                val rankedCombined = withContext(Dispatchers.Default) {
                    com.shyan.dreamin.data.recommendation.IntelliMatchEngine.fuzzyRankSearchResults(state.searchQuery, combined)
                }
                _uiState.update {
                    it.copy(
                        searchResults = rankedCombined,
                        searchPage = nextPage,
                        isLoadingMoreSearch = false,
                        hasMoreSearchResults = songs.size >= 15
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.e("MusicVM", "loadMore failed: ${e.message}")
                _uiState.update { it.copy(isLoadingMoreSearch = false) }
            }
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        _uiState.update { it.copy(searchQuery = "", isSearchActive = false, isSearching = false, searchResults = emptyList(), didYouMeanQuery = null) }
    }

    fun clearSearch() {
        closeSearch()
    }

    fun refreshData() {
        android.util.Log.d("MusicVM", "Refreshing all data...")
        clearAllCaches()
        loadChart(forceRefresh = true)
        loadRecentlyPlayed()
        loadTopSongs()
        loadFavorites()
        loadPlaylists()
        loadDownloads()
        _uiState.value.currentSong?.let { fetchRecommendations(it.id) }
    }

    private suspend fun fetchTrendingChartOnDevice(): List<Song> = withContext(Dispatchers.IO) {
        val trendingQueries = listOf(
            "The GOAT Yuvan songs",
            "Amaran GV Prakash",
            "Vettaiyan Anirudh",
            "Dragon Leon James",
            "Sai Abhyankkar Aasa Kooda",
            "Nilavuku En Mel Ennadi Kobam Dhanush",
            "Love Insurance Kompany Dheema",
            "Devara Anirudh Tamil",
            "Leo Anirudh hits",
            "Jailer Anirudh songs",
            "Thalapathy 69 Anirudh"
        )

        val deferredResults = trendingQueries.map { q ->
            async {
                searchOnDevice(q, limit = 3, targetLanguage = "tamil")
            }
        }

        val allSongs = mutableListOf<Song>()
        deferredResults.forEach { def ->
            allSongs.addAll(def.await())
        }

        OfficialSongFilter.cleanOfficialList(allSongs)
    }

    private fun loadChart(forceRefresh: Boolean = false) {
        viewModelScope.launch(Dispatchers.IO) {
            if (forceRefresh) {
                _uiState.update { it.copy(isLoadingChart = true) }
            } else {
                val cached = userPrefs.cachedChart.first()
                val hasStaleJunk = cached.any { it.title.contains("Scooty", ignoreCase = true) || it.title.contains("Don'u", ignoreCase = true) }
                if (cached.isNotEmpty() && !hasStaleJunk && cached.any { it.artist.isNotBlank() }) {
                    _uiState.update { it.copy(trendingCharts = cached, isLoadingChart = false) }
                    prefetchTopTrendingArtworks(cached)
                } else {
                    _uiState.update { it.copy(isLoadingChart = true) }
                }
            }

            // 1. Fetch Real-Time Live Top 50 Chart from Apple Music Official Feed
            val liveChartSongs = mutableListOf<Song>()
            try {
                val pairs = com.shyan.dreamin.data.service.AppleChartsService.fetchLiveAppleTopCharts()
                val deferredSearches = pairs.take(25).map { (t, a) ->
                    async {
                        val cleanT = t.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
                        val firstArtist = a.split(",").firstOrNull()?.split("&")?.firstOrNull()?.trim() ?: ""
                        searchOnDevice("$cleanT $firstArtist", limit = 1, targetLanguage = "tamil")
                    }
                }
                deferredSearches.forEach { def ->
                    val res = def.await()
                    if (res.isNotEmpty()) {
                        liveChartSongs.add(res.first())
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "Apple Live Chart fetch error: ${e.message}")
            }

            val finalTrending = if (liveChartSongs.isNotEmpty()) {
                OfficialSongFilter.cleanOfficialList(liveChartSongs)
            } else {
                fetchTrendingChartOnDevice()
            }

            if (finalTrending.isNotEmpty()) {
                _uiState.update { it.copy(trendingCharts = finalTrending, isLoadingChart = false) }
                userPrefs.saveChartCache(finalTrending)
                // Proactively resolve official movie posters for charts
                finalTrending.forEach { chartSong ->
                    launch(Dispatchers.IO) {
                        try {
                            val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(chartSong)
                            if (!poster.isNullOrBlank()) {
                                updateSongArtworkAcrossApp(chartSong.id, poster)
                            }
                        } catch (_: Exception) {}
                    }
                }
            } else {
                _uiState.update { it.copy(isLoadingChart = false) }
            }
        }
    }

    private suspend fun fetchJioSaavnRelated(songId: String): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        try {
            val url = "https://www.jiosaavn.com/api.php?__call=reco.getreco&api_version=4&_format=json&_marker=0&ctx=web6dot0&pid=$songId"
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val text = NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
            val array = org.json.JSONArray(text)
            for (i in 0 until array.length()) {
                val it = array.getJSONObject(i)
                val id = it.optString("id", "")
                val title = unescapeHtml(it.optString("title", it.optString("song", "")))
                val artist = unescapeHtml(it.optString("primary_artists", it.optString("singers", it.optString("artist", ""))))
                val image = cleanArtworkUrl(it.optString("image", ""))
                val more = it.optJSONObject("more_info")
                val durSec = more?.optLong("duration", 0L) ?: 0L
                val album = unescapeHtml(more?.optString("album", "") ?: "")
                if (id.isNotBlank() && title.isNotBlank()) {
                    results.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            artworkUrl = image,
                            duration = durSec * 1000L
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        results
    }

    fun clearAllCaches() {
        com.shyan.dreamin.data.service.YouTubeRadioService.clearCache()
        com.shyan.dreamin.data.service.AudioStreamResolver.clearCache()
        com.shyan.dreamin.data.service.OfficialArtworkService.clearCache()
        com.shyan.dreamin.data.service.PaletteMemoryCache.clearCache()
    }

    private suspend fun resolveRadioCandidates(currentSong: Song, limit: Int): List<Song> = withContext(Dispatchers.IO) {
        val ytSongs = mutableListOf<Song>()
        try {
            val pairs = com.shyan.dreamin.data.service.YouTubeRadioService.fetchRadioRecommendations(currentSong)
            if (pairs.isNotEmpty()) {
                val deferredSearches = pairs.take(limit).map { (t, a) ->
                    async {
                        val cleanT = t.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
                        val firstArtist = a.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
                        val cand = searchOnDevice("$cleanT $firstArtist", limit = 1, targetLanguage = "tamil").firstOrNull()
                            ?: searchOnDevice(cleanT, limit = 1, targetLanguage = "tamil").firstOrNull()
                        if (cand != null && OfficialSongFilter.isOfficial(cand, rejectHindi = true)) {
                            cand
                        } else null
                    }
                }
                deferredSearches.forEach { def ->
                    val res = def.await()
                    if (res != null) {
                        ytSongs.add(res)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "Radio candidate error: ${e.message}")
        }
        ytSongs
    }

    private fun fetchUpNext(songId: String) {
        if (_uiState.value.playlistQueueActive) return
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isFetchingUpNext = true) }
            try {
                val currentSong = _uiState.value.currentSong ?: return@launch
                val ytSongs = resolveRadioCandidates(currentSong, limit = 16)

                val finalQueueTracks = if (ytSongs.isNotEmpty()) {
                    SmartQueueEngine.buildQueue(
                        ytRadioHits = ytSongs,
                        fallbackHits = emptyList(),
                        currentSong = currentSong,
                        existingQueue = _uiState.value.queue,
                        limit = 18
                    )
                } else {
                    val primaryArtist = currentSong.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
                    val related = fetchJioSaavnRelated(songId)
                    val artistHits = if (primaryArtist.isNotBlank()) searchOnDevice("$primaryArtist hits", limit = 10) else emptyList()
                    SmartQueueEngine.buildQueue(
                        ytRadioHits = emptyList(),
                        fallbackHits = related + artistHits,
                        currentSong = currentSong,
                        existingQueue = _uiState.value.queue,
                        limit = 15
                    )
                }

                if (finalQueueTracks.isNotEmpty()) {
                    _uiState.update { state ->
                        if (state.playlistQueueActive) return@update state
                        val existingIds = state.queue.map { it.id }.toSet()
                        val newUnique = finalQueueTracks.filter { it.id !in existingIds }
                        state.copy(queue = state.queue + newUnique)
                    }
                    finalQueueTracks.forEach { qSong ->
                        launch(Dispatchers.IO) {
                            try {
                                val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(qSong)
                                if (!poster.isNullOrBlank()) {
                                    updateSongArtworkAcrossApp(qSong.id, poster)
                                }
                            } catch (_: Exception) {}
                        }
                    }
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "fetchUpNext error: ${e.message}")
            } finally {
                _uiState.update { it.copy(isFetchingUpNext = false) }
            }
        }
    }

    private fun fetchRecommendations(songId: String) {
        val seedTitle = _uiState.value.currentSong?.artist
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val currentSong = _uiState.value.currentSong ?: return@launch
                val primaryArtist = currentSong.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
                val ytRecSongs = resolveRadioCandidates(currentSong, limit = 12)

                val finalRecs = if (ytRecSongs.isNotEmpty()) {
                    OfficialSongFilter.cleanOfficialList(ytRecSongs, rejectHindi = true)
                        .filter { it.id != songId && !FeedbackEngine.isSuppressed(it) }
                } else {
                    val fallback = searchOnDevice("$primaryArtist hits", limit = 10, targetLanguage = "tamil")
                    OfficialSongFilter.cleanOfficialList(fallback, rejectHindi = true)
                        .filter { it.id != songId && !FeedbackEngine.isSuppressed(it) }
                }

                _uiState.update { it.copy(recommendations = finalRecs, recommendationSeedTitle = seedTitle) }
                finalRecs.forEach { recSong ->
                    launch(Dispatchers.IO) {
                        try {
                            val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(recSong)
                            if (!poster.isNullOrBlank()) {
                                updateSongArtworkAcrossApp(recSong.id, poster)
                            }
                        } catch (_: Exception) {}
                    }
                }
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
        _uiState.update { state ->
            val queue = state.queue
            val current = state.currentSong
            val idx = if (current != null) queue.indexOfFirst { it.id == current.id } else -1
            val reorderedQueue = if (newShuffle && idx >= 0 && idx + 1 < queue.size) {
                val played = queue.take(idx + 1)
                val upcoming = queue.drop(idx + 1).shuffled()
                played + upcoming
            } else {
                queue
            }
            state.copy(isShuffle = newShuffle, queue = reorderedQueue)
        }
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

    fun extractColorsFromArtwork(artworkUrl: String) {
        extractDominantColor(artworkUrl)
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
        openPlaylistJob?.cancel()
        colorExtractJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        runCatching {
            getApplication<Application>().unregisterReceiver(mediaActionReceiver)
        }
        super.onCleared()
    }
}
