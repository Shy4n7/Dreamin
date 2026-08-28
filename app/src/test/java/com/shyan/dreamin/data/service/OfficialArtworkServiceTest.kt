package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class OfficialArtworkServiceTest {

    @Before
    fun setUp() {
        OfficialArtworkService.clearCache()
    }

    @Test
    fun testCachePutAndGet() {
        val song = Song(
            id = "song96",
            title = "Thaabangale",
            artist = "Govind Vasantha, Chinmayi",
            artworkUrl = ""
        )
        val expectedPoster = "https://c.saavncdn.com/137/96-Original-Motion-Picture-Soundtrack-Tamil-2018-500x500.jpg"

        assertNull(OfficialArtworkService.getCachedPoster(song))
        OfficialArtworkService.putCachedPoster(song, expectedPoster)
        assertEquals(expectedPoster, OfficialArtworkService.getCachedPoster(song))
    }

    @Test
    fun testThaabangale96PosterResolution() = runBlocking {
        val song = Song(
            id = "thaabangale_test",
            title = "Thaabangale",
            artist = "Govind Vasantha, Chinmayi",
            artworkUrl = ""
        )

        val poster = OfficialArtworkService.resolveOfficialMoviePoster(song)
        assertNotNull("Poster should be resolved", poster)
        assertTrue("Poster should not be blank", poster!!.isNotBlank())
        assertFalse("Poster should reject BigHits / Mixtape compilation", poster.contains("BigHits", ignoreCase = true))
        assertFalse("Poster should reject Mixtape covers", poster.contains("Mixtape", ignoreCase = true))
    }
}
