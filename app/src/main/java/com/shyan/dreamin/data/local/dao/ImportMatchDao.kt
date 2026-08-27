package com.shyan.dreamin.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.shyan.dreamin.data.local.entity.ImportMatchEntity

@Dao
interface ImportMatchDao {

    @Query("SELECT * FROM import_matches WHERE spotifySignature = :signature LIMIT 1")
    suspend fun getMatch(signature: String): ImportMatchEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatch(match: ImportMatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMatches(matches: List<ImportMatchEntity>)

    @Query("DELETE FROM import_matches WHERE timestamp < :expiryTime")
    suspend fun cleanOldMatches(expiryTime: Long)
}
