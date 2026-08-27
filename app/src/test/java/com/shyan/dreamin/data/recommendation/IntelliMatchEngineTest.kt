package com.shyan.dreamin.data.recommendation

import com.shyan.dreamin.data.model.Song
import org.junit.Assert.*
import org.junit.Test

class IntelliMatchEngineTest {

    @Test
    fun testDecomposeTitle_extractsBaseTitleAndStripsNestedBrackets() {
        val (base1, full1) = IntelliMatchEngine.decomposeTitle("Idhazhin Oram - The Innocence of Love")
        assertEquals("Idhazhin Oram", base1)
        assertEquals("Idhazhin Oram - The Innocence of Love", full1)

        val (base2, full2) = IntelliMatchEngine.decomposeTitle("Theethiriyaai (From \"Brahmastra (Tamil)\")")
        assertEquals("Theethiriyaai", base2)
        assertEquals("Theethiriyaai", full2)

        val (base3, full3) = IntelliMatchEngine.decomposeTitle("Arabic Kuthu - Halamithi Habibo (From \"Beast\")")
        assertEquals("Arabic Kuthu", base3)
        assertEquals("Arabic Kuthu - Halamithi Habibo", full3)
    }

    @Test
    fun testNormalizePhonetics_handlesTransliterations() {
        val p1 = IntelliMatchEngine.normalizePhonetics("Kannazhaga")
        val p2 = IntelliMatchEngine.normalizePhonetics("Kanazhaga")
        assertEquals(p1, p2)

        val m1 = IntelliMatchEngine.normalizePhonetics("Mazhaiye")
        val m2 = IntelliMatchEngine.normalizePhonetics("Mazhaye")
        assertEquals(m1, m2)
    }

    @Test
    fun testJaroWinkler_returnsHighSimilarityForTypoAndTranspositions() {
        val score = IntelliMatchEngine.jaroWinkler("anirudh ravichander", "anirudh ravichandran")
        assertTrue("Expected similarity > 0.9, got $score", score >= 0.90)
    }

    @Test
    fun testEvaluateCandidate_highConfidenceMatch() {
        val candidate = Song(
            id = "test_1",
            title = "Idhazhin Oram (From \"3\")",
            artist = "Anirudh Ravichander, Ajesh Ashok",
            artworkUrl = "http://example.com/cover.jpg",
            duration = 207000L
        )

        val result = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Idhazhin Oram - The Innocence of Love",
            targetArtist = "Anirudh Ravichander, Ajesh",
            targetDurationMs = 207000L,
            candidate = candidate,
            targetLanguage = "tamil"
        )

        assertTrue(result.score >= 1750)
        assertEquals(IntelliMatchEngine.MatchConfidence.HIGH, result.confidence)
    }

    @Test
    fun testEvaluateCandidate_punctuationAgnosticArtist() {
        val candidate = Song(
            id = "test_2",
            title = "Manasellam Mazhaiye",
            artist = "G. V. Prakash Kumar, Saindhavi",
            artworkUrl = "http://example.com/cover.jpg",
            duration = 315000L
        )

        val result = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Manasellam Mazhaiye",
            targetArtist = "G.V. Prakash Kumar, Saindhavi",
            targetDurationMs = 315000L,
            candidate = candidate,
            targetLanguage = "tamil"
        )

        assertTrue(result.score >= 1750)
        assertEquals(IntelliMatchEngine.MatchConfidence.HIGH, result.confidence)
    }
}
