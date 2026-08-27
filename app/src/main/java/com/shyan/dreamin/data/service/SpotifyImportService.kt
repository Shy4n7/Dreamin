package com.shyan.dreamin.data.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.network.NetworkService
import okhttp3.Request

data class SpotifyImportedTrack(
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
    val artworkUrl: String = "",
    val suggestedCandidate: Song? = null
)

data class SpotifyPlaylistDetails(
    val id: String,
    val title: String,
    val coverUrl: String,
    val totalTracks: Int,
    val tracks: List<SpotifyImportedTrack>
)

/**
 * 🎵 High-Performance Spotify Playlist Import Service.
 *
 * Direct, zero-bloat pipeline:
 * 1. Resolves short & standard Spotify playlist URLs.
 * 2. Fetches playlist metadata & session token from public embed endpoint (tracks 1–100).
 * 3. Extracts complete playlist tracklist via spclient (tracks 101–5,000+).
 * 4. Resolves missing track metadata concurrently with OkHttp connection pooling & OEmbed fallback.
 */
object SpotifyImportService {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    /**
     * Extracts the 22-character Spotify Playlist ID from any Spotify URL.
     * Supports open.spotify.com, spotify.link redirects, intl prefixes, and query parameters.
     */
    suspend fun resolvePlaylistId(inputUrl: String): String? = withContext(Dispatchers.IO) {
        try {
            var urlStr = inputUrl.trim()
            if (!urlStr.startsWith("http://") && !urlStr.startsWith("https://")) {
                urlStr = "https://$urlStr"
            }

            // If it's a short link (spotify.link / spoti.fi), follow redirect
            if (urlStr.contains("spotify.link") || urlStr.contains("spoti.fi")) {
                val conn = URL(urlStr).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = false
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.connectTimeout = 8000
                conn.readTimeout = 8000
                conn.connect()
                val redirectUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (!redirectUrl.isNullOrBlank()) {
                    urlStr = redirectUrl
                }
            }

            // Match playlist ID: /playlist/([a-zA-Z0-9]{22})
            val pattern = Pattern.compile("playlist[/:]([a-zA-Z0-9]{22})")
            val matcher = pattern.matcher(urlStr)
            if (matcher.find()) {
                return@withContext matcher.group(1)
            }
            null
        } catch (e: Exception) {
            android.util.Log.w("SpotifyImport", "resolvePlaylistId failed: ${e.message}")
            null
        }
    }

