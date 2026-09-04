package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.local.dao.PlaylistDao
import com.shyan.dreamin.data.local.entity.PlaylistEntity
import com.shyan.dreamin.data.local.entity.PlaylistSongEntity
import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import androidx.compose.runtime.Immutable

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val createdAt: Long,
    val coverUrl: String? = null,
    val songCount: Int = 0,
    val spotifyPlaylistId: String? = null
)

class PlaylistRepository(private val dao: PlaylistDao) {

    fun observePlaylists(): Flow<List<Playlist>> =
        dao.observePlaylists().map { entities -> entities.map { it.toPlaylist() } }

    suspend fun createPlaylist(name: String, coverUrl: String? = null, spotifyPlaylistId: String? = null): Long =
        dao.insertPlaylist(PlaylistEntity(name = name, coverUrl = coverUrl, spotifyPlaylistId = spotifyPlaylistId))

    suspend fun getSpotifyPlaylistId(playlistId: Long): String? =
        dao.getSpotifyPlaylistId(playlistId)

    suspend fun updateSpotifyPlaylistId(playlistId: Long, spotifyId: String?) =
        dao.updateSpotifyPlaylistId(playlistId, spotifyId)

    suspend fun deletePlaylist(playlistId: Long) =
        dao.deletePlaylist(playlistId)

    suspend fun renamePlaylist(playlistId: Long, name: String) =
        dao.renamePlaylist(playlistId, name)

    suspend fun updatePlaylistCover(playlistId: Long, coverUrl: String?) =
        dao.updatePlaylistCover(playlistId, coverUrl)

    suspend fun addSong(playlistId: Long, song: Song, position: Int) {
        dao.insertSong(
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl,
                position = position
            )
        )
    }

    suspend fun addSongs(playlistId: Long, songs: List<Song>, startPosition: Int = 0) {
        val entities = songs.mapIndexed { index, song ->
            PlaylistSongEntity(
                playlistId = playlistId,
                songId = song.id,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl,
                position = startPosition + index
            )
        }
        dao.insertSongs(entities)
    }

    suspend fun removeSong(playlistId: Long, songId: String) =
        dao.removeSong(playlistId, songId)

    suspend fun removeSongs(playlistId: Long, songIds: List<String>) =
        dao.removeSongs(playlistId, songIds)

    suspend fun updateSongPositions(playlistId: Long, songIdsInOrder: List<String>) =
        dao.updateSongPositions(playlistId, songIdsInOrder)

    fun observeSongs(playlistId: Long): Flow<List<Song>> =
        dao.observeSongs(playlistId).map { entities ->
            entities.map { it.toSong() }
        }

    suspend fun getSongs(playlistId: Long): List<Song> =
        dao.getSongs(playlistId).map { it.toSong() }

    suspend fun updateSongArtwork(songId: String, artworkUrl: String) =
        dao.updateSongArtwork(songId, artworkUrl)

    suspend fun getFirstFourArtworks(playlistId: Long): List<String> =
        dao.getFirstFourSongs(playlistId).mapNotNull { it.artworkUrl.takeIf { url -> url.isNotBlank() } }
}

private fun com.shyan.dreamin.data.local.dao.PlaylistWithCount.toPlaylist() = Playlist(
    id = id,
    name = name,
    createdAt = createdAt,
    coverUrl = coverUrl,
    songCount = songCount,
    spotifyPlaylistId = spotifyPlaylistId
)

private fun PlaylistSongEntity.toSong() = Song(
    id = songId, title = title, artist = artist, artworkUrl = artworkUrl
)
