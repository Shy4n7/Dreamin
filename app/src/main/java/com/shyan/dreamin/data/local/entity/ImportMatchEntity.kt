package com.shyan.dreamin.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "import_matches")
data class ImportMatchEntity(
    @PrimaryKey
    val spotifySignature: String, // Normalized signature: "cleanTitle|cleanArtist"
    val songId: String,
    val songTitle: String,
    val songArtist: String,
    val artworkUrl: String,
    val duration: Long,
    val confidenceScore: Int,
    val timestamp: Long = System.currentTimeMillis()
)
