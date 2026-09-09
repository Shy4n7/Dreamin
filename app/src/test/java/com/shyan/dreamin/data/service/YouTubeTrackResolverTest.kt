package com.shyan.dreamin.data.service

import com.shyan.dreamin.data.model.AudioFormatInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class YouTubeTrackResolverTest {

    @Test
    fun testExtractVideoIdFromShortUrl() {
        val url = "https://youtu.be/SmXnQZ0Hvjw?si=Nd6adX6dNDG03fU3"
        val id = YouTubeTrackResolver.extractVideoId(url)
        assertEquals("SmXnQZ0Hvjw", id)
        assertTrue(YouTubeTrackResolver.isYouTubeQuery(url))
    }

    @Test
    fun testExtractVideoIdFromStandardUrl() {
        val url = "https://www.youtube.com/watch?v=SmXnQZ0Hvjw&list=RDMM"
        val id = YouTubeTrackResolver.extractVideoId(url)
        assertEquals("SmXnQZ0Hvjw", id)
        assertTrue(YouTubeTrackResolver.isYouTubeQuery(url))
    }

    @Test
    fun testExtractVideoIdFromMusicUrl() {
        val url = "https://music.youtube.com/watch?v=SmXnQZ0Hvjw"
        val id = YouTubeTrackResolver.extractVideoId(url)
        assertEquals("SmXnQZ0Hvjw", id)
        assertTrue(YouTubeTrackResolver.isYouTubeQuery(url))
    }

    @Test
    fun testExtractVideoIdFromShortsUrl() {
        val url = "https://youtube.com/shorts/SmXnQZ0Hvjw"
        val id = YouTubeTrackResolver.extractVideoId(url)
        assertEquals("SmXnQZ0Hvjw", id)
        assertTrue(YouTubeTrackResolver.isYouTubeQuery(url))
    }

    @Test
    fun testExtractVideoIdFromRawId() {
        val rawId = "SmXnQZ0Hvjw"
        val id = YouTubeTrackResolver.extractVideoId(rawId)
        assertEquals("SmXnQZ0Hvjw", id)
        assertTrue(YouTubeTrackResolver.isYouTubeQuery(rawId))
    }

    @Test
    fun testExtractVideoIdFromNormalSearchQuery() {
        val query = "Rathinamo Female Version"
        val id = YouTubeTrackResolver.extractVideoId(query)
        assertNull(id)
        assertFalse(YouTubeTrackResolver.isYouTubeQuery(query))
    }

    @Test
    fun testAudioFormatBadgeFormatting() {
        val aac = AudioFormatInfo(codec = "AAC", bitrateKbps = 320, sampleRateHz = 44100, isLossless = false, source = "JioSaavn")
        assertEquals("320k AAC", aac.displayQualityBadge)

        val opus = AudioFormatInfo(codec = "Opus", bitrateKbps = 160, sampleRateHz = 48000, isLossless = false, source = "YouTube Music")
        assertEquals("160k Opus", opus.displayQualityBadge)

        val flac = AudioFormatInfo(codec = "FLAC", bitrateKbps = 1411, sampleRateHz = 44100, isLossless = true, source = "Local")
        assertEquals("Lossless FLAC", flac.displayQualityBadge)

        val hiRes = AudioFormatInfo(codec = "FLAC", bitrateKbps = 4608, sampleRateHz = 96000, isLossless = true, source = "Local")
        assertEquals("Hi-Res 24-bit", hiRes.displayQualityBadge)
    }
}
