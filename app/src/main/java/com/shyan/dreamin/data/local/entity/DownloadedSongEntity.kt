package com.shyan.dreamin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.shyan.dreamin.data.model.Song

@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val duration: Long,
    val localFilePath: String,
    val fileSizeBytes: Long = 0L,
    val downloadedAt: Long = System.currentTimeMillis()
) {
    fun toSong(): Song = Song(
        id = songId,
        title = title,
        artist = artist,
        artworkUrl = artworkUrl,
        duration = duration
    )
}
