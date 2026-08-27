package com.shyan.dreamin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: String,
    val title: String,
    val artist: String,
    val artworkUrl: String,
    val addedAt: Long = System.currentTimeMillis()
)
