package com.shyan.dreamin.data.local.dao

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.shyan.dreamin.data.local.entity.PlayHistoryEntity
import kotlinx.coroutines.flow.Flow

/** Lightweight result type for queries that don't return all PlayHistoryEntity fields. */
data class SongSummary(
    @ColumnInfo(name = "songId") val songId: String,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "artist") val artist: String,
    @ColumnInfo(name = "artworkUrl") val artworkUrl: String
)

@Dao
interface PlayHistoryDao {

    @Insert
    suspend fun insert(entry: PlayHistoryEntity)

    @Query("UPDATE play_history SET artworkUrl = :artworkUrl WHERE songId = :songId AND artworkUrl NOT LIKE '%scdn.co%' AND artworkUrl NOT LIKE '%spotify%'")
    suspend fun updateArtwork(songId: String, artworkUrl: String)

    @Query("SELECT * FROM play_history ORDER BY playedAt DESC LIMIT :limit")
    fun getRecent(limit: Int = 20): Flow<List<PlayHistoryEntity>>

    @Query("""
        SELECT songId, title, artist, artworkUrl
        FROM play_history
        GROUP BY songId
        ORDER BY COUNT(*) DESC
        LIMIT :limit
    """)
    fun getMostPlayed(limit: Int = 20): Flow<List<SongSummary>>

    @Query("SELECT COUNT(*) FROM play_history WHERE playedAt >= :since")
    fun countSinceFlow(since: Long): Flow<Int>

    @Query("SELECT COUNT(*) FROM play_history WHERE playedAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT SUM(durationMs) FROM play_history WHERE playedAt >= :since")
    suspend fun totalDurationMsSince(since: Long): Long?

    @Query("""
        SELECT songId, title, artist, artworkUrl
        FROM play_history
        WHERE playedAt >= :since
        GROUP BY songId
        ORDER BY COUNT(*) DESC
        LIMIT 1
    """)
    suspend fun topSongSince(since: Long): SongSummary?

    @Query("""
        SELECT artist
        FROM play_history
        WHERE playedAt >= :since
        GROUP BY artist
        ORDER BY COUNT(*) DESC
        LIMIT 1
    """)
    suspend fun topArtistSince(since: Long): String?
}
