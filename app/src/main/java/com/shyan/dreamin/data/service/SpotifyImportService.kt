package com.shyan.dreamin.data.service

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.util.regex.Pattern

data class SpotifyImportedTrack(
    val title: String,
    val artist: String,
    val durationMs: Long = 0L,
    val artworkUrl: String = ""
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
 * 2. Fetches playlist metadata & session token from public embed endpoint.
 * 3. Automatically paginates all tracks via official Web API (100–5,000+ tracks).
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

            // Extract dynamic session accessToken to paginate beyond 100 songs
            val sessionToken = data.optJSONObject("session")?.optString("accessToken")
                ?: data.optString("accessToken").takeIf { it.isNotBlank() }
                ?: run {
                    val tokenRegex = Regex(""""accessToken"\s*:\s*"([A-Za-z0-9_.\-]{30,})"""")
                    tokenRegex.find(html)?.groupValues?.getOrNull(1)
                }

            if (!sessionToken.isNullOrBlank() && tracks.size >= 100) {
                android.util.Log.d("SpotifyImport", "Paginating tracks using Spotify Web session token...")
                var offset = tracks.size
                var hasMore = true
                while (hasMore && offset < 5000) {
                    var pageConn: HttpURLConnection? = null
                    try {
                        val pageUrl = URL("https://api.spotify.com/v1/playlists/$playlistId/tracks?offset=$offset&limit=100")
                        pageConn = pageUrl.openConnection() as HttpURLConnection
                        pageConn.requestMethod = "GET"
                        pageConn.setRequestProperty("Authorization", "Bearer $sessionToken")
                        pageConn.setRequestProperty("User-Agent", USER_AGENT)
                        pageConn.setRequestProperty("App-Platform", "WebPlayer")
                        pageConn.setRequestProperty("Referer", "https://open.spotify.com/")
                        pageConn.connectTimeout = 8000
                        pageConn.readTimeout = 8000

                        if (pageConn.responseCode == 200) {
                            val pageText = BufferedReader(InputStreamReader(pageConn.inputStream, Charsets.UTF_8)).use { it.readText() }
                            val pageRoot = JSONObject(pageText)
                            val pItems = pageRoot.optJSONArray("items")
                            val prevCount = tracks.size
                            parseTrackItems(pItems, tracks)
                            val added = tracks.size - prevCount
                            offset += added
                            hasMore = added > 0 && pageRoot.optString("next", "").isNotBlank()
                            android.util.Log.d("SpotifyImport", "Paginated +$added tracks (accumulated: ${tracks.size})")
                        } else {
                            android.util.Log.w("SpotifyImport", "Pagination stopped at code ${pageConn.responseCode}")
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SpotifyImport", "Pagination exception: ${e.message}")
                        break
                    } finally {
                        pageConn?.disconnect()
                    }
                }
            }

            android.util.Log.d("SpotifyImport", "Total tracks fetched for '$title': ${tracks.size}")

            return@withContext SpotifyPlaylistDetails(
                id = playlistId,
                title = title,
                coverUrl = coverUrl,
                totalTracks = tracks.size,
                tracks = tracks
            )
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImport", "fetchPlaylistDetails error: ${e.message}", e)
            return@withContext null
        } finally {
            conn?.disconnect()
        }
    }

    private fun parseTrackItems(items: JSONArray?, outList: MutableList<SpotifyImportedTrack>) {
        if (items == null) return
        for (i in 0 until items.length()) {
            val item = items.optJSONObject(i) ?: continue
            val trackObj = item.optJSONObject("track") ?: continue
            val trackName = trackObj.optString("name", "").trim()
            val artistsArray = trackObj.optJSONArray("artists")
            val artists = mutableListOf<String>()
            if (artistsArray != null) {
                for (a in 0 until artistsArray.length()) {
                    val name = artistsArray.optJSONObject(a)?.optString("name")
                    if (!name.isNullOrBlank()) artists.add(name)
                }
            }
            val durationMs = trackObj.optLong("duration_ms", 0L)
            val albumObj = trackObj.optJSONObject("album")
            val albumImages = albumObj?.optJSONArray("images")
            val artworkUrl = albumImages?.optJSONObject(0)?.optString("url") ?: ""

            if (trackName.isNotBlank()) {
                outList.add(
                    SpotifyImportedTrack(
                        title = trackName,
                        artist = if (artists.isNotEmpty()) artists.joinToString(", ") else "Unknown Artist",
                        durationMs = durationMs,
                        artworkUrl = artworkUrl
                    )
                )
            }
        }
    }
}
