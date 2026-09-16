package com.shyan.dreamin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shyan.dreamin.data.local.entity.RecognizedSongEntity
import kotlinx.coroutines.flow.Flow

/**
 * 🎵 Room DAO for managing song recognition history.
 */
@Dao
interface RecognizedSongDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: RecognizedSongEntity): Long

    @Query("SELECT * FROM recognized_songs ORDER BY recognizedAt DESC LIMIT :limit")
    fun getAll(limit: Int = 50): Flow<List<RecognizedSongEntity>>

    @Query("DELETE FROM recognized_songs WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM recognized_songs")
    suspend fun clearAll()
}
