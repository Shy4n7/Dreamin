package com.shyan.dreamin.data.service

import android.util.Log
import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * YouTube InnerTube fallback stream resolver.
 *
 * Used when JioSaavn cannot resolve a playable stream URL for a song.
 * Searches YouTube Music for the best audio-only match and returns
 * a direct m4a stream URL (128 kbps / AUDIO_QUALITY_MEDIUM).
 *
 * No API key is required -- InnerTube is the same JSON API YouTube itself uses.
 * Stream URLs expire in ~6 hours; results are cached in AudioStreamResolver's LRU.
 */
object YtMusicFallbackResolver {

    private const val TAG = "YtFallback"
    private const val INNERTUBE_API_URL = "https://music.youtube.com/youtubei/v1"
    private const val INNERTUBE_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-NKNELL6OA"

    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private fun buildContext(query: String? = null, videoId: String? = null): String {
        val base = org.json.JSONObject().apply {
            put("context", org.json.JSONObject().apply {
                put("client", org.json.JSONObject().apply {
                    put("clientName", "WEB_REMIX")
                    put("clientVersion", "1.20250101.01.00")
                    put("hl", "en")
                    put("gl", "IN")
                })
            })
        }
        if (query != null) {
            base.put("query", query)
            // Songs-only filter for YouTube Music search
            base.put("params", "EgWKAQIIAWoKEAMQBBAJEAoQBQ==")
        }
        if (videoId != null) {
            base.put("videoId", videoId)
            base.put("playbackContext", org.json.JSONObject().apply {
                put("contentPlaybackContext", org.json.JSONObject().apply {
                    put("signatureTimestamp", 20697)
                    put("html5Preference", "HTML5_PREF_WANTS")
                })
            })
        }
        return base.toString()
    }

    /**
     * Attempts to resolve a playable audio stream URL for [song] via YouTube Music.
     * Returns null if no match found or on any error.
     */
    suspend fun resolveStreamUrl(song: Song): String? = withContext(Dispatchers.IO) {
        try {
            val videoId = searchYoutubeMusic(song) ?: return@withContext null
            Log.d(TAG, "YT match: videoId=$videoId for '${song.displayTitle}'")
            fetchAudioStreamUrl(videoId)
        } catch (e: Exception) {
            Log.w(TAG, "YT fallback failed for '${song.displayTitle}': ${e.message}")
            null
        }
    }

