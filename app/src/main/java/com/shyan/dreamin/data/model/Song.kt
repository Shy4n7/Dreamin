package com.shyan.dreamin.data.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName

private val FROM_SUFFIX_REGEX = Regex("""\s*[\(\[]?\s*[Ff][Rr][Oo][Mm]\s+.+[\)\]]?\s*$""")

@Immutable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    @SerializedName("artwork_url") val artworkUrl: String = "",
    val duration: Long = 0L,
    @SerializedName("play_count") val playCount: Long = 0L
) {
    val displayArtworkUrl: String get() {
        val cached = com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(this)
        if (!cached.isNullOrBlank()) return cached
        return resolvePoster(title, artworkUrl)
    }

    companion object {
        fun resolvePoster(title: String, rawUrl: String): String {
            if (rawUrl.isBlank()) return ""
            val highResUrl = rawUrl
                .replace(Regex("_\\d+x\\d+\\."), "_500x500.")
                .replace("50x50", "500x500")
                .replace("150x150", "500x500")
            return if (highResUrl.startsWith("http://")) "https://" + highResUrl.substring(7) else highResUrl
        }
    }
    val displayTitle: String get() {
        val withoutBrackets = title.replace(Regex("""\s*[\(\[].*?[\)\]]\s*$"""), "").trim()
        return if (withoutBrackets.isNotBlank()) withoutBrackets else title
    }

    val subtitleTag: String? get() {
        val m = Regex("""(?i)[\(\[]\s*(?:(?:from|movie)\s+["'“”‘]?(.*?)["'“”’]?|(the\s+[^()\[\]]+)|([^()\[\]]+))\s*[\)\]]""").find(title)
        return m?.let {
            val movie = it.groupValues[1].trim()
            val desc = it.groupValues[2].trim().ifBlank { it.groupValues[3].trim() }
            if (movie.isNotBlank()) "🎬 $movie"
            else if (desc.isNotBlank() && !desc.equals("audio", ignoreCase = true) && !desc.equals("official", ignoreCase = true)) desc
            else null
        }
    }
}

data class RegisterRequest(val name: String, val device_id: String = "")
data class SearchResponse(val results: List<Song> = emptyList())
data class ChartResponse(val songs: List<Song> = emptyList())
data class PlayResponse(
    @SerializedName("stream_url") val streamUrl: String? = null,
    @SerializedName("proxy_url") val proxyUrl: String? = null
)
data class UpNextResponse(val songs: List<Song> = emptyList())
data class RecommendResponse(val recommendations: List<Song> = emptyList())

enum class TrackRepeatMode { OFF, ONE, ALL }

@Immutable
data class ListeningStats(
    val songsThisWeek: Int = 0,
    val minutesThisWeek: Long = 0L,
    val topSongThisWeek: Song? = null,
    val topArtistThisWeek: String? = null
)

@Immutable
sealed class PlaybackState {
    object Idle : PlaybackState()
    object Loading : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}

