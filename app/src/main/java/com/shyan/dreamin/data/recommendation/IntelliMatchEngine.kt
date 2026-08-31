package com.shyan.dreamin.data.recommendation

import com.shyan.dreamin.data.model.Song
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * 🧠 IntelliMatch Engine — Intelligent Multi-Factor Music Matcher.
 *
 * Implements:
 * - Jaro-Winkler string similarity distance
 * - Token Set / Sort ratios for inverted or reordered title/artist tokens
 * - Indian & Regional music transliteration phonetic normalizer
 * - Multi-factor confidence score classifier (HIGH, MEDIUM, LOW)
 */
object IntelliMatchEngine {

    enum class MatchConfidence {
        HIGH,      // Score >= 750: Confident automatic match
        MEDIUM,    // Score in 450..749: Plausible match, eligible for auto-selection
        LOW        // Score < 450: Unreliable or poor match, offered as smart suggestion
    }

    data class MatchResult(
        val song: Song,
        val score: Int,
        val confidence: MatchConfidence,
        val reason: String
    )

    /**
     * Decomposes and cleans raw titles by extracting base title, removing nested brackets,
     * stripping noise keywords, and extracting potential film/soundtrack keywords.
     */
    fun decomposeTitle(rawTitle: String): Pair<String, String> {
        var cleaned = rawTitle
        var prev = ""
        while (prev != cleaned) {
            prev = cleaned
            cleaned = cleaned
                .replace(Regex("\\([^()]*\\)"), "")
                .replace(Regex("\\[[^\\[\\]]*\\]"), "")
                .replace(Regex("\\{[^{}]*\\}"), "")
        }
        val fullClean = cleaned.replace(Regex("[\"\'“”‘’]"), "").trim().ifBlank { rawTitle.trim() }
        val subtitleParts = fullClean.split(Regex("\\s+[-–—:|]\\s+"))
        val baseTitle = subtitleParts.firstOrNull()?.trim()?.ifBlank { fullClean } ?: fullClean
        return Pair(baseTitle, fullClean)
    }

    /**
     * Normalizes transliterations and phonetic quirks in Indian film and pop music.
     * e.g. "mazhaye" <-> "mazhaiye", "thamizhan" <-> "tamilan", "kadavule" <-> "kadavuley"
     */
    fun normalizePhonetics(input: String): String {
        var str = input.lowercase().trim()
        str = str.replace(Regex("[^a-z0-9\\s]"), "")
        str = str.replace("zh", "l")
            .replace("ai", "ay")
            .replace("ey", "ay")
            .replace("ee", "i")
            .replace("oo", "u")
            .replace("th", "t")
            .replace("dh", "d")
            .replace("bh", "b")
            .replace("gh", "g")
            .replace("ph", "p")
            .replace("aa", "a")
            .replace("ii", "i")
            .replace("uu", "u")
        // Remove repeated consecutive letters (e.g. "kannazhaga" -> "kanazhaga")
        str = str.replace(Regex("(.)\\1+"), "$1")
        return str.replace(Regex("\\s+"), "")
    }

