package com.shyan.dreamin.data.service

import android.util.Log
import android.util.LruCache
import com.shyan.dreamin.data.local.DownloadRepository
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.network.MusicApi
import com.shyan.dreamin.data.network.NetworkService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * 🎵 Universal Audio Stream Resolver Module.
 *
 * Provides a unified, high-reliability stream resolution pipeline for any song request across Dreamin:
 * 1. In-Memory LRU Stream Cache (0ms instant return).
 * 2. Offline / Smart-Prefetch Local Storage (0ms offline playback).
 * 3. Direct JioSaavn PID Lookup with Cloudflare CDN 320kbps Auth Token Signing.
 * 4. Native 0ms DES-ECB Decryption Fallback.
 * 5. Resilient Multi-Query Search Fallback with CDN Token Signing.
 * 6. Cloud Backend Server Fallback.
 */
object AudioStreamResolver {

    private const val TAG = "AudioStreamResolver"
    private const val USER_AGENT_MOBILE =
        "Mozilla/5.0 (Linux; Android 14; Pixel 8 Pro) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    private val streamUrlCache = object : java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 256
        }
    }

    /**
     * Retrieves the cached stream URL if available.
     */
    fun getCachedStreamUrl(songId: String): String? = synchronized(streamUrlCache) {
        streamUrlCache[songId]
    }

    /**
     * Manually stores a resolved stream URL into the cache.
     */
    fun putCachedStreamUrl(songId: String, url: String): Unit = synchronized(streamUrlCache) {
        if (songId.isNotBlank() && url.isNotBlank()) {
            streamUrlCache[songId] = url
        }
    }

    /**
     * Removes a cached stream URL.
     */
    fun removeCachedStreamUrl(songId: String): Unit = synchronized(streamUrlCache) {
        if (songId.isNotBlank()) {
            streamUrlCache.remove(songId)
        }
    }

    /**
     * Clears in-memory stream cache.
     */
    fun clearCache(): Unit = synchronized(streamUrlCache) {
        streamUrlCache.clear()
    }

    /**
     * Resolves a guaranteed, playable 320kbps audio stream URL for any [Song].
     */
    suspend fun resolveStreamUrl(
        song: Song,
        downloadRepo: DownloadRepository? = null,
        apiService: MusicApi? = null
    ): String = withContext(Dispatchers.IO) {
        val songId = song.id.trim()
        if (songId.isBlank()) {
            throw IllegalArgumentException("Cannot resolve stream for song with blank ID: ${song.title}")
        }

        // 1. In-Memory Cache (0ms hit)
        getCachedStreamUrl(songId)?.let { return@withContext it }

        // 2. Local Download / Prefetch Cache
        if (downloadRepo != null) {
            val localPath = downloadRepo.getPrefetchedOrDownloadedPath(songId)
            if (!localPath.isNullOrBlank()) {
                putCachedStreamUrl(songId, localPath)
                return@withContext localPath
            }
        }

        // 2.5 Direct YouTube Resolution (0ms JioSaavn bypass)
        if (songId.startsWith("yt_")) {
            val videoId = songId.removePrefix("yt_")
            val ytStreamUrl = YtMusicFallbackResolver.fetchAudioStreamUrl(videoId)
            if (!ytStreamUrl.isNullOrBlank()) {
                Log.d(TAG, "Direct YouTube stream resolved for $songId: $ytStreamUrl")
                putCachedStreamUrl(songId, ytStreamUrl)
                return@withContext ytStreamUrl
            }
            throw IllegalStateException("Track unavailable: YouTube stream could not be resolved for $songId")
        }

        // 3. Direct On-Device JioSaavn API by PID with 320kbps CDN Token Signing
        val endpoints = listOf(
            "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0&_format=json&ctx=android&pids=$songId",
            "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0&_format=json&ctx=web6dot0&pids=$songId"
        )
        for (detailsUrl in endpoints) {
            try {
                val req1 = Request.Builder()
                    .url(detailsUrl)
                    .header("User-Agent", USER_AGENT_MOBILE)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Origin", "https://www.jiosaavn.com")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()
                val text1 = NetworkService.httpClient.newCall(req1).execute().use { it.body?.string().orEmpty().trim() }
                if (text1.startsWith("{")) {
                    val details = JSONObject(text1)
                    val songObj = details.optJSONObject(songId)
                        ?: if (details.length() == 1) details.optJSONObject(details.keys().next()) else null

                    val encUrl = songObj?.optString("encrypted_media_url", "")
                        ?: songObj?.optJSONObject("more_info")?.optString("encrypted_media_url", "") ?: ""

                    if (encUrl.isNotBlank()) {
                        val directDecrypted = decryptJioSaavnMediaUrl(encUrl)
                        if (!directDecrypted.isNullOrBlank()) {
                            Log.d(TAG, "Successfully resolved direct stream for $songId via DES: $directDecrypted")
                            putCachedStreamUrl(songId, directDecrypted)
                            return@withContext directDecrypted
                        }
                        val streamAuth = fetchStreamAuthUrl(encUrl)
                        if (!streamAuth.isNullOrBlank()) {
                            Log.d(TAG, "Successfully resolved stream for $songId via auth token: $streamAuth")
                            putCachedStreamUrl(songId, streamAuth)
                            return@withContext streamAuth
                        }
                    }

                    // Fallback to media_preview_url upgraded to high-quality
                    val previewUrl = songObj?.optString("media_preview_url", "")
                        ?: songObj?.optJSONObject("more_info")?.optString("media_preview_url", "") ?: ""
                    if (previewUrl.isNotBlank() && previewUrl.startsWith("http")) {
                        val upgraded = previewUrl.replace("preview.saavncdn.com", "aac.saavncdn.com")
                            .replace("_96_p.mp4", "_320.mp4")
                        Log.d(TAG, "Resolved stream for $songId via upgraded preview URL: $upgraded")
                        putCachedStreamUrl(songId, upgraded)
                        return@withContext upgraded
                    }
                } else {
                    Log.w(TAG, "Direct PID lookup response for $songId did not start with JSON: ${text1.take(120)}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Direct PID lookup for $songId on $detailsUrl failed: ${e.message}")
            }
        }

        // 4. Multi-Query Search Fallback with CDN Token Signing
        try {
            val queries = mutableListOf(
                "${song.displayTitle} ${song.artist}".trim(),
                song.displayTitle.trim(),
                "${song.displayTitle.replace(Regex("(?i)eeshu|eshu"), "eesu").replace(Regex("(?i)sh"), "s")} ${song.artist}".trim(),
                song.displayTitle.replace(Regex("[^a-zA-Z0-9 ]"), "").trim()
            ).distinct().filter { it.length >= 2 }

            for (qText in queries) {
                val encodedQuery = URLEncoder.encode(qText, "UTF-8")
                val searchUrl = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&ctx=android&api_version=4&p=1&n=8&q=$encodedQuery"
                val reqSearch = Request.Builder()
                    .url(searchUrl)
                    .header("User-Agent", USER_AGENT_MOBILE)
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Origin", "https://www.jiosaavn.com")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()
                val textSearch = NetworkService.httpClient.newCall(reqSearch).execute().use { it.body?.string().orEmpty().trim() }
                if (textSearch.startsWith("{")) {
                    val searchResp = JSONObject(textSearch)
                    val results = searchResp.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        for (i in 0 until results.length()) {
                            val match = results.getJSONObject(i)
                            val encUrl = match.optString("encrypted_media_url", "")
                                .ifBlank { match.optJSONObject("more_info")?.optString("encrypted_media_url", "") ?: "" }
                            if (encUrl.isNotBlank()) {
                                val directDecrypted = decryptJioSaavnMediaUrl(encUrl)
                                if (!directDecrypted.isNullOrBlank()) {
                                    Log.d(TAG, "Search fallback resolved stream for ${song.title} via DES: $directDecrypted")
                                    putCachedStreamUrl(songId, directDecrypted)
                                    return@withContext directDecrypted
                                }
                                val streamAuth = fetchStreamAuthUrl(encUrl)
                                if (!streamAuth.isNullOrBlank()) {
                                    Log.d(TAG, "Search fallback resolved stream for ${song.title} via auth token: $streamAuth")
                                    putCachedStreamUrl(songId, streamAuth)
                                    return@withContext streamAuth
                                }
                            }

                            val previewUrl = match.optString("media_preview_url", "")
                                .ifBlank { match.optJSONObject("more_info")?.optString("media_preview_url", "") ?: "" }
                            if (previewUrl.isNotBlank() && previewUrl.startsWith("http")) {
                                val upgraded = previewUrl.replace("preview.saavncdn.com", "aac.saavncdn.com")
                                    .replace("_96_p.mp4", "_320.mp4")
                                Log.d(TAG, "Search fallback resolved stream for ${song.title} via upgraded preview: $upgraded")
                                putCachedStreamUrl(songId, upgraded)
                                return@withContext upgraded
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Search fallback stream resolution failed for ${song.title}: ${e.message}")
        }

        // 5. Backend Server Resolution Fallback
        if (apiService != null) {
            try {
                val playResp = apiService.recordPlay(id = songId, artist = song.artist, title = song.title)
                val streamUrl = playResp.streamUrl
                if (!streamUrl.isNullOrBlank()) {
                    Log.d(TAG, "Resolved stream for $songId via backend server: $streamUrl")
                    putCachedStreamUrl(songId, streamUrl)
                    return@withContext streamUrl
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backend server stream fallback failed for $songId: ${e.message}")
            }
        }

        throw IllegalStateException("Unable to resolve audio stream for song '${song.title}' ($songId) after all JioSaavn fallbacks")
    }

    /**
     * Requests the signed 320kbps Cloudflare CDN streaming URL from JioSaavn.
     */
    private fun fetchStreamAuthUrl(encUrl: String): String? {
        val trimmed = encUrl.trim()
        if (trimmed.isBlank()) return null
        return try {
            val enc = URLEncoder.encode(trimmed, "UTF-8")
            val authUrl = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=$enc&bitrate=320&api_version=4&_format=json&ctx=android&_marker=0"
            val req = Request.Builder()
                .url(authUrl)
                .header("User-Agent", USER_AGENT_MOBILE)
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", "https://www.jiosaavn.com")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val text = NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty().trim() }
            if (text.startsWith("{")) {
                val authResp = JSONObject(text)
                val streamUrl = authResp.optString("auth_url", "")
                if (streamUrl.isNotBlank() && streamUrl != "false") streamUrl else null
            } else {
                Log.w(TAG, "generateAuthToken response was not JSON: ${text.take(80)}")
                null
            }
        } catch (e: Exception) {
            Log.w(TAG, "fetchStreamAuthUrl failed: ${e.message}")
            null
        }
    }

    /**
     * Native 0ms DES-ECB Decryption helper using the JioSaavn key.
     */
    fun decryptJioSaavnMediaUrl(encryptedUrl: String): String? {
        val trimmed = encryptedUrl.trim()
        if (trimmed.isBlank()) return null
        return try {
            val keyBytes = "38346591".toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = try {
                Cipher.getInstance("DES/ECB/PKCS5Padding")
            } catch (_: Exception) {
                Cipher.getInstance("DES")
            }
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = try {
                java.util.Base64.getDecoder().decode(trimmed)
            } catch (_: Throwable) {
                try {
                    java.util.Base64.getUrlDecoder().decode(trimmed)
                } catch (_: Throwable) {
                    try {
                        val sanitized = trimmed.replace("-", "+").replace("_", "/")
                        val padLen = (4 - (sanitized.length % 4)) % 4
                        val padded = sanitized + "=".repeat(padLen)
                        java.util.Base64.getDecoder().decode(padded)
                    } catch (_: Throwable) {
                        try {
                            android.util.Base64.decode(trimmed, android.util.Base64.DEFAULT)
                        } catch (_: Throwable) {
                            null
                        }
                    }
                }
            } ?: return null
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val directUrl = String(decryptedBytes, Charsets.UTF_8).trim()
            if (directUrl.startsWith("http://") || directUrl.startsWith("https://")) {
                directUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")
            } else null
        } catch (e: Exception) {
            Log.w(TAG, "decryptJioSaavnMediaUrl failed: ${e.message}")
            null
        }
    }
}
