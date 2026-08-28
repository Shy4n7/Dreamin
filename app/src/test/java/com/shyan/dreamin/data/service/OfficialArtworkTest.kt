package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class OfficialArtworkTest {

    @Before
    fun setUp() {
        OfficialArtworkService.clearCache()
    }

    @Test
    fun testEnnaiMaatrumKadhaleResolvesNaanumRowdyDhaan() = runBlocking {
        val song = Song(
            id = "test_1",
            title = "Ennai Maatrum Kadhale",
            artist = "Vignesh Shivan, Anirudh Ravichander",
            artworkUrl = "https://c.saavncdn.com/test_love_waves.jpg"
        )
        val resolved = OfficialArtworkService.resolveOfficialMoviePoster(song, "tamil")
        assertNotNull(resolved)
        assertTrue(resolved!!.isNotBlank())
    }

    @Test
    fun testNenjukkulPeidhidumResolvesVaaranamAayiram() = runBlocking {
        val song = Song(
            id = "test_2",
            title = "Nenjukkul Peidhidum",
            artist = "Thamarai, Harris Jayaraj, Hariharan, Devan Ekambaram",
            artworkUrl = "https://c.saavncdn.com/test_love_diaries.jpg"
        )
        val resolved = OfficialArtworkService.resolveOfficialMoviePoster(song, "tamil")
        assertNotNull(resolved)
        assertTrue(resolved!!.isNotBlank())
    }

    @Test
    fun testIdhazhinOramResolvesMovieThree() = runBlocking {
        val song = Song(
            id = "test_3",
            title = "Idhazhin Oram",
            artist = "Anirudh Ravichander, Ajesh, Aishwarya",
            artworkUrl = "https://c.saavncdn.com/test_memoirs_of_love.jpg"
        )
        val resolved = OfficialArtworkService.resolveOfficialMoviePoster(song, "tamil")
        assertNotNull(resolved)
        assertTrue(resolved!!.isNotBlank())
    }
}
