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
    private var openPlaylistJob: Job? = null
    private var colorExtractJob: Job? = null
    private var prefetchJob: Job? = null
    private var preloadJob: Job? = null
    private var lyricsJob: Job? = null
    private var artistJob: Job? = null
    private var albumJob: Job? = null
    private val dismissedSyncAlertPlaylists = java.util.concurrent.ConcurrentHashMap.newKeySet<Long>()
    private val unmatchableTrackSignatures = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    private val mediaActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                MusicService.ACTION_PLAY_NEXT -> playNext()
                MusicService.ACTION_PLAY_PREVIOUS -> playPrevious()
                MusicService.ACTION_TOGGLE_FAVORITE -> _uiState.value.currentSong?.let { toggleFavoriteFor(it) }
                MusicService.ACTION_TOGGLE_SHUFFLE -> toggleShuffle()
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
                // Throttle background poster resolution for top 5 recently played after initial render settles
                delay(3000L)
                songs.take(5).forEach { song ->
                    launch(Dispatchers.IO) {
                        try {
                            if (song.artworkUrl.isBlank() || song.artworkUrl.contains("default") || com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(song) == null) {
                                val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                                if (!poster.isNullOrBlank() && poster != song.artworkUrl) {
                                    updateSongArtworkAcrossApp(song.id, poster)
                                    historyRepo.updateArtwork(song.id, poster)
                                }
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
                // Throttle background poster resolution for top 5 top songs after initial render settles
                delay(4000L)
                songs.take(5).forEach { song ->
                    launch(Dispatchers.IO) {
                        try {
                            if (song.artworkUrl.isBlank() || song.artworkUrl.contains("default") || com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(song) == null) {
                                val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                                if (!poster.isNullOrBlank() && poster != song.artworkUrl) {
                                    updateSongArtworkAcrossApp(song.id, poster)
                                    historyRepo.updateArtwork(song.id, poster)
                                }
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
                delay(5000L)
                com.shyan.dreamin.data.service.OfficialArtworkService.prefetchSongListArtworks(songs.take(8), viewModelScope) { id, poster ->
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
                // Load artworks from DB without CPU-heavy synchronous palette extraction on startup
                val artworks = withContext(Dispatchers.IO) {
                    lists.associate { playlist ->
                        val arts = playlistRepo.getFirstFourArtworks(playlist.id)
                        playlist.id to arts
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
        val coverArt = _uiState.value.playlists.find { it.id == playlistId }?.coverUrl
            ?: _uiState.value.playlistArtworks[playlistId]?.firstOrNull()
        if (!coverArt.isNullOrBlank()) {
            val cached = com.shyan.dreamin.data.service.PaletteMemoryCache.getCachedTriad(coverArt)
            if (cached != null) {
                com.shyan.dreamin.data.service.PaletteMemoryCache.putTriad("playlist_$playlistId", cached)
            }
        }
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
        dismissedSyncAlertPlaylists.add(playlistId)
        _uiState.update { state ->
            state.copy(spotifySyncAlerts = state.spotifySyncAlerts.filterNot { it.playlistId == playlistId })
        }
    }

    fun unlinkSpotifyPlaylist(playlistId: Long) {
        viewModelScope.launch(Dispatchers.IO) {
            playlistRepo.updateSpotifyPlaylistId(playlistId, null)
            dismissedSyncAlertPlaylists.add(playlistId)
            dismissSpotifySyncAlert(playlistId)
        }
    }

    private suspend fun isTrackAlreadyInPlaylist(
        track: com.shyan.dreamin.data.service.SpotifyImportedTrack,
        existingSongs: List<Song>,
        existingKeys: Set<String>
    ): Boolean {
        // 1. Direct normalized key
        val key = com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(track.title)
        if (existingKeys.contains(key)) return true

        // 2. Decomposed base title key & full clean key
        val (baseTitle, fullClean) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(track.title)
        val baseKey = com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(baseTitle)
        if (baseKey.length >= 3 && existingKeys.contains(baseKey)) return true
        val fullCleanKey = com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(fullClean)
        if (fullCleanKey.length >= 3 && existingKeys.contains(fullCleanKey)) return true

        // 3. Learning memory cache hit: if this track was previously matched to a song that is in existingSongs
        val primaryArtist = track.artist.split(",", "&", "feat.", "ft.", "/", ";").firstOrNull()?.trim() ?: ""
        val signature = "${baseTitle.lowercase().trim()}|${primaryArtist.lowercase().trim()}"
        try {
            val cached = importMatchDao.getMatch(signature)
            if (cached != null && existingSongs.any { it.id == cached.songId }) {
                return true
            }
        } catch (_: Exception) {}

        // 4. Substring & artist overlap check against existing songs
        val targetArtistsNormalized = track.artist.lowercase()
            .split(",", "&", "feat.", "ft.", "/", ";")
            .map { it.replace(Regex("[^a-z0-9]"), "").trim() }
            .filter { it.length >= 2 }

        val targetBaseKey = baseKey.replace(Regex("[^a-z0-9]"), "")
        if (targetBaseKey.length >= 4) {
            val existsWithArtist = existingSongs.any { s ->
                val sKey = com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(s.displayTitle)
                val titleMatches = sKey == targetBaseKey ||
                    (sKey.length >= 4 && (sKey.contains(targetBaseKey) || targetBaseKey.contains(sKey))) ||
                    com.shyan.dreamin.data.recommendation.IntelliMatchEngine.jaroWinkler(sKey, targetBaseKey) >= 0.85
                if (!titleMatches) return@any false

                val sArtistNorm = s.artist.lowercase().replace(Regex("[^a-z0-9]"), "")
                targetArtistsNormalized.isEmpty() || targetArtistsNormalized.any { sArtistNorm.contains(it) || it.contains(sArtistNorm) }
            }
            if (existsWithArtist) return true
        }

        return false
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
                    if (dismissedSyncAlertPlaylists.contains(pl.id)) continue

                    try {
                        val details = com.shyan.dreamin.data.service.SpotifyImportService.fetchPlaylistDetails(spotifyId, getApplication())
                        if (details == null) {
                            alerts.add(SpotifySyncAlert(playlistId = pl.id, playlistName = pl.name, newTrackCount = 0, isUnavailable = true))
                        } else {
                            val existingSongs = playlistRepo.getSongs(pl.id)
                            val existingKeys = existingSongs.map { com.shyan.dreamin.data.recommendation.OfficialSongFilter.normalizeSongKey(it.displayTitle) }.toSet()
                            val newTracks = details.tracks.filterNot { t ->
                                val (baseTitle, _) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(t.title)
                                val primaryArtist = t.artist.split(",", "&", "feat.", "ft.", "/", ";").firstOrNull()?.trim() ?: ""
                                val sig = "${pl.id}|${baseTitle.lowercase().trim()}|${primaryArtist.lowercase().trim()}"
                                if (unmatchableTrackSignatures.contains(sig)) return@filterNot true

                                isTrackAlreadyInPlaylist(t, existingSongs, existingKeys)
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
        dismissedSyncAlertPlaylists.remove(playlistId)
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
                    isTrackAlreadyInPlaylist(t, existingSongs, existingKeys)
                }

                if (newTracks.isNotEmpty()) {
                    val playlistLang = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.detectDominantPlaylistLanguage(details.tracks, details.title)
                    val newlyMatched = mutableListOf<Song>()
                    for (track in newTracks) {
                        val (baseTitle, _) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(track.title)
                        val primaryArtist = track.artist.split(",", "&", "feat.", "ft.", "/", ";").firstOrNull()?.trim() ?: ""
                        val sig = "${playlistId}|${baseTitle.lowercase().trim()}|${primaryArtist.lowercase().trim()}"

                        val (matched, _) = matchSpotifyTrack(track, playlistLang)
                        if (matched != null) {
                            if (existingSongs.none { it.id == matched.id } && newlyMatched.none { it.id == matched.id }) {
                                val art = if (track.artworkUrl.isNotBlank()) track.artworkUrl else matched.artworkUrl
                                newlyMatched.add(matched.copy(artworkUrl = art))
                            }
                        } else {
                            unmatchableTrackSignatures.add(sig)
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
                val cachedTitleClean = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(cached.songTitle).first.lowercase().replace(Regex("[^a-z0-9]"), "")
                val targetTitleClean = cleanBaseTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
                val sim = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.jaroWinkler(cachedTitleClean, targetTitleClean)
                val isSimilar = sim >= 0.60 ||
                    (cachedTitleClean.length >= 4 && targetTitleClean.contains(cachedTitleClean)) ||
                    (targetTitleClean.length >= 4 && cachedTitleClean.contains(targetTitleClean))

                if (isSimilar) {
                    val cachedSong = Song(
                        id = cached.songId,
                        title = cached.songTitle,
                        artist = cached.songArtist,
                        artworkUrl = cached.artworkUrl,
                        duration = cached.duration
                    )
                    return@withContext Pair(cachedSong, null)
                }
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

    fun removeSongsFromPlaylist(playlistId: Long, songIds: Collection<String>) {
        if (songIds.isEmpty()) return
        val currentSongs = _uiState.value.openPlaylistSongs.filterNot { songIds.contains(it.id) }
        _uiState.update { it.copy(openPlaylistSongs = currentSongs) }

        viewModelScope.launch {
            playlistRepo.removeSongs(playlistId, songIds.toList())
        }
    }

    fun reorderPlaylistSongs(playlistId: Long, fromIndex: Int, toIndex: Int) {
        val currentSongs = _uiState.value.openPlaylistSongs.toMutableList()
        if (fromIndex !in currentSongs.indices || toIndex !in currentSongs.indices || fromIndex == toIndex) return

        val movedSong = currentSongs.removeAt(fromIndex)
        currentSongs.add(toIndex, movedSong)
        _uiState.update { it.copy(openPlaylistSongs = currentSongs) }

        viewModelScope.launch {
            val songIds = currentSongs.map { it.id }
            playlistRepo.updateSongPositions(playlistId, songIds)
        }
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
        val totalMs = minutes * 60_000L
        val endMs = System.currentTimeMillis() + totalMs
        _uiState.update { it.copy(sleepTimerEndMs = endMs) }
        val args = android.os.Bundle().apply {
            putLong(MusicService.EXTRA_SLEEP_TIMER_END_MS, endMs)
        }
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(MusicService.CUSTOM_COMMAND_SET_SLEEP_TIMER, android.os.Bundle.EMPTY),
            args
        )
    }

    fun cancelSleepTimer() {
        _uiState.update { it.copy(sleepTimerEndMs = null) }
        controller?.sendCustomCommand(
            androidx.media3.session.SessionCommand(MusicService.CUSTOM_COMMAND_CANCEL_SLEEP_TIMER, android.os.Bundle.EMPTY),
            android.os.Bundle.EMPTY
        )
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
                        playNext(isAutoEnd = true)
                    }
                    PlaybackState.Loading
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

            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // If ExoPlayer transitioned automatically to the preloaded track at index 1,
                // prune the finished track at index 0 so the active track remains at index 0.
                val c = controller
                if (c != null && c.mediaItemCount > 1 && c.currentMediaItemIndex > 0) {
                    c.removeMediaItem(0)
                }
            }

            if (id != null) {
                _uiState.update { state ->
                    val matching = state.queue.find { it.id == id }
                    if (matching != null) {
                        state.copy(
                            currentSong = matching,
                            currentSongIsFavorite = state.favorites.any { f -> f.id == matching.id },
                            userQueuedSongIds = state.userQueuedSongIds.filter { qId -> qId != matching.id }
                        )
                    } else {
                        val title = meta?.title?.toString()?.takeIf { it.isNotBlank() }
                        if (title != null) {
                            val newSong = Song(
                                id = id,
                                title = title,
                                artist = meta.artist?.toString() ?: "",
                                artworkUrl = meta.artworkUri?.toString() ?: ""
                            )
                            state.copy(
                                currentSong = newSong,
                                currentSongIsFavorite = state.favorites.any { f -> f.id == newSong.id },
                                userQueuedSongIds = state.userQueuedSongIds.filter { qId -> qId != newSong.id }
                            )
                        } else state
                    }
                }
            }
            _progress.value = PlaybackProgress(0L, duration)
            _uiState.value.currentSong?.let { activeSong ->
                viewModelScope.launch { historyRepo.recordPlay(activeSong) }
                extractDominantColor(activeSong.displayArtworkUrl)
                loadLyricsForCurrentSong()

                // Auto-extend queue when reaching the last 2 songs
                if (!_uiState.value.playlistQueueActive) {
                    val currentQueue = _uiState.value.queue
                    val currentIdx = currentQueue.indexOfFirst { it.id == activeSong.id }
                    val remaining = if (currentIdx >= 0) currentQueue.size - (currentIdx + 1) else 0
                    if (com.shyan.dreamin.data.recommendation.SmartQueueEngine.shouldAutoExtendQueue(remaining, _uiState.value.playlistQueueActive, _uiState.value.isFetchingUpNext)) {
                        val seedSong = currentQueue.lastOrNull() ?: activeSong
                        fetchUpNext(seedSong.id)
                    }
                }

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

            // Immediately preload the next track into index 1
            preloadNextMediaItemInQueue()
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
                    android.util.Log.w("MusicVM", "Song failed to resolve after error, auto-advancing: ${e.message}")
                    _uiState.update { state ->
                        state.copy(
                            queue = state.queue.filter { s -> s.id != current.id },
                            playbackState = PlaybackState.Loading
                        )
                    }
                    playNext(isAutoEnd = true)
                }
            }
        }
    }

    private val backgroundSyncDispatcher = Dispatchers.IO.limitedParallelism(2)
    private var lastPrefetchedTrackId: String? = null

    private fun startPositionPoller() {
        val powerManager = getApplication<Application>().getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        viewModelScope.launch {
            var saveCounter = 0
            while (isActive) {
                val isScreenOn = powerManager?.isInteractive ?: true
                val pollDelay = if (isScreenOn) 100L else 2500L
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

                saveCounter += if (isScreenOn) 1 else 25
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
                        val official = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                        if (!official.isNullOrBlank() && official != song.artworkUrl) {
                            updateSongArtworkAcrossApp(song.id, official)
                        }
                        val finalUrl = official ?: song.displayArtworkUrl
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
                            val albType = item.optJSONObject("more_info")?.optString("album_type", "")?.lowercase() ?: ""
                            val itemImage = item.optString("image", "").lowercase()
                            val isEditorialOrPlaylist = itemImage.contains("/editorial/") || itemImage.contains("/playlist/") || itemImage.contains("/818/") || itemImage.contains("/888/") || itemImage.contains("compilation")

                            var score = 1000
                            val isComp = compilationRegex.containsMatchIn(alb)
                            val isSingle = alb.contains("single") || albType == "single"
                            if (isComp) {
                                score += 8000
                            } else if (isSingle) {
                                score += 5000 // Penalize single release so full movie soundtrack album is prioritized
                            } else {
                                score -= 400
                                if (alb.contains("soundtrack") || alb.contains("original motion picture")) {
                                    score -= 800
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

                        val itemLanguage = bestItem.optString("language").ifBlank { more.optString("language") }.lowercase().trim()
                        val itemAlbum = unescapeHtml(more.optString("album").ifBlank { bestItem.optString("album") })

                        val songItem = Song(
                            id = bestItem.optString("id"),
                            title = rawTitle,
                            artist = artist,
                            artworkUrl = image,
                            duration = durationSec * 1000L,
                            playCount = playCount,
                            language = itemLanguage,
                            album = itemAlbum
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

    suspend fun searchAlbumsOnDevice(query: String, limit: Int = 8): List<AlbumItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getAlbumResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=$limit&p=1"

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
            if (!text.startsWith("{")) return@withContext emptyList()
            val root = org.json.JSONObject(text)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()

            val albums = mutableListOf<AlbumItem>()
            val seenKeys = mutableSetOf<String>()

            for (i in 0 until results.length()) {
                val it = results.getJSONObject(i)
                val id = it.optString("id")
                val rawTitle = unescapeHtml(it.optString("title"))
                if (rawTitle.isBlank()) continue

                val more = it.optJSONObject("more_info") ?: org.json.JSONObject()
                val year = it.optString("year").ifBlank { more.optString("year") }
                val songCount = more.optString("song_count").toIntOrNull() ?: 0

                val artistMap = more.optJSONObject("artistMap")
                val primaryArr = artistMap?.optJSONArray("primary_artists")
                var artist = if (primaryArr != null && primaryArr.length() > 0) {
                    (0 until primaryArr.length()).joinToString(", ") { idx -> unescapeHtml(primaryArr.getJSONObject(idx).optString("name")) }
                } else {
                    unescapeHtml(more.optString("music").ifBlank { it.optString("subtitle") })
                }
                if (artist.isBlank()) {
                    artist = "Original Soundtrack"
                }

                val language = it.optString("language").ifBlank { more.optString("language") }.lowercase().trim()
                val rawImage = it.optString("image")
                val cleanImage = cleanArtworkUrl(rawImage)

                val dedupeKey = "${rawTitle.lowercase()}_$year"
                if (seenKeys.add(dedupeKey)) {
                    albums.add(
                        AlbumItem(
                            id = id,
                            title = rawTitle,
                            artworkUrl = cleanImage,
                            year = year,
                            songCount = songCount,
                            artist = artist,
                            language = language
                        )
                    )
                }
            }
            albums
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "searchAlbumsOnDevice failed: ${e.message}")
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

    /**
     * Gapless Playback Engine: Preloads the immediate next media item from the active queue
     * directly into ExoPlayer's playlist (index 1). When the current track ends, ExoPlayer
     * seamlessly crossfades into the preloaded track with 0ms gap.
     */
    private fun preloadNextMediaItemInQueue(forceImmediate: Boolean = false) {
        preloadJob?.cancel()
        preloadJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                if (!forceImmediate) {
                    // Wait briefly so current song finishes its immediate setup & buffering
                    delay(1200)
                }

                val state = _uiState.value
                val currentSong = state.currentSong ?: return@launch
                val queue = state.queue
                val currentIdx = queue.indexOfFirst { it.id == currentSong.id }

                val nextSong: Song = when {
                    state.repeatMode == TrackRepeatMode.ONE -> currentSong
                    currentIdx >= 0 && currentIdx + 1 < queue.size -> queue[currentIdx + 1]
                    (state.repeatMode == TrackRepeatMode.ALL || state.playlistQueueActive) && queue.isNotEmpty() -> queue.first()
                    else -> null
                } ?: return@launch

                val isAlreadyQueued = withContext(Dispatchers.Main) {
                    val c = controller ?: return@withContext true
                    if (c.mediaItemCount > 1) {
                        val queuedId = runCatching { c.getMediaItemAt(1).mediaId }.getOrNull()
                        queuedId == nextSong.id
                    } else false
                }
                if (isAlreadyQueued) return@launch

                val streamUrl = resolveStreamUrl(nextSong)
                if (streamUrl.isBlank()) return@launch

                val uri = parseAudioUri(streamUrl)
                val mediaItem = MediaItem.Builder()
                    .setMediaId(nextSong.id)
                    .setUri(uri)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(nextSong.title)
                            .setArtist(nextSong.artist)
                            .setArtworkUri(
                                nextSong.displayArtworkUrl.takeIf { it.isNotBlank() }
                                    ?.let { android.net.Uri.parse(it) }
                            )
                            .build()
                    )
                    .build()

                withContext(Dispatchers.Main) {
                    val activeC = controller ?: return@withContext
                    if (activeC.mediaItemCount > 1) {
                        val currentQueuedId = runCatching { activeC.getMediaItemAt(1).mediaId }.getOrNull()
                        if (currentQueuedId == nextSong.id) return@withContext
                        activeC.removeMediaItem(1)
                    }
                    activeC.addMediaItem(mediaItem)
                    android.util.Log.d("MusicVM", "Preloaded next track into ExoPlayer: ${nextSong.title}")
                }

                try {
                    val context = getApplication<Application>()
                    com.shyan.dreamin.service.PreBufferManager.preBufferUpcomingStream(context, streamUrl, viewModelScope)
                } catch (_: Exception) {}
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "Failed to preload next track: ${e.message}")
            }
        }
    }

    /**
     * Synchronizes ExoPlayer's preloaded item with queue changes immediately.
     * When songs are queued next, reordered, or removed, any stale preloaded track
     * at index 1 is pruned from ExoPlayer and replaced with the true upcoming song.
     */
    private fun syncPreloadedMediaItem() {
        val state = _uiState.value
        val current = state.currentSong ?: return
        val queue = state.queue
        val currentIdx = queue.indexOfFirst { it.id == current.id }
        val nextSong: Song? = when {
            state.repeatMode == TrackRepeatMode.ONE -> current
            currentIdx >= 0 && currentIdx + 1 < queue.size -> queue[currentIdx + 1]
            (state.repeatMode == TrackRepeatMode.ALL || state.playlistQueueActive) && queue.isNotEmpty() -> queue.first()
            else -> null
        }

        val activeC = controller
        if (activeC != null && activeC.mediaItemCount > 1) {
            val queuedId = runCatching { activeC.getMediaItemAt(1).mediaId }.getOrNull()
            if (queuedId != null && queuedId != nextSong?.id) {
                activeC.removeMediaItem(1)
            }
        }

        if (nextSong != null) {
            preloadNextMediaItemInQueue(forceImmediate = true)
        }
    }

    private suspend fun resolveStreamUrl(song: Song): String {
        // 1. Try JioSaavn resolution pipeline (cache → local → API → search → backend)
        try {
            val url = com.shyan.dreamin.data.service.AudioStreamResolver.resolveStreamUrl(
                song = song,
                downloadRepo = downloadRepo,
                apiService = api
            )
            _uiState.update { it.copy(currentSongStreamSource = null) }
            return url
        } catch (e: IllegalStateException) {
            android.util.Log.w("MusicVM", "JioSaavn stream unavailable for '${song.title}', trying YouTube fallback")
        }

        // 2. YouTube Music InnerTube fallback (audio-only, no API key)
        val ytUrl = com.shyan.dreamin.data.service.YtMusicFallbackResolver.resolveStreamUrl(song)
        if (!ytUrl.isNullOrBlank()) {
            // Cache under the song ID so subsequent plays hit the LRU cache
            com.shyan.dreamin.data.service.AudioStreamResolver.putCachedStreamUrl(song.id, ytUrl)
            _uiState.update { it.copy(currentSongStreamSource = "youtube") }
            android.util.Log.d("MusicVM", "YouTube fallback stream resolved for '${song.title}'")
            return ytUrl
        }

        _uiState.update { it.copy(currentSongStreamSource = null) }
        throw IllegalStateException("Track unavailable: '${song.title}' could not be streamed from JioSaavn or YouTube.")
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
        val isPlaylistActive = fromPlaylist || (preserveQueue && _uiState.value.playlistQueueActive)
        val isAlreadyInQueue = _uiState.value.queue.any { it.id == song.id }

        // Optimistic UI: update song + state immediately so artwork/title swap is instant
        _uiState.update {
            val newQueue = when {
                preserveQueue || (isAlreadyInQueue && isPlaylistActive) -> {
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
                else -> {
                    // Non-playlist song: reset queue to this single song; YouTube recommendations will populate Up Next
                    listOf(song)
                }
            }
            it.copy(
                currentSong = song,
                queue = newQueue,
                playbackState = PlaybackState.Loading,
                currentSongIsFavorite = it.favorites.any { s -> s.id == song.id },
                playlistQueueActive = isPlaylistActive,
                userQueuedSongIds = it.userQueuedSongIds.filter { qId -> qId != song.id },
                currentSongStreamSource = null
            )
        }
        _progress.value = PlaybackProgress(0L, 0L)
        extractDominantColor(song.displayArtworkUrl)
        loadLyricsForCurrentSong()

        // Resolve official original movie soundtrack artwork
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val official = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                if (!official.isNullOrBlank() && official != song.artworkUrl) {
                    updateSongArtworkAcrossApp(song.id, official)
                    extractDominantColor(official)
                }
            } catch (_: Exception) {}
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
                        fetchUpNext(song.id)
                    }
                }
                fetchRecommendations(song.id)
                triggerSmartQueuePrefetch(song.id)
                preloadNextMediaItemInQueue()
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

        _uiState.update { state ->
            fun shouldUpdate(s: Song?): Boolean = s != null && s.id == songId

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

        if (_uiState.value.currentSong?.id == songId) {
            extractDominantColor(officialPoster)
        }

        // Persist to Room database so playlists, favorites, history, and offline library reflect updated posters!
        viewModelScope.launch(Dispatchers.IO) {
            try {
                playlistRepo.updateSongArtwork(songId, officialPoster)
                downloadRepo.updateArtwork(songId, officialPoster)
                historyRepo.updateArtwork(songId, officialPoster)
                favoritesRepo.updateArtwork(songId, officialPoster)
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

    fun openAlbum(album: AlbumItem) {
        _uiState.update {
            it.copy(
                selectedAlbum = AlbumDetail(
                    id = album.id,
                    title = album.title,
                    artist = album.artist,
                    artworkUrl = album.artworkUrl,
                    year = album.year,
                    songCount = album.songCount,
                    language = album.language,
                    isLoading = true
                )
            )
        }

        albumJob?.cancel()
        albumJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = "https://www.jiosaavn.com/api.php?__call=content.getAlbumDetails&_format=json&_marker=0&api_version=4&ctx=web6dot0&albumid=${album.id}"
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .build()
                val resp = NetworkService.httpClient.newCall(req).execute()
                val text = resp.body?.string().orEmpty().trim()
                if (!text.startsWith("{")) {
                    _uiState.update { it.copy(selectedAlbum = it.selectedAlbum?.copy(isLoading = false)) }
                    return@launch
                }
                val root = org.json.JSONObject(text)
                val songList = root.optJSONArray("list") ?: org.json.JSONArray()
                val songs = mutableListOf<Song>()
                val albumTitle = unescapeHtml(root.optString("title").ifBlank { album.title })
                val albumYear = root.optString("year").ifBlank { album.year }
                val albumImage = cleanArtworkUrl(root.optString("image").ifBlank { album.artworkUrl })

                for (i in 0 until songList.length()) {
                    val it = songList.getJSONObject(i)
                    val rawTitle = unescapeHtml(it.optString("title"))
                    if (rawTitle.isBlank()) continue

                    val more = it.optJSONObject("more_info") ?: org.json.JSONObject()
                    val durationSec = more.optString("duration").toLongOrNull() ?: 0L
                    val image = cleanArtworkUrl(it.optString("image").ifBlank { albumImage })
                    val playCount = it.optString("play_count").toLongOrNull()
                        ?: (more.optString("play_count").toLongOrNull() ?: 0L)

                    val artistMap = more.optJSONObject("artistMap")
                    val primaryArr = artistMap?.optJSONArray("primary_artists")
                    var artist = if (primaryArr != null && primaryArr.length() > 0) {
                        (0 until primaryArr.length()).joinToString(", ") { idx -> unescapeHtml(primaryArr.getJSONObject(idx).optString("name")) }
                    } else unescapeHtml(more.optString("singers").ifBlank { it.optString("subtitle") })

                    if (artist.isBlank()) {
                        val music = unescapeHtml(more.optString("music"))
                        artist = if (music.isNotBlank()) music else album.artist.ifBlank { "Original Soundtrack" }
                    }

                    val itemLanguage = it.optString("language").ifBlank { more.optString("language") }.lowercase().trim()

                    val songItem = Song(
                        id = it.optString("id"),
                        title = rawTitle,
                        artist = artist,
                        artworkUrl = image,
                        duration = durationSec * 1000L,
                        playCount = playCount,
                        language = itemLanguage,
                        album = albumTitle
                    )
                    songs.add(songItem)
                }

                _uiState.update {
                    it.copy(
                        selectedAlbum = it.selectedAlbum?.copy(
                            title = albumTitle,
                            year = albumYear,
                            artworkUrl = albumImage,
                            songCount = songs.size,
                            songs = songs,
                            isLoading = false
                        )
                    )
                }
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "Failed to load album details: ${e.message}")
                _uiState.update { it.copy(selectedAlbum = it.selectedAlbum?.copy(isLoading = false)) }
            }
        }
    }

    fun closeAlbum() {
        albumJob?.cancel()
        _uiState.update { it.copy(selectedAlbum = null) }
    }

    fun playAlbum(album: AlbumDetail, startSong: Song? = null) {
        if (album.songs.isNotEmpty()) {
            val songToPlay = startSong ?: album.songs.first()
            _uiState.update { it.copy(queue = album.songs, playlistQueueActive = false) }
            playSong(songToPlay, fromPlaylist = false, preserveQueue = true)
        } else if (startSong != null) {
            playSong(startSong)
        }
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
                    // If recommendations are currently fetching, wait briefly for them
                    if (_uiState.value.isFetchingUpNext) {
                        for (i in 0..15) {
                            delay(150)
                            val updatedQueue = _uiState.value.queue
                            val updatedIdx = updatedQueue.indexOfFirst { it.id == current.id }
                            if (updatedIdx >= 0 && updatedIdx + 1 < updatedQueue.size) {
                                withContext(Dispatchers.Main) {
                                    playSong(updatedQueue[updatedIdx + 1], fromPlaylist = false, preserveQueue = true)
                                }
                                return@launch
                            }
                        }
                    }

                    val tracks = com.shyan.dreamin.data.service.YouTubeRadioService.fetchRadioRecommendations(current)
                    for (track in tracks.take(8)) {
                        val cleanT = track.title.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
                        val firstArtist = track.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
                        val cand = searchOnDevice("$cleanT $firstArtist", limit = 1).firstOrNull()
                            ?: searchOnDevice(cleanT, limit = 1).firstOrNull()
                        if (cand != null && cand.id != current.id && OfficialSongFilter.isOfficial(cand, rejectHindi = false)) {
                            val finalCand = if (track.artworkUrl.isNotBlank() && (cand.artworkUrl.isBlank() || cand.artworkUrl.contains("150x150") || cand.artworkUrl.contains("50x50") || cand.artworkUrl.contains("default"))) {
                                cand.copy(artworkUrl = track.artworkUrl, album = track.album.ifBlank { cand.album })
                            } else cand
                            withContext(Dispatchers.Main) {
                                playSong(finalCand, fromPlaylist = false, preserveQueue = true)
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

                    // Failsafe: Never leave player in dead unhandled STATE_ENDED that drops foreground notification
                    withContext(Dispatchers.Main) {
                        controller?.seekTo(0L)
                        controller?.play()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Continuous autoplay radio error: ${e.message}")
                    withContext(Dispatchers.Main) {
                        controller?.seekTo(0L)
                        controller?.play()
                    }
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
        syncPreloadedMediaItem()
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
        syncPreloadedMediaItem()
    }

    fun removeFromQueue(song: Song) {
        _uiState.update { state ->
            state.copy(
                queue = state.queue.filter { s -> s.id != song.id },
                userQueuedSongIds = state.userQueuedSongIds.filter { id -> id != song.id }
            )
        }
        syncPreloadedMediaItem()
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
        syncPreloadedMediaItem()
    }

    fun clearQueue() {
        _uiState.update { state ->
            val current = state.currentSong
            state.copy(
                queue = if (current != null) listOf(current) else emptyList(),
                userQueuedSongIds = emptyList()
            )
        }
        syncPreloadedMediaItem()
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
        syncPreloadedMediaItem()
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
        playSong(session.song)
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
                searchResults = if (trimmed.isEmpty()) emptyList() else it.searchResults,
                searchAlbumResults = if (trimmed.isEmpty()) emptyList() else it.searchAlbumResults
            )
        }
        searchJob?.cancel()
        if (trimmed.isNotEmpty()) {
            searchJob = viewModelScope.launch {
                delay(220)
                try {
                    val songsDeferred = async(Dispatchers.IO) {
                        var songs = searchOnDevice(trimmed, page = 1)
                        if (songs.isEmpty() && didYouMean != null) {
                            val altSongs = searchOnDevice(didYouMean, page = 1)
                            if (altSongs.isNotEmpty()) {
                                songs = altSongs
                            }
                        }
                        songs
                    }
                    val albumsDeferred = async(Dispatchers.IO) {
                        searchAlbumsOnDevice(trimmed, limit = 8)
                    }

                    val rawSongs = songsDeferred.await()
                    val albums = albumsDeferred.await()

                    val rankedSongs = withContext(Dispatchers.Default) {
                        com.shyan.dreamin.data.recommendation.IntelliMatchEngine.fuzzyRankSearchResults(trimmed, rawSongs)
                    }
                    if (trimmed.length >= 2) userPrefs.addRecentSearch(trimmed)
                    _uiState.update {
                        it.copy(
                            searchResults = rankedSongs,
                            searchAlbumResults = albums,
                            hasMoreSearchResults = rankedSongs.size >= 15,
                            isSearching = false
                        )
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    android.util.Log.e("MusicVM", "Search failed: ${e.javaClass.simpleName}: ${e.message}")
                    _uiState.update {
                        it.copy(
                            searchResults = emptyList(),
                            searchAlbumResults = emptyList(),
                            isSearching = false,
                            searchError = "Couldn't reach search. Check your connection."
                        )
                    }
                }
            }
        } else {
            _uiState.update {
                it.copy(
                    isSearching = false,
                    searchResults = emptyList(),
                    searchAlbumResults = emptyList(),
                    didYouMeanQuery = null
                )
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
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearchActive = false,
                isSearching = false,
                searchResults = emptyList(),
                searchAlbumResults = emptyList(),
                didYouMeanQuery = null
            )
        }
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
            val cached = userPrefs.cachedChart.first()
            val hasStaleJunk = cached.any { it.title.contains("Scooty", ignoreCase = true) || it.title.contains("Don'u", ignoreCase = true) }
            val hasValidCache = cached.isNotEmpty() && !hasStaleJunk && cached.any { it.artist.isNotBlank() }

            if (forceRefresh) {
                _uiState.update { it.copy(isLoadingChart = true) }
            } else if (hasValidCache) {
                _uiState.update { it.copy(trendingCharts = cached, isLoadingChart = false) }
                prefetchTopTrendingArtworks(cached)
                // Defer background Apple Music chart sync by 10s to ensure 120 FPS startup smoothness
                delay(10000L)
            } else {
                _uiState.update { it.copy(isLoadingChart = true) }
            }

            // 1. Fetch Real-Time Live Top 50 Chart from Apple Music Official Feed (bounded concurrency)
            val liveChartSongs = mutableListOf<Song>()
            try {
                val pairs = com.shyan.dreamin.data.service.AppleChartsService.fetchLiveAppleTopCharts()
                val chartSemaphore = Semaphore(2)
                val deferredSearches = pairs.take(20).map { (t, a) ->
                    async {
                        chartSemaphore.withPermit {
                            val cleanT = t.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
                            val firstArtist = a.split(",").firstOrNull()?.split("&")?.firstOrNull()?.trim() ?: ""
                            searchOnDevice("$cleanT $firstArtist", limit = 1, targetLanguage = "tamil")
                        }
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
                // Proactively resolve official movie posters for top 6 charts throttled
                val posterSemaphore = Semaphore(2)
                finalTrending.take(6).forEach { chartSong ->
                    launch(Dispatchers.IO) {
                        try {
                            posterSemaphore.withPermit {
                                if (chartSong.artworkUrl.isBlank() || chartSong.artworkUrl.contains("default") || com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(chartSong) == null) {
                                    val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(chartSong)
                                    if (!poster.isNullOrBlank()) {
                                        updateSongArtworkAcrossApp(chartSong.id, poster)
                                    }
                                }
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
            val tracks = com.shyan.dreamin.data.service.YouTubeRadioService.fetchRadioRecommendations(currentSong)
            if (tracks.isNotEmpty()) {
                val deferredSearches = tracks.take(limit).map { track ->
                    async {
                        val cleanT = track.title.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
                        val firstArtist = track.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
                        val cand = searchOnDevice("$cleanT $firstArtist", limit = 1).firstOrNull()
                            ?: searchOnDevice(cleanT, limit = 1).firstOrNull()
                        if (cand != null && cand.id != currentSong.id && OfficialSongFilter.isOfficial(cand, rejectHindi = false)) {
                            val finalCand = if (track.artworkUrl.isNotBlank() && (cand.artworkUrl.isBlank() || cand.artworkUrl.contains("150x150") || cand.artworkUrl.contains("50x50") || cand.artworkUrl.contains("default"))) {
                                cand.copy(artworkUrl = track.artworkUrl, album = track.album.ifBlank { cand.album })
                            } else cand
                            finalCand
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

                    // Preload the next item in ExoPlayer if it only has 1 item
                    preloadNextMediaItemInQueue()

                    // Pre-buffer the immediate upcoming track for seamless zero-gap transitions
                    finalQueueTracks.firstOrNull()?.let { upcomingSong ->
                        launch(Dispatchers.IO) {
                            try {
                                val stream = resolveStreamUrl(upcomingSong)
                                if (stream.isNotBlank()) {
                                    val context = getApplication<Application>()
                                    com.shyan.dreamin.service.PreBufferManager.preBufferUpcomingStream(context, stream, viewModelScope)
                                }
                            } catch (_: Exception) {}
                        }
                    }

                    finalQueueTracks.forEach { qSong ->
                        launch(Dispatchers.IO) {
                            try {
                                if (qSong.artworkUrl.isBlank() || qSong.artworkUrl.contains("default") || com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(qSong) == null) {
                                    val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(qSong)
                                    if (!poster.isNullOrBlank()) {
                                        updateSongArtworkAcrossApp(qSong.id, poster)
                                    }
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
                val fallback = searchOnDevice("$primaryArtist hits", limit = 10, targetLanguage = currentSong.language.ifBlank { "" })
                    OfficialSongFilter.cleanOfficialList(fallback, rejectHindi = true)
                        .filter { it.id != songId && !FeedbackEngine.isSuppressed(it) }
                }

                _uiState.update { it.copy(recommendations = finalRecs, recommendationSeedTitle = seedTitle) }
                finalRecs.forEach { recSong ->
                    launch(Dispatchers.IO) {
                        try {
                            if (recSong.artworkUrl.isBlank() || recSong.artworkUrl.contains("default") || com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(recSong) == null) {
                                val poster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(recSong)
                                if (!poster.isNullOrBlank()) {
                                    updateSongArtworkAcrossApp(recSong.id, poster)
                                }
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
        openPlaylistJob?.cancel()
        colorExtractJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        runCatching {
            getApplication<Application>().unregisterReceiver(mediaActionReceiver)
        }
        super.onCleared()
    }
}