    /**
     * Fetches complete playlist details and all tracks using session-token pagination.
     */
    suspend fun fetchPlaylistDetails(playlistId: String, context: Context? = null): SpotifyPlaylistDetails? = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            val url = URL("https://open.spotify.com/embed/playlist/$playlistId")
            conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode != 200) {
                android.util.Log.w("SpotifyImport", "Embed request failed with HTTP ${conn.responseCode}")
                return@withContext null
            }

            val html = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }

            // Extract <script id="__NEXT_DATA__" type="application/json">...</script>
            val marker = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            val startIndex = html.indexOf(marker)
            if (startIndex == -1) return@withContext null

            val jsonStart = startIndex + marker.length
            val jsonEnd = html.indexOf("</script>", jsonStart)
            if (jsonEnd == -1) return@withContext null

            val jsonStr = html.substring(jsonStart, jsonEnd).trim()
            val root = JSONObject(jsonStr)

            val props = root.optJSONObject("props") ?: return@withContext null
            val pageProps = props.optJSONObject("pageProps") ?: return@withContext null
            val state = pageProps.optJSONObject("state") ?: return@withContext null
            val data = state.optJSONObject("data") ?: return@withContext null
            val entity = data.optJSONObject("entity") ?: return@withContext null

            val title = entity.optString("name", "Spotify Playlist")
            val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            val coverUrl = coverSources?.optJSONObject(0)?.optString("url") ?: ""

            val trackListArray = entity.optJSONArray("trackList") ?: return@withContext null
            val tracks = mutableListOf<SpotifyImportedTrack>()

            for (i in 0 until trackListArray.length()) {
                val item = trackListArray.getJSONObject(i)
                val trackTitle = item.optString("title", "").trim()
                var artistName = item.optString("subtitle", "").trim()

                if (artistName.isBlank()) {
                    val artistsArray = item.optJSONArray("artists")
                    if (artistsArray != null && artistsArray.length() > 0) {
                        val names = mutableListOf<String>()
                        for (a in 0 until artistsArray.length()) {
                            val artObj = artistsArray.optJSONObject(a)
                            val name = artObj?.optString("name")
                            if (!name.isNullOrBlank()) names.add(name)
                        }
                        artistName = names.joinToString(", ")
                    }
                }

                val duration = item.optLong("duration", 0L)
                val trackCover = item.optString("displayImageUri").ifBlank {
                    item.optJSONObject("coverArt")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")
                        ?: item.optJSONObject("album")?.optJSONObject("coverArt")?.optJSONArray("sources")?.optJSONObject(0)?.optString("url")
                        ?: ""
                }

                if (trackTitle.isNotBlank()) {
                    tracks.add(
                        SpotifyImportedTrack(
                            title = trackTitle,
                            artist = if (artistName.isNotBlank()) artistName else "Unknown Artist",
                            durationMs = duration,
                            artworkUrl = trackCover
                        )
                    )
                }
            }

            // Extract dynamic session accessToken to paginate beyond 100 songs via spclient
            val tokenRegex = Regex(""""accessToken"\s*:\s*"([A-Za-z0-9_.\-]{30,})"""")
            val sessionToken = tokenRegex.find(html)?.groupValues?.getOrNull(1)
                ?: data.optJSONObject("session")?.optString("accessToken")
                ?: data.optString("accessToken").takeIf { it.isNotBlank() }

            var reportedTotal = tracks.size

            if (!sessionToken.isNullOrBlank()) {
                var spConn: HttpURLConnection? = null
                try {
                    val spUrl = URL("https://spclient.wg.spotify.com/playlist/v2/playlist/$playlistId")
                    spConn = spUrl.openConnection() as HttpURLConnection
                    spConn.requestMethod = "GET"
                    spConn.setRequestProperty("Authorization", "Bearer $sessionToken")
                    spConn.setRequestProperty("User-Agent", USER_AGENT)
                    spConn.setRequestProperty("Accept", "application/json")
                    spConn.setRequestProperty("Referer", "https://open.spotify.com/")
                    spConn.connectTimeout = 8000
                    spConn.readTimeout = 8000

                    if (spConn.responseCode == 200) {
                        val spText = BufferedReader(InputStreamReader(spConn.inputStream, Charsets.UTF_8)).use { it.readText() }
                        val spRoot = JSONObject(spText)
                        val spLength = spRoot.optInt("length", 0)
                        val items = spRoot.optJSONObject("contents")?.optJSONArray("items")
                        val actualItemCount = items?.length() ?: 0
                        reportedTotal = maxOf(tracks.size, spLength, actualItemCount)

                        if (items != null && items.length() > tracks.size) {
                            val remainingUris = mutableListOf<String>()
                            for (i in tracks.size until items.length()) {
                                val u = items.optJSONObject(i)?.optString("uri", "") ?: ""
                                if (u.startsWith("spotify:track:")) {
                                    remainingUris.add(u.removePrefix("spotify:track:"))
                                }
                            }

                            if (remainingUris.isNotEmpty()) {
                                android.util.Log.d("SpotifyImport", "Fetching ${remainingUris.size} tracks beyond 100 concurrently...")
                                val fetchedTracks = fetchTracksConcurrently(remainingUris)
                                tracks.addAll(fetchedTracks)
                            }
                        }
                    }
                } catch (e: Exception) {
                    android.util.Log.w("SpotifyImport", "spclient pagination error: ${e.message}")
                } finally {
                    spConn?.disconnect()
                }
            }

            val finalTotal = maxOf(tracks.size, reportedTotal)
            android.util.Log.d("SpotifyImport", "Total tracks fetched for '$title': ${tracks.size} (total reported: $finalTotal)")

            return@withContext SpotifyPlaylistDetails(
                id = playlistId,
                title = title,
                coverUrl = coverUrl,
                totalTracks = finalTotal,
                tracks = tracks
            )
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImport", "fetchPlaylistDetails error: ${e.message}", e)
            return@withContext null
        } finally {
            conn?.disconnect()
        }
    }

    private suspend fun fetchTracksConcurrently(trackIds: List<String>): List<SpotifyImportedTrack> = coroutineScope {
        val semaphore = kotlinx.coroutines.sync.Semaphore(20)
        trackIds.map { tid ->
            async(Dispatchers.IO) {
                semaphore.acquire()
                try {
                    fetchSingleTrackResilient(tid)
                } catch (e: Exception) {
                    android.util.Log.w("SpotifyImport", "Failed to fetch track $tid: ${e.message}")
                    null
                } finally {
                    semaphore.release()
                }
            }
        }.awaitAll().filterNotNull()
    }

    private fun fetchSingleTrackResilient(trackId: String): SpotifyImportedTrack? {
        // Pass 1: Spotify Embed Track page via connection-pooled HTTP client
        for (attempt in 0..1) {
            try {
                val request = Request.Builder()
                    .url("https://open.spotify.com/embed/track/$trackId")
                    .header("User-Agent", USER_AGENT)
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .build()

                NetworkService.httpClient.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        val html = response.body?.string().orEmpty()
                        val marker = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
                        val startIndex = html.indexOf(marker)
                        if (startIndex != -1) {
                            val jsonStart = startIndex + marker.length
                            val jsonEnd = html.indexOf("</script>", jsonStart)
                            if (jsonEnd != -1) {
                                val root = JSONObject(html.substring(jsonStart, jsonEnd).trim())
                                val entity = root.optJSONObject("props")?.optJSONObject("pageProps")?.optJSONObject("state")?.optJSONObject("data")?.optJSONObject("entity")
                                if (entity != null) {
                                    val title = entity.optString("name").ifBlank { entity.optString("title", "") }.trim()
                                    if (title.isNotBlank()) {
                                        val artistsArr = entity.optJSONArray("artists")
                                        val artists = mutableListOf<String>()
                                        if (artistsArr != null) {
                                            for (a in 0 until artistsArr.length()) {
                                                val name = artistsArr.optJSONObject(a)?.optString("name")
                                                if (!name.isNullOrBlank()) artists.add(name)
                                            }
                                        }
                                        val artistName = if (artists.isNotEmpty()) artists.joinToString(", ") else entity.optString("subtitle", "Unknown Artist")
                                        val duration = entity.optLong("duration", 0L)
                                        val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
                                        val coverUrl = coverSources?.optJSONObject(0)?.optString("url") ?: ""

                                        return SpotifyImportedTrack(
                                            title = title,
                                            artist = artistName,
                                            durationMs = duration,
                                            artworkUrl = coverUrl
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (_: Exception) {
                // Retry once
            }
        }

        // Pass 2: OEmbed Fallback
        try {
            val oeRequest = Request.Builder()
                .url("https://open.spotify.com/oembed?url=https://open.spotify.com/track/$trackId")
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .build()

            NetworkService.httpClient.newCall(oeRequest).execute().use { response ->
                if (response.isSuccessful) {
                    val oeJson = JSONObject(response.body?.string().orEmpty())
                    val title = oeJson.optString("title", "").trim()
                    val thumbUrl = oeJson.optString("thumbnail_url", "")
                    if (title.isNotBlank()) {
                        return SpotifyImportedTrack(
                            title = title,
                            artist = "Unknown Artist",
                            durationMs = 0L,
                            artworkUrl = thumbUrl
                        )
                    }
                }
            }
        } catch (_: Exception) {
            // Silently proceed
        }

        return null
    }
}
