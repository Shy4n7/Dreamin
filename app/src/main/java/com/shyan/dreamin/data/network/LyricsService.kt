package com.shyan.dreamin.data.network

import com.shyan.dreamin.data.model.LyricLine
import com.shyan.dreamin.data.model.LyricsState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object LyricsService {

    private val LRC_REGEX = Regex("""\[(\d{1,2}):(\d{2}(?:\.\d{1,3})?)\](.*)""")
    private val OFFSET_REGEX = Regex("""(?i)\[offset:\s*([+-]?\d+)\]""")
    private val lyricsCache = android.util.LruCache<String, LyricsState>(100)

    suspend fun fetchLyrics(trackName: String, artistName: String, durationSec: Long): LyricsState = withContext(Dispatchers.IO) {
        // Clean track title
        val cleanTitle = trackName
            .replace(Regex("""(?i)\s*[\(\[]?\s*(?:from|movie|ost|official|audio|video|lyric|song|hd).*?[\)\]]?"""), "")
            .replace(Regex("""[\(\[].*?[\)\]]"""), "")
            .trim()
            .ifBlank { trackName }

        val cleanArtist = artistName.split(",").firstOrNull()?.trim() ?: artistName
        val cacheKey = "$cleanTitle-$cleanArtist".lowercase()

        // 1. Instant 0ms RAM Cache Hit
        lyricsCache.get(cacheKey)?.let { return@withContext it }

        // 2. Try LRCLIB Search with language-weighting and candidate scoring
        try {
            val queries = listOf(
                "$cleanTitle $cleanArtist",
                "$cleanTitle Tamil",
                cleanTitle
            )

            for (query in queries) {
                val encodedQuery = URLEncoder.encode(query, "UTF-8")
                val searchUrl = "https://lrclib.net/api/search?q=$encodedQuery"
                val reqSearch = okhttp3.Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", "DreaminMusicPlayer/2.0 (https://github.com/Shy4n7/Dreamin)")
                    .build()
                val jsonStr = NetworkService.httpClient.newCall(reqSearch).execute().use { resp ->
                    if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
                }
                if (jsonStr.isNotBlank()) {
                    val arr = org.json.JSONArray(jsonStr)
                    var bestItem: JSONObject? = null
                    var highestScore = -999

                    for (i in 0 until arr.length().coerceAtMost(10)) {
                        val item = arr.getJSONObject(i)
                        val score = scoreCandidate(item, cleanTitle, cleanArtist, durationSec)
                        if (score > highestScore) {
                            highestScore = score
                            bestItem = item
                        }
                    }

                    if (bestItem != null && highestScore > 0) {
                        val synced = bestItem.optString("syncedLyrics", "")
                        if (synced.isNotBlank()) {
                            val parsed = parseLrc(synced)
                            if (parsed.isNotEmpty()) {
                                val state = LyricsState.Success(parsed, isSynced = true)
                                lyricsCache.put(cacheKey, state)
                                return@withContext state
                            }
                        }

                        val plain = bestItem.optString("plainLyrics", "")
                        if (plain.isNotBlank()) {
                            val lines = plain.lines().map { it.trim() }.filter { it.isNotBlank() }
                            val plainParsed = lines.mapIndexed { idx, line ->
                                LyricLine(timestampMs = idx * 4000L, text = line)
                            }
                            val state = LyricsState.Success(plainParsed, isSynced = false)
                            lyricsCache.put(cacheKey, state)
                            return@withContext state
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("LyricsService", "LRCLIB search error: ${e.message}")
        }

        // 3. Try LRCLIB Exact GET as fallback
        try {
            val encodedTrack = URLEncoder.encode(cleanTitle, "UTF-8")
            val encodedArtist = URLEncoder.encode(cleanArtist, "UTF-8")
            var urlStr = "https://lrclib.net/api/get?track_name=$encodedTrack&artist_name=$encodedArtist"
            if (durationSec > 0) {
                urlStr += "&duration=$durationSec"
            }

            val req = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "DreaminMusicPlayer/2.0 (https://github.com/Shy4n7/Dreamin)")
                .build()
            val jsonStr = NetworkService.httpClient.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string().orEmpty() else ""
            }
            if (jsonStr.isNotBlank()) {
                val json = JSONObject(jsonStr)
                val score = scoreCandidate(json, cleanTitle, cleanArtist, durationSec)
                if (score > 0) {
                    val syncedLyrics = json.optString("syncedLyrics", "")
                    if (syncedLyrics.isNotBlank()) {
                        val parsed = parseLrc(syncedLyrics)
                        if (parsed.isNotEmpty()) {
                            val state = LyricsState.Success(parsed, isSynced = true)
                            lyricsCache.put(cacheKey, state)
                            return@withContext state
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("LyricsService", "LRCLIB exact fetch error: ${e.message}")
        }

        // 4. JioSaavn Lyrics API Fallback for South Indian & Bollywood tracks
        try {
            val saavnSearchQuery = URLEncoder.encode("$cleanTitle $cleanArtist", "UTF-8")
            val saavnSearchUrl = "https://www.jiosaavn.com/api.php?__call=autocomplete.get&query=$saavnSearchQuery&_format=json&_marker=0&ctx=android"
            val reqSaavn = okhttp3.Request.Builder()
                .url(saavnSearchUrl)
                .header("User-Agent", "Mozilla/5.0")
                .build()
            val jsonStr = NetworkService.httpClient.newCall(reqSaavn).execute().use { respSaavn ->
                if (respSaavn.isSuccessful) respSaavn.body?.string().orEmpty() else ""
            }
            if (jsonStr.isNotBlank()) {
                val root = JSONObject(jsonStr)
                val songsArr = root.optJSONObject("songs")?.optJSONArray("data")
                val songId = songsArr?.optJSONObject(0)?.optString("id", "")
                if (!songId.isNullOrBlank()) {
                    val lyricsUrl = "https://www.jiosaavn.com/api.php?__call=lyrics.getLyrics&lyrics_id=$songId&_format=json&_marker=0&ctx=android"
                    val reqLyrics = okhttp3.Request.Builder().url(lyricsUrl).header("User-Agent", "Mozilla/5.0").build()
                    val lyricJsonStr = NetworkService.httpClient.newCall(reqLyrics).execute().use { respLyrics ->
                        if (respLyrics.isSuccessful) respLyrics.body?.string().orEmpty() else ""
                    }
                    if (lyricJsonStr.isNotBlank()) {
                        val lyricJson = JSONObject(lyricJsonStr)
                        val rawLyrics = lyricJson.optString("lyrics", "")
                        if (rawLyrics.isNotBlank()) {
                            val cleanLyrics = rawLyrics
                                .replace("<br />", "\n")
                                .replace("<br>", "\n")
                                .replace("<br/>", "\n")
                                .replace("&quot;", "\"")
                                .replace("&amp;", "&")
                                .lines()
                                .map { it.trim() }
                                .filter { it.isNotBlank() }

                            if (cleanLyrics.isNotEmpty()) {
                                val plainParsed = cleanLyrics.mapIndexed { idx, line ->
                                    LyricLine(timestampMs = idx * 4000L, text = line)
                                }
                                val state = LyricsState.Success(plainParsed, isSynced = false)
                                lyricsCache.put(cacheKey, state)
                                return@withContext state
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.d("LyricsService", "JioSaavn lyrics fallback error: ${e.message}")
        }

        LyricsState.NotFound
    }

    private fun scoreCandidate(
        item: JSONObject,
        cleanTitle: String,
        cleanArtist: String,
        targetDurationSec: Long
    ): Int {
        val synced = item.optString("syncedLyrics", "")
        val plain = item.optString("plainLyrics", "")
        val lyricsText = (synced.ifBlank { plain }).lowercase()
        val track = item.optString("trackName", "").lowercase()
        val artist = item.optString("artistName", "").lowercase()
        val album = item.optString("albumName", "").lowercase()
        val itemDur = item.optLong("duration", 0L)

        var score = 0

        if (synced.isNotBlank()) score += 50
        else if (plain.isNotBlank()) score += 10

        // Exact track title match
        if (track.contains(cleanTitle.lowercase())) score += 35

        // Tamil script or Tamil keywords match
        val hasTamilChars = lyricsText.any { it in '\u0B80'..'\u0BFF' }
        if (hasTamilChars || album.contains("tamil") || track.contains("tamil") || artist.contains("anirudh") || artist.contains("dhanush")) {
            score += 45
        }

        // Heavy penalty for Telugu dubs if searching for Tamil original (e.g. Kannuladha vs Kannazhaga)
        val hasTeluguChars = lyricsText.any { it in '\u0C00'..'\u0C7F' }
        if (hasTeluguChars || album.contains("telugu") || track.contains("telugu") || lyricsText.contains("kannuladha") || lyricsText.contains("nuvvu")) {
            score -= 100
        }

        // Duration proximity
        if (targetDurationSec > 0 && itemDur > 0) {
            val delta = Math.abs(itemDur - targetDurationSec)
            if (delta <= 3) score += 30
            else if (delta <= 7) score += 15
            else score -= 20
        }

        return score
    }

    private fun parseLrc(lrcContent: String): List<LyricLine> {
        val list = mutableListOf<LyricLine>()
        var offsetMs = 0L

        // Check for [offset: +/-ms] tag
        val offsetMatch = OFFSET_REGEX.find(lrcContent)
        if (offsetMatch != null) {
            offsetMs = offsetMatch.groupValues[1].toLongOrNull() ?: 0L
        }

        lrcContent.lineSequence().forEach { line ->
            val match = LRC_REGEX.find(line.trim())
            if (match != null) {
                val min = match.groupValues[1].toLongOrNull() ?: 0L
                val secFloat = match.groupValues[2].toDoubleOrNull() ?: 0.0
                val text = match.groupValues[3].trim()
                val rawTimestampMs = (min * 60 * 1000L) + (secFloat * 1000).toLong()
                val timestampMs = (rawTimestampMs + offsetMs).coerceAtLeast(0L)
                if (text.isNotBlank() && !text.startsWith("[") && !text.endsWith("]")) {
                    list.add(LyricLine(timestampMs = timestampMs, text = text))
                }
            }
        }
        return list.sortedBy { it.timestampMs }
    }
}
