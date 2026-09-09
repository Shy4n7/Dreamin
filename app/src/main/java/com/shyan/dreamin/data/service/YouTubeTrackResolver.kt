package com.shyan.dreamin.data.service

import android.util.Log
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.network.NetworkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.net.URLEncoder
import java.util.regex.Pattern

/**
 * 🎵 Dedicated YouTube Track Resolution & Search Engine.
 *
 * Provides:
 * 1. Direct YouTube URL & Video ID extraction.
 * 2. Instant track metadata resolution via YouTube oEmbed + InnerTube details.
 * 3. YouTube Music fallback search when primary catalogs return empty.
 */
object YouTubeTrackResolver {

    private const val TAG = "YouTubeTrackResolver"
    private const val INNERTUBE_API_URL = "https://music.youtube.com/youtubei/v1"
    private const val INNERTUBE_API_KEY = "AIzaSyC9XL3ZjWddXya6X74dJoCTL-NKNELL6OA"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    // Matcher for youtu.be/<id>, youtube.com/watch?v=<id>, youtube.com/shorts/<id>, music.youtube.com/watch?v=<id>
    private val YT_URL_PATTERN = Pattern.compile(
        """(?:https?://)?(?:(?:www\.|m\.|music\.)?youtube\.com/(?:watch\?.*v=|shorts/|embed/)|youtu\.be/)([a-zA-Z0-9_-]{11})""",
        Pattern.CASE_INSENSITIVE
    )

    // Strict 11-char standalone video ID check
    private val YT_ID_PATTERN = Pattern.compile("""^[a-zA-Z0-9_-]{11}$""")

    /**
     * Extracts an 11-character YouTube video ID from a URL or raw ID string.
     * Returns null if the input is not a YouTube link or ID.
     */
    fun extractVideoId(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return null

        val urlMatcher = YT_URL_PATTERN.matcher(trimmed)
        if (urlMatcher.find()) {
            return urlMatcher.group(1)
        }

        // If user entered or pasted an exact 11-char ID without spaces
        if (YT_ID_PATTERN.matcher(trimmed).matches()) {
            return trimmed
        }

        return null
    }

    /**
     * Checks if a search query is a YouTube link or video ID.
     */
    fun isYouTubeQuery(query: String): Boolean = extractVideoId(query) != null

    /**
     * Resolves complete metadata for a given [videoId] and synthesizes a playable [Song].
     */
    suspend fun resolveTrackFromVideoId(videoId: String): Song? = withContext(Dispatchers.IO) {
        val cleanId = videoId.trim()
        if (cleanId.length != 11) return@withContext null

        try {
            var title = ""
            var artist = ""
            var durationMs = 0L
            var artworkUrl = "https://i.ytimg.com/vi/$cleanId/maxresdefault.jpg"

            // 1. Fast, lightweight YouTube oEmbed lookup (<100ms, public, zero auth)
            try {
                val oEmbedUrl = "https://www.youtube.com/oembed?url=https://www.youtube.com/watch?v=$cleanId&format=json"
                val oEmbedReq = Request.Builder()
                    .url(oEmbedUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .build()

                NetworkService.httpClient.newCall(oEmbedReq).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val body = resp.body?.string().orEmpty()
                        if (body.isNotBlank()) {
                            val json = JSONObject(body)
                            title = json.optString("title", "").trim()
                            artist = json.optString("author_name", "")
                                .replace(Regex("""(?i)\s*-\s*topic$"""), "")
                                .replace(Regex("""(?i)\s*vevo$"""), "")
                                .trim()
                            val thumb = json.optString("thumbnail_url", "")
                            if (thumb.isNotBlank()) {
                                artworkUrl = Song.resolvePoster(title, thumb)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "oEmbed resolution failed for $cleanId: ${e.message}")
            }

            // 2. InnerTube /player lookup for exact duration and fallback title/artist
            try {
                val body = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", "1.20250101.01.00")
                            put("hl", "en")
                            put("gl", "IN")
                        })
                    })
                    put("videoId", cleanId)
                }.toString().toRequestBody(JSON_MEDIA_TYPE)

                val req = Request.Builder()
                    .url("$INNERTUBE_API_URL/player?key=$INNERTUBE_API_KEY&prettyPrint=false")
                    .post(body)
                    .header("Content-Type", "application/json")
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", "1.20250101.01.00")
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .build()

