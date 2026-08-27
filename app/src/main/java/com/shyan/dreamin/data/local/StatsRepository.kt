package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.local.dao.PlayHistoryDao
import com.shyan.dreamin.data.model.ListeningStats
import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow

class StatsRepository(private val dao: PlayHistoryDao) {

    fun weeklyStatsFlow(): Flow<ListeningStats> {
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1_000L
        return dao.countSinceFlow(weekAgo).flatMapLatest {
            flow { emit(getWeeklyStats()) }
        }
    }

    suspend fun getWeeklyStats(): ListeningStats {
        val weekAgo = System.currentTimeMillis() - 7 * 24 * 60 * 60 * 1_000L
        val songs = dao.countSince(weekAgo)
        val totalMs = dao.totalDurationMsSince(weekAgo) ?: 0L
        val topSongSummary = dao.topSongSince(weekAgo)
        val topArtist = dao.topArtistSince(weekAgo)
        val topSong = topSongSummary?.let {
            Song(id = it.songId, title = it.title, artist = it.artist, artworkUrl = it.artworkUrl)
        }
        return ListeningStats(
            songsThisWeek = songs,
            minutesThisWeek = totalMs / 60_000L,
            topSongThisWeek = topSong,
            topArtistThisWeek = topArtist
        )
    }
}
