package com.shyan.dreamin.data.local.dao

import androidx.room.*
import com.shyan.dreamin.data.local.entity.DownloadedSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloaded_songs ORDER BY downloadedAt DESC")
    fun getAllDownloads(): Flow<List<DownloadedSongEntity>>

    @Query("SELECT * FROM downloaded_songs WHERE songId = :songId LIMIT 1")
    suspend fun getDownload(songId: String): DownloadedSongEntity?

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE songId = :songId)")
    fun isDownloadedFlow(songId: String): Flow<Boolean>

    @Query("SELECT EXISTS(SELECT 1 FROM downloaded_songs WHERE songId = :songId)")
    suspend fun isDownloaded(songId: String): Boolean

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDownload(song: DownloadedSongEntity)

    @Query("DELETE FROM downloaded_songs WHERE songId = :songId")
    suspend fun deleteDownload(songId: String)

    @Query("UPDATE downloaded_songs SET artworkUrl = :artworkUrl WHERE songId = :songId")
    suspend fun updateArtwork(songId: String, artworkUrl: String)

    @Query("DELETE FROM downloaded_songs")
    suspend fun deleteAll()
}
