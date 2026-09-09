package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.local.dao.PlayHistoryDao
import com.shyan.dreamin.data.local.dao.SongSummary
import com.shyan.dreamin.data.local.entity.PlayHistoryEntity
import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class PlayHistoryRepository(private val dao: PlayHistoryDao) {

    /** Most recent plays, de-duplicated by song so the same track doesn't repeat. */
    fun getRecentlyPlayed(limit: Int = 15): Flow<List<Song>> =
        dao.getRecent(limit).map { entities ->
            entities.distinctBy { it.songId }.map { it.toSong() }
        }

    /** Most-played songs of all time. */
    fun getTopSongs(limit: Int = 10): Flow<List<Song>> =
        dao.getMostPlayed(limit).map { summaries -> summaries.map { it.toSong() } }

    suspend fun recordPlay(song: Song) {
        val poster = if (song.id.startsWith("yt_")) {
            val ytId = song.id.removePrefix("yt_")
            "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
        } else {
            com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(song) ?: song.artworkUrl
        }
        dao.insert(
            PlayHistoryEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                artworkUrl = poster,
                durationMs = song.duration
            )
        )
    }

    suspend fun updateArtwork(songId: String, artworkUrl: String) {
        if (songId.startsWith("yt_")) return
        dao.updateArtwork(songId, artworkUrl)
    }
}

private fun PlayHistoryEntity.toSong(): Song {
    if (songId.startsWith("yt_")) {
        val ytId = songId.removePrefix("yt_")
        return Song(
            id = songId,
            title = title,
            artist = artist,
            artworkUrl = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg",
            duration = durationMs
        )
    }
    val fallback = Song(id = songId, title = title, artist = artist, artworkUrl = artworkUrl)
    val cached = com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(fallback)
    return Song(
        id = songId,
        title = title,
        artist = artist,
        artworkUrl = cached ?: Song.resolvePoster(title, artworkUrl),
        duration = durationMs
    )
}

private fun SongSummary.toSong(): Song {
    if (songId.startsWith("yt_")) {
        val ytId = songId.removePrefix("yt_")
        return Song(
            id = songId,
            title = title,
            artist = artist,
            artworkUrl = "https://i.ytimg.com/vi/$ytId/hqdefault.jpg"
        )
    }
    val fallback = Song(id = songId, title = title, artist = artist, artworkUrl = artworkUrl)
    val cached = com.shyan.dreamin.data.service.OfficialArtworkService.getCachedPoster(fallback)
    return Song(
        id = songId,
        title = title,
        artist = artist,
        artworkUrl = cached ?: Song.resolvePoster(title, artworkUrl)
    )
}
