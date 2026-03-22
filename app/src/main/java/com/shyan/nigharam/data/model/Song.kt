package com.shyan.nigharam.data.model

import com.google.gson.annotations.SerializedName

// ── Core domain model ──────────────────────────────────────────────────────────

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    @SerializedName("artwork_url") val artworkUrl: String = "",
    val duration: Long = 0L          // ms, 0 if unknown
)

// ── API Response wrappers ──────────────────────────────────────────────────────

data class SearchResponse(
    val results: List<Song> = emptyList()
)

data class ChartResponse(
    val songs: List<Song> = emptyList()
)

data class PlayResponse(
    @SerializedName("stream_url") val streamUrl: String,
    @SerializedName("proxy_url") val proxyUrl: String? = null
)

data class UpNextResponse(
    val songs: List<Song> = emptyList()
)

data class RecommendResponse(
    val recommendations: List<Song> = emptyList()
)

// ── UI State ───────────────────────────────────────────────────────────────────

sealed class PlaybackState {
    object Idle : PlaybackState()
    object Loading : PlaybackState()
    object Playing : PlaybackState()
    object Paused : PlaybackState()
    data class Error(val message: String) : PlaybackState()
}

data class PlayerUiState(
    val currentSong: Song? = null,
    val playbackState: PlaybackState = PlaybackState.Idle,
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Song> = emptyList(),
    val searchResults: List<Song> = emptyList(),
    val trendingCharts: List<Song> = emptyList(),
    val recommendations: List<Song> = emptyList(),
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val searchError: String? = null,           // non-null = show error banner
    val isQueueVisible: Boolean = false,
    val dominantColor: Int = 0xFF1A1A2E.toInt()
)