@Immutable
data class PlaybackProgress(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

@Immutable
data class LyricLine(
    val timestampMs: Long,
    val text: String,
    val romanizedText: String = com.shyan.dreamin.data.util.IndicRomanizer.transliterateTamilToEnglish(text)
)

@Immutable
sealed class LyricsState {
    object Idle : LyricsState()
    object Loading : LyricsState()
    data class Success(val lines: List<LyricLine>, val isSynced: Boolean = true) : LyricsState()
    data class Plain(val text: String) : LyricsState()
    object NotFound : LyricsState()
}

@Immutable
data class ArtistProfile(
    val name: String,
    val artworkUrl: String = "",
    val topSongs: List<Song> = emptyList(),
    val albums: List<AlbumItem> = emptyList(),
    val bio: String = "",
    val isLoading: Boolean = false
)

@Immutable
data class AlbumItem(
    val id: String,
    val title: String,
    val artworkUrl: String = "",
    val year: String = "",
    val songCount: Int = 0
)

enum class DownloadStatus {
    NOT_DOWNLOADED,
    DOWNLOADING,
    DOWNLOADED,
    ERROR
}

@Immutable
data class PlayerUiState(
    val currentSong: Song? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val queue: List<Song> = emptyList(),
    val searchResults: List<Song> = emptyList(),
    val trendingCharts: List<Song> = emptyList(),
    val recommendations: List<Song> = emptyList(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isQueueVisible: Boolean = false,
    val dominantColor: Int = 0xFF6C5CE7.toInt(),
    val secondaryColor: Int = 0xFF8E44AD.toInt(),
    val accentColor: Int = 0xFF00CEC9.toInt(),
    val recommendationSeedTitle: String? = null,
    val isLoadingChart: Boolean = true,
    val userName: String? = null,  // null = DataStore not yet loaded; "" = first launch
    val isShuffle: Boolean = false,
    val repeatMode: TrackRepeatMode = TrackRepeatMode.OFF,
    val recentlyPlayed: List<Song> = emptyList(),
    val topSongs: List<Song> = emptyList(),
    val favorites: List<Song> = emptyList(),
    val currentSongIsFavorite: Boolean = false,
    val listeningStats: ListeningStats = ListeningStats(),
    val sleepTimerEndMs: Long? = null,
    val lastSession: com.shyan.dreamin.data.local.UserPreferencesDataStore.LastSession? = null,
    val recentSearches: List<String> = emptyList(),
    val searchPage: Int = 1,
    val isLoadingMoreSearch: Boolean = false,
    val hasMoreSearchResults: Boolean = false,
    val playlists: List<com.shyan.dreamin.data.local.Playlist> = emptyList(),
    val playlistArtworks: Map<Long, List<String>> = emptyMap(),
    val openPlaylistId: Long? = null,
    val openPlaylistSongs: List<Song> = emptyList(),
    val playlistQueueActive: Boolean = false,
    val userQueuedSongIds: List<String> = emptyList(),
    val searchError: String? = null,
    val isSearching: Boolean = false,
    // Lyrics, Downloads & Artist Profile extensions
    val lyricsState: LyricsState = LyricsState.Idle,
    val isLyricsViewOpen: Boolean = false,
    val downloadedSongs: List<Song> = emptyList(),
    val downloadingSongIds: Set<String> = emptySet(),
    val selectedArtistProfile: ArtistProfile? = null,
    val spotifyImportState: SpotifyImportState = SpotifyImportState.Idle,
    val isFetchingUpNext: Boolean = false,
    val didYouMeanQuery: String? = null,
    val detectedSpotifyClipboardUrl: String? = null,
    val isSyncingSpotifyPlaylist: Boolean = false,
    val spotifySyncAlerts: List<SpotifySyncAlert> = emptyList()
)

@Immutable
data class SpotifySyncAlert(
    val playlistId: Long,
    val playlistName: String,
    val newTrackCount: Int,
    val isUnavailable: Boolean = false
)

@Immutable
sealed class SpotifyImportState {
    object Idle : SpotifyImportState()
    data class FetchingMetadata(val url: String) : SpotifyImportState()
    data class MatchingTracks(
        val playlistTitle: String,
        val coverUrl: String,
        val currentTrackIndex: Int,
        val totalTracks: Int,
        val matchedCount: Int,
        val currentTrackName: String,
        val currentTrackArtist: String = "",
        val currentArtworkUrl: String = ""
    ) : SpotifyImportState()
    data class Success(
        val playlistId: Long,
        val playlistTitle: String,
        val matchedCount: Int,
        val totalTracks: Int,
        val coverUrl: String = "",
        val matchedSongs: List<Song> = emptyList(),
        val unmatchedTracks: List<com.shyan.dreamin.data.service.SpotifyImportedTrack> = emptyList(),
        val spotifyPlaylistId: String? = null
    ) : SpotifyImportState()
    data class Error(val message: String) : SpotifyImportState()
}

