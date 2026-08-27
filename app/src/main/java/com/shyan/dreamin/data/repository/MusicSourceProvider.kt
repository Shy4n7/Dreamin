package com.shyan.dreamin.data.repository

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.network.MusicApi
import com.shyan.dreamin.data.network.NetworkService
import com.shyan.dreamin.data.service.YouTubeRadioService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Unified contract for pluggable music providers (JioSaavn, YouTube Music, Local Storage, etc.).
 */
interface MusicSourceProvider {
    val providerId: String
    val displayName: String

    suspend fun search(query: String, page: Int = 1, limit: Int = 15): Result<List<Song>>
    suspend fun getStreamUrl(song: Song): Result<String>
    suspend fun getUpNext(currentSong: Song, excludeIds: List<String> = emptyList(), limit: Int = 10): Result<List<Song>>
}

/**
 * JioSaavn backend API music provider.
 */
class JioSaavnMusicProvider(private val api: MusicApi = NetworkService.api) : MusicSourceProvider {
    override val providerId: String = "jiosaavn"
    override val displayName: String = "JioSaavn"

    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = api.search(query, page, limit)
            Result.success(response.results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> = withContext(Dispatchers.IO) {
        if (song.id.isNotBlank()) {
            Result.success(song.id)
        } else {
            Result.failure(IllegalStateException("No stream URL found for ${song.displayTitle}"))
        }
    }

    override suspend fun getUpNext(currentSong: Song, excludeIds: List<String>, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val excludeStr = excludeIds.joinToString(",")
            val response = api.getUpNext(currentSong.id, excludeStr, limit)
            Result.success(response.songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * YouTube Music Innertube provider for discovery and recommendations.
 */
class YouTubeMusicProvider : MusicSourceProvider {
    override val providerId: String = "youtube"
    override val displayName: String = "YouTube Music"

    override suspend fun search(query: String, page: Int, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val pairs = YouTubeRadioService.fetchTrendingTamilSongs()
            val songs = pairs.mapIndexed { index, (title, artist) ->
                Song(
                    id = "yt_$index",
                    title = title,
                    artist = artist,
                    duration = 0L
                )
            }
            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStreamUrl(song: Song): Result<String> = withContext(Dispatchers.IO) {
        Result.failure(UnsupportedOperationException("Stream extraction uses backend proxy"))
    }

    override suspend fun getUpNext(currentSong: Song, excludeIds: List<String>, limit: Int): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val pairs = YouTubeRadioService.fetchRadioRecommendations(currentSong)
            val songs = pairs.filter { (title, _) -> !excludeIds.contains(title) }.take(limit).mapIndexed { idx, (title, artist) ->
                Song(
                    id = "yt_radio_$idx",
                    title = title,
                    artist = artist,
                    duration = 0L
                )
            }
            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
