package com.shyan.dreamin.viewmodel

import android.app.Application
import android.content.ComponentName
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
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CancellationException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONObject

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

    private var isListenerAttached = false
    private var searchJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var openPlaylistJob: Job? = null
    private var colorExtractJob: Job? = null
    private var prefetchJob: Job? = null
    private var lyricsJob: Job? = null
    private var artistJob: Job? = null
    private val streamUrlCache = android.util.LruCache<String, String>(100)
    private val paletteColorCache = android.util.LruCache<String, Triple<Int, Int, Int>>(100)

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
        loadRecentlyPlayed()
        loadTopSongs()
        loadFavorites()
        loadStats()
        loadPlaylists()
        loadDownloads()

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
                // Proactively resolve official movie posters in background
                songs.forEach { song ->
                    launch(Dispatchers.IO) {
                        try {
                            val official = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                            if (!official.isNullOrBlank() && official != song.artworkUrl) {
                                updateSongArtworkAcrossApp(song.id, official)
                            }
                        } catch (_: Exception) {}
                    }
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

    fun importSpotifyPlaylist(url: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(spotifyImportState = SpotifyImportState.FetchingMetadata(url)) }

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

            processTrackMatching(details.title, details.coverUrl, details.tracks)
        }
    }

    private suspend fun processTrackMatching(title: String, coverUrl: String, tracks: List<com.shyan.dreamin.data.service.SpotifyImportedTrack>) {
        val langRegex = Regex("(?i)\\b(tamil|telugu|hindi|malayalam|kannada|punjabi|english)\\b")
        val playlistLang = langRegex.find(title)?.value?.lowercase() ?: "tamil"

        val total = tracks.size
        val matchedArray = arrayOfNulls<Song>(total)
        val progressCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val matchedCounter = java.util.concurrent.atomic.AtomicInteger(0)
        val semaphore = kotlinx.coroutines.sync.Semaphore(6)

        kotlinx.coroutines.coroutineScope {
            tracks.forEachIndexed { index, track ->
                launch {
                    semaphore.acquire()
                    try {
                        kotlinx.coroutines.delay((index % 6) * 30L)
                        val matched = matchSpotifyTrack(track, playlistLang)
                        if (matched != null) {
                            val finalArtwork = when {
                                track.artworkUrl.isNotBlank() -> track.artworkUrl
                                matched.artworkUrl.isNotBlank() -> matched.artworkUrl
                                else -> ""
                            }
                            val songWithArt = matched.copy(artworkUrl = finalArtwork)
                            matchedArray[index] = songWithArt
                            matchedCounter.incrementAndGet()
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("MusicVM", "Failed to match track: ${track.title} - ${e.message}")
                    } finally {
                        semaphore.release()
                        val currDone = progressCounter.incrementAndGet()
                        val currMatched = matchedCounter.get()
                        if (currDone % 4 == 0 || currDone == total) {
                            _uiState.update {
                                it.copy(
                                    spotifyImportState = SpotifyImportState.MatchingTracks(
                                        playlistTitle = title,
                                        coverUrl = coverUrl,
                                        currentTrackIndex = currDone,
                                        totalTracks = total,
                                        matchedCount = currMatched,
                                        currentTrackName = track.title
                                    )
                                )
                            }
                        }
                    }
                }
            }
        }

        val matchedSongs = matchedArray.filterNotNull()
        val unmatchedTracks = tracks.filterIndexed { index, _ -> matchedArray[index] == null }

        if (matchedSongs.isEmpty()) {
            _uiState.update {
                it.copy(spotifyImportState = SpotifyImportState.Error("No matching tracks could be found in the catalog."))
            }
            return
        }

        val newPlaylistId = playlistRepo.createPlaylist(name = title, coverUrl = coverUrl.takeIf { it.isNotBlank() })
        playlistRepo.addSongs(newPlaylistId, matchedSongs)

        _uiState.update {
            it.copy(
                spotifyImportState = SpotifyImportState.Success(
                    playlistId = newPlaylistId,
                    playlistTitle = title,
                    matchedCount = matchedSongs.size,
                    totalTracks = total,
                    coverUrl = coverUrl,
                    unmatchedTracks = unmatchedTracks
                )
            )
        }
    }

    private suspend fun matchSpotifyTrack(
        track: com.shyan.dreamin.data.service.SpotifyImportedTrack,
        playlistLanguage: String
    ): Song? = withContext(Dispatchers.IO) {
        val rawTitle = track.title
        val rawArtist = track.artist

        // 1. Language Detection from track title
        val langRegex = Regex("(?i)\\b(tamil|telugu|hindi|malayalam|kannada|punjabi|english)\\b")
        val trackLangMatch = langRegex.find(rawTitle)
        val targetLang = trackLangMatch?.value?.lowercase() ?: playlistLanguage.ifBlank { "tamil" }

        // 2. Multi-tier Clean Title Extraction:
        // A. Strip nested bracket structures repeatedly to prevent stray trailing quotes or brackets
        var bracketCleaned = rawTitle
        var prevBracketCleaned = ""
        while (prevBracketCleaned != bracketCleaned) {
            prevBracketCleaned = bracketCleaned
            bracketCleaned = bracketCleaned
                .replace(Regex("\\([^()]*\\)"), "")
                .replace(Regex("\\[[^\\[\\]]*\\]"), "")
                .replace(Regex("\\{[^{}]*\\}"), "")
        }
        val fullClean = bracketCleaned.replace(Regex("[\"\'“”‘’]"), "").trim().ifBlank { rawTitle.trim() }

        // B. Extract base title before subtitles (e.g. "Idhazhin Oram - The Innocence of Love" -> "Idhazhin Oram")
        val subtitleParts = fullClean.split(Regex("\\s+[-–—:]\\s+"))
        val cleanBaseTitle = subtitleParts.firstOrNull()?.trim()?.ifBlank { fullClean } ?: fullClean

        val primaryArtist = rawArtist.split(",", "&", "feat.", "ft.", "/", ";").firstOrNull()?.trim() ?: ""

        // 3. Multi-Pass Search Strategy
        // Pass 1: Direct Clean Base Title (matches catalog & soundtrack records immediately)
        var candidates = searchOnDevice(cleanBaseTitle, limit = 8)

        // Pass 2: Base Title + Primary Artist (for disambiguation if Pass 1 had no hits)
        if (candidates.isEmpty() && primaryArtist.isNotBlank()) {
            val query = "$cleanBaseTitle $primaryArtist".trim()
            candidates = searchOnDevice(query, limit = 8)
        }

        // Pass 3: Full Clean Title (if title had subtitle text that was actually relevant)
        if (candidates.isEmpty() && fullClean.length > cleanBaseTitle.length) {
            candidates = searchOnDevice(fullClean, limit = 8)
        }

        // Pass 4: Secondary Fallback: Server / YouTube Music Engine for Indie & Non-Catalog Releases
        if (candidates.isEmpty()) {
            try {
                val serverResp = api.search(cleanBaseTitle, page = 1, limit = 8)
                if (serverResp.results.isNotEmpty()) {
                    candidates = serverResp.results.filter { cand ->
                        OfficialSongFilter.isOfficial(cand, rejectHindi = false)
                    }
                }
            } catch (_: Exception) {}
        }
        if (candidates.isEmpty() && primaryArtist.isNotBlank()) {
            try {
                val serverResp = api.search("$cleanBaseTitle $primaryArtist", page = 1, limit = 8)
                if (serverResp.results.isNotEmpty()) {
                    candidates = serverResp.results.filter { cand ->
                        OfficialSongFilter.isOfficial(cand, rejectHindi = false)
                    }
                }
            } catch (_: Exception) {}
        }

        if (candidates.isEmpty()) return@withContext null

        // 4. Multi-Factor Scoring with Punctuation-Agnostic Artist Matching & Relaxed Duration
        val spotifyArtistsNormalized = rawArtist.lowercase()
            .split(",", "&", "feat.", "ft.", "/", ";")
            .map { it.replace(Regex("[^a-z0-9]"), "").trim() }
            .filter { it.length >= 2 }

        val cleanSpotifyBaseKey = cleanBaseTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
        val cleanSpotifyFullKey = fullClean.lowercase().replace(Regex("[^a-z0-9]"), "")

        val bestMatch = candidates.maxByOrNull { candidate ->
            var score = 1000
            val candTitle = candidate.title.lowercase()
            val candCleanKey = candidate.displayTitle.lowercase().replace(Regex("[^a-z0-9]"), "")
            val candArtistNormalized = candidate.artist.lowercase().replace(Regex("[^a-z0-9]"), "")

            // Title Match Score
            if (candCleanKey == cleanSpotifyBaseKey || candCleanKey == cleanSpotifyFullKey) {
                score += 700
            } else if (candCleanKey.contains(cleanSpotifyBaseKey) || cleanSpotifyBaseKey.contains(candCleanKey)) {
                score += 450
            }

            // Language Penalty & Bonus
            val otherLanguages = listOf("telugu", "hindi", "kannada", "malayalam", "punjabi").filter { it != targetLang }
            for (other in otherLanguages) {
                if (candTitle.contains("($other)") || candTitle.contains("[$other]")) {
                    score -= 300
                }
            }
            if (candTitle.contains("($targetLang)") || candTitle.contains("[$targetLang]")) {
                score += 350
            }

            // Punctuation-Agnostic Artist Overlap Score (e.g. "gvprakashkumar" matches "g.v. prakash kumar")
            var artistHits = 0
            for (sa in spotifyArtistsNormalized) {
                if (candArtistNormalized.contains(sa) || sa.contains(candArtistNormalized)) {
                    artistHits++
                    score += 200
                }
            }
            if (artistHits > 0) score += 250

            // Duration Match Score (Relaxed ±25s tolerance for soundtrack/movie master cuts)
            if (track.durationMs > 0 && candidate.duration > 0) {
                val diffSec = kotlin.math.abs(track.durationMs - candidate.duration) / 1000
                if (diffSec <= 6) score += 250
                else if (diffSec <= 20) score += 120
                else if (diffSec > 60) score -= 300
            }

            score
        }

        return@withContext bestMatch
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
            val playbackState = when {
                state == Player.STATE_BUFFERING -> PlaybackState.Loading
                state == Player.STATE_READY && controller?.isPlaying == true -> PlaybackState.Playing
                state == Player.STATE_READY -> PlaybackState.Paused
                state == Player.STATE_ENDED -> {
                    viewModelScope.launch { playNext(isAutoEnd = true) }
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
                    } else state.currentSong
                )
            }
            _progress.value = PlaybackProgress(0L, duration)
            _uiState.value.currentSong?.let { activeSong ->
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

        override fun onPlayerError(error: PlaybackException) {
            android.util.Log.e("MusicVM", "ExoPlayer error: ${error.message}", error)
            val current = _uiState.value.currentSong ?: return
            streamUrlCache.remove(current.id)
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

    fun prefetchQueueArtworks(queue: List<Song>, currentSongId: String?) {
        viewModelScope.launch(Dispatchers.IO) {
            val context = getApplication<Application>()
            val currentIndex = queue.indexOfFirst { it.id == currentSongId }
            val nextSongs = if (currentIndex >= 0) {
                queue.drop(currentIndex + 1).take(4)
            } else {
                queue.take(4)
            }
            nextSongs.forEach { song ->
                val artUrl = song.displayArtworkUrl
                if (artUrl.isNotBlank()) {
                    val req = coil.request.ImageRequest.Builder(context)
                        .data(artUrl)
                        .memoryCachePolicy(coil.request.CachePolicy.ENABLED)
                        .build()
                    coil.Coil.imageLoader(context).enqueue(req)
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
    private val compilationRegex = Regex("(?i)\\b(fire & desire|fire and desire|desire|best of|this is|top |hits|vol\\b|vol\\.|volume|love notes|collection|playlist|raga collective|kondattam|selected|radio|superhit|compilation|greatest hits|evergreen|melody|melodies|latest|essential|party|workout|for the road|romance|mashup|area boys|konjam|thamizh music|pure love|soulful|feel good|night drive|summer vibes|chill tracks|unlimited|hits of)\\b")

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
        targetLanguage: String = ""
    ): List<Song> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=$limit&p=$page"
        
        for (attempt in 0..1) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .build()
                val resp = NetworkService.httpClient.newCall(req).execute()
                val text = resp.body?.string().orEmpty()
                val root = org.json.JSONObject(text)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val rawItems = mutableListOf<org.json.JSONObject>()
                    for (i in 0 until results.length()) {
                        val it = results.getJSONObject(i)
                        val itemLang = it.optString("language").ifBlank { it.optJSONObject("more_info")?.optString("language") ?: "" }.lowercase().trim()
                        if (targetLanguage.isNotBlank() && itemLang.isNotBlank() && !itemLang.equals(targetLanguage, ignoreCase = true)) {
                            continue
                        }
                        rawItems.add(it)
                    }

                    val grouped = rawItems.groupBy {
                        it.optString("title").replace(Regex("(?i)\\s*\\(?\\s*from\\s+[\"\'\u201c\u2018].*?[\"\'\u201d\u2019]?\\s*\\)?$"), "")
                            .replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                    }
                    val songs = mutableListOf<Song>()
                    for ((_, items) in grouped) {
                        // 1. Detect movie from title tags: (From "Movie"), (From 'Movie'), (From Movie), [From "Movie"], etc.
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

                        // 2. Select best candidate release from the provider by prioritizing genuine movie soundtrack album artwork
                        val bestItem = items.minByOrNull { item ->
                            val alb = unescapeHtml(item.optJSONObject("more_info")?.optString("album", "")?.trim() ?: "").lowercase()
                            val itemImage = item.optString("image", "").lowercase()
                            val isEditorialOrPlaylist = itemImage.contains("/editorial/") || itemImage.contains("/playlist/") || itemImage.contains("/818/") || itemImage.contains("/888/") || itemImage.contains("compilation")

                            var score = 1000
                            val isComp = compilationRegex.containsMatchIn(alb)
                            if (isComp) {
                                score += 8000 // Reject compilation releases (e.g. Fire & Desire, Hits of...)
                            } else {
                                score -= 400
                                if (alb.contains("soundtrack") || alb.contains("original motion picture")) {
                                    score -= 600
                                }
                            }
                            if (movieDetected.isNotBlank() && (alb == movieDetected || alb.contains(movieDetected) || movieDetected.contains(alb))) {
                                score -= 1000 // Exact movie album match!
                            }
                            if (!isEditorialOrPlaylist) {
                                score -= 200 // Real original album cover (not generic playlist collage)
                            }
                            score
                        } ?: items.first()

                        val rawTitle = unescapeHtml(bestItem.optString("title"))
                        if (noiseRegex.containsMatchIn(rawTitle)) continue
                        val more = bestItem.optJSONObject("more_info") ?: org.json.JSONObject()
                        val durationSec = more.optString("duration").toLongOrNull() ?: 0L
                        if (durationSec in 1..74 || durationSec > 540) continue

                        val image = cleanArtworkUrl(bestItem.optString("image"))
                        val artistMap = more.optJSONObject("artistMap")
                        val primaryArr = artistMap?.optJSONArray("primary_artists")
                        val artist = if (primaryArr != null && primaryArr.length() > 0) {
                            (0 until primaryArr.length()).joinToString(", ") { unescapeHtml(primaryArr.getJSONObject(it).optString("name")) }
                        } else unescapeHtml(more.optString("singers").ifBlank { "" })
                        val songItem = Song(
                            id = bestItem.optString("id"),
                            title = rawTitle,
                            artist = artist,
                            artworkUrl = image,
                            duration = durationSec * 1000L
                        )
                        if (OfficialSongFilter.isOfficial(songItem)) {
                            songs.add(songItem)
                        }
                    }
                    if (songs.isNotEmpty()) return@withContext songs.take(limit)
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
            serverResp.results
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun extractDominantColor(artworkUrl: String) {
        if (artworkUrl.isBlank()) return

        // 1. Instant 0ms RAM cache hit: apply colors immediately without background decoding
        paletteColorCache.get(artworkUrl)?.let { (cachedDom, cachedSec, cachedAcc) ->
            _uiState.update {
                it.copy(
                    dominantColor = cachedDom,
                    secondaryColor = cachedSec,
                    accentColor = cachedAcc
                )
            }
            return
        }

        colorExtractJob?.cancel()
        colorExtractJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val context = getApplication<Application>()
                val loader = coil.Coil.imageLoader(context)
                val request = coil.request.ImageRequest.Builder(context)
                    .data(artworkUrl)
                    .allowHardware(false)
                    .build()
                val result = (loader.execute(request) as? coil.request.SuccessResult)?.drawable
                val bitmap = (result as? android.graphics.drawable.BitmapDrawable)?.bitmap
                if (bitmap != null) {
                    val palette = androidx.palette.graphics.Palette.from(bitmap).generate()
                    val dominant = palette.getVibrantColor(
                        palette.getDominantColor(
                            palette.getMutedColor(0xFF6C5CE7.toInt())
                        )
                    )
                    val secondary = palette.getDarkVibrantColor(
                        palette.getMutedColor(
                            palette.getDarkMutedColor(0xFF8E44AD.toInt())
                        )
                    )
                    val accent = palette.getLightVibrantColor(
                        palette.getLightMutedColor(0xFF00CEC9.toInt())
                    )

                    paletteColorCache.put(artworkUrl, Triple(dominant, secondary, accent))

                    _uiState.update {
                        it.copy(
                            dominantColor = dominant,
                            secondaryColor = secondary,
                            accentColor = accent
                        )
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun triggerSmartQueuePrefetch(currentSongId: String?) {
        prefetchJob?.cancel()
        prefetchJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                delay(2500) // Delay prefetch so it never starves current song's initial buffer
                val queue = _uiState.value.queue
                val idx = if (currentSongId != null) queue.indexOfFirst { it.id == currentSongId } else -1
                val upNextTracks = if (idx >= 0) queue.drop(idx + 1).take(1) else queue.take(1)

                for (song in upNextTracks) {
                    val cachedPath = downloadRepo.getPrefetchedOrDownloadedPath(song.id)
                    if (cachedPath == null) {
                        val streamUrl = resolveStreamUrl(song)
                        if (!streamUrl.startsWith("file://") && !streamUrl.startsWith("/")) {
                            downloadRepo.prefetchSong(song, streamUrl)
                        }
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private fun prefetchNextSongStream(nextSong: Song?) {
        triggerSmartQueuePrefetch(nextSong?.id)
    }

    private suspend fun resolveStreamUrl(song: Song): String = withContext(Dispatchers.IO) {
        val songId = song.id
        streamUrlCache.get(songId)?.let { return@withContext it }

        // Local offline download & smart prefetch check (0ms instant playback)
        val localPath = downloadRepo.getPrefetchedOrDownloadedPath(songId)
        if (localPath != null) {
            streamUrlCache.put(songId, localPath)
            return@withContext localPath
        }

        // Attempt 1: Direct on-device JioSaavn API by PID
        try {
            val detailsUrl = "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0&_format=json&pids=$songId"
            val req1 = okhttp3.Request.Builder()
                .url(detailsUrl)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val text1 = NetworkService.httpClient.newCall(req1).execute().use { it.body?.string().orEmpty() }
            val details = JSONObject(text1)
            val encUrl = details.optJSONObject(songId)?.optString("encrypted_media_url", "") ?: ""
            if (encUrl.isNotBlank()) {
                val enc = URLEncoder.encode(encUrl, "UTF-8")
                val authUrl = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=$enc&bitrate=320&api_version=4&_format=json&ctx=web6dot0&_marker=0"
                val req2 = okhttp3.Request.Builder()
                    .url(authUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()
                val text2 = NetworkService.httpClient.newCall(req2).execute().use { it.body?.string().orEmpty() }
                val authResp = JSONObject(text2)
                val streamUrl = authResp.optString("auth_url", "")
                if (streamUrl.isNotBlank() && streamUrl != "false") {
                    streamUrlCache.put(songId, streamUrl)
                    return@withContext streamUrl
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "Direct PID stream resolution for $songId failed (${e.message}), attempting search fallback...")
        }

        // Attempt 2: Direct Search Fallback (resolves songs where ID is non-numeric/YouTube/external/Spotify)
        try {
            val queries = mutableListOf(
                "${song.displayTitle} ${song.artist}".trim(),
                song.displayTitle.trim(),
                "${song.displayTitle.replace(Regex("(?i)eeshu|eshu"), "eesu").replace(Regex("(?i)sh"), "s")} ${song.artist}".trim(),
                song.displayTitle.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
            ).distinct().filter { it.length >= 2 }

            for (qText in queries) {
                val encodedQuery = URLEncoder.encode(qText, "UTF-8")
                val searchUrl = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&cc=in&p=1&n=8&q=$encodedQuery"
                val reqSearch = okhttp3.Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()
                val textSearch = NetworkService.httpClient.newCall(reqSearch).execute().use { it.body?.string().orEmpty() }
                val searchResp = JSONObject(textSearch)
                val results = searchResp.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    for (i in 0 until results.length()) {
                        val match = results.getJSONObject(i)
                        val encUrl = match.optString("encrypted_media_url", "")
                        if (encUrl.isNotBlank()) {
                            val enc = URLEncoder.encode(encUrl, "UTF-8")
                            val authUrl = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=$enc&bitrate=320&api_version=4&_format=json&ctx=web6dot0&_marker=0"
                            val reqAuth = okhttp3.Request.Builder()
                                .url(authUrl)
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                                .header("Referer", "https://www.jiosaavn.com/")
                                .build()
                            val textAuth = NetworkService.httpClient.newCall(reqAuth).execute().use { it.body?.string().orEmpty() }
                            val authResp = JSONObject(textAuth)
                            val streamUrl = authResp.optString("auth_url", "")
                            if (streamUrl.isNotBlank() && streamUrl != "false") {
                                streamUrlCache.put(songId, streamUrl)
                                return@withContext streamUrl
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "Search fallback failed for ${song.title}: ${e.message}")
        }

        // Attempt 3: Backend server resolution fallback (reliable host network connection)
        try {
            val playResp = api.recordPlay(id = songId, artist = song.artist, title = song.title)
            val streamUrl = playResp.streamUrl
            if (!streamUrl.isNullOrBlank()) {
                streamUrlCache.put(songId, streamUrl)
                return@withContext streamUrl
            }
        } catch (e: Exception) {
            android.util.Log.e("MusicVM", "Backend stream fallback failed for $songId: ${e.message}")
        }

        throw IllegalStateException("Unable to resolve stream for song ${song.title} ($songId)")
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

        // Resolve 100% official original movie soundtrack artwork in background
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val official = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                if (!official.isNullOrBlank() && official != song.artworkUrl) {
                    updateSongArtworkAcrossApp(song.id, official)
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

                controller?.apply {
                    setMediaItem(mediaItem)
                    prepare()
                    play()
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

                // Real-time live official movie poster resolution
                launch(Dispatchers.IO) {
                    try {
                        val officialPoster = com.shyan.dreamin.data.service.OfficialArtworkService.resolveOfficialMoviePoster(song)
                        if (!officialPoster.isNullOrBlank()) {
                            updateSongArtworkAcrossApp(song.id, officialPoster)
                            extractDominantColor(officialPoster)
                        }
                    } catch (_: Exception) {}
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
        _uiState.update { state ->
            val newCurrentSong = if (state.currentSong?.id == songId) {
                state.currentSong.copy(artworkUrl = officialPoster)
            } else state.currentSong

            val newQueue = state.queue.map { if (it.id == songId) it.copy(artworkUrl = officialPoster) else it }
            val newTrending = state.trendingCharts.map { if (it.id == songId) it.copy(artworkUrl = officialPoster) else it }
            val newRecs = state.recommendations.map { if (it.id == songId) it.copy(artworkUrl = officialPoster) else it }
            val newSearchResults = state.searchResults.map { if (it.id == songId) it.copy(artworkUrl = officialPoster) else it }
            val newRecentlyPlayed = state.recentlyPlayed.map { if (it.id == songId) it.copy(artworkUrl = officialPoster) else it }
            val newTopSongs = state.topSongs.map { if (it.id == songId) it.copy(artworkUrl = officialPoster) else it }
            val newOpenPlaylistSongs = state.openPlaylistSongs.map { if (it.id == songId) it.copy(artworkUrl = officialPoster) else it }
            val newFavorites = state.favorites.map { if (it.id == songId) it.copy(artworkUrl = officialPoster) else it }

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

        // Persist to Room database so playlists and offline library permanently reflect authentic movie posters!
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
            _uiState.update { it.copy(downloadingSongIds = it.downloadingSongIds + song.id) }
            try {
                val streamUrl = resolveStreamUrl(song)
                val result = downloadRepo.downloadSong(song, streamUrl)
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

        // Track playback completion / skip in FeedbackEngine
        val pos = controller?.currentPosition ?: 0L
        val dur = controller?.duration ?: 0L
        FeedbackEngine.recordTrackEvent(current, pos, dur)

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
            if (!state.playlistQueueActive && remaining <= 2 && !_uiState.value.isFetchingUpNext) {
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
                                playSong(cand, fromPlaylist = false, preserveQueue = false)
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
                                playSong(artistHits.first(), fromPlaylist = false, preserveQueue = false)
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
            val currentIdx = queue.indexOfFirst { it.id == state.currentSong?.id }
            val insertAt = if (currentIdx >= 0) currentIdx + 1 else 0
            queue.removeAll { it.id == song.id }
            queue.add(insertAt.coerceAtMost(queue.size), song)
            state.copy(queue = queue.toList())
        }
    }

    fun playNext(song: Song) {
        _uiState.update { state ->
            val queue = state.queue.toMutableList()
            val currentIdx = queue.indexOfFirst { it.id == state.currentSong?.id }
            val insertAt = if (currentIdx >= 0) currentIdx + 1 else 0
            queue.removeAll { it.id == song.id }
            queue.add(insertAt.coerceAtMost(queue.size), song)
            state.copy(queue = queue.toList())
        }
    }

    fun removeFromQueue(song: Song) {
        _uiState.update { it.copy(queue = it.queue.filter { s -> s.id != song.id }) }
    }

    fun restoreToQueue(song: Song, index: Int) {
        _uiState.update { state ->
            val queue = state.queue.toMutableList()
            if (queue.none { it.id == song.id }) {
                val insertIdx = index.coerceIn(0, queue.size)
                queue.add(insertIdx, song)
                state.copy(queue = queue.toList())
            } else {
                state
            }
        }
    }

    fun clearQueue() {
        _uiState.update { state ->
            val current = state.currentSong
            state.copy(queue = if (current != null) listOf(current) else emptyList())
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
        playSong(session.song)
    }

    fun activateSearch() {
        _uiState.update { it.copy(isSearchActive = true) }
    }

    fun setSearchQuery(query: String) {
        val trimmed = query.trim()
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchActive = true,
                searchPage = 1,
                hasMoreSearchResults = false,
                searchError = null,
                isSearching = trimmed.isNotEmpty(),
                searchResults = if (trimmed.isEmpty()) emptyList() else it.searchResults
            )
        }
        searchJob?.cancel()
        if (trimmed.isNotEmpty()) {
            searchJob = viewModelScope.launch {
                delay(220)
                try {
                    val songs = searchOnDevice(trimmed, page = 1)
                    if (trimmed.length >= 2) userPrefs.addRecentSearch(trimmed)
                    _uiState.update {
                        it.copy(
                            searchResults = songs,
                            hasMoreSearchResults = songs.size >= 15,
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
            _uiState.update { it.copy(isSearching = false, searchResults = emptyList()) }
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
                _uiState.update {
                    it.copy(
                        searchResults = it.searchResults + songs,
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
        _uiState.update { it.copy(searchQuery = "", isSearchActive = false, isSearching = false, searchResults = emptyList()) }
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
        streamUrlCache.evictAll()
        paletteColorCache.evictAll()
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
        if (artworkUrl.isBlank()) return
        val appContext = getApplication<Application>()
        colorExtractJob?.cancel()
        colorExtractJob = viewModelScope.launch {
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
        openPlaylistJob?.cancel()
        colorExtractJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
        runCatching {
            getApplication<Application>().unregisterReceiver(mediaActionReceiver)
        }
        super.onCleared()
    }
}
