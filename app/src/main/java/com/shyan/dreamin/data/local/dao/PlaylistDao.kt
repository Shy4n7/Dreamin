package com.shyan.dreamin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.shyan.dreamin.data.local.entity.PlaylistEntity
import com.shyan.dreamin.data.local.entity.PlaylistSongEntity
import kotlinx.coroutines.flow.Flow

data class PlaylistWithCount(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val createdAt: Long,
    val songCount: Int,
    val spotifyPlaylistId: String? = null
)

@Dao
interface PlaylistDao {

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: Long)

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Query("UPDATE playlists SET coverUrl = :coverUrl WHERE id = :playlistId")
    suspend fun updatePlaylistCover(playlistId: Long, coverUrl: String?)

    @Query("SELECT spotifyPlaylistId FROM playlists WHERE id = :playlistId")
    suspend fun getSpotifyPlaylistId(playlistId: Long): String?

    @Query("UPDATE playlists SET spotifyPlaylistId = :spotifyId WHERE id = :playlistId")
    suspend fun updateSpotifyPlaylistId(playlistId: Long, spotifyId: String?)

    @Query("""
        SELECT p.id, p.name, p.coverUrl, p.createdAt, p.spotifyPlaylistId,
               (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) as songCount
        FROM playlists p
        ORDER BY p.createdAt DESC
    """)
    fun observePlaylists(): Flow<List<PlaylistWithCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSong(playlistId: Long, songId: String)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    fun observeSongs(playlistId: Long): Flow<List<PlaylistSongEntity>>

    @Transaction
    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC")
    suspend fun getSongs(playlistId: Long): List<PlaylistSongEntity>

    @Query("UPDATE playlist_songs SET artworkUrl = :artworkUrl WHERE songId = :songId")
    suspend fun updateSongArtwork(songId: String, artworkUrl: String)

    @Query("SELECT * FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position ASC LIMIT 4")
    suspend fun getFirstFourSongs(playlistId: Long): List<PlaylistSongEntity>
}
