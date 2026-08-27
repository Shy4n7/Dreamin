package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.local.dao.PlayHistoryDao
import com.shyan.dreamin.data.local.dao.SongSummary
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StatsRepositoryTest {

    private val dao: PlayHistoryDao = mockk()
    private val repo = StatsRepository(dao)

    // ─── songsThisWeek ───────────────────────────────────────────────────────

    @Test
    fun `getWeeklyStats returns song count from dao`() = runTest {
        stubDao(songs = 42)

        val stats = repo.getWeeklyStats()

        assertEquals(42, stats.songsThisWeek)
    }

    @Test
    fun `getWeeklyStats returns zero songs when none played`() = runTest {
        stubDao(songs = 0)

        val stats = repo.getWeeklyStats()

        assertEquals(0, stats.songsThisWeek)
    }

    // ─── minutesThisWeek ─────────────────────────────────────────────────────

    @Test
    fun `getWeeklyStats converts milliseconds to minutes`() = runTest {
        stubDao(durationMs = 5_400_000L) // 90 minutes

        val stats = repo.getWeeklyStats()

        assertEquals(90L, stats.minutesThisWeek)
    }

    @Test
    fun `getWeeklyStats truncates partial minutes`() = runTest {
        stubDao(durationMs = 90_500L) // 1.5 minutes → 1

        val stats = repo.getWeeklyStats()

        assertEquals(1L, stats.minutesThisWeek)
    }

    @Test
    fun `getWeeklyStats returns zero minutes when dao returns null duration`() = runTest {
        stubDao(durationMs = null)

        val stats = repo.getWeeklyStats()

        assertEquals(0L, stats.minutesThisWeek)
    }

    @Test
    fun `getWeeklyStats returns zero minutes when duration is zero`() = runTest {
        stubDao(durationMs = 0L)

        val stats = repo.getWeeklyStats()

        assertEquals(0L, stats.minutesThisWeek)
    }

    // ─── topSong ─────────────────────────────────────────────────────────────

    @Test
    fun `getWeeklyStats maps top song summary to Song`() = runTest {
        stubDao(
            topSong = SongSummary(songId = "top1", title = "Top Hit", artist = "Star", artworkUrl = "art.jpg")
        )

        val stats = repo.getWeeklyStats()

        assertNotNull(stats.topSongThisWeek)
        assertEquals("top1", stats.topSongThisWeek!!.id)
        assertEquals("Top Hit", stats.topSongThisWeek!!.title)
        assertEquals("Star", stats.topSongThisWeek!!.artist)
        assertEquals("art.jpg", stats.topSongThisWeek!!.artworkUrl)
    }

    @Test
    fun `getWeeklyStats returns null top song when none played this week`() = runTest {
        stubDao(topSong = null)

        val stats = repo.getWeeklyStats()

        assertNull(stats.topSongThisWeek)
    }

    // ─── topArtist ───────────────────────────────────────────────────────────

    @Test
    fun `getWeeklyStats returns top artist name from dao`() = runTest {
        stubDao(topArtist = "Greatest Artist")

        val stats = repo.getWeeklyStats()

        assertEquals("Greatest Artist", stats.topArtistThisWeek)
    }

    @Test
    fun `getWeeklyStats returns null top artist when none played this week`() = runTest {
        stubDao(topArtist = null)

        val stats = repo.getWeeklyStats()

        assertNull(stats.topArtistThisWeek)
    }

    // ─── since threshold ─────────────────────────────────────────────────────

    @Test
    fun `getWeeklyStats queries exactly 7 days back`() = runTest {
        val sinceSlot = slot<Long>()
        val beforeCall = System.currentTimeMillis()
        coEvery { dao.countSince(capture(sinceSlot)) } returns 10
        coEvery { dao.totalDurationMsSince(any()) } returns 0L
        coEvery { dao.topSongSince(any()) } returns null
        coEvery { dao.topArtistSince(any()) } returns null

        repo.getWeeklyStats()

        val afterCall = System.currentTimeMillis()
        val sevenDaysMs = 7 * 24 * 60 * 60 * 1_000L
        val expectedMin = beforeCall - sevenDaysMs
        val expectedMax = afterCall - sevenDaysMs
        assertTrue(sinceSlot.isCaptured, "dao.countSince was not called")
        assertTrue(
            sinceSlot.captured in expectedMin..expectedMax,
            "Expected since=${sinceSlot.captured} to be in [$expectedMin, $expectedMax]"
        )
    }

    // ─── helper ──────────────────────────────────────────────────────────────

    private fun stubDao(
        songs: Int = 0,
        durationMs: Long? = 0L,
        topSong: SongSummary? = null,
        topArtist: String? = null
    ) {
        coEvery { dao.countSince(any()) } returns songs
        coEvery { dao.totalDurationMsSince(any()) } returns durationMs
        coEvery { dao.topSongSince(any()) } returns topSong
        coEvery { dao.topArtistSince(any()) } returns topArtist
    }
}
