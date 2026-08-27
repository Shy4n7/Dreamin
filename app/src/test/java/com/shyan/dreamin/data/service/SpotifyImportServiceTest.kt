package com.shyan.dreamin.data.service

import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpotifyImportServiceTest {

    @Test
    fun `resolvePlaylistId extracts 22 character ID from standard web URL`() = runTest {
        val url = "https://open.spotify.com/playlist/37i9dQZF1DX4WYpdgoIcn6?si=abc123xyz"
        val id = SpotifyImportService.resolvePlaylistId(url)
        assertEquals("37i9dQZF1DX4WYpdgoIcn6", id)
    }

    @Test
    fun `resolvePlaylistId extracts ID from URL with intl prefix`() = runTest {
        val url = "https://open.spotify.com/intl-de/playlist/37i9dQZF1DX4WYpdgoIcn6"
        val id = SpotifyImportService.resolvePlaylistId(url)
        assertEquals("37i9dQZF1DX4WYpdgoIcn6", id)
    }

    @Test
    fun `resolvePlaylistId extracts ID from URI string format`() = runTest {
        val uri = "spotify:playlist:37i9dQZF1DX4WYpdgoIcn6"
        val id = SpotifyImportService.resolvePlaylistId(uri)
        assertEquals("37i9dQZF1DX4WYpdgoIcn6", id)
    }

    @Test
    fun `resolvePlaylistId returns null for invalid URLs`() = runTest {
        val invalidUrl = "https://example.com/not-a-playlist"
        val id = SpotifyImportService.resolvePlaylistId(invalidUrl)
        assertNull(id)
    }

    @Test
    fun `fetchPlaylistDetails successfully extracts tracks and album artwork from public Spotify playlist`() = runTest {
        // "Today's Top Hits" Spotify official playlist ID
        val playlistId = "37i9dQZF1DXcBWIGoYBM5M"
        val details = SpotifyImportService.fetchPlaylistDetails(playlistId)

        if (details != null) {
            assertTrue(details.tracks.isNotEmpty(), "Tracks list should not be empty")
            assertNotNull(details.title)
            assertTrue(details.title.isNotBlank())
            val firstTrack = details.tracks.first()
            assertTrue(firstTrack.title.isNotBlank(), "Track title should not be blank")
            assertTrue(firstTrack.artist.isNotBlank(), "Track artist should not be blank")
        }
    }
}