    private fun searchYoutubeMusic(song: Song): String? {
        val queries = buildList {
            val title = song.displayTitle.trim()
            val artist = song.artist.trim()
            if (artist.isNotBlank()) add("$title $artist")
            add(title)
        }.distinct()

        for (query in queries) {
            try {
                val body = buildContext(query = query).toRequestBody(JSON_MEDIA_TYPE)
                val req = Request.Builder()
                    .url("$INNERTUBE_API_URL/search?key=$INNERTUBE_API_KEY&prettyPrint=false")
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", "1.20250101.01.00")
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .build()

                val text = com.shyan.dreamin.data.network.NetworkService.httpClient
                    .newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                    }
                if (text.isBlank()) continue

                val videoId = parseSearchVideoId(JSONObject(text), song)
                if (videoId != null) return videoId
            } catch (e: Exception) {
                Log.d(TAG, "YT search query '$query' failed: ${e.message}")
            }
        }
        return null
    }

    private fun parseSearchVideoId(json: JSONObject, song: Song): String? {
        return try {
            val tabs = json
                .optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return null

            val sectionContents = tabs.getJSONObject(0)
                .optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return null

            for (i in 0 until sectionContents.length()) {
                val shelf = sectionContents.getJSONObject(i)
                    .optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    val item = items.getJSONObject(j)
                        .optJSONObject("musicResponsiveListItemRenderer") ?: continue

                    // Extract videoId via overlay play button endpoint
                    val videoId = item
                        .optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        .takeIf { !it.isNullOrBlank() } ?: continue

                    // Duration check: accept if within +-35 seconds
                    val durationSec = extractDurationSec(item)
                    if (song.duration <= 0L || durationSec <= 0L ||
                        kotlin.math.abs(durationSec * 1000L - song.duration) <= 35_000L) {
                        return videoId
                    }
                }
            }
            null
        } catch (e: Exception) {
            Log.d(TAG, "parseSearchVideoId error: ${e.message}")
            null
        }
    }

    private fun extractDurationSec(item: JSONObject): Long {
        return try {
            val flexColumns = item.optJSONArray("flexColumns") ?: return 0L
            for (i in 0 until flexColumns.length()) {
                val runs = flexColumns.getJSONObject(i)
                    .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                    ?.optJSONObject("text")
                    ?.optJSONArray("runs") ?: continue
                for (j in 0 until runs.length()) {
                    val text = runs.getJSONObject(j).optString("text", "")
                    if (text.matches(Regex("\\d+:\\d{2}(:\\d{2})?"))) {
                        val parts = text.split(":")
                        return when (parts.size) {
                            2 -> parts[0].toLong() * 60 + parts[1].toLong()
                            3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                            else -> 0L
                        }
                    }
                }
            }
            0L
        } catch (_: Exception) { 0L }
    }

    @Volatile
    private var cachedVisitorData: String? = null

    private fun getOrFetchVisitorData(): String? {
        cachedVisitorData?.let { return it }
        return synchronized(this) {
            cachedVisitorData ?: run {
                try {
                    val clientJson = JSONObject().apply {
                        put("clientName", "VISIONOS")
                        put("clientVersion", "1.04")
                        put("clientId", "101")
                        put("clientScreen", "WATCH")
                        put("platform", "MOBILE")
                        put("hl", "en")
                        put("gl", "US")
                    }
                    val body = JSONObject().apply {
                        put("context", JSONObject().apply {
                            put("client", clientJson)
                        })
                    }.toString().toRequestBody(JSON_MEDIA_TYPE)

                    val req = Request.Builder()
                        .url("https://www.youtube.com/youtubei/v1/visitor_id?key=$INNERTUBE_API_KEY&prettyPrint=false")
                        .post(body)
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "com.google.ios.youtube/1.04 (RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; en_US)")
                        .header("X-YouTube-Client-Name", "101")
                        .header("X-YouTube-Client-Version", "1.04")
                        .build()

                    val resp = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(req).execute()
                    val text = resp.use { it.body?.string().orEmpty() }
                    val vData = JSONObject(text).optJSONObject("responseContext")?.optString("visitorData", "")
                    if (!vData.isNullOrBlank()) {
                        cachedVisitorData = vData
                        vData
                    } else null
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch visitorData: ${e.message}")
                    null
                }
            }
        }
    }

    fun fetchAudioStreamUrl(videoId: String): String? {
        val streamUrl = fetchVisionOsAudioStream(videoId)
        if (!streamUrl.isNullOrBlank()) return streamUrl

        // Invalidate visitorData and retry once
        cachedVisitorData = null
        return fetchVisionOsAudioStream(videoId)
    }

    private fun fetchVisionOsAudioStream(videoId: String): String? {
        val visitorData = getOrFetchVisitorData()
        val clientJson = JSONObject().apply {
            put("clientName", "VISIONOS")
            put("clientVersion", "1.04")
            put("clientId", "101")
            put("clientScreen", "WATCH")
            put("platform", "MOBILE")
            put("hl", "en")
            put("gl", "US")
            if (!visitorData.isNullOrBlank()) {
                put("visitorData", visitorData)
            }
        }

        val body = JSONObject().apply {
            put("context", JSONObject().apply {
                put("client", clientJson)
            })
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
        }.toString().toRequestBody(JSON_MEDIA_TYPE)

        val req = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?key=$INNERTUBE_API_KEY&prettyPrint=false")
            .post(body)
            .header("Content-Type", "application/json")
            .header("User-Agent", "com.google.ios.youtube/1.04 (RealityDevice17,1; U; CPU visionOS 26_6_0 like Mac OS X; en_US)")
            .header("X-YouTube-Client-Name", "101")
            .header("X-YouTube-Client-Version", "1.04")
            .build()

        val text = com.shyan.dreamin.data.network.NetworkService.httpClient
            .newCall(req).execute().use { it.body?.string().orEmpty() }
        if (text.isBlank()) return null

        val adaptiveFormats = JSONObject(text)
            .optJSONObject("streamingData")
            ?.optJSONArray("adaptiveFormats") ?: return null

        data class AudioFmt(val url: String, val bitrate: Int, val quality: String, val itag: Int)
        val audioFormats = mutableListOf<AudioFmt>()

        for (i in 0 until adaptiveFormats.length()) {
            val fmt = adaptiveFormats.getJSONObject(i)
            val mime = fmt.optString("mimeType", "")
            val url = fmt.optString("url", "")
            if (!mime.startsWith("audio/") || url.isBlank()) continue
            audioFormats.add(
                AudioFmt(
                    url = url,
                    bitrate = fmt.optInt("bitrate", 0),
                    quality = fmt.optString("audioQuality", ""),
                    itag = fmt.optInt("itag", 0)
                )
            )
        }

        if (audioFormats.isEmpty()) return null

        // Prioritize highest audio bitrate (itag 251 160-175kbps Opus, then itag 140 128kbps AAC)
        val best = audioFormats.maxByOrNull { it.bitrate } ?: return null
        Log.d(TAG, "VISIONOS stream resolved for $videoId: itag=${best.itag}, bitrate=${best.bitrate}")
        return best.url
    }
}
