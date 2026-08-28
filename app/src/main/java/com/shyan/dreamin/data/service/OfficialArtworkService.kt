package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object OfficialArtworkService {

    private val artworkCache = object : java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 1000
        }
    }

    val COMPILATION_REGEX = Regex(
        "(?i)\\b(mixtape|bighits|big hits|think music|thinkmusic|double delights|double delight|delights|delight|" +
        "double|duo|duets|triple|jodi|combo|treats|fire & desire|fire and desire|desire|this is|best of|" +
        "top hits|hits|vol\\b|vol\\.|volume|love notes|collection|playlist|raga collective|kondattam|selected|" +
        "radio|superhit|compilation|greatest hits|evergreen|melody|melodies|latest|essential|party|workout|" +
        "romance|mashup|area boys|konjam|thamizh music|special|tribute|celebration|magic of|voice of|golden|" +
        "non stop|jukebox|rewind|finesse|starry|mazhaiyum|thooral|pure|simply|anthology|sounds of|sensational|" +
        "absolute|trending version|ungaludan|dhamaka|masterworks|all about love|sun-kissed|summer vibes|" +
        "my playlist|words of|mazhaikaalam|joy|saaral|singer special|in the words of|feel good|night drive|" +
        "soulful|chill tracks|unlimited|hits of|love waves|love diaries|the love diaries|memoirs of love|" +
        "memoirs|love story|love stories|valentines|valentine|romantic hits|love collection|love mix|" +
        "romance mix|love songs|super singer|rockstar|the girlfriend mix|the first love tapes|this is kaadhal|" +
        "latest evergreen melody|mazhai & kaadhal|dhanush dhamaka|endrendrum|pure love|sweet romance|evergreen love|" +
        "kaadhal hits|kadhal hits|suriya hits|vijay hits|ajith hits|dhanush hits|anirudh hits|harris hits|rahman hits|" +
        "mass|dhanush mass|vijay mass|ajith mass|suriya mass|anirudh mass|karthi mass|rajini mass|kamal mass|" +
        "take\\s*\\d+|take\\d+|masterworks|hits of\\s+[a-z]+|[a-z]+\\s+hits|" +
        "cover version|cover|tribute version|tribute|acoustic cover|unplugged cover|reprise version|" +
        "i\\s*(?:love|heart|❤️|♥)\\s+[a-z\\s]+|favourite\\s+[a-z\\s]+|favorite\\s+[a-z\\s]+|" +
        "vibration|vibrations|hit songs|super hit songs|melody songs|love hits|sad songs|evergreen hits|hit collection|" +
        "kollywood|most romantic|lofi mix|lofi|mashup|trap vibe|trap|drill|edm|remix|slowed|reverb|8d\\s*audio|" +
        "chillout|unplugged|piano version|acoustic version)\\b"
    )

    private val KNOWN_LYRICISTS = setOf(
        "vignesh shivan", "thamarai", "vaali", "vairamuthu", "kabilan", "pa. vijay", "snehan", 
        "yugabharathi", "madhan karky", "karky", "arunraja kamaraj", "rokesh", "vivek", 
        "ku. karthik", "selvaraghavan", "gkb", "eknath", "mani amudhavan"
    )

    fun prefetchSongListArtworks(
        songs: List<Song>,
        scope: kotlinx.coroutines.CoroutineScope,
        onResolved: ((songId: String, posterUrl: String) -> Unit)? = null
    ) {
        songs.forEach { song ->
            scope.launch(Dispatchers.IO) {
                try {
                    val poster = resolveOfficialMoviePoster(song)
                    if (!poster.isNullOrBlank() && poster != song.artworkUrl) {
                        onResolved?.invoke(song.id, poster)
                    }
                } catch (_: Exception) {}
            }
        }
    }

    fun getCachedPoster(song: Song): String? = synchronized(artworkCache) {
        if (song.id.isNotBlank()) {
            artworkCache[song.id]?.let { return it }
        }
        val fullKey = "${song.displayTitle.lowercase()}_${song.artist.lowercase()}".trim()
        artworkCache[fullKey]?.let { return it }
        val titleOnlyKey = song.displayTitle.lowercase().trim()
        artworkCache[titleOnlyKey]?.let { return it }
        return null
    }

    fun putCachedPoster(song: Song, posterUrl: String): Unit = synchronized(artworkCache) {
        if (posterUrl.isBlank()) return
        if (song.id.isNotBlank()) artworkCache[song.id] = posterUrl
        val fullKey = "${song.displayTitle.lowercase()}_${song.artist.lowercase()}".trim()
        artworkCache[fullKey] = posterUrl
        val titleOnlyKey = song.displayTitle.lowercase().trim()
        artworkCache[titleOnlyKey] = posterUrl
    }

    fun clearCache(): Unit = synchronized(artworkCache) {
        artworkCache.clear()
    }

    /**
     * Dynamically fetches the 100% official original movie soundtrack poster
     * directly from official movie catalogs matching the song's language and DNA.
     */
    suspend fun resolveOfficialMoviePoster(song: Song, targetLanguage: String = "tamil"): String? = withContext(Dispatchers.IO) {
        getCachedPoster(song)?.let { return@withContext it }

        val cacheKey = "${song.displayTitle.lowercase()}_${song.artist.lowercase()}".trim()
        val titleKey = song.displayTitle.lowercase().trim()

        val (baseTitle, fullClean) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(song.displayTitle)
        val cleanTitle = if (baseTitle.isNotBlank()) baseTitle else fullClean

        // Smart artist selection: ignore lyricists if composer/singer is present
        val allArtists = song.artist.split(",", "&", "feat.", "ft.", "/").map { it.trim() }.filter { it.isNotBlank() }
        val nonLyricists = allArtists.filter { a -> KNOWN_LYRICISTS.none { a.contains(it, ignoreCase = true) } }
        val primaryArtist = nonLyricists.firstOrNull() ?: allArtists.firstOrNull() ?: ""

        // Context-aware language inference for regional soundtrack accuracy
        val detectedLang = when {
            song.title.contains("tamil", ignoreCase = true) || song.artist.contains("anirudh", ignoreCase = true) || song.artist.contains("rahman", ignoreCase = true) || song.artist.contains("yuvan", ignoreCase = true) || song.artist.contains("ilayaraja", ignoreCase = true) || song.artist.contains("harris", ignoreCase = true) || song.artist.contains("santhosh", ignoreCase = true) || song.artist.contains("g.v.", ignoreCase = true) || song.artist.contains("gv prakash", ignoreCase = true) || song.artist.contains("sid sriram", ignoreCase = true) || song.artist.contains("dhibu", ignoreCase = true) -> "tamil"
            song.title.contains("telugu", ignoreCase = true) || song.artist.contains("thaman", ignoreCase = true) || song.artist.contains("devi sri prasad", ignoreCase = true) || song.artist.contains("dsp", ignoreCase = true) || song.artist.contains("keeravani", ignoreCase = true) || song.artist.contains("anurag kulkarni", ignoreCase = true) -> "telugu"
            song.title.contains("malayalam", ignoreCase = true) || song.artist.contains("sushin shyam", ignoreCase = true) || song.artist.contains("shaan rahman", ignoreCase = true) || song.artist.contains("hesham", ignoreCase = true) || song.artist.contains("gopi sundar", ignoreCase = true) || song.artist.contains("vidyasagar", ignoreCase = true) -> "malayalam"
            song.title.contains("hindi", ignoreCase = true) || song.artist.contains("arijit", ignoreCase = true) || song.artist.contains("pritam", ignoreCase = true) || song.artist.contains("vishal", ignoreCase = true) || song.artist.contains("shekhar", ignoreCase = true) || song.artist.contains("atif", ignoreCase = true) || song.artist.contains("badshah", ignoreCase = true) -> "hindi"
            else -> targetLanguage
        }

        // Try extracting movie name from title tag (e.g. From "Duet", From '3', From Paiyaa, From "24")
        var movieName = ""
        val m = Regex("(?i)\\s*\\(?\\s*(?:from|movie)\\s+[\"\'\u201c\u2018]?(.*?)[\"\'\u201d\u2019]?\\s*\\)?").find(song.title)
        if (m != null) {
            val cand = m.groupValues[1].replace(Regex("[\"\'\u201c\u2018\u201d\u2019]"), "").trim()
            if (cand.isNotBlank() && cand.length >= 2) {
                movieName = cand
            }
        }

        // Dual-Source Score Arbitration: Query both Apple Music and JioSaavn
        val appleResult = fetchAppleMusicOfficialCover(cleanTitle, primaryArtist, detectedLang)
        val saavnResult = fetchJioSaavnSongOfficialCover(cleanTitle, primaryArtist, movieName, detectedLang)

        val bestPoster = when {
            appleResult != null && saavnResult != null -> {
                if (appleResult.second >= saavnResult.second) appleResult.first else saavnResult.first
            }
            appleResult != null -> appleResult.first
            saavnResult != null -> saavnResult.first
            else -> null
        }

        if (!bestPoster.isNullOrBlank()) {
            artworkCache.put(cacheKey, bestPoster)
            artworkCache.put(titleKey, bestPoster)
            if (song.id.isNotBlank()) artworkCache.put(song.id, bestPoster)
            return@withContext bestPoster
        }

        // Query JioSaavn Official Movie Album API if movie name is identified
        if (movieName.isNotBlank()) {
            val albumPoster = fetchJioSaavnMovieAlbumCover(movieName, detectedLang)
            if (!albumPoster.isNullOrBlank()) {
                artworkCache.put(cacheKey, albumPoster)
                artworkCache.put(titleKey, albumPoster)
                if (song.id.isNotBlank()) artworkCache.put(song.id, albumPoster)
                return@withContext albumPoster
            }
        }

        // Phonetic Fallback via IntelliMatch (e.g. "mazhaye" -> "mazhaiye")
        val phoneticAlt = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.generatePhoneticSuggestions(cleanTitle)
        if (!phoneticAlt.isNullOrBlank()) {
            val altApple = fetchAppleMusicOfficialCover(phoneticAlt, primaryArtist, detectedLang)
            val altSaavn = fetchJioSaavnSongOfficialCover(phoneticAlt, primaryArtist, movieName, detectedLang)
            val altPoster = when {
                altApple != null && altSaavn != null -> if (altApple.second >= altSaavn.second) altApple.first else altSaavn.first
                altApple != null -> altApple.first
                altSaavn != null -> altSaavn.first
                else -> null
            }
            if (!altPoster.isNullOrBlank()) {
                artworkCache.put(cacheKey, altPoster)
                artworkCache.put(titleKey, altPoster)
                if (song.id.isNotBlank()) artworkCache.put(song.id, altPoster)
                return@withContext altPoster
            }
        }

        null
    }

    private fun unescape(s: String): String {
        return s.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#039;", "'")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun fetchAppleMusicOfficialCover(title: String, artist: String, targetLanguage: String): Pair<String, Int>? {
        try {
            val (baseTitle, _) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(title)
            val cleanTitle = if (baseTitle.isNotBlank()) baseTitle else title
            val langHint = if (targetLanguage.isNotBlank()) targetLanguage.replaceFirstChar { it.uppercase() } else "Tamil"
            
            val searchQueries = mutableListOf<String>()
            val artistTokens = artist.split(",", "&", "/", "feat.", "ft.").map { it.trim() }.filter { it.isNotBlank() }
            for (a in artistTokens.take(2)) {
                searchQueries.add("$cleanTitle $a")
            }
            if (artist.isNotBlank() && !searchQueries.contains("$cleanTitle $artist")) {
                searchQueries.add("$cleanTitle $artist")
            }
            searchQueries.add("$cleanTitle $langHint")
            searchQueries.add(cleanTitle)

            var bestCover: String? = null
            var bestScore = Int.MIN_VALUE
            val queryNorm = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.normalizePhonetics(cleanTitle)

            for (q in searchQueries) {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val urlStr = "https://itunes.apple.com/search?term=$encoded&entity=song&country=IN&limit=10"
                val req = okhttp3.Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .build()
                val text = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
                val root = JSONObject(text)
                val results = root.optJSONArray("results") ?: continue

                for (i in 0 until results.length()) {
                    val it = results.getJSONObject(i)
                    val trackName = unescape(it.optString("trackName", ""))
                    val collectionName = unescape(it.optString("collectionName", ""))
                    val rawArtwork = it.optString("artworkUrl100", "")

                    if (rawArtwork.isBlank()) continue

                    val trackNorm = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.normalizePhonetics(trackName)
                    val isPhoneticMatch = trackNorm.contains(queryNorm) || queryNorm.contains(trackNorm)
                    val jaro = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.jaroWinkler(queryNorm, trackNorm)
                    if (!isPhoneticMatch && jaro < 0.75) continue

                    var score = (jaro * 5000).toInt()

                    val isCompilation = COMPILATION_REGEX.containsMatchIn(collectionName)
                    if (isCompilation) {
                        score -= 60000
                    } else {
                        score += 10000
                        if (collectionName.contains("Soundtrack", ignoreCase = true) || collectionName.contains("Original Motion Picture", ignoreCase = true) || collectionName.contains("Original Soundtrack", ignoreCase = true)) {
                            score += 25000
                        }
                    }

                    // Prefer vocal original over remixes, lofi, trap, covers, instrumental/karaoke versions
                    if (trackName.contains("Cover", ignoreCase = true) ||
                        trackName.contains("Tribute", ignoreCase = true) ||
                        trackName.contains("Trap", ignoreCase = true) ||
                        trackName.contains("Remix", ignoreCase = true) ||
                        trackName.contains("Lofi", ignoreCase = true) ||
                        trackName.contains("Instrumental", ignoreCase = true) ||
                        trackName.contains("Karaoke", ignoreCase = true) ||
                        trackName.contains("Rendition", ignoreCase = true)
                    ) {
                        score -= 40000
                    }

                    // Language Affinity Guard
                    val textCombined = "$trackName $collectionName".lowercase()
                    val otherLangs = listOf("telugu", "hindi", "kannada", "malayalam", "tamil", "punjabi", "bengali")
                        .filter { it != targetLanguage.lowercase() }
                    for (other in otherLangs) {
                        if (textCombined.contains("($other)") || 
                            textCombined.contains("[$other]") || 
                            textCombined.contains("- $other") || 
                            textCombined.contains("from \"$other\"")
                        ) {
                            score -= 30000
                        }
                    }
                    if (textCombined.contains("($targetLanguage)") || 
                        textCombined.contains("[$targetLanguage]") || 
                        textCombined.contains("- $targetLanguage")
                    ) {
                        score += 15000
                    }

                    // Authentic Movie Album Alignment Check (e.g. From "Yaaradi Nee Mohini")
                    val movieFromTrack = Regex("(?i)from\\s+[\"\'\u201c\u2018]?(.*?)[\"\'\u201d\u2019]?\\s*[\\)\\]]").find(trackName)?.groupValues?.get(1)?.trim()
                    if (!movieFromTrack.isNullOrBlank()) {
                        if (collectionName.contains(movieFromTrack, ignoreCase = true)) {
                            score += 35000 // Verified Authentic Theatrical Movie Album
                        } else {
                            score -= 30000 // Repackaged into compilation/playlist
                        }
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestCover = rawArtwork.replace("100x100bb.jpg", "1000x1000bb.jpg")
                    }
                }
            }

            if (bestCover != null && bestScore > 0) return Pair(bestCover, bestScore)
        } catch (_: Exception) {}
        return null
    }

    private fun fetchJioSaavnMovieAlbumCover(movieName: String, targetLanguage: String): String? {
        try {
            val langHint = if (targetLanguage.isNotBlank()) targetLanguage.replaceFirstChar { it.uppercase() } else "Tamil"
            val encoded = URLEncoder.encode("$movieName $langHint", "UTF-8")
            val urlStr = "https://www.jiosaavn.com/api.php?__call=search.getAlbumResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=5&p=1"
            val req = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val text = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }

            val root = JSONObject(text)
            val results = root.optJSONArray("results")
            if (results != null && results.length() > 0) {
                for (i in 0 until results.length()) {
                    val alb = results.getJSONObject(i)
                    val albTitle = unescape(alb.optString("title", ""))
                    val albImage = alb.optString("image", "")
                    val isCompilation = COMPILATION_REGEX.containsMatchIn(albTitle)
                    val isEditorial = albImage.contains("/editorial/") || albImage.contains("/playlist/") || albImage.contains("default")

                    // Language check on album title
                    val albLower = albTitle.lowercase()
                    val otherLangs = listOf("telugu", "hindi", "kannada", "malayalam", "tamil", "punjabi")
                        .filter { it != targetLanguage.lowercase() }
                    val isOtherLang = otherLangs.any { albLower.contains("($it)") || albLower.contains("[$it]") }

                    if (albImage.isNotBlank() && !isCompilation && !isEditorial && !isOtherLang) {
                        return toHighResCover(albImage)
                    }
                }
                val firstImg = results.getJSONObject(0).optString("image")
                if (firstImg.isNotBlank() && !firstImg.contains("/editorial/")) return toHighResCover(firstImg)
            }
        } catch (_: Exception) {}
        return null
    }

    private fun fetchJioSaavnSongOfficialCover(
        title: String,
        artist: String,
        movieHint: String,
        targetLanguage: String
    ): Pair<String, Int>? {
        try {
            val langHint = if (targetLanguage.isNotBlank()) targetLanguage.replaceFirstChar { it.uppercase() } else "Tamil"
            val searchQueries = mutableListOf<String>()
            val artistTokens = artist.split(",", "&", "/", "feat.", "ft.").map { it.trim() }.filter { it.isNotBlank() }
            for (a in artistTokens.take(2)) {
                searchQueries.add("$title $a")
            }
            if (artist.isNotBlank() && !searchQueries.contains("$title $artist")) {
                searchQueries.add("$title $artist")
            }
            searchQueries.add("$title $langHint")
            searchQueries.add(title)

            var bestCover: String? = null
            var bestScore = Int.MIN_VALUE
            val queryNorm = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.normalizePhonetics(title)

            for (q in searchQueries) {
                val encoded = URLEncoder.encode(q, "UTF-8")
                val urlStr = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=12&p=1"
                val req = okhttp3.Request.Builder()
                    .url(urlStr)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .header("Referer", "https://www.jiosaavn.com/")
                    .build()
                val text = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }

                val root = JSONObject(text)
                val results = root.optJSONArray("results") ?: continue

                for (i in 0 until results.length()) {
                    val it = results.getJSONObject(i)
                    val resTitle = unescape(it.optString("title", ""))
                    val more = it.optJSONObject("more_info") ?: JSONObject()
                    val alb = unescape(more.optString("album", ""))
                    val img = it.optString("image", "")
                    val isEditorial = img.contains("/editorial/") || img.contains("/playlist/") || img.contains("default")

                    if (img.isBlank() || isEditorial) continue

                    val resNorm = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.normalizePhonetics(resTitle)
                    val isPhoneticMatch = resNorm.contains(queryNorm) || queryNorm.contains(resNorm)
                    val jaro = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.jaroWinkler(queryNorm, resNorm)
                    if (!isPhoneticMatch && jaro < 0.75) continue

                    var score = (jaro * 5000).toInt()

                    if (movieHint.isNotBlank() && alb.contains(movieHint, ignoreCase = true)) {
                        score += 15000
                    }

                    val isCompilation = COMPILATION_REGEX.containsMatchIn(alb)
                    if (isCompilation) {
                        score -= 60000 // Heavy rejection for compilation albums
                    } else {
                        score += 10000
                        if (alb.contains("Soundtrack", ignoreCase = true) || alb.contains("Original Motion Picture", ignoreCase = true) || alb.contains("Original Soundtrack", ignoreCase = true)) {
                            score += 25000
                        }
                    }

                    // Prefer vocal original over remixes, lofi, trap, covers, instrumental/karaoke versions
                    if (resTitle.contains("Cover", ignoreCase = true) ||
                        resTitle.contains("Tribute", ignoreCase = true) ||
                        resTitle.contains("Trap", ignoreCase = true) ||
                        resTitle.contains("Remix", ignoreCase = true) ||
                        resTitle.contains("Lofi", ignoreCase = true) ||
                        resTitle.contains("Instrumental", ignoreCase = true) ||
                        resTitle.contains("Karaoke", ignoreCase = true) ||
                        resTitle.contains("Rendition", ignoreCase = true)
                    ) {
                        score -= 40000
                    }

                    // Language Affinity Guard
                    val textCombined = "$resTitle $alb".lowercase()
                    val otherLangs = listOf("telugu", "hindi", "kannada", "malayalam", "tamil", "punjabi", "bengali")
                        .filter { it != targetLanguage.lowercase() }
                    for (other in otherLangs) {
                        if (textCombined.contains("($other)") || 
                            textCombined.contains("[$other]") || 
                            textCombined.contains("- $other")
                        ) {
                            score -= 30000
                        }
                    }
                    if (textCombined.contains("($targetLanguage)") || 
                        textCombined.contains("[$targetLanguage]") || 
                        textCombined.contains("- $targetLanguage")
                    ) {
                        score += 15000
                    }

                    // Authentic Movie Album Alignment Check (e.g. From "Yaaradi Nee Mohini")
                    val movieFromTrack = Regex("(?i)from\\s+[\"\'\u201c\u2018]?(.*?)[\"\'\u201d\u2019]?\\s*[\\)\\]]").find(resTitle)?.groupValues?.get(1)?.trim()
                    if (!movieFromTrack.isNullOrBlank()) {
                        if (alb.contains(movieFromTrack, ignoreCase = true)) {
                            score += 35000 // Verified Authentic Theatrical Movie Album
                        } else {
                            score -= 30000 // Repackaged into compilation/playlist
                        }
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestCover = img
                    }
                }
            }

            if (bestCover != null && bestScore > 0) return Pair(toHighResCover(bestCover), bestScore)
        } catch (_: Exception) {}
        return null
    }

    private fun toHighResCover(rawUrl: String): String {
        val highRes = rawUrl
            .replace(Regex("_\\d+x\\d+\\."), "_500x500.")
            .replace("150x150", "500x500")
            .replace("50x50", "500x500")
        return if (highRes.startsWith("http://")) "https://" + highRes.substring(7) else highRes
    }
}