    /**
     * Computes Jaro-Winkler distance between two strings (0.0 to 1.0).
     */
    fun jaroWinkler(s1: String, s2: String): Double {
        if (s1 == s2) return 1.0
        if (s1.isEmpty() || s2.isEmpty()) return 0.0

        val maxDist = (max(s1.length, s2.length) / 2) - 1
        val s1Matches = BooleanArray(s1.length)
        val s2Matches = BooleanArray(s2.length)

        var matches = 0
        for (i in s1.indices) {
            val start = max(0, i - maxDist)
            val end = min(i + maxDist + 1, s2.length)
            for (j in start until end) {
                if (s2Matches[j]) continue
                if (s1[i] != s2[j]) continue
                s1Matches[i] = true
                s2Matches[j] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var k = 0
        for (i in s1.indices) {
            if (!s1Matches[i]) continue
            while (!s2Matches[k]) k++
            if (s1[i] != s2[k]) transpositions++
            k++
        }

        val jaro = (matches.toDouble() / s1.length +
                    matches.toDouble() / s2.length +
                    (matches - transpositions / 2.0) / matches) / 3.0

        // Winkler prefix bonus (up to 4 chars)
        var prefix = 0
        val maxPrefix = min(4, min(s1.length, s2.length))
        for (i in 0 until maxPrefix) {
            if (s1[i] == s2[i]) prefix++ else break
        }

        return jaro + (prefix * 0.1 * (1.0 - jaro))
    }

    /**
     * Context-aware language inference for Spotify Playlists.
     * Scans playlist title, track titles, and regional artist profiles to detect the dominant language.
     * Returns "tamil", "malayalam", "telugu", "hindi", "kannada", "punjabi", or "english".
     */
    fun detectDominantPlaylistLanguage(
        tracks: List<com.shyan.dreamin.data.service.SpotifyImportedTrack>,
        playlistTitle: String = ""
    ): String {
        val langScores = mutableMapOf(
            "tamil" to 0,
            "malayalam" to 0,
            "telugu" to 0,
            "hindi" to 0,
            "kannada" to 0,
            "punjabi" to 0,
            "english" to 0
        )

        val plLower = playlistTitle.lowercase()
        for (lang in langScores.keys) {
            if (plLower.contains(lang)) {
                langScores[lang] = langScores.getValue(lang) + 15
            }
        }

        val malayalamArtists = setOf(
            "sushin shyam", "hesham abdul wahab", "jassie gift", "deepak dev",
            "shaan rahman", "alphons joseph", "gopi sundar", "bijibal", "mg sreekumar",
            "vineeth sreenivasan", "job kurian", "dabzee", "vedan", "vishnu vijay",
            "christo xavier", "rex vijayan", "kailas", "sithara krishnakumar",
            "najim arshad", "sooraj santhosh", "k.s. harisankar", "ks harisankar"
        )
        val tamilArtists = setOf(
            "anirudh ravichander", "a.r. rahman", "ar rahman", "yuvan shankar raja", "harris jayaraj",
            "g.v. prakash", "gv prakash", "santhosh narayanan", "d. imman", "d imman",
            "deva", "ilayaraja", "ilaiyaraaja", "sid sriram", "anthony daasan",
            "dhee", "sean roldan", "hiphop tamizha", "sam c.s.", "sam cs", "leon james", "ghibran"
        )
        val teluguArtists = setOf(
            "devi sri prasad", "dsp", "thaman s", "s. thaman", "m.m. keeravani", "keeravani",
            "mickey j. meyer", "anup rubens", "chaitan bharadwaj", "mahathi swara sagar",
            "ram miriyala", "anurag kulkarni", "sri krishna", "geetha madhuri", "mangli",
            "hema chandra", "rahul sipligunj", "penchal das"
        )
        val hindiArtists = setOf(
            "arijit singh", "pritam", "vishal-shekhar", "vishal mishra", "sachin-jigar",
            "tanishk bagchi", "neha kakkar", "badshah", "guru randhawa",
            "sonu nigam", "shaan", "kk", "kumar sanu", "alka yagnik", "udit narayan",
            "amit trivedi", "mithoon", "shankar-ehsaan-loy", "jubin nautiyal", "b praak"
        )

        for (t in tracks) {
            val titleLower = t.title.lowercase()
            val artistLower = t.artist.lowercase()

            for (lang in listOf("tamil", "malayalam", "telugu", "hindi", "kannada", "punjabi", "english")) {
                if (titleLower.contains("($lang)") || titleLower.contains("[$lang]")) {
                    langScores[lang] = langScores.getValue(lang) + 10
                }
            }

            if (malayalamArtists.any { artistLower.contains(it) }) {
                langScores["malayalam"] = langScores.getValue("malayalam") + 3
            }
            if (tamilArtists.any { artistLower.contains(it) }) {
                langScores["tamil"] = langScores.getValue("tamil") + 3
            }
            if (teluguArtists.any { artistLower.contains(it) }) {
                langScores["telugu"] = langScores.getValue("telugu") + 3
            }
            if (hindiArtists.any { artistLower.contains(it) }) {
                langScores["hindi"] = langScores.getValue("hindi") + 3
            }
        }

        val best = langScores.maxByOrNull { it.value }
        return if (best != null && best.value > 0) best.key else "tamil"
    }

    /**
     * Evaluates and scores a candidate song against a target Spotify track.
     */
    fun evaluateCandidate(
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long,
        candidate: Song,
        targetLanguage: String = "tamil"
    ): MatchResult {
        val cleanTargetLower = targetTitle.trim().lowercase()
        if (cleanTargetLower == "spotify" || cleanTargetLower.startsWith("spotify track") || cleanTargetLower.startsWith("spotify playlist") || cleanTargetLower.isBlank()) {
            return MatchResult(candidate, 0, MatchConfidence.LOW, "Blocked dummy track title")
        }

        var score = 1000
        val (targetBase, targetFull) = decomposeTitle(targetTitle)
        val (candBase, _) = decomposeTitle(candidate.title)

        val targetBaseKey = targetBase.lowercase().replace(Regex("[^a-z0-9]"), "")
        val candBaseKey = candBase.lowercase().replace(Regex("[^a-z0-9]"), "")
        val targetFullKey = targetFull.lowercase().replace(Regex("[^a-z0-9]"), "")

        // 1. Exact & Jaro-Winkler Title Similarity
        val titleJaro = max(
            jaroWinkler(targetBaseKey, candBaseKey),
            jaroWinkler(targetFullKey, candBaseKey)
        )
        val phonJaro = jaroWinkler(normalizePhonetics(targetBase), normalizePhonetics(candBase))
        val maxTitleSim = max(titleJaro, phonJaro)

        if (targetBaseKey == candBaseKey || targetFullKey == candBaseKey) {
            score += 750
        } else if (maxTitleSim >= 0.90) {
            score += 650
        } else if (maxTitleSim >= 0.78 || candBaseKey.contains(targetBaseKey) || targetBaseKey.contains(candBaseKey)) {
            score += 480
        } else if (maxTitleSim >= 0.65) {
            score += 250
        } else {
            score -= 300
        }

        // 2. Artist Overlap & Role Matching (Punctuation-Agnostic)
        val targetArtistsNormalized = targetArtist.lowercase()
            .split(",", "&", "feat.", "ft.", "/", ";")
            .map { it.replace(Regex("[^a-z0-9]"), "").trim() }
            .filter { it.length >= 2 }

        val candArtistNormalized = candidate.artist.lowercase().replace(Regex("[^a-z0-9]"), "")

        var artistHits = 0
        for (ta in targetArtistsNormalized) {
            if (candArtistNormalized.contains(ta) || ta.contains(candArtistNormalized)) {
                artistHits++
                score += 220
            } else {
                val candSubTokens = candidate.artist.lowercase().split(",", "&", "/", " ")
                    .map { it.replace(Regex("[^a-z0-9]"), "").trim() }
                    .filter { it.length >= 3 }
                if (candSubTokens.any { jaroWinkler(ta, it) >= 0.85 }) {
                    artistHits++
                    score += 160
                }
            }
        }
        if (artistHits > 0) score += 250

        // 3. Language Affinity (Context-aware preference for targetLanguage over other regional versions)
        val candTitleLower = candidate.title.lowercase()
        val otherLanguages = listOf("telugu", "hindi", "kannada", "malayalam", "tamil", "punjabi").filter { it != targetLanguage.lowercase() }
        for (other in otherLanguages) {
            if (candTitleLower.contains("($other)") || candTitleLower.contains("[$other]")) {
                score -= 300
            }
        }
        if (candTitleLower.contains("($targetLanguage)") || candTitleLower.contains("[$targetLanguage]")) {
            score += 350
        }

        val isExactTrackMatch = (targetBaseKey == candBaseKey || maxTitleSim >= 0.85) && artistHits > 0

        // 4. Adaptive Duration Tolerance (Soundtrack vs Audio Cut)
        if (targetDurationMs > 0 && candidate.duration > 0) {
            val diffSec = abs(targetDurationMs - candidate.duration) / 1000
            if (diffSec <= 6) score += 250
            else if (diffSec <= 18) score += 140
            else if (diffSec <= 30) score += 60
            else if (diffSec > 60) score -= 300
        }

        // 5. Stream Popularity Weight (Logarithmic Scoring)
        // Prioritizes official multi-million stream releases over fan/karaoke/low-stream duplicates
        if (candidate.playCount > 0L) {
            val logPopularity = kotlin.math.log10(candidate.playCount.toDouble()).coerceAtLeast(0.0)
            score += (logPopularity * 150.0).toInt()
        }

        val confidence = when {
            isExactTrackMatch || score >= 1600 -> MatchConfidence.HIGH
            score >= 1200 -> MatchConfidence.MEDIUM
            else -> MatchConfidence.LOW
        }

        val reason = "TitleSim: ${(maxTitleSim * 100).toInt()}%, ArtistsMatched: $artistHits, DurDiff: ${if (targetDurationMs > 0 && candidate.duration > 0) "${abs(targetDurationMs - candidate.duration) / 1000}s" else "N/A"}"

        return MatchResult(
            song = candidate,
            score = score,
            confidence = confidence,
            reason = reason
        )
    }

    /**
     * Ranks a list of candidates and picks the highest scoring match if it meets minimum threshold.
     */
    fun findBestMatch(
        targetTitle: String,
        targetArtist: String,
        targetDurationMs: Long,
        candidates: List<Song>,
        targetLanguage: String = "tamil"
    ): MatchResult? {
        if (candidates.isEmpty()) return null
        return candidates.map { cand ->
            evaluateCandidate(targetTitle, targetArtist, targetDurationMs, cand, targetLanguage)
        }.maxByOrNull { it.score }
    }

    // Noise patterns found in downloaded/local files (websites, bitrates, rip tags)
    private val websiteWatermarkRegex = Regex("(?i)\\b(masstamilan|isaimini|songspk|tamilmv|starmusiq|kuttyweb|pagalworld|sensongs|mp3mad|raaga|gaana|jiosaavn|wynk|hungama|naasongs|filmywap|cinejosh|tamildada|tamiltunes|123musiq|southmp3|mobcup|ringtones|djtamil|djremix|desinode|freshmaza|mp3skull|beemp3)\\b")
    private val bitrateWatermarkRegex = Regex("(?i)\\b(320\\s*kbps|128\\s*kbps|192\\s*kbps|256\\s*kbps|64\\s*kbps|vbr|cbr|flac|lossless|cd\\s*rip|dvd\\s*rip|web\\s*rip|hq|hd|audio|track|mp3|m4a|aac|wav|ogg)\\b")
    private val trackNumberPrefixRegex = Regex("^[0-9]{1,3}\\s*[-._]\\s*")

    /**
     * Cleans raw local or downloaded filenames and tags into official clean titles and artists.
     */
    fun cleanTrackMetadata(rawTitle: String, rawArtist: String = ""): Pair<String, String> {
        var cleanTitle = rawTitle.trim()

        // 1. Strip track number prefixes like "01 - " or "01_"
        cleanTitle = trackNumberPrefixRegex.replace(cleanTitle, "")

        // 2. Strip file extensions
        cleanTitle = cleanTitle.replace(Regex("(?i)\\.(mp3|m4a|flac|wav|ogg|aac)$"), "")

        // 3. Strip website and bitrate watermarks
        cleanTitle = websiteWatermarkRegex.replace(cleanTitle, "")
        cleanTitle = bitrateWatermarkRegex.replace(cleanTitle, "")

        // 4. Strip domain-like extensions (e.g. .com, .dev, .org, .net, .co)
        cleanTitle = cleanTitle.replace(Regex("(?i)\\.(com|dev|org|net|co|in|is|io|ws|me|cc)\\b"), "")

        // 5. Clean brackets and noise
        val (_, fullClean) = decomposeTitle(cleanTitle)
        val finalTitle = fullClean.replace(Regex("\\s+"), " ").trim(' ', '-', '_', '.', '|', ':')

        // 6. Clean artist tags
        var cleanArtist = rawArtist.trim()
        val (_, cleanArtistDecomposed) = decomposeTitle(cleanArtist)
        cleanArtist = websiteWatermarkRegex.replace(cleanArtistDecomposed, "")
        cleanArtist = bitrateWatermarkRegex.replace(cleanArtist, "")
        cleanArtist = cleanArtist.replace(Regex("(?i)\\.(com|dev|org|net|co|in|is|io|ws|me|cc)\\b"), "")
        cleanArtist = cleanArtist.replace(Regex("(?i)\\[[^\\[\\]]*\\]|\\([^()]*\\)"), "")
        cleanArtist = cleanArtist.replace(Regex("\\s+"), " ").trim(' ', '-', '_', '.', '|', ':')

        return Pair(
            if (finalTitle.isNotBlank()) finalTitle else rawTitle.trim(),
            if (cleanArtist.isNotBlank()) cleanArtist else "Various Artists"
        )
    }

    /**
     * Dictionary of common regional phonetic spelling corrections and variations.
     */
    private val canonicalPhoneticReplacements = listOf(
        Pair(Regex("(?i)\\baniruth\\b"), "Anirudh"),
        Pair(Regex("(?i)\\billayaraja\\b"), "Ilayaraja"),
        Pair(Regex("(?i)\\bilyaraja\\b"), "Ilayaraja"),
        Pair(Regex("(?i)\\billaiyaraaja\\b"), "Ilayaraja"),
        Pair(Regex("(?i)\\bmazhaye\\b"), "Mazhaiye"),
        Pair(Regex("(?i)\\bkannazhaga\\b"), "Kanazhaga"),
        Pair(Regex("(?i)\\barabikuthu\\b"), "Arabic Kuthu"),
        Pair(Regex("(?i)\\barabic\\s*koothu\\b"), "Arabic Kuthu"),
        Pair(Regex("(?i)\\bthalaivar\\b"), "Thalaivar"),
        Pair(Regex("(?i)\\btalaivar\\b"), "Thalaivar"),
        Pair(Regex("(?i)\\byuvan\\s*shankar\\b"), "Yuvan Shankar Raja"),
        Pair(Regex("(?i)\\bsid\\s*sreeram\\b"), "Sid Sriram"),
        Pair(Regex("(?i)\\bshreya\\s*goshal\\b"), "Shreya Ghoshal"),
        Pair(Regex("(?i)\\bchinmayee\\b"), "Chinmayi"),
        Pair(Regex("(?i)\\bnaresh\\s*iyer\\b"), "Naresh Iyer"),
        Pair(Regex("(?i)\\bharrish\\s*jayaraj\\b"), "Harris Jayaraj"),
        Pair(Regex("(?i)\\bharris\\s*jeyaraj\\b"), "Harris Jayaraj")
    )

    /**
     * Analyzes a user search query and generates a canonical "Did you mean" suggestion if a known typo or variant exists.
     */
    fun generatePhoneticSuggestions(query: String): String? {
        val trimmed = query.trim()
        if (trimmed.length < 3) return null

        var suggested = trimmed
        var replaced = false

        for ((regex, canonical) in canonicalPhoneticReplacements) {
            if (regex.containsMatchIn(suggested)) {
                suggested = regex.replace(suggested, canonical)
                replaced = true
            }
        }

        if (replaced && !suggested.equals(trimmed, ignoreCase = true)) {
            return suggested
        }

        // Generic phonetic transformations (e.g. trailing "ye" -> "iye", "zhaye" -> "zhaiye")
        if (suggested.contains("zhaye", ignoreCase = true)) {
            return suggested.replace(Regex("(?i)zhaye"), "zhaiye")
        }

        return null
    }

    /**
     * Ranks search candidates using multi-factor phonetic, token similarity, and stream popularity against the user query.
     */
    fun fuzzyRankSearchResults(query: String, candidates: List<Song>): List<Song> {
        if (candidates.isEmpty()) return candidates
        val queryNorm = normalizePhonetics(query)
        val queryTokens = query.lowercase().split(" ").filter { it.length >= 2 }

        return candidates.sortedByDescending { song ->
            var score = 0L

            val titleNorm = normalizePhonetics(song.displayTitle)
            val artistNorm = normalizePhonetics(song.artist)

            // 1. Direct phonetic exact match, prefix, or containment for title
            if (titleNorm.equals(queryNorm, ignoreCase = true)) {
                score += 5000L
            } else if (titleNorm.startsWith(queryNorm) || queryNorm.startsWith(titleNorm)) {
                score += 3000L
            } else if (titleNorm.contains(queryNorm) || queryNorm.contains(titleNorm)) {
                score += 2000L
            } else {
                val jaro = jaroWinkler(queryNorm, titleNorm)
                score += (jaro * 1200).toLong()
            }

            // 2. Direct phonetic exact match or prefix for artist
            if (artistNorm.equals(queryNorm, ignoreCase = true)) {
                score += 2500L
            } else if (artistNorm.contains(queryNorm) || queryNorm.contains(artistNorm)) {
                score += 1500L
            } else {
                val jaroArtist = jaroWinkler(queryNorm, artistNorm)
                if (jaroArtist >= 0.70) {
                    score += (jaroArtist * 800).toLong()
                }
            }

            // 3. Token overlap with title & artist
            val songTitleTokens = song.displayTitle.lowercase().split(" ", "(", ")", "-", "_")
            val songArtistTokens = song.artist.lowercase().split(" ", ",", "&")
            for (qt in queryTokens) {
                val qtNorm = normalizePhonetics(qt)
                if (songTitleTokens.any { normalizePhonetics(it) == qtNorm }) {
                    score += 500L
                }
                if (songArtistTokens.any { normalizePhonetics(it) == qtNorm }) {
                    score += 400L
                }
            }

            // 4. Stream Popularity Weight (Industry Standard Logarithmic Scoring)
            // 5M streams gives ~log10(5000000) * 150 = ~1005 bonus points
            if (song.playCount > 0L) {
                val logPopularity = kotlin.math.log10(song.playCount.toDouble()).coerceAtLeast(0.0)
                score += (logPopularity * 150.0).toLong()
            }

            // 5. Title cleanliness bonus (soundtrack/official versions over compilations/remixes)
            if (!song.title.contains("remix", ignoreCase = true) && !song.title.contains("mashup", ignoreCase = true)) {
                score += 200L
            }

            score
        }
    }
}
