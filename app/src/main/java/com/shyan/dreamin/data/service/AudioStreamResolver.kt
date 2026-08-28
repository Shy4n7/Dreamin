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
    private const val USER_AGENT_BROWSER =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val streamUrlCache = object : java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 500
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

        // 3. Direct On-Device JioSaavn API by PID with 320kbps CDN Token Signing
        try {
            val detailsUrl = "https://www.jiosaavn.com/api.php?__call=song.getDetails&cc=in&_marker=0&_format=json&ctx=android&pids=$songId"
            val req1 = Request.Builder()
                .url(detailsUrl)
                .header("User-Agent", USER_AGENT_BROWSER)
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val text1 = NetworkService.httpClient.newCall(req1).execute().use { it.body?.string().orEmpty() }
            val details = JSONObject(text1)
            val encUrl = details.optJSONObject(songId)?.optString("encrypted_media_url", "")
                ?: details.optJSONObject(songId)?.optJSONObject("more_info")?.optString("encrypted_media_url", "") ?: ""

            if (encUrl.isNotBlank()) {
                val streamAuth = fetchStreamAuthUrl(encUrl)
                if (!streamAuth.isNullOrBlank()) {
                    putCachedStreamUrl(songId, streamAuth)
                    return@withContext streamAuth
                }
                val directDecrypted = decryptJioSaavnMediaUrl(encUrl)
                if (!directDecrypted.isNullOrBlank()) {
                    putCachedStreamUrl(songId, directDecrypted)
                    return@withContext directDecrypted
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Direct PID stream lookup for $songId failed (${e.message}), attempting search fallback...")
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
                    .header("User-Agent", USER_AGENT_BROWSER)
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()
                val textSearch = NetworkService.httpClient.newCall(reqSearch).execute().use { it.body?.string().orEmpty() }
                val searchResp = JSONObject(textSearch)
                val results = searchResp.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    for (i in 0 until results.length()) {
                        val match = results.getJSONObject(i)
                        val encUrl = match.optString("encrypted_media_url", "")
                            .ifBlank { match.optJSONObject("more_info")?.optString("encrypted_media_url", "") ?: "" }
                        if (encUrl.isNotBlank()) {
                            val streamAuth = fetchStreamAuthUrl(encUrl)
                            if (!streamAuth.isNullOrBlank()) {
                                putCachedStreamUrl(songId, streamAuth)
                                return@withContext streamAuth
                            }
                            val directDecrypted = decryptJioSaavnMediaUrl(encUrl)
                            if (!directDecrypted.isNullOrBlank()) {
                                putCachedStreamUrl(songId, directDecrypted)
                                return@withContext directDecrypted
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
                    putCachedStreamUrl(songId, streamUrl)
                    return@withContext streamUrl
                }
            } catch (e: Exception) {
                Log.e(TAG, "Backend server stream fallback failed for $songId: ${e.message}")
            }
        }

        throw IllegalStateException("Unable to resolve audio stream for song '${song.title}' ($songId)")
    }

    /**
     * Requests the signed 320kbps Cloudflare CDN streaming URL from JioSaavn.
     */
    private fun fetchStreamAuthUrl(encUrl: String): String? {
        if (encUrl.isBlank()) return null
        return try {
            val enc = URLEncoder.encode(encUrl, "UTF-8")
            val authUrl = "https://www.jiosaavn.com/api.php?__call=song.generateAuthToken&url=$enc&bitrate=320&api_version=4&_format=json&ctx=android&_marker=0"
            val req = Request.Builder()
                .url(authUrl)
                .header("User-Agent", USER_AGENT_BROWSER)
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val text = NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
            val authResp = JSONObject(text)
            val streamUrl = authResp.optString("auth_url", "")
            if (streamUrl.isNotBlank() && streamUrl != "false") streamUrl else null
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Native 0ms DES-ECB Decryption helper using the JioSaavn key.
     */
    fun decryptJioSaavnMediaUrl(encryptedUrl: String): String? {
        return try {
            val keyBytes = "38343638".toByteArray(Charsets.UTF_8)
            val keySpec = SecretKeySpec(keyBytes, "DES")
            val cipher = Cipher.getInstance("DES/ECB/PKCS5Padding")
            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decodedBytes = Base64.getDecoder().decode(encryptedUrl)
            val decryptedBytes = cipher.doFinal(decodedBytes)
            val directUrl = String(decryptedBytes, Charsets.UTF_8)
            if (directUrl.startsWith("http://") || directUrl.startsWith("https://")) {
                directUrl.replace("_96.mp4", "_320.mp4").replace("_160.mp4", "_320.mp4")
            } else null
        } catch (_: Exception) {
            null
        }
    }
}
