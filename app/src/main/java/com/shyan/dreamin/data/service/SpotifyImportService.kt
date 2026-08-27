package com.shyan.dreamin.data.service

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.json.JSONArray
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

object SpotifyImportService {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

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
            e.printStackTrace()
            null
        }
    }

    /**
     * Fetches metadata and tracklist for a given Spotify Playlist ID.
     * Uses multi-tiered resolvers with full pagination to fetch all tracks (100–1000+ songs).
     */
    suspend fun fetchPlaylistDetails(playlistId: String, context: Context? = null): SpotifyPlaylistDetails? = withContext(Dispatchers.IO) {
        // Strategy 1: Official API / Session Token Grant with Full Multi-Page Pagination
        val fromApi = fetchFromWebApi(playlistId, context)
        if (fromApi != null && fromApi.tracks.size >= fromApi.totalTracks && fromApi.tracks.isNotEmpty()) {
            android.util.Log.d("SpotifyImport", "Web API Full Success: ${fromApi.tracks.size}/${fromApi.totalTracks} tracks fetched!")
            return@withContext fromApi
        }

        // Strategy 2: Multi-Page SpotifyDown Gateway Resolver
        val fromSpotifyDown = fetchViaSpotifyDownPaginated(playlistId)
        if (fromSpotifyDown != null && fromSpotifyDown.tracks.isNotEmpty()) {
            android.util.Log.d("SpotifyImport", "SpotifyDown Gateway SUCCESS: ${fromSpotifyDown.tracks.size} tracks fetched!")
            return@withContext fromSpotifyDown
        }

        // Strategy 3: SpotiSongDownloader Public Gateway Resolver
        val fromSpotiSong = fetchViaSpotiSongDownloader(playlistId)
        if (fromSpotiSong != null && fromSpotiSong.tracks.isNotEmpty()) {
            android.util.Log.d("SpotifyImport", "SpotiSongDownloader SUCCESS: ${fromSpotiSong.tracks.size} tracks fetched!")
            return@withContext fromSpotiSong
        }

        // Strategy 4: Background WebView DOM Scroll Scraper (bypasses restrictions, gets ALL 300+ tracks)
        if (context != null) {
            val fromDom = fetchPlaylistViaWebViewDom(context, playlistId)
            if (fromDom != null && fromDom.tracks.isNotEmpty()) {
                android.util.Log.d("SpotifyImport", "WebView DOM Scraper SUCCESS: ${fromDom.tracks.size} tracks fetched!")
                return@withContext fromDom
            }
        }

        // Strategy 5: Embed Page Scraping fallback
        val fromEmbed = fetchFromEmbed(playlistId)
        fromApi ?: fromEmbed
    }

    /**
     * SpotifyDown Multi-Page Gateway: Queries metadata and loops through all track batches.
     */
    private fun fetchViaSpotifyDownPaginated(playlistId: String): SpotifyPlaylistDetails? {
        try {
            var playlistTitle = "Spotify Playlist"
            var playlistCover = ""

            // 1. Fetch metadata
            try {
                val metaUrl = URL("https://api.spotifydown.com/metadata/playlist/$playlistId")
                val metaConn = metaUrl.openConnection() as HttpURLConnection
                metaConn.requestMethod = "GET"
                metaConn.setRequestProperty("User-Agent", USER_AGENT)
                metaConn.setRequestProperty("Origin", "https://spotifydown.com")
                metaConn.setRequestProperty("Referer", "https://spotifydown.com/")
                metaConn.connectTimeout = 8000
                metaConn.readTimeout = 8000
                if (metaConn.responseCode == 200) {
                    val metaJson = metaConn.inputStream.bufferedReader().use { it.readText() }
                    val metaObj = JSONObject(metaJson)
                    if (metaObj.optBoolean("success", true)) {
                        val title = metaObj.optString("title", "").ifBlank { metaObj.optString("name", "") }
                        if (title.isNotBlank()) playlistTitle = title
                        playlistCover = metaObj.optString("cover", "").ifBlank { metaObj.optString("coverUrl", "") }
                    }
                }
                metaConn.disconnect()
            } catch (_: Exception) {}

            // 2. Fetch tracks in pagination loop (offset = 0, 100, 200...)
            val allTracks = mutableListOf<SpotifyImportedTrack>()
            var offset = 0
            var hasNext = true

            while (hasNext && offset < 5000) {
                val trackUrl = if (offset == 0) {
                    URL("https://api.spotifydown.com/trackList/playlist/$playlistId")
                } else {
                    URL("https://api.spotifydown.com/trackList/playlist/$playlistId?offset=$offset")
                }

                val conn = trackUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Origin", "https://spotifydown.com")
                conn.setRequestProperty("Referer", "https://spotifydown.com/")
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                if (conn.responseCode != 200) {
                    conn.disconnect()
                    break
                }

                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val obj = JSONObject(jsonStr)
                if (!obj.optBoolean("success", true)) break

                val tracksArray = obj.optJSONArray("trackList") ?: obj.optJSONArray("tracks") ?: JSONArray()
                if (tracksArray.length() == 0) break

                for (i in 0 until tracksArray.length()) {
                    val item = tracksArray.getJSONObject(i)
                    val tTitle = item.optString("title", "").ifBlank { item.optString("name", "") }.trim()
                    val tArtist = item.optString("artists", "").ifBlank { item.optString("artist", "Unknown Artist") }.trim()
                    val tCover = item.optString("cover", "").ifBlank { item.optString("coverUrl", "") }
                    if (tTitle.isNotBlank()) {
                        allTracks.add(SpotifyImportedTrack(title = tTitle, artist = tArtist, artworkUrl = tCover))
                    }
                    if (playlistCover.isBlank() && tCover.isNotBlank()) {
                        playlistCover = tCover
                    }
                }

                val nextOffset = obj.optInt("nextOffset", -1)
                if (nextOffset > offset) {
                    offset = nextOffset
                } else {
                    offset += tracksArray.length()
                }

                hasNext = tracksArray.length() >= 100 || nextOffset > 0
            }

            if (allTracks.isNotEmpty()) {
                return SpotifyPlaylistDetails(
                    id = playlistId,
                    title = playlistTitle,
                    coverUrl = playlistCover,
                    totalTracks = allTracks.size,
                    tracks = allTracks
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("SpotifyImport", "fetchViaSpotifyDownPaginated failed: ${e.message}")
        }
        return null
    }

    /**
     * SpotiSongDownloader Public Gateway: Resolves playlist items via xtracklist endpoint.
     */
    private fun fetchViaSpotiSongDownloader(playlistId: String): SpotifyPlaylistDetails? {
        try {
            val url = URL("https://spotisongdownloader.com/api/composer/spotify/xtracklist.php?url=https://open.spotify.com/playlist/$playlistId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Referer", "https://spotisongdownloader.com/")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()

                val root = JSONObject(jsonStr)
                val tracksArray = root.optJSONArray("tracks") ?: root.optJSONArray("trackList") ?: JSONArray()
                val tracks = mutableListOf<SpotifyImportedTrack>()
                var title = root.optString("playlist_name", "Spotify Playlist")
                val coverUrl = root.optString("playlist_cover", "")

                for (i in 0 until tracksArray.length()) {
                    val item = tracksArray.getJSONObject(i)
                    val tTitle = item.optString("song_name", "").ifBlank { item.optString("name", "") }.trim()
                    val tArtist = item.optString("artist_name", "").ifBlank { item.optString("artist", "Unknown Artist") }.trim()
                    val tCover = item.optString("song_cover", "")
                    if (tTitle.isNotBlank()) {
                        tracks.add(SpotifyImportedTrack(title = tTitle, artist = tArtist, artworkUrl = tCover))
                    }
                }

                if (tracks.isNotEmpty()) {
                    return SpotifyPlaylistDetails(
                        id = playlistId,
                        title = title,
                        coverUrl = coverUrl,
                        totalTracks = tracks.size,
                        tracks = tracks
                    )
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.w("SpotifyImport", "fetchViaSpotiSongDownloader failed: ${e.message}")
        }
        return null
    }

    private suspend fun fetchPlaylistViaWebViewDom(context: Context, playlistId: String): SpotifyPlaylistDetails? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<SpotifyPlaylistDetails?>()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        var webViewRef: android.webkit.WebView? = null

        mainHandler.post {
            try {
                val cookieMgr = android.webkit.CookieManager.getInstance()
                cookieMgr.setAcceptCookie(true)

                val webView = android.webkit.WebView(context.applicationContext)
                webViewRef = webView
                webView.layout(0, 0, 1080, 4000)
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

                class DomJsBridge {
                    @android.webkit.JavascriptInterface
                    fun onPlaylistScraped(jsonStr: String?) {
                        if (!jsonStr.isNullOrBlank() && !deferred.isCompleted) {
                            try {
                                val obj = JSONObject(jsonStr)
                                val title = obj.optString("title", "Spotify Playlist")
                                val coverUrl = obj.optString("coverUrl", "")
                                val tracksArray = obj.optJSONArray("tracks") ?: org.json.JSONArray()

                                val tracks = mutableListOf<SpotifyImportedTrack>()
                                for (i in 0 until tracksArray.length()) {
                                    val tObj = tracksArray.getJSONObject(i)
                                    val trackTitle = tObj.optString("title", "").trim()
                                    val artist = tObj.optString("artist", "Unknown Artist").trim()
                                    if (trackTitle.isNotBlank()) {
                                        tracks.add(SpotifyImportedTrack(title = trackTitle, artist = artist))
                                    }
                                }

                                android.util.Log.d("SpotifyImport", "WebView DOM Scraper completed with ${tracks.size} tracks")

                                if (tracks.isNotEmpty()) {
                                    deferred.complete(
                                        SpotifyPlaylistDetails(
                                            id = playlistId,
                                            title = title,
                                            coverUrl = coverUrl,
                                            totalTracks = tracks.size,
                                            tracks = tracks
                                        )
                                    )
                                }
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }

                webView.addJavascriptInterface(DomJsBridge(), "AndroidDomBridge")

                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val jsScraper = """
                            (function() {
                                var collectedTracks = {};
                                var scrollPasses = 0;
                                var maxScrolls = 60;
                                var lastTrackCount = 0;
                                var unchangedPasses = 0;

                                function scrapeCurrentDOM() {
                                    var titleEl = document.querySelector('h1') || document.querySelector('[data-testid="playlist-page-title"]');
                                    var playlistTitle = titleEl ? titleEl.innerText.trim() : 'Spotify Playlist';
                                    
                                    var imgEl = document.querySelector('img[src*="image"]') || document.querySelector('img[src*="spotifycdn"]');
                                    var coverUrl = imgEl ? imgEl.src : '';

                                    var rows = document.querySelectorAll('[data-testid="tracklist-row"], div[role="row"]');
                                    rows.forEach(function(row) {
                                        var titleNode = row.querySelector('a[href*="/track/"]') || 
                                                        row.querySelector('[data-testid="internal-track-link"]') || 
                                                        row.querySelector('[aria-colindex="2"] a') ||
                                                        row.querySelector('[aria-colindex="2"] span');
                                                        
                                        var artistNodes = row.querySelectorAll('a[href*="/artist/"], [aria-colindex="3"] a, [data-testid="artist-name"]');
                                        
                                        var tName = titleNode ? titleNode.innerText.trim() : '';
                                        var artists = [];
                                        artistNodes.forEach(function(a) {
                                            var name = a.innerText.trim();
                                            if (name && name !== tName && artists.indexOf(name) === -1) {
                                                artists.push(name);
                                            }
                                        });

                                        if (tName) {
                                            var key = tName.toLowerCase() + '___' + artists.join(',').toLowerCase();
                                            collectedTracks[key] = {
                                                title: tName,
                                                artist: artists.length > 0 ? artists.join(', ') : 'Unknown Artist'
                                            };
                                        }
                                    });

                                    scrollPasses++;
                                    var currentCount = Object.keys(collectedTracks).length;
                                    if (currentCount === lastTrackCount) {
                                        unchangedPasses++;
                                    } else {
                                        unchangedPasses = 0;
                                        lastTrackCount = currentCount;
                                    }

                                    var scrollNode = document.querySelector('.os-viewport') || 
                                                     document.querySelector('[main-page-scroll-node="true"]') || 
                                                     document.querySelector('main') || 
                                                     document.documentElement;
                                                     
                                    if (scrollNode) {
                                        scrollNode.scrollTop += 3000;
                                    }
                                    window.scrollBy(0, 3000);

                                    if (rows.length > 0) {
                                        rows[rows.length - 1].scrollIntoView({ behavior: 'auto', block: 'end' });
                                    }

                                    if (scrollPasses < maxScrolls && unchangedPasses < 5) {
                                        setTimeout(scrapeCurrentDOM, 180);
                                    } else {
                                        var trackList = [];
                                        for (var k in collectedTracks) {
                                            trackList.push(collectedTracks[k]);
                                        }
                                        window.AndroidDomBridge.onPlaylistScraped(JSON.stringify({
                                            title: playlistTitle,
                                            coverUrl: coverUrl,
                                            tracks: trackList
                                        }));
                                    }
                                }

                                setTimeout(scrapeCurrentDOM, 500);
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(jsScraper, null)
                    }
                }

                webView.loadUrl("https://open.spotify.com/playlist/$playlistId")
            } catch (e: Exception) {
                if (!deferred.isCompleted) deferred.complete(null)
            }
        }

        val result = kotlinx.coroutines.withTimeoutOrNull(45000L) {
            deferred.await()
        }

        mainHandler.post {
            try {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        result
    }

    private fun fetchFromEmbed(playlistId: String): SpotifyPlaylistDetails? {
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

            if (conn.responseCode != 200) return null

            val html = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }

            // Extract <script id="__NEXT_DATA__" type="application/json">...</script>
            val marker = "<script id=\"__NEXT_DATA__\" type=\"application/json\">"
            val startIndex = html.indexOf(marker)
            if (startIndex == -1) return null

            val jsonStart = startIndex + marker.length
            val jsonEnd = html.indexOf("</script>", jsonStart)
            if (jsonEnd == -1) return null

            val jsonStr = html.substring(jsonStart, jsonEnd).trim()
            val root = JSONObject(jsonStr)

            val props = root.optJSONObject("props") ?: return null
            val pageProps = props.optJSONObject("pageProps") ?: return null
            val state = pageProps.optJSONObject("state") ?: return null
            val data = state.optJSONObject("data") ?: return null
            val entity = data.optJSONObject("entity") ?: return null

            val title = entity.optString("name", "Spotify Playlist")
            val coverSources = entity.optJSONObject("coverArt")?.optJSONArray("sources")
            val coverUrl = coverSources?.optJSONObject(0)?.optString("url") ?: ""

            val trackListArray = entity.optJSONArray("trackList") ?: return null
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

            // Extract embedded accessToken to paginate beyond 100 songs
            val embeddedToken = extractAccessTokenFromNextData(root) ?: run {
                val tokenRegex = Regex(""""accessToken"\s*:\s*"([A-Za-z0-9_.\-]{30,})"""")
                tokenRegex.find(html)?.groupValues?.getOrNull(1)
            }

            if (!embeddedToken.isNullOrBlank() && tracks.size >= 100) {
                android.util.Log.d("SpotifyImport", "Embed found session token. Paginating remaining tracks...")
                var offset = tracks.size
                var hasMore = true
                while (hasMore && offset < 5000) {
                    var pageConn: HttpURLConnection? = null
                    try {
                        val pageUrl = URL("https://api.spotify.com/v1/playlists/$playlistId/tracks?offset=$offset&limit=100")
                        pageConn = pageUrl.openConnection() as HttpURLConnection
                        pageConn.requestMethod = "GET"
                        pageConn.setRequestProperty("Authorization", "Bearer $embeddedToken")
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
                            android.util.Log.d("SpotifyImport", "Paginated +$added tracks (total so far: ${tracks.size})")
                        } else {
                            android.util.Log.w("SpotifyImport", "Pagination response code: ${pageConn.responseCode}")
                            break
                        }
                    } catch (e: Exception) {
                        android.util.Log.w("SpotifyImport", "Pagination error at offset $offset: ${e.message}")
                        break
                    } finally {
                        pageConn?.disconnect()
                    }
                }
            }

            return SpotifyPlaylistDetails(
                id = playlistId,
                title = title,
                coverUrl = coverUrl,
                totalTracks = tracks.size,
                tracks = tracks
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        } finally {
            conn?.disconnect()
        }
    }

    private fun extractAccessTokenFromNextData(root: JSONObject): String? {
        try {
            val props = root.optJSONObject("props") ?: return null
            val pageProps = props.optJSONObject("pageProps") ?: return null
            val state = pageProps.optJSONObject("state") ?: return null
            val data = state.optJSONObject("data") ?: return null
            return data.optJSONObject("session")?.optString("accessToken")
                ?: data.optString("accessToken").takeIf { it.isNotBlank() }
                ?: root.optString("accessToken").takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            return null
        }
    }

    // Configurable Spotify API Credentials (from developer.spotify.com)
    var customClientId: String? = null
    var customClientSecret: String? = null

    private suspend fun obtainAccessToken(context: Context?, playlistId: String): String? {
        // Strategy 1: Extract accessToken from EMBED page HTML (Unrestricted web session token)
        try {
            val embedUrl = URL("https://open.spotify.com/embed/playlist/$playlistId")
            val conn = embedUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val html = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()

                val tokenRegex = Regex(""""accessToken"\s*:\s*"([A-Za-z0-9_.\-]{30,})"""")
                val match = tokenRegex.find(html)
                if (match != null && match.groupValues[1].isNotBlank()) {
                    val token = match.groupValues[1]
                    android.util.Log.d("SpotifyImport", "Embed session token SUCCESS: token=${token.take(15)}...")
                    return token
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.w("SpotifyImport", "Embed token fetch error: ${e.message}")
        }

        // Strategy 2: Direct Web Player Token request
        try {
            val tokenUrl = URL("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
            val conn = tokenUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "https://open.spotify.com/")
            conn.setRequestProperty("Origin", "https://open.spotify.com")
            conn.setRequestProperty("App-Platform", "WebPlayer")
            conn.setRequestProperty("Spotify-App-Version", "1.2.35.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val tokenJson = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val accessToken = JSONObject(tokenJson).optString("accessToken", "")
                conn.disconnect()
                if (accessToken.isNotBlank()) {
                    android.util.Log.d("SpotifyImport", "Direct Web Player token SUCCESS: ${accessToken.take(15)}...")
                    return accessToken
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.w("SpotifyImport", "Direct token endpoint failed: ${e.message}")
        }

        // Strategy 1: Background WebView Web Player Session Token (Unrestricted session token)
        if (context != null) {
            try {
                android.util.Log.d("SpotifyImport", "Strategy 1: trying WebView session token fetch...")
                val webViewToken = fetchAccessTokenViaWebView(context, playlistId)
                if (!webViewToken.isNullOrBlank()) {
                    android.util.Log.d("SpotifyImport", "Strategy 1 SUCCESS: Web Player Token=${webViewToken.take(15)}...")
                    return webViewToken
                }
                android.util.Log.w("SpotifyImport", "Strategy 1: WebView returned null")
            } catch (e: Exception) {
                android.util.Log.e("SpotifyImport", "Strategy 1 failed", e)
            }
        }

        // Strategy 2: Direct Web Player Token request
        try {
            android.util.Log.d("SpotifyImport", "Strategy 2: direct token endpoint...")
            val tokenUrl = URL("https://open.spotify.com/get_access_token?reason=transport&productType=web_player")
            val conn = tokenUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept", "application/json")
            conn.setRequestProperty("Referer", "https://open.spotify.com/")
            conn.setRequestProperty("Origin", "https://open.spotify.com")
            conn.setRequestProperty("App-Platform", "WebPlayer")
            conn.setRequestProperty("Spotify-App-Version", "1.2.35.0")
            conn.connectTimeout = 8000
            conn.readTimeout = 8000

            if (conn.responseCode == 200) {
                val tokenJson = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                val accessToken = JSONObject(tokenJson).optString("accessToken", "")
                conn.disconnect()
                if (accessToken.isNotBlank()) {
                    android.util.Log.d("SpotifyImport", "Strategy 2 SUCCESS: direct token=${accessToken.take(15)}...")
                    return accessToken
                }
            } else {
                android.util.Log.w("SpotifyImport", "Strategy 2: returned ${conn.responseCode}")
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImport", "Strategy 2 failed", e)
        }

        // Strategy 3: Extract accessToken from EMBED page HTML (Fallback)
        try {
            val embedUrl = URL("https://open.spotify.com/embed/playlist/$playlistId")
            val conn = embedUrl.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Accept-Language", "en-US,en;q=0.9")
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            android.util.Log.d("SpotifyImport", "Strategy 3: fetching embed page for token...")
            if (conn.responseCode == 200) {
                val html = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                conn.disconnect()

                val tokenRegex = Regex(""""accessToken"\s*:\s*"([A-Za-z0-9_.\-]{30,})"""")
                val match = tokenRegex.find(html)
                if (match != null && match.groupValues[1].isNotBlank()) {
                    val token = match.groupValues[1]
                    android.util.Log.d("SpotifyImport", "Strategy 3 SUCCESS: token=${token.take(15)}...")
                    return token
                }
            } else {
                conn.disconnect()
            }
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImport", "Strategy 3 failed", e)
        }

        android.util.Log.e("SpotifyImport", "All token strategies failed")
        return null
    }


    private suspend fun fetchAccessTokenViaWebView(context: Context, playlistId: String): String? = withContext(Dispatchers.IO) {
        val deferred = CompletableDeferred<String?>()
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        var webViewRef: android.webkit.WebView? = null
        mainHandler.post {
            try {
                val cookieMgr = android.webkit.CookieManager.getInstance()
                cookieMgr.setAcceptCookie(true)
                cookieMgr.setAcceptThirdPartyCookies(android.webkit.WebView(context.applicationContext), true)

                val webView = android.webkit.WebView(context.applicationContext)
                webViewRef = webView
                webView.settings.javaScriptEnabled = true
                webView.settings.domStorageEnabled = true
                webView.settings.userAgentString = USER_AGENT

                class JsBridge {
                    @android.webkit.JavascriptInterface
                    fun onTokenReceived(token: String?) {
                        android.util.Log.d("SpotifyImport", "WebView JS Bridge received token: ${token?.take(15)}...")
                        if (!token.isNullOrBlank() && !deferred.isCompleted) {
                            deferred.complete(token)
                        }
                    }
                }

                webView.addJavascriptInterface(JsBridge(), "AndroidTokenBridge")

                webView.webViewClient = object : android.webkit.WebViewClient() {
                    override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        val js = """
                            (function() {
                                function tryFetch(attemptsLeft) {
                                    fetch('https://open.spotify.com/get_access_token?reason=transport&productType=web_player')
                                        .then(function(r) { return r.json(); })
                                        .then(function(data) {
                                            if (data && data.accessToken) {
                                                window.AndroidTokenBridge.onTokenReceived(data.accessToken);
                                            } else if (attemptsLeft > 0) {
                                                setTimeout(function() { tryFetch(attemptsLeft - 1); }, 500);
                                            }
                                        }).catch(function(e) {
                                            if (attemptsLeft > 0) {
                                                setTimeout(function() { tryFetch(attemptsLeft - 1); }, 500);
                                            }
                                        });
                                }
                                tryFetch(10);
                            })();
                        """.trimIndent()
                        view?.evaluateJavascript(js, null)
                    }
                }

                webView.loadUrl("https://open.spotify.com/")
            } catch (e: Exception) {
                android.util.Log.e("SpotifyImport", "WebView error", e)
                if (!deferred.isCompleted) deferred.complete(null)
            }
        }

        val token = kotlinx.coroutines.withTimeoutOrNull(7000L) {
            deferred.await()
        }

        mainHandler.post {
            try {
                webViewRef?.stopLoading()
                webViewRef?.destroy()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        token
    }

    private suspend fun fetchFromWebApi(playlistId: String, context: Context?): SpotifyPlaylistDetails? {
        try {
            val accessToken = obtainAccessToken(context, playlistId) ?: return null

            // Step 2: Query playlist header info (title, cover, initial tracks & total count)
            val apiUrl = URL("https://api.spotify.com/v1/playlists/$playlistId")
            val apiConn = apiUrl.openConnection() as HttpURLConnection
            apiConn.requestMethod = "GET"
            apiConn.setRequestProperty("Authorization", "Bearer $accessToken")
            apiConn.setRequestProperty("User-Agent", USER_AGENT)
            apiConn.setRequestProperty("App-Platform", "WebPlayer")
            apiConn.setRequestProperty("Spotify-App-Version", "1.2.35.0")
            apiConn.setRequestProperty("Referer", "https://open.spotify.com/")
            apiConn.setRequestProperty("Origin", "https://open.spotify.com")
            apiConn.connectTimeout = 10000
            apiConn.readTimeout = 10000

            val respCode = apiConn.responseCode
            android.util.Log.d("SpotifyImport", "Web API initial playlist response code: $respCode")

            if (respCode != 200) {
                val errText = try {
                    BufferedReader(InputStreamReader(apiConn.errorStream, Charsets.UTF_8)).use { it.readText() }
                } catch (e: Exception) { "" }
                android.util.Log.w("SpotifyImport", "Web API error response ($respCode): $errText")
                apiConn.disconnect()
                return null
            }

            val respJson = BufferedReader(InputStreamReader(apiConn.inputStream, Charsets.UTF_8)).use { it.readText() }
            apiConn.disconnect()

            val root = JSONObject(respJson)
            val playlistName = root.optString("name", "Spotify Playlist")
            val images = root.optJSONArray("images")
            val coverUrl = images?.optJSONObject(0)?.optString("url") ?: ""

            val tracksObj = root.optJSONObject("tracks") ?: return null
            val totalReported = tracksObj.optInt("total", 0)
            val tracks = mutableListOf<SpotifyImportedTrack>()

            // Parse initial page items
            parseTrackItems(tracksObj.optJSONArray("items"), tracks)
            var nextUrl = tracksObj.optString("next", "").takeIf { it.isNotBlank() }

            android.util.Log.d("SpotifyImport", "Web API parsed initial page: ${tracks.size} tracks, totalReported=$totalReported, nextUrl=$nextUrl")

            // Step 3: Multi-Page Pagination Loop for Large Playlists (up to 10,000 limit)
            var pageCount = 0
            while (!nextUrl.isNullOrBlank() && tracks.size < 10000 && pageCount < 100) {
                pageCount++
                var pageConn: HttpURLConnection? = null
                try {
                    val pUrl = URL(nextUrl)
                    pageConn = pUrl.openConnection() as HttpURLConnection
                    pageConn.requestMethod = "GET"
                    pageConn.setRequestProperty("Authorization", "Bearer $accessToken")
                    pageConn.setRequestProperty("User-Agent", USER_AGENT)
                    pageConn.setRequestProperty("App-Platform", "WebPlayer")
                    pageConn.setRequestProperty("Spotify-App-Version", "1.2.35.0")
                    pageConn.setRequestProperty("Referer", "https://open.spotify.com/")
                    pageConn.setRequestProperty("Origin", "https://open.spotify.com")
                    pageConn.connectTimeout = 8000
                    pageConn.readTimeout = 8000

                    val pCode = pageConn.responseCode
                    if (pCode != 200) {
                        val pErr = try {
                            BufferedReader(InputStreamReader(pageConn.errorStream, Charsets.UTF_8)).use { it.readText() }
                        } catch (e: Exception) { "" }
                        android.util.Log.w("SpotifyImport", "Pagination page $pageCount failed ($pCode): $pErr")
                        break
                    }
                    val pageText = BufferedReader(InputStreamReader(pageConn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    val pageRoot = JSONObject(pageText)
                    val pItems = pageRoot.optJSONArray("items")
                    parseTrackItems(pItems, tracks)
                    nextUrl = pageRoot.optString("next", "").takeIf { it.isNotBlank() }
                    android.util.Log.d("SpotifyImport", "Pagination page $pageCount fetched ${pItems?.length() ?: 0} items. Total accumulated: ${tracks.size}, nextUrl=$nextUrl")
                } catch (e: Exception) {
                    android.util.Log.e("SpotifyImport", "Pagination exception on page $pageCount", e)
                    break
                } finally {
                    pageConn?.disconnect()
                }
            }

            android.util.Log.d("SpotifyImport", "Web API finished: total tracks fetched = ${tracks.size}")

            return SpotifyPlaylistDetails(
                id = playlistId,
                title = playlistName,
                coverUrl = coverUrl,
                totalTracks = if (totalReported > 0) totalReported else tracks.size,
                tracks = tracks
            )
        } catch (e: Exception) {
            android.util.Log.e("SpotifyImport", "fetchFromWebApi failed", e)
            return null
        }
    }

    private fun parseTrackItems(items: org.json.JSONArray?, outList: MutableList<SpotifyImportedTrack>) {
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
