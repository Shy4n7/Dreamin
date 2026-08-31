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

    @Test
    fun testDetectDominantPlaylistLanguage_malayalamPlaylist() {
        val tracks = listOf(
            com.shyan.dreamin.data.service.SpotifyImportedTrack(
                title = "Illuminati",
                artist = "Sushin Shyam, Dabzee",
                durationMs = 193000L
            ),
            com.shyan.dreamin.data.service.SpotifyImportedTrack(
                title = "Kuthanthram",
                artist = "Sushin Shyam, Vedan",
                durationMs = 175000L
            ),
            com.shyan.dreamin.data.service.SpotifyImportedTrack(
                title = "Darshana (From \"Hridayam\")",
                artist = "Hesham Abdul Wahab, Darshana Rajendran",
                durationMs = 269000L
            )
        )
        val detected = IntelliMatchEngine.detectDominantPlaylistLanguage(tracks, playlistTitle = "Chill Hits")
        assertEquals("malayalam", detected)
    }

    @Test
    fun testDetectDominantPlaylistLanguage_tamilPlaylist() {
        val tracks = listOf(
            com.shyan.dreamin.data.service.SpotifyImportedTrack(
                title = "Badass",
                artist = "Anirudh Ravichander",
                durationMs = 229000L
            ),
            com.shyan.dreamin.data.service.SpotifyImportedTrack(
                title = "Arabic Kuthu",
                artist = "Anirudh Ravichander, Jonita Gandhi",
                durationMs = 280000L
            ),
            com.shyan.dreamin.data.service.SpotifyImportedTrack(
                title = "Marakkuma Nenjam",
                artist = "A.R. Rahman",
                durationMs = 250000L
            )
        )
        val detected = IntelliMatchEngine.detectDominantPlaylistLanguage(tracks, playlistTitle = "My Songs")
        assertEquals("tamil", detected)
    }

    @Test
    fun testLanguageAffinity_prefersTargetLanguageVersion() {
        val malayalamCandidate = Song(
            id = "mal_1",
            title = "Aalroorathil (From \"Film\") (Malayalam)",
            artist = "Sushin Shyam",
            artworkUrl = "",
            duration = 200000L
        )
        val teluguDubCandidate = Song(
            id = "tel_1",
            title = "Aalroorathil (From \"Film\") (Telugu)",
            artist = "Sushin Shyam",
            artworkUrl = "",
            duration = 200000L
        )

        val malResult = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Aalroorathil",
            targetArtist = "Sushin Shyam",
            targetDurationMs = 200000L,
            candidate = malayalamCandidate,
            targetLanguage = "malayalam"
        )
        val telResult = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Aalroorathil",
            targetArtist = "Sushin Shyam",
            targetDurationMs = 200000L,
            candidate = teluguDubCandidate,
            targetLanguage = "malayalam"
        )

        assertTrue("Malayalam version should score higher than Telugu dub in Malayalam playlist", malResult.score > telResult.score)
    }

    @Test
    fun testCandidateMatchingPrioritizesHighStreamCountOverLowStreamDuplicate() {
        val blockbusterOriginal = Song(
            id = "hit_1",
            title = "Malare",
            artist = "Vijay Yesudas, Rajesh Murugesan",
            artworkUrl = "",
            duration = 316000L,
            playCount = 45000000L // 45M streams
        )
        val fanCoverOrDuplicate = Song(
            id = "cover_1",
            title = "Malare",
            artist = "Vijay Yesudas, Rajesh Murugesan",
            artworkUrl = "",
            duration = 316000L,
            playCount = 1200L // 1.2K streams
        )

        val hitScore = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Malare",
            targetArtist = "Vijay Yesudas",
            targetDurationMs = 316000L,
            candidate = blockbusterOriginal,
            targetLanguage = "malayalam"
        )
        val coverScore = IntelliMatchEngine.evaluateCandidate(
            targetTitle = "Malare",
            targetArtist = "Vijay Yesudas",
            targetDurationMs = 316000L,
            candidate = fanCoverOrDuplicate,
            targetLanguage = "malayalam"
        )

        assertTrue(
            "Blockbuster with 45M streams should score significantly higher than low-stream duplicate",
            hitScore.score > coverScore.score
        )

        val best = IntelliMatchEngine.findBestMatch(
            targetTitle = "Malare",
            targetArtist = "Vijay Yesudas",
            targetDurationMs = 316000L,
            candidates = listOf(fanCoverOrDuplicate, blockbusterOriginal),
            targetLanguage = "malayalam"
        )

        assertNotNull(best)
        assertEquals("hit_1", best?.song?.id)
    }
}
