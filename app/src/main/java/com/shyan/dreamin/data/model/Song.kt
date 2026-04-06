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
    val duration: Long = 0L
) {
    val displayTitle: String get() = FROM_SUFFIX_REGEX.replace(title, "").trim()
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

data class ListeningStats(
    val songsThisWeek: Int = 0,
    val minutesThisWeek: Long = 0L,
    val topSongThisWeek: Song? = null,
    val topArtistThisWeek: String? = null
)

sealed class PlaybackState {
    object Idle : PlaybackState()
    object Loading : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}

data class PlaybackProgress(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L
)

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
    val dominantColor: Int = 0xFF1A1A2E.toInt(),
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
    val searchError: String? = null
)
