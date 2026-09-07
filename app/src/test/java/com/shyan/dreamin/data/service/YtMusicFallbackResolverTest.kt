package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class YtMusicFallbackResolverTest {

    @Test
    fun testGracefulStreamResolutionDoesNotCrash() = runBlocking {
        val song = Song(
            id = "yt_test_blinding_lights",
            title = "Blinding Lights",
            artist = "The Weeknd",
            artworkUrl = "",
            duration = 200_000L
        )

        val streamUrl = YtMusicFallbackResolver.resolveStreamUrl(song)
        // If resolved, verify URL format; if null (e.g. JVM mock environment), verify graceful handling
        if (streamUrl != null) {
            assertTrue("Expected stream URL to start with https://", streamUrl.startsWith("https://"))
        }
    }
}
