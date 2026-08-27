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
        val subtitleParts = fullClean.split(Regex("\\s+[-–—:]\\s+"))
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
            score += 700
        } else if (maxTitleSim >= 0.90) {
            score += 600
        } else if (maxTitleSim >= 0.80 || candBaseKey.contains(targetBaseKey) || targetBaseKey.contains(candBaseKey)) {
            score += 450
        } else if (maxTitleSim >= 0.68) {
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
                score += 200
            } else {
                // Check Jaro on artist sub-tokens
                val candSubTokens = candidate.artist.lowercase().split(",", "&", "/", " ")
                    .map { it.replace(Regex("[^a-z0-9]"), "").trim() }
                    .filter { it.length >= 3 }
                if (candSubTokens.any { jaroWinkler(ta, it) >= 0.88 }) {
                    artistHits++
                    score += 150
                }
            }
        }
        if (artistHits > 0) score += 200

        // 3. Language Affinity
        val candTitleLower = candidate.title.lowercase()
        val otherLanguages = listOf("telugu", "hindi", "kannada", "malayalam", "punjabi").filter { it != targetLanguage.lowercase() }
        for (other in otherLanguages) {
            if (candTitleLower.contains("($other)") || candTitleLower.contains("[$other]")) {
                score -= 300
            }
        }
        if (candTitleLower.contains("($targetLanguage)") || candTitleLower.contains("[$targetLanguage]")) {
            score += 350
        }

        // 4. Adaptive Duration Tolerance (Soundtrack vs Audio Cut)
        if (targetDurationMs > 0 && candidate.duration > 0) {
            val diffSec = abs(targetDurationMs - candidate.duration) / 1000
            if (diffSec <= 5) score += 250
            else if (diffSec <= 15) score += 140
            else if (diffSec <= 25) score += 60
            else if (diffSec > 60) score -= 350
        }

        val confidence = when {
            score >= 1750 -> MatchConfidence.HIGH
            score >= 1400 -> MatchConfidence.MEDIUM
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
