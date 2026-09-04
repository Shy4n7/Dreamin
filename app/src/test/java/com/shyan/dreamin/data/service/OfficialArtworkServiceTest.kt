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

    @Test
    fun testMunbeVaaTheatricalPosterResolution() = runBlocking {
        val song = Song(
            id = "munbe_vaa_test",
            title = "Munbe Vaa",
            artist = "Naresh Iyer, Shreya Ghoshal",
            artworkUrl = ""
        )

        val poster = OfficialArtworkService.resolveOfficialMoviePoster(song)
        assertNotNull("Poster should be resolved", poster)
        assertTrue("Poster should not be blank", poster!!.isNotBlank())
        assertFalse("Poster should reject A.R. Rahman Vibration compilation", poster.contains("Vibration", ignoreCase = true))
    }

    @Test
    fun testMalarePremamTheatricalPosterResolution() = runBlocking {
        val song = Song(
            id = "malare_premam_test",
            title = "Malare",
            artist = "Vijay Yesudas, Rajesh Murugesan",
            artworkUrl = ""
        )

        val poster = OfficialArtworkService.resolveOfficialMoviePoster(song)
        assertNotNull("Poster should be resolved", poster)
        assertTrue("Poster should not be blank", poster!!.isNotBlank())
        assertFalse("Poster should reject Power dubbed album", poster.contains("Power", ignoreCase = true))
    }

    @Test
    fun testMalareKarnaTheatricalPosterResolution() = runBlocking {
        val song = Song(
            id = "malare_karna_test",
            title = "Malare",
            artist = "Vidyasagar, S.P. Balasubrahmanyam",
            artworkUrl = ""
        )

        val poster = OfficialArtworkService.resolveOfficialMoviePoster(song)
        assertNotNull("Poster should be resolved", poster)
        assertTrue("Poster should not be blank", poster!!.isNotBlank())
        assertFalse("Poster should reject Power dubbed album", poster.contains("Power-Tamil", ignoreCase = true))
    }

    @Test
    fun testDonuDonuMaariTheatricalPosterResolution() = runBlocking {
        val song = Song(
            id = "donu_donu_test",
            title = "Don'u Don'u Don'u",
            artist = "Dhanush, Anirudh Ravichander",
            artworkUrl = ""
        )

        val poster = OfficialArtworkService.resolveOfficialMoviePoster(song)
        assertNotNull("Poster should be resolved", poster)
        assertTrue("Poster should not be blank", poster!!.isNotBlank())
        assertFalse("Poster should reject Dhanush Mass compilation", poster.contains("Dhanush-Mass", ignoreCase = true))
    }

    @Test
    fun testMentalManadhilOkKanmaniPosterResolution() = runBlocking {
        val song = Song(
            id = "mental_manadhil_test",
            title = "Mental Manadhil",
            artist = "A.R. Rahman, Jonita Gandhi",
            artworkUrl = ""
        )

        val poster = OfficialArtworkService.resolveOfficialMoviePoster(song)
        assertNotNull("Poster should be resolved", poster)
        assertTrue("Poster should not be blank", poster!!.isNotBlank())
    }

    @Test
    fun testPachaiNirameAlaipayutheyPosterResolution() = runBlocking {
        val song = Song(
            id = "pachai_nirame_test",
            title = "Pachai Nirame",
            artist = "A.R. Rahman, Hariharan",
            artworkUrl = ""
        )

        val poster = OfficialArtworkService.resolveOfficialMoviePoster(song)
        assertNotNull("Poster should be resolved", poster)
        assertTrue("Poster should not be blank", poster!!.isNotBlank())
        assertFalse("Poster should reject Trap Vibe single", poster.contains("Trap", ignoreCase = true))
    }

    @Test
    fun testFetchCandidatePostersReturnsMultipleOptions() = runBlocking {
        val song = Song(
            id = "malare_candidates_test",
            title = "Malare",
            artist = "Vijay Yesudas, Rajesh Murugesan",
            artworkUrl = ""
        )

        val candidates = OfficialArtworkService.fetchCandidatePosters(song)
        assertNotNull("Candidates list should not be null", candidates)
        assertTrue("Should return candidate posters", candidates.isNotEmpty())
    }

    @Test
    fun testCompilationRegexDetectsNewPatterns() {
        assertTrue(OfficialArtworkService.COMPILATION_REGEX.containsMatchIn("A. R. Rahman Vibration"))
        assertTrue(OfficialArtworkService.COMPILATION_REGEX.containsMatchIn("AR RAHMAN Hit Songs"))
        assertTrue(OfficialArtworkService.COMPILATION_REGEX.containsMatchIn("Kollywood's Most Romantic Songs"))
        assertTrue(OfficialArtworkService.COMPILATION_REGEX.containsMatchIn("Munbe Vaa - Lofi Mix"))
        assertTrue(OfficialArtworkService.COMPILATION_REGEX.containsMatchIn("Dhanush Mass"))
        assertTrue(OfficialArtworkService.COMPILATION_REGEX.containsMatchIn("Pachai Nirame (From \"Alaipayuthey\") [Trap Vibe] - Single"))
    }

    @Test
    fun testArtworkResolutionScaling() {
        val googleThumb = "https://lh3.googleusercontent.com/abc=w120-h120-l90-rj"
        assertEquals("https://lh3.googleusercontent.com/abc=w800-h800-l90-rj", Song.resolvePoster("Test", googleThumb))

        val ytThumb = "https://i.ytimg.com/vi/abc123xyz/mqdefault.jpg"
        assertEquals("https://i.ytimg.com/vi/abc123xyz/maxresdefault.jpg", Song.resolvePoster("Test", ytThumb))

        val saavnThumb = "https://c.saavncdn.com/123/Song_150x150.jpg"
        assertEquals("https://c.saavncdn.com/123/Song_500x500.jpg", Song.resolvePoster("Test", saavnThumb))

        val appleThumb = "https://is1-ssl.mzstatic.com/image/thumb/Music123/v4/abc/100x100bb.jpg"
        assertEquals("https://is1-ssl.mzstatic.com/image/thumb/Music123/v4/abc/1000x1000bb.jpg", Song.resolvePoster("Test", appleThumb))
    }

    @Test
    fun testNoCrossContaminationByTitle() {
        val punjabiSong = Song(
            id = "punjabi_jackpot",
            title = "Jackpot",
            artist = "Cheema Y, Gur Sidhu",
            artworkUrl = "https://c.saavncdn.com/111/Jackpot-Punjabi-500x500.jpg",
            language = "punjabi"
        )
        val tamilSong = Song(
            id = "tamil_jackpot",
            title = "Jackpot",
            artist = "Vishal Chandrashekhar",
            artworkUrl = "https://c.saavncdn.com/222/Jackpot-Tamil-500x500.jpg",
            language = "tamil"
        )

        OfficialArtworkService.putCachedPoster(punjabiSong, punjabiSong.artworkUrl)
        OfficialArtworkService.putCachedPoster(tamilSong, tamilSong.artworkUrl)

        assertEquals(punjabiSong.artworkUrl, OfficialArtworkService.getCachedPoster(punjabiSong))
        assertEquals(tamilSong.artworkUrl, OfficialArtworkService.getCachedPoster(tamilSong))
    }
}
