package com.shyan.dreamin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val coverUrl: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val spotifyPlaylistId: String? = null
)
