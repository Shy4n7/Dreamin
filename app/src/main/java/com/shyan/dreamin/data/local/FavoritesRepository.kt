package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.local.dao.FavoriteDao
import com.shyan.dreamin.data.local.entity.FavoriteEntity
import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class FavoritesRepository(private val dao: FavoriteDao) {

    fun observeAll(): Flow<List<Song>> =
        dao.observeAll().map { entities -> entities.map { it.toSong() } }

    fun isFavorite(songId: String): Flow<Boolean> =
        dao.isFavorite(songId)

    suspend fun addFavorite(song: Song) {
        dao.insert(
            FavoriteEntity(
                songId = song.id,
                title = song.title,
                artist = song.artist,
                artworkUrl = song.artworkUrl
            )
        )
    }

    suspend fun removeFavorite(songId: String) {
        dao.delete(songId)
    }

    suspend fun updateArtwork(songId: String, artworkUrl: String) {
        dao.updateArtwork(songId, artworkUrl)
    }
}

private fun FavoriteEntity.toSong() = Song(
    id = songId, title = title, artist = artist, artworkUrl = Song.resolvePoster(title, artworkUrl)
)