                NetworkService.httpClient.newCall(req).execute().use { resp ->
                    if (resp.isSuccessful) {
                        val bodyText = resp.body?.string().orEmpty()
                        if (bodyText.isNotBlank()) {
                            val root = JSONObject(bodyText)
                            val details = root.optJSONObject("videoDetails")
                            if (details != null) {
                                if (title.isBlank()) {
                                    title = details.optString("title", "").trim()
                                }
                                if (artist.isBlank()) {
                                    artist = details.optString("author", "")
                                        .replace(Regex("""(?i)\s*-\s*topic$"""), "")
                                        .trim()
                                }
                                val sec = details.optLong("lengthSeconds", 0L)
                                if (sec > 0L) {
                                    durationMs = sec * 1000L
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.d(TAG, "InnerTube player lookup failed for $cleanId: ${e.message}")
            }

            if (title.isBlank()) {
                title = "YouTube Track ($cleanId)"
            }
            if (artist.isBlank()) {
                artist = "YouTube"
            }

            Song(
                id = "yt_$cleanId",
                title = title,
                artist = artist,
                artworkUrl = artworkUrl,
                duration = durationMs,
                language = "YouTube",
                album = "YouTube"
            )
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resolve track from videoId $cleanId: ${e.message}")
            null
        }
    }

    /**
     * Searches YouTube Music for songs matching [query].
     * Returns a list of playable [Song] instances tagged with "yt_" ID prefix.
     */
    suspend fun searchYouTubeMusic(query: String, limit: Int = 15): List<Song> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return@withContext emptyList()

        try {
            val body = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20250101.01.00")
                        put("hl", "en")
                        put("gl", "IN")
                    })
                })
                put("query", trimmed)
                // Filter specifically for tracks / songs
                put("params", "EgWKAQIIAWoKEAMQBBAJEAoQBQ==")
            }.toString().toRequestBody(JSON_MEDIA_TYPE)

            val req = Request.Builder()
                .url("$INNERTUBE_API_URL/search?key=$INNERTUBE_API_KEY&prettyPrint=false")
                .post(body)
                .header("Content-Type", "application/json")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20250101.01.00")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .build()

            val text = NetworkService.httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (text.isBlank()) return@withContext emptyList()

            val results = mutableListOf<Song>()
            parseSearchResults(JSONObject(text), results, limit)
            results
        } catch (e: Exception) {
            Log.w(TAG, "searchYouTubeMusic failed for '$trimmed': ${e.message}")
            emptyList()
        }
    }

    private fun parseSearchResults(json: JSONObject, out: MutableList<Song>, limit: Int) {
        try {
            val tabs = json
                .optJSONObject("contents")
                ?.optJSONObject("tabbedSearchResultsRenderer")
                ?.optJSONArray("tabs") ?: return

            val sectionContents = tabs.getJSONObject(0)
                .optJSONObject("tabRenderer")
                ?.optJSONObject("content")
                ?.optJSONObject("sectionListRenderer")
                ?.optJSONArray("contents") ?: return

            for (i in 0 until sectionContents.length()) {
                val shelf = sectionContents.getJSONObject(i)
                    .optJSONObject("musicShelfRenderer") ?: continue
                val items = shelf.optJSONArray("contents") ?: continue

                for (j in 0 until items.length()) {
                    if (out.size >= limit) return
                    val item = items.getJSONObject(j)
                        .optJSONObject("musicResponsiveListItemRenderer") ?: continue

                    // Extract Video ID
                    val videoId = item
                        .optJSONObject("overlay")
                        ?.optJSONObject("musicItemThumbnailOverlayRenderer")
                        ?.optJSONObject("content")
                        ?.optJSONObject("musicPlayButtonRenderer")
                        ?.optJSONObject("playNavigationEndpoint")
                        ?.optJSONObject("watchEndpoint")
                        ?.optString("videoId")
                        ?.takeIf { it.isNotBlank() }
                        ?: item.optJSONObject("playlistItemData")?.optString("videoId")
                        ?: continue

                    val flexColumns = item.optJSONArray("flexColumns") ?: continue
                    if (flexColumns.length() < 1) continue

                    // Column 1: Title
                    val col1Runs = flexColumns.getJSONObject(0)
                        .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                        ?.optJSONObject("text")
                        ?.optJSONArray("runs")
                    val title = col1Runs?.optJSONObject(0)?.optString("text")?.trim() ?: ""
                    if (title.isBlank()) continue

                    // Column 2: Artist & Album info
                    var artist = "YouTube"
                    var albumName = ""
                    var durationSec = 0L

                    if (flexColumns.length() >= 2) {
                        val col2Runs = flexColumns.getJSONObject(1)
                            .optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                            ?.optJSONObject("text")
                            ?.optJSONArray("runs")

                        if (col2Runs != null) {
                            val artistsList = mutableListOf<String>()
                            var afterBullet = false
                            for (r in 0 until col2Runs.length()) {
                                val runText = col2Runs.optJSONObject(r)?.optString("text")?.trim() ?: ""
                                if (runText == "•") {
                                    afterBullet = true
                                    continue
                                }
                                if (runText.matches(Regex("""\d+:\d+(:\d+)?"""))) {
                                    durationSec = parseDurationStringToSec(runText)
                                    continue
                                }
                                if (runText.equals("Song", ignoreCase = true) || runText.equals("Video", ignoreCase = true)) {
                                    continue
                                }
                                if (!afterBullet) {
                                    artistsList.add(runText)
                                } else if (albumName.isBlank()) {
                                    albumName = runText
                                }
                            }
                            if (artistsList.isNotEmpty()) {
                                artist = artistsList.joinToString(", ")
                            }
                        }
                    }

                    // Thumbnail
                    var rawThumb = ""
                    val thumbArr = item.optJSONObject("thumbnail")
                        ?.optJSONObject("musicThumbnailRenderer")
                        ?.optJSONObject("thumbnail")
                        ?.optJSONArray("thumbnails")
                    if (thumbArr != null && thumbArr.length() > 0) {
                        rawThumb = thumbArr.optJSONObject(thumbArr.length() - 1)?.optString("url") ?: ""
                    }
                    if (rawThumb.isBlank()) {
                        rawThumb = "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
                    }
                    val poster = Song.resolvePoster(title, rawThumb)

                    out.add(
                        Song(
                            id = "yt_$videoId",
                            title = title,
                            artist = artist,
                            artworkUrl = poster,
                            duration = durationSec * 1000L,
                            language = "YouTube",
                            album = albumName.ifBlank { "YouTube" }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "parseSearchResults error: ${e.message}")
        }
    }

    private fun parseDurationStringToSec(text: String): Long {
        return try {
            val parts = text.split(":")
            when (parts.size) {
                2 -> parts[0].toLong() * 60 + parts[1].toLong()
                3 -> parts[0].toLong() * 3600 + parts[1].toLong() * 60 + parts[2].toLong()
                else -> 0L
            }
        } catch (_: Exception) { 0L }
    }
}
