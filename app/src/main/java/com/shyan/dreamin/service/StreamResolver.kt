package com.shyan.dreamin.service

import com.shyan.dreamin.data.local.DownloadRepository
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.network.MusicApi
import com.shyan.dreamin.data.service.AudioStreamResolver
import com.shyan.dreamin.data.service.YtMusicFallbackResolver
import java.util.concurrent.ConcurrentHashMap

class StreamResolver(
    private val downloadRepo: DownloadRepository,
    private val api: MusicApi
) {

    suspend fun resolve(song: Song): String {
        try {
            return AudioStreamResolver.resolveStreamUrl(song = song, downloadRepo = downloadRepo, apiService = api)
        } catch (e: IllegalStateException) {
            android.util.Log.w(TAG, "JioSaavn stream unavailable for '${song.title}', trying YouTube fallback")
        }

        val ytUrl = YtMusicFallbackResolver.resolveStreamUrl(song)
        if (!ytUrl.isNullOrBlank()) {
            AudioStreamResolver.putCachedStreamUrl(song.id, ytUrl)
            youtubeSongIds.add(song.id)
            android.util.Log.d(TAG, "YouTube fallback stream resolved for '${song.title}'")
            return ytUrl
        }

        throw IllegalStateException("Track unavailable: '${song.title}' could not be streamed from JioSaavn or YouTube.")
    }

    companion object {
        private const val TAG = "StreamResolver"
        private val youtubeSongIds: MutableSet<String> = ConcurrentHashMap.newKeySet()

        fun isYoutubeStream(songId: String): Boolean =
            songId.startsWith("yt_") || youtubeSongIds.contains(songId)
    }
}
