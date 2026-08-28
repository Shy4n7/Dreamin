package com.shyan.dreamin.data.recommendation

import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IntelliMatchFuzzySearchTest {

    @Test
    fun testPhoneticSuggestionGenerator() {
        assertEquals("Anirudh", IntelliMatchEngine.generatePhoneticSuggestions("aniruth"))
        assertEquals("Anirudh hits", IntelliMatchEngine.generatePhoneticSuggestions("aniruth hits"))
        assertEquals("Ilayaraja", IntelliMatchEngine.generatePhoneticSuggestions("illayaraja"))
        assertEquals("Ilayaraja melodies", IntelliMatchEngine.generatePhoneticSuggestions("ilyaraja melodies"))
        assertEquals("Arabic Kuthu", IntelliMatchEngine.generatePhoneticSuggestions("arabikuthu"))
        assertEquals("Kanazhaga", IntelliMatchEngine.generatePhoneticSuggestions("kannazhaga"))
        assertEquals("Mazhaiye", IntelliMatchEngine.generatePhoneticSuggestions("mazhaye"))
    }

    @Test
    fun testFuzzyRankSearchResults() {
        val candidates = listOf(
            Song(id = "1", title = "Random Tamil Hit", artist = "Various Artists"),
            Song(id = "2", title = "Mazhaiye Mazhaiye", artist = "Unnikrishnan"),
            Song(id = "3", title = "Mazhai Kuruvi", artist = "A.R. Rahman"),
            Song(id = "4", title = "Megam Karukatha", artist = "Dhanush, Anirudh")
        )

        // Searching "mazhaye" should rank "Mazhaiye Mazhaiye" as #1
        val ranked = IntelliMatchEngine.fuzzyRankSearchResults("mazhaye", candidates)
        assertEquals("Mazhaiye Mazhaiye", ranked.first().title)
    }

    @Test
    fun testArtistPhoneticFuzzyRanking() {
        val candidates = listOf(
            Song(id = "1", title = "Badass", artist = "Anirudh Ravichander"),
            Song(id = "2", title = "Aalaporaan Thamizhan", artist = "A.R. Rahman"),
            Song(id = "3", title = "Kadavule", artist = "Leon James")
        )

        // Searching "aniruth" should rank Anirudh's song as #1
        val ranked = IntelliMatchEngine.fuzzyRankSearchResults("aniruth", candidates)
        assertEquals("Badass", ranked.first().title)
    }

    @Test
    fun testCleanTrackMetadata() {
        val rawTitle = "01 - Hukum - Thalaivar Alappara [MassTamilan.dev - 320Kbps].mp3"
        val rawArtist = "Anirudh Ravichander - MassTamilan"

        val (cleanTitle, cleanArtist) = IntelliMatchEngine.cleanTrackMetadata(rawTitle, rawArtist)

        assertEquals("Hukum - Thalaivar Alappara", cleanTitle)
        assertEquals("Anirudh Ravichander", cleanArtist)
    }

    @Test
    fun testCleanTrackMetadataNoisyWebsites() {
        val rawTitle = "04_Why_This_Kolaveri_Di_320kbps_[SongsPK.com].mp3"
        val rawArtist = "Dhanush, Anirudh [Isaimini.co]"

        val (cleanTitle, cleanArtist) = IntelliMatchEngine.cleanTrackMetadata(rawTitle, rawArtist)

        assertTrue(cleanTitle.contains("Why_This_Kolaveri_Di") || cleanTitle.contains("Why This Kolaveri Di"))
        assertEquals("Dhanush, Anirudh", cleanArtist)
    }
}
