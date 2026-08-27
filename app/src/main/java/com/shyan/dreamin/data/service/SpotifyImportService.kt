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
     * Tries anonymous Spotify Web Token API first with full pagination, then falls back to Embed scraping.
     */
    suspend fun fetchPlaylistDetails(playlistId: String, context: Context? = null): SpotifyPlaylistDetails? = withContext(Dispatchers.IO) {
        // Strategy 1: Official API Client Credentials Grant with Full Multi-Page Pagination
        val fromApi = fetchFromWebApi(playlistId, context)
        if (fromApi != null && (fromApi.tracks.size >= fromApi.totalTracks || fromApi.tracks.size > 100)) {
            android.util.Log.d("SpotifyImport", "Web API Full Success: ${fromApi.tracks.size}/${fromApi.totalTracks} tracks fetched!")
            return@withContext fromApi
        }

        // Strategy 2: Background WebView DOM Scroll Scraper (bypasses Premium/API restrictions, gets ALL 300+ tracks)
        if (context != null) {
            val fromDom = fetchPlaylistViaWebViewDom(context, playlistId)
            if (fromDom != null && fromDom.tracks.isNotEmpty()) {
                android.util.Log.d("SpotifyImport", "WebView DOM Scraper SUCCESS: ${fromDom.tracks.size} tracks fetched!")
                return@withContext fromDom
            }
        }

        // Strategy 3: Public Open Converter API Gateway (automatically parses all 300+ songs for free)
        val fromConverter = fetchViaOpenConverter(playlistId)
        if (fromConverter != null && fromConverter.tracks.isNotEmpty()) {
            android.util.Log.d("SpotifyImport", "Open Converter SUCCESS: ${fromConverter.tracks.size} tracks fetched automatically!")
            return@withContext fromConverter
        }

        // Strategy 4: Public Embed Page Scraping fallback (100 tracks cap fallback)
        val fromEmbed = fetchFromEmbed(playlistId)
        fromApi ?: fromEmbed
    }

    private fun fetchViaOpenConverter(playlistId: String): SpotifyPlaylistDetails? {
        try {
            val url = URL("https://api.spotifydown.com/tracklist/playlist/$playlistId")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", USER_AGENT)
            conn.setRequestProperty("Origin", "https://spotifydown.com")
            conn.setRequestProperty("Referer", "https://spotifydown.com/")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            if (conn.responseCode == 200) {
                val jsonStr = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(jsonStr)
                if (obj.optBoolean("success", true)) {
                    val tracksArray = obj.optJSONArray("trackList") ?: obj.optJSONArray("tracks") ?: JSONArray()
                    val tracks = mutableListOf<SpotifyImportedTrack>()
                    var title = "Spotify Playlist"
                    var coverUrl = ""

                    for (i in 0 until tracksArray.length()) {
                        val item = tracksArray.getJSONObject(i)
                        val tTitle = item.optString("title", "").ifBlank { item.optString("name", "") }
                        val tArtist = item.optString("artists", "").ifBlank { item.optString("artist", "Unknown Artist") }
                        if (tTitle.isNotBlank()) {
                            tracks.add(SpotifyImportedTrack(title = tTitle, artist = tArtist))
                        }
                        if (i == 0) {
                            coverUrl = item.optString("cover", "").ifBlank { item.optString("coverUrl", "") }
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
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("SpotifyImport", "fetchViaOpenConverter failed: ${e.message}")
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

    // Configurable Spotify API Credentials (from developer.spotify.com)
    var customClientId: String? = "e8cdf8b62ec5471e9c8730ae1da37b6a"
    var customClientSecret: String? = "d3bc867fffe94b529676eea52c916f2d"

    private suspend fun obtainAccessToken(context: Context?, playlistId: String): String? {
        // Strategy 0: Official Spotify Developer API Client Credentials (100% reliable, zero rate limits)
        if (!customClientId.isNullOrBlank() && !customClientSecret.isNullOrBlank()) {
            try {
                android.util.Log.d("SpotifyImport", "Strategy 0: trying Client Credentials grant...")
                val tokenUrl = URL("https://accounts.spotify.com/api/token")
                val conn = tokenUrl.openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                val auth = android.util.Base64.encodeToString(
                    "${customClientId}:${customClientSecret}".toByteArray(),
                    android.util.Base64.NO_WRAP
                )
                conn.setRequestProperty("Authorization", "Basic $auth")
                conn.doOutput = true
                conn.connectTimeout = 8000
                conn.readTimeout = 8000

                val body = "grant_type=client_credentials"
                conn.outputStream.write(body.toByteArray())

                if (conn.responseCode == 200) {
                    val jsonStr = BufferedReader(InputStreamReader(conn.inputStream, Charsets.UTF_8)).use { it.readText() }
                    conn.disconnect()
                    val token = JSONObject(jsonStr).optString("access_token", "")
                    if (token.isNotBlank()) {
                        android.util.Log.d("SpotifyImport", "Strategy 0 SUCCESS: Client Credentials Token=${token.take(15)}...")
                        return token
                    }
                } else {
                    android.util.Log.w("SpotifyImport", "Strategy 0: returned ${conn.responseCode}")
                    conn.disconnect()
                }
            } catch (e: Exception) {
                android.util.Log.e("SpotifyImport", "Strategy 0 failed", e)
            }
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
