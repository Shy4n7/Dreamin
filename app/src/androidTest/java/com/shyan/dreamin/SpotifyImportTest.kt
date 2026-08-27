package com.shyan.dreamin

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.shyan.dreamin.data.service.SpotifyImportService
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*

@RunWith(AndroidJUnit4::class)
class SpotifyImportTest {

    @Test
    fun testSpotifyPlaylistImportDirectly() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val playlistId = "1S9U0WSqjDuQL0wVnoygDO"

        println("TEST_LOG: Starting direct SpotifyImportService test for playlist: $playlistId")
        val details = SpotifyImportService.fetchPlaylistDetails(playlistId, appContext)

        assertNotNull("Playlist details should not be null", details)
        println("TEST_LOG: Playlist Title = ${details?.title}")
        println("TEST_LOG: Total Reported = ${details?.totalTracks}")
        println("TEST_LOG: Actual Tracks Fetched = ${details?.tracks?.size}")

        if (details != null && details.tracks.isNotEmpty()) {
            println("TEST_LOG: First track: ${details.tracks.first().title} by ${details.tracks.first().artist}")
            println("TEST_LOG: Last track: ${details.tracks.last().title} by ${details.tracks.last().artist}")
        }

        assertTrue(
            "Track count should be > 100 for this 300+ playlist, but got ${details?.tracks?.size}",
            (details?.tracks?.size ?: 0) > 100
        )
    }
}
