package com.shyan.dreamin.data.repository

import com.shyan.dreamin.data.model.AlbumItem
import com.shyan.dreamin.data.model.Song
import com.shyan.dreamin.data.network.MusicApi
import com.shyan.dreamin.data.network.NetworkService
import com.shyan.dreamin.data.recommendation.OfficialSongFilter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.net.URLEncoder

class CatalogRepository(private val api: MusicApi = NetworkService.api) {

    private val noiseRegex = Regex("(?i)\\b(remix|8d|8d audio|bass boosted|slowed|reverb|cover|lofi|status|dialogue|teaser|trailer|jukebox|mashup|bgm|ringtone|sped up)\\b")
    private val compilationRegex = com.shyan.dreamin.data.service.OfficialArtworkService.COMPILATION_REGEX

    fun cleanArtworkUrl(url: String): String {
        if (url.isBlank()) return ""
        val highRes = url.replace(Regex("_\\d+x\\d+\\."), "_500x500.")
            .replace("50x50", "500x500")
            .replace("150x150", "500x500")
        return if (highRes.startsWith("http://")) "https://" + highRes.substring(7) else highRes
    }

    fun unescapeHtml(text: String): String {
        return text.replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&#39;", "'")
    }

    suspend fun searchOnDevice(
        query: String, 
        limit: Int = 15, 
        page: Int = 1,
        targetLanguage: String = "",
        rejectHindi: Boolean = false
    ): List<Song> = withContext(Dispatchers.IO) {
        val encoded = URLEncoder.encode(query, "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=$limit&p=$page"
        
        for (attempt in 0..1) {
            try {
                val req = okhttp3.Request.Builder()
                    .url(url)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .header("Accept", "application/json, text/plain, */*")
                    .header("Origin", "https://www.jiosaavn.com")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()
                val resp = NetworkService.httpClient.newCall(req).execute()
                val text = resp.body?.string().orEmpty().trim()
                if (text.startsWith("<") || !text.startsWith("{")) {
                    if (attempt == 0) kotlinx.coroutines.delay(200L)
                    continue
                }
                val root = org.json.JSONObject(text)
                val results = root.optJSONArray("results")
                if (results != null && results.length() > 0) {
                    val allItems = mutableListOf<org.json.JSONObject>()
                    val langItems = mutableListOf<org.json.JSONObject>()
                    for (i in 0 until results.length()) {
                        val it = results.getJSONObject(i)
                        allItems.add(it)
                        val itemLang = it.optString("language").ifBlank { it.optJSONObject("more_info")?.optString("language") ?: "" }.lowercase().trim()
                        if (targetLanguage.isBlank() || itemLang.isBlank() || itemLang.equals(targetLanguage, ignoreCase = true)) {
                            langItems.add(it)
                        }
                    }
                    val rawItems = if (langItems.isNotEmpty()) langItems else allItems

                    val grouped = rawItems.groupBy {
                        it.optString("title").replace(Regex("(?i)\\s*\\(?\\s*from\\s+[\"\'\u201c\u2018].*?[\"\'\u201d\u2019]?\\s*\\)?$"), "")
                            .replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
                    }
                    val songs = mutableListOf<Song>()
                    for ((_, items) in grouped) {
                        // 1. Detect movie from title tags
                        var movieDetected = ""
                        for (it in items) {
                            val m = Regex("(?i)\\(?\\s*(?:from|movie)\\s+[\"\'\u201c\u2018]?(.*?)[\"\'\u201d\u2019]?\\s*\\)?").find(it.optString("title"))
                            if (m != null) {
                                val cand = m.groupValues[1].replace(Regex("[\"\'\u201c\u2018\u201d\u2019]"), "").trim().lowercase()
                                if (cand.isNotBlank() && cand.length >= 2) {
                                    movieDetected = cand
                                    break
                                }
                            }
                        }

                        // 2. Select best candidate release from the provider
                        val bestItem = items.minByOrNull { item ->
                            val alb = unescapeHtml(item.optJSONObject("more_info")?.optString("album", "")?.trim() ?: "").lowercase()
                            val albType = item.optJSONObject("more_info")?.optString("album_type", "")?.lowercase() ?: ""
                            val itemImage = item.optString("image", "").lowercase()
                            val isEditorialOrPlaylist = itemImage.contains("/editorial/") || itemImage.contains("/playlist/") || itemImage.contains("/818/") || itemImage.contains("/888/") || itemImage.contains("compilation")

                            var score = 1000
                            val isComp = compilationRegex.containsMatchIn(alb)
                            val isSingle = alb.contains("single") || albType == "single"
                            if (isComp) {
                                score += 8000
                            } else if (isSingle) {
                                score += 5000 // Penalize single release so full movie soundtrack album is prioritized
                            } else {
                                score -= 400
                                if (alb.contains("soundtrack") || alb.contains("original motion picture")) {
                                    score -= 800
                                }
                            }
                            if (movieDetected.isNotBlank() && (alb == movieDetected || alb.contains(movieDetected) || movieDetected.contains(alb))) {
                                score -= 1000
                            }
                            if (!isEditorialOrPlaylist) {
                                score -= 200
                            }
                            score
                        } ?: items.first()

                        val rawTitle = unescapeHtml(bestItem.optString("title"))
                        if (noiseRegex.containsMatchIn(rawTitle)) continue
                        val more = bestItem.optJSONObject("more_info") ?: org.json.JSONObject()
                        val durationSec = more.optString("duration").toLongOrNull() ?: 0L
                        if (durationSec in 1..49 || durationSec > 600) continue

                        val image = cleanArtworkUrl(bestItem.optString("image"))
                        val playCount = bestItem.optString("play_count").toLongOrNull()
                            ?: (more.optString("play_count").toLongOrNull() ?: 0L)

                        val artistMap = more.optJSONObject("artistMap")
                        val primaryArr = artistMap?.optJSONArray("primary_artists")
                        var artist = if (primaryArr != null && primaryArr.length() > 0) {
                            (0 until primaryArr.length()).joinToString(", ") { unescapeHtml(primaryArr.getJSONObject(it).optString("name")) }
                        } else unescapeHtml(more.optString("singers").ifBlank { "" })

                        if (artist.isBlank()) {
                            val music = unescapeHtml(more.optString("music"))
                            val subtitle = unescapeHtml(bestItem.optString("subtitle"))
                            val album = unescapeHtml(more.optString("album"))
                            artist = when {
                                music.isNotBlank() -> music
                                subtitle.isNotBlank() -> subtitle
                                album.isNotBlank() -> album
                                else -> "Original Soundtrack"
                            }
                        }

                        val itemLanguage = bestItem.optString("language").ifBlank { more.optString("language") }.lowercase().trim()
                        val itemAlbum = unescapeHtml(more.optString("album").ifBlank { bestItem.optString("album") })

                        val songItem = Song(
                            id = bestItem.optString("id"),
                            title = rawTitle,
                            artist = artist,
                            artworkUrl = image,
                            duration = durationSec * 1000L,
                            playCount = playCount,
                            language = itemLanguage,
                            album = itemAlbum
                        )
                        if (OfficialSongFilter.isOfficial(songItem, rejectHindi = rejectHindi)) {
                            songs.add(songItem)
                        }
                    }
                    if (songs.isNotEmpty()) {
                        val ranked = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.fuzzyRankSearchResults(query, songs)
                        return@withContext ranked.take(limit)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                android.util.Log.w("MusicVM", "searchOnDevice attempt $attempt failed: ${e.message}")
                if (attempt == 0) {
                    kotlinx.coroutines.delay(200L)
                }
            }
        }
        // Seamless fallback to server search
        try {
            val serverResp = api.search(query, page = page, limit = limit)
            com.shyan.dreamin.data.recommendation.IntelliMatchEngine.fuzzyRankSearchResults(query, serverResp.results).take(limit)
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun searchAlbumsOnDevice(query: String, limit: Int = 8): List<AlbumItem> = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return@withContext emptyList()
        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://www.jiosaavn.com/api.php?__call=search.getAlbumResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=$limit&p=1"

        try {
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                .header("Accept", "application/json, text/plain, */*")
                .header("Origin", "https://www.jiosaavn.com")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val resp = NetworkService.httpClient.newCall(req).execute()
            val text = resp.body?.string().orEmpty().trim()
            if (!text.startsWith("{")) return@withContext emptyList()
            val root = org.json.JSONObject(text)
            val results = root.optJSONArray("results") ?: return@withContext emptyList()

            val albums = mutableListOf<AlbumItem>()
            val seenKeys = mutableSetOf<String>()

            for (i in 0 until results.length()) {
                val it = results.getJSONObject(i)
                val id = it.optString("id")
                val rawTitle = unescapeHtml(it.optString("title"))
                if (rawTitle.isBlank()) continue

                val more = it.optJSONObject("more_info") ?: org.json.JSONObject()
                val year = it.optString("year").ifBlank { more.optString("year") }
                val songCount = more.optString("song_count").toIntOrNull() ?: 0

                val artistMap = more.optJSONObject("artistMap")
                val primaryArr = artistMap?.optJSONArray("primary_artists")
                var artist = if (primaryArr != null && primaryArr.length() > 0) {
                    (0 until primaryArr.length()).joinToString(", ") { idx -> unescapeHtml(primaryArr.getJSONObject(idx).optString("name")) }
                } else {
                    unescapeHtml(more.optString("music").ifBlank { it.optString("subtitle") })
                }
                if (artist.isBlank()) {
                    artist = "Original Soundtrack"
                }

                val language = it.optString("language").ifBlank { more.optString("language") }.lowercase().trim()
                val rawImage = it.optString("image")
                val cleanImage = cleanArtworkUrl(rawImage)

                val dedupeKey = "${rawTitle.lowercase()}_$year"
                if (seenKeys.add(dedupeKey)) {
                    albums.add(
                        AlbumItem(
                            id = id,
                            title = rawTitle,
                            artworkUrl = cleanImage,
                            year = year,
                            songCount = songCount,
                            artist = artist,
                            language = language
                        )
                    )
                }
            }
            albums
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "searchAlbumsOnDevice failed: ${e.message}")
            emptyList()
        }
    }

    suspend fun fetchJioSaavnRelated(songId: String): List<Song> = withContext(Dispatchers.IO) {
        val results = mutableListOf<Song>()
        try {
            val url = "https://www.jiosaavn.com/api.php?__call=reco.getreco&api_version=4&_format=json&_marker=0&ctx=web6dot0&pid=$songId"
            val req = okhttp3.Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val text = NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
            val array = org.json.JSONArray(text)
            for (i in 0 until array.length()) {
                val it = array.getJSONObject(i)
                val id = it.optString("id", "")
                val title = unescapeHtml(it.optString("title", it.optString("song", "")))
                val artist = unescapeHtml(it.optString("primary_artists", it.optString("singers", it.optString("artist", ""))))
                val image = cleanArtworkUrl(it.optString("image", ""))
                val more = it.optJSONObject("more_info")
                val durSec = more?.optLong("duration", 0L) ?: 0L
                val album = unescapeHtml(more?.optString("album", "") ?: "")
                if (id.isNotBlank() && title.isNotBlank()) {
                    results.add(
                        Song(
                            id = id,
                            title = title,
                            artist = artist,
                            artworkUrl = image,
                            duration = durSec * 1000L
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        results
    }

    suspend fun resolveRadioCandidates(currentSong: Song, limit: Int): List<Song> = withContext(Dispatchers.IO) {
        val ytSongs = mutableListOf<Song>()
        try {
            val tracks = com.shyan.dreamin.data.service.YouTubeRadioService.fetchRadioRecommendations(currentSong)
            if (tracks.isNotEmpty()) {
                val deferredSearches = tracks.take(limit).map { track ->
                    async {
                        val cleanT = track.title.replace(Regex("""\s*[\(\[].*?[\)\]]"""), "").trim()
                        val firstArtist = track.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
                        val cand = searchOnDevice("$cleanT $firstArtist", limit = 1).firstOrNull()
                            ?: searchOnDevice(cleanT, limit = 1).firstOrNull()
                        if (cand != null && cand.id != currentSong.id && OfficialSongFilter.isOfficial(cand, rejectHindi = false)) {
                            val finalCand = if (track.artworkUrl.isNotBlank() && (cand.artworkUrl.isBlank() || cand.artworkUrl.contains("150x150") || cand.artworkUrl.contains("50x50") || cand.artworkUrl.contains("default"))) {
                                cand.copy(artworkUrl = track.artworkUrl, album = track.album.ifBlank { cand.album })
                            } else cand
                            finalCand
                        } else null
                    }
                }
                deferredSearches.forEach { def ->
                    val res = def.await()
                    if (res != null) {
                        ytSongs.add(res)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.w("MusicVM", "Radio candidate error: ${e.message}")
        }
        ytSongs
    }
}
