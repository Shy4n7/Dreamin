package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.Song
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class AudioStreamResolverTest {

    @Before
    fun setUp() {
        AudioStreamResolver.clearCache()
    }

    @Test
    fun testCachePutAndGet() {
        val songId = "testSong123"
        val expectedUrl = "https://ac.cf.saavncdn.com/test_320.mp4"

        assertNull(AudioStreamResolver.getCachedStreamUrl(songId))
        AudioStreamResolver.putCachedStreamUrl(songId, expectedUrl)
        assertEquals(expectedUrl, AudioStreamResolver.getCachedStreamUrl(songId))
    }

    @Test
    fun testOfflineLocalDownloadResolution() = runBlocking {
        val song = Song(
            id = "offlineSong1",
            title = "Test Song",
            artist = "Test Artist",
            artworkUrl = ""
        )
        AudioStreamResolver.putCachedStreamUrl(song.id, "/storage/emulated/0/Music/song.mp3")

        val resolved = AudioStreamResolver.resolveStreamUrl(song)
        assertEquals("/storage/emulated/0/Music/song.mp3", resolved)
    }

    @Test
    fun testDesDecryptionSanity() {
        // Encrypted URL sample with standard DES encryption
        val encryptedSample = "3mBfW5aK57o="
        // Ensures decryptJioSaavnMediaUrl doesn't crash on arbitrary inputs
        val result = AudioStreamResolver.decryptJioSaavnMediaUrl(encryptedSample)
        // If not valid URL, it safely returns null
        assertTrue(result == null || result.startsWith("http"))
    }
}
