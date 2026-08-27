package com.shyan.dreamin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "play_history")
data class PlayHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val durationMs: Long = 0L,
    val playedAt: Long = System.currentTimeMillis()
)
