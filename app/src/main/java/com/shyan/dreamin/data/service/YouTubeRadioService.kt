package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.network.NetworkService
import com.shyan.dreamin.data.recommendation.OfficialSongFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

object YouTubeRadioService {

    private val radioCache = ConcurrentHashMap<String, List<Pair<String, String>>>()
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    /**
     * Fetches real-time official YouTube Music Trending Tamil Songs.
     * 100% Free, untrackable, anonymous Innertube request.
     */
    suspend fun fetchTrendingTamilSongs(): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Pair<String, String>>()
        try {
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "IN")
                    })
                })
                put("query", "Trending Tamil songs")
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val text = NetworkService.httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (text.isNotBlank()) {
                val root = JSONObject(text)
                extractWebRemixSongs(root, results)
            }
        } catch (e: Exception) {
            android.util.Log.d("YouTubeRadioService", "fetchTrendingTamilSongs error: ${e.message}")
        }
        return@withContext results.filter { (title, _) ->
            title.isNotBlank() && !title.contains("Playlist", ignoreCase = true) && !title.contains("Album", ignoreCase = true)
        }
    }

    fun clearCache() {
        radioCache.clear()
    }

    /**
     * Fetches the official YouTube Music Radio Queue for a given seed track.
     * Returns a list of (TrackTitle, Artist) recommendations.
     */
    suspend fun fetchRadioRecommendations(song: Song): List<Pair<String, String>> = withContext(Dispatchers.IO) {
        val cacheKey = "${song.displayTitle.lowercase()}_${song.artist.lowercase()}".trim()
        radioCache[cacheKey]?.let { return@withContext it }

        val primaryArtist = song.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
        val cleanTitle = song.displayTitle.replace(Regex("""\s*[\(\[].*?[\)\]]\s*$"""), "").trim()

        val queries = listOf(
            "$cleanTitle $primaryArtist tamil song",
            "$cleanTitle $primaryArtist tamil",
            "$cleanTitle $primaryArtist"
        )

        for (q in queries) {
            try {
                // 1. Search YouTube Music to obtain seed videoId
                val videoId = searchVideoId(q)
                if (videoId != null) {
                    // 2. Fetch official Next / Radio Queue from Innertube
                    val rawTracks = fetchNextRadioQueue(videoId)
                    val filtered = rawTracks.filter { (title, artist) ->
                        val dummy = Song(id = "", title = title, artist = artist)
                        OfficialSongFilter.isOfficial(dummy, rejectHindi = true)
                    }

                    if (filtered.isNotEmpty()) {
                        radioCache[cacheKey] = filtered
                        return@withContext filtered
                    }
                }
            } catch (_: Exception) {}
        }

        emptyList()
    }

    fun searchVideoId(query: String): String? {
        try {
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "ta")
                        put("gl", "IN")
                    })
                })
                put("query", query)
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/search")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val text = NetworkService.httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (text.isNotBlank()) {
                val root = JSONObject(text)
                return findVideoId(root)
            }
        } catch (e: Exception) {
            android.util.Log.d("YouTubeRadioService", "searchVideoId error: ${e.message}")
        }
        return null
    }

    private fun fetchNextRadioQueue(videoId: String): List<Pair<String, String>> {
        val results = mutableListOf<Pair<String, String>>()
        try {
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "ta")
                        put("gl", "IN")
                    })
                })
                put("videoId", videoId)
                put("playlistId", "RDAMVM$videoId")
                put("isAudioOnly", true)
            }

            val req = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(body.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            val text = NetworkService.httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }

            if (text.isNotBlank()) {
                val root = JSONObject(text)
                extractPlaylistPanelTracks(root, results)
            }
        } catch (e: Exception) {
            android.util.Log.d("YouTubeRadioService", "fetchNextRadioQueue error: ${e.message}")
        }
        return results
    }

    private fun findVideoId(json: Any): String? {
        when (json) {
            is JSONObject -> {
                val optId = json.optString("videoId")
                if (optId.length == 11) return optId
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val res = findVideoId(json.get(k))
                    if (res != null) return res
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    val res = findVideoId(json.get(i))
                    if (res != null) return res
                }
            }
        }
        return null
    }

    private fun extractPlaylistPanelTracks(json: Any, out: MutableList<Pair<String, String>>) {
        when (json) {
            is JSONObject -> {
                val panel = json.optJSONObject("playlistPanelVideoRenderer")
                if (panel != null) {
                    val titleRuns = panel.optJSONObject("title")?.optJSONArray("runs")
                    val title = titleRuns?.optJSONObject(0)?.optString("text") ?: ""

                    val bylineRuns = panel.optJSONObject("longBylineText")?.optJSONArray("runs")
                        ?: panel.optJSONObject("shortBylineText")?.optJSONArray("runs")
                    val artistList = mutableListOf<String>()
                    if (bylineRuns != null) {
                        for (i in 0 until bylineRuns.length()) {
                            val txt = bylineRuns.optJSONObject(i)?.optString("text") ?: ""
                            if (txt != " • " && !txt.contains("views", ignoreCase = true) && !txt.matches(Regex("""\d+:\d+"""))) {
                                artistList.add(txt)
                            }
                        }
                    }
                    val artist = artistList.firstOrNull() ?: ""

                    if (title.isNotBlank() && !title.contains("Playlist", ignoreCase = true)) {
                        out.add(Pair(title, artist))
                    }
                    return
                }
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    extractPlaylistPanelTracks(json.get(k), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    extractPlaylistPanelTracks(json.get(i), out)
                }
            }
        }
    }

    private fun extractWebRemixSongs(json: Any, out: MutableList<Pair<String, String>>) {
        when (json) {
            is JSONObject -> {
                val item = json.optJSONObject("musicResponsiveListItemRenderer")
                if (item != null) {
                    val flex = item.optJSONArray("flexColumns")
                    if (flex != null && flex.length() >= 2) {
                        val col1 = flex.optJSONObject(0)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        val col2 = flex.optJSONObject(1)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")

                        val title = col1?.optJSONObject("text")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text") ?: ""
                        val artistRuns = col2?.optJSONObject("text")?.optJSONArray("runs")
                        val artistList = mutableListOf<String>()
                        if (artistRuns != null) {
                            for (i in 0 until artistRuns.length()) {
                                val txt = artistRuns.optJSONObject(i)?.optString("text") ?: ""
                                if (txt != " • " && txt != "Song" && txt != "Album" && txt != "Video" && txt != "Artist") {
                                    artistList.add(txt)
                                }
                            }
                        }
                        val artist = artistList.joinToString(" ").trim()
                        if (title.isNotBlank() && !title.contains("Top Hits", ignoreCase = true) && !title.contains("Tamil New Songs", ignoreCase = true)) {
                            out.add(Pair(title, artist))
                        }
                    }
                }
                val keys = json.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    extractWebRemixSongs(json.get(k), out)
                }
            }
            is JSONArray -> {
                for (i in 0 until json.length()) {
                    extractWebRemixSongs(json.get(i), out)
                }
            }
        }
    }
}
