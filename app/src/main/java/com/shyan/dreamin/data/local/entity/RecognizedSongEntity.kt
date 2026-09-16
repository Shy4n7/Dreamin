package com.shyan.dreamin.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 🎵 Room database entity representing a song identified via acoustic recognition.
 */
@Entity(
    tableName = "recognized_songs",
    indices = [Index("recognizedAt")]
)
data class RecognizedSongEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    val album: String = "",
    val artworkUrl: String = "",
    val recognizedAt: Long = System.currentTimeMillis(),
    val songId: String = ""
)
