package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URLEncoder

object OfficialArtworkService {

    private val artworkCache = object : java.util.LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 1000
        }
    }

    private val COMPILATION_REGEX = Regex(
        "(?i)\\b(mixtape|bighits|big hits|think music|thinkmusic|double delights|double delight|delights|delight|double|duo|duets|triple|jodi|combo|treats|fire & desire|fire and desire|desire|this is|best of|top hits|hits|vol\\b|vol\\.|volume|love notes|collection|playlist|raga collective|kondattam|selected|radio|superhit|compilation|greatest hits|evergreen|melody|melodies|latest|essential|party|workout|romance|mashup|area boys|konjam|thamizh music|special|tribute|celebration|magic of|voice of|golden|non stop|jukebox|rewind|finesse|starry|mazhaiyum|thooral|pure|simply|anthology|sounds of|sensational|absolute|trending version|ungaludan|dhamaka|masterworks|all about love|sun-kissed|summer vibes|my playlist|words of|mazhaikaalam|joy|saaral|special|singer special|in the words of|feel good|night drive|soulful|chill tracks|unlimited|hits of)\\b"
    )

    fun getCachedPoster(song: Song): String? = synchronized(artworkCache) {
        if (song.id.isNotBlank()) {
            artworkCache[song.id]?.let { return it }
        }
        val fullKey = "${song.displayTitle.lowercase()}_${song.artist.lowercase()}".trim()
        artworkCache[fullKey]?.let { return it }
        val titleOnlyKey = song.displayTitle.lowercase().trim()
        return artworkCache[titleOnlyKey]
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
        val primaryArtist = song.artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""

        // Context-aware language inference for regional soundtrack accuracy
        val detectedLang = when {
            song.title.contains("tamil", ignoreCase = true) || song.artist.contains("anirudh", ignoreCase = true) || song.artist.contains("rahman", ignoreCase = true) || song.artist.contains("yuvan", ignoreCase = true) || song.artist.contains("ilayaraja", ignoreCase = true) || song.artist.contains("harris", ignoreCase = true) || song.artist.contains("santhosh", ignoreCase = true) || song.artist.contains("g.v.", ignoreCase = true) || song.artist.contains("gv prakash", ignoreCase = true) || song.artist.contains("sid sriram", ignoreCase = true) || song.artist.contains("dhibu", ignoreCase = true) -> "tamil"
            song.title.contains("telugu", ignoreCase = true) || song.artist.contains("thaman", ignoreCase = true) || song.artist.contains("devi sri prasad", ignoreCase = true) || song.artist.contains("dsp", ignoreCase = true) || song.artist.contains("keeravani", ignoreCase = true) || song.artist.contains("anurag kulkarni", ignoreCase = true) -> "telugu"
            song.title.contains("malayalam", ignoreCase = true) || song.artist.contains("sushin shyam", ignoreCase = true) || song.artist.contains("shaan rahman", ignoreCase = true) || song.artist.contains("hesham", ignoreCase = true) || song.artist.contains("gopi sundar", ignoreCase = true) || song.artist.contains("vidyasagar", ignoreCase = true) -> "malayalam"
            song.title.contains("hindi", ignoreCase = true) || song.artist.contains("arijit", ignoreCase = true) || song.artist.contains("pritam", ignoreCase = true) || song.artist.contains("vishal", ignoreCase = true) || song.artist.contains("shekhar", ignoreCase = true) || song.artist.contains("atif", ignoreCase = true) || song.artist.contains("badshah", ignoreCase = true) -> "hindi"
            else -> targetLanguage
        }

        // 1. Tier-1: Query Apple Music Official Movie Soundtrack Catalog (1000x1000 Official Theatrical Covers)
        val applePoster = fetchAppleMusicOfficialCover(cleanTitle, primaryArtist, detectedLang)
        if (!applePoster.isNullOrBlank()) {
            artworkCache.put(cacheKey, applePoster)
            artworkCache.put(titleKey, applePoster)
            if (song.id.isNotBlank()) artworkCache.put(song.id, applePoster)
            return@withContext applePoster
        }

        // 2. Try extracting movie name from title tag (e.g. From "Duet", From '3', From Paiyaa, From "24")
        var movieName = ""
        val m = Regex("(?i)\\s*\\(?\\s*(?:from|movie)\\s+[\"\'\u201c\u2018]?(.*?)[\"\'\u201d\u2019]?\\s*\\)?").find(song.title)
        if (m != null) {
            val cand = m.groupValues[1].replace(Regex("[\"\'\u201c\u2018\u201d\u2019]"), "").trim()
            if (cand.isNotBlank() && cand.length >= 2) {
                movieName = cand
            }
        }

        // 3. Query JioSaavn Official Movie Album API if movie name is identified
        if (movieName.isNotBlank()) {
            val albumPoster = fetchJioSaavnMovieAlbumCover(movieName, detectedLang)
            if (!albumPoster.isNullOrBlank()) {
                artworkCache.put(cacheKey, albumPoster)
                artworkCache.put(titleKey, albumPoster)
                if (song.id.isNotBlank()) artworkCache.put(song.id, albumPoster)
                return@withContext albumPoster
            }
        }

        // 4. Query JioSaavn Official Song Catalog with strict title & movie soundtrack prioritization
        val songPoster = fetchJioSaavnSongOfficialCover(cleanTitle, primaryArtist, movieName, detectedLang)
        if (!songPoster.isNullOrBlank()) {
            artworkCache.put(cacheKey, songPoster)
            artworkCache.put(titleKey, songPoster)
            if (song.id.isNotBlank()) artworkCache.put(song.id, songPoster)
            return@withContext songPoster
        }

        // 5. Phonetic Fallback via IntelliMatch (e.g. "mazhaye" -> "mazhaiye")
        val phoneticAlt = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.generatePhoneticSuggestions(cleanTitle)
        if (!phoneticAlt.isNullOrBlank()) {
            val altPoster = fetchAppleMusicOfficialCover(phoneticAlt, primaryArtist, detectedLang)
                ?: fetchJioSaavnSongOfficialCover(phoneticAlt, primaryArtist, movieName, detectedLang)
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

    private fun fetchAppleMusicOfficialCover(title: String, artist: String, targetLanguage: String): String? {
        try {
            val (baseTitle, _) = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.decomposeTitle(title)
            val cleanTitle = if (baseTitle.isNotBlank()) baseTitle else title
            val cleanArtist = artist.split(",", "&", "feat.", "ft.", "/").firstOrNull()?.trim() ?: ""
            val langHint = if (targetLanguage.isNotBlank()) targetLanguage.replaceFirstChar { it.uppercase() } else "Tamil"
            val q = if (cleanArtist.isNotBlank()) "$cleanTitle $cleanArtist" else "$cleanTitle $langHint"
            val encoded = URLEncoder.encode(q, "UTF-8")
            val urlStr = "https://itunes.apple.com/search?term=$encoded&entity=song&country=IN&limit=10"
            val req = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .build()
            val text = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }
            val root = JSONObject(text)
            val results = root.optJSONArray("results")
            if (results != null && results.length() > 0) {
                var bestCover: String? = null
                var bestScore = Int.MIN_VALUE
                val queryNorm = com.shyan.dreamin.data.recommendation.IntelliMatchEngine.normalizePhonetics(cleanTitle)

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
                        score -= 30000
                    } else {
                        score += 8000
                        if (collectionName.contains("Soundtrack", ignoreCase = true) || collectionName.contains("Original Motion Picture", ignoreCase = true)) {
                            score += 10000
                        }
                    }

                    // Language Affinity Guard (prevents picking Telugu/Hindi dub posters when Tamil is intended)
                    val textCombined = "$trackName $collectionName".lowercase()
                    val otherLangs = listOf("telugu", "hindi", "kannada", "malayalam", "tamil", "punjabi")
                        .filter { it != targetLanguage.lowercase() }
                    for (other in otherLangs) {
                        if (textCombined.contains("($other)") || 
                            textCombined.contains("[$other]") || 
                            textCombined.contains("- $other") || 
                            textCombined.contains("from \"$other\"")
                        ) {
                            score -= 25000
                        }
                    }
                    if (textCombined.contains("($targetLanguage)") || 
                        textCombined.contains("[$targetLanguage]") || 
                        textCombined.contains("- $targetLanguage")
                    ) {
                        score += 15000
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestCover = rawArtwork.replace("100x100bb.jpg", "1000x1000bb.jpg")
                    }
                }

                if (bestCover != null && bestScore > 0) return bestCover
            }
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
    ): String? {
        try {
            val langHint = if (targetLanguage.isNotBlank()) targetLanguage.replaceFirstChar { it.uppercase() } else "Tamil"
            val q = if (artist.isNotBlank()) "$title $artist" else "$title $langHint"
            val encoded = URLEncoder.encode(q, "UTF-8")
            val urlStr = "https://www.jiosaavn.com/api.php?__call=search.getResults&_format=json&_marker=0&api_version=4&ctx=web6dot0&q=$encoded&n=12&p=1"
            val req = okhttp3.Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .header("Referer", "https://www.jiosaavn.com/")
                .build()
            val text = com.shyan.dreamin.data.network.NetworkService.httpClient.newCall(req).execute().use { it.body?.string().orEmpty() }

            val root = JSONObject(text)
            val results = root.optJSONArray("results")
            if (results != null && results.length() > 0) {
                var bestCover: String? = null
                var bestScore = Int.MIN_VALUE
                val cleanQueryTitle = title.lowercase().replace(Regex("[^a-z0-9]"), "")

                for (i in 0 until results.length()) {
                    val it = results.getJSONObject(i)
                    val resTitle = unescape(it.optString("title", ""))
                    val cleanResTitle = resTitle.lowercase().replace(Regex("[^a-z0-9]"), "")

                    // Candidate title must match query song title
                    val isExactMatch = cleanResTitle == cleanQueryTitle
                    val isContained = cleanResTitle.contains(cleanQueryTitle) || cleanQueryTitle.contains(cleanResTitle)
                    if (!isContained) continue

                    val more = it.optJSONObject("more_info") ?: JSONObject()
                    val alb = unescape(more.optString("album", ""))
                    val img = it.optString("image", "")
                    val isEditorial = img.contains("/editorial/") || img.contains("/playlist/") || img.contains("default")

                    if (img.isBlank() || isEditorial) continue

                    var score = 0
                    if (isExactMatch) score += 5000
                    else score += 2000

                    if (movieHint.isNotBlank() && alb.contains(movieHint, ignoreCase = true)) {
                        score += 8000
                    }

                    val isCompilation = COMPILATION_REGEX.containsMatchIn(alb)
                    if (isCompilation) {
                        score -= 30000 // Heavy rejection for compilation albums like Double Delights, Fire & Desire, Hits of...
                    } else {
                        score += 6000
                        if (alb.contains("Soundtrack", ignoreCase = true) || alb.contains("Original Motion Picture", ignoreCase = true)) {
                            score += 8000
                        }
                    }

                    // Language Affinity Guard
                    val textCombined = "$resTitle $alb".lowercase()
                    val otherLangs = listOf("telugu", "hindi", "kannada", "malayalam", "tamil", "punjabi")
                        .filter { it != targetLanguage.lowercase() }
                    for (other in otherLangs) {
                        if (textCombined.contains("($other)") || 
                            textCombined.contains("[$other]") || 
                            textCombined.contains("- $other")
                        ) {
                            score -= 25000
                        }
                    }
                    if (textCombined.contains("($targetLanguage)") || 
                        textCombined.contains("[$targetLanguage]") || 
                        textCombined.contains("- $targetLanguage")
                    ) {
                        score += 15000
                    }

                    if (score > bestScore) {
                        bestScore = score
                        bestCover = img
                    }
                }

                if (bestCover != null && bestScore > 0) return toHighResCover(bestCover)
            }
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
