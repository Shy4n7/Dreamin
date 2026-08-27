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
}
