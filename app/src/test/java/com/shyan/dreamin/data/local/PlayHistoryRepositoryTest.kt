package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.local.dao.PlayHistoryDao
import com.shyan.dreamin.data.local.dao.SongSummary
import com.shyan.dreamin.data.local.entity.PlayHistoryEntity
import com.shyan.dreamin.data.model.Song
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PlayHistoryRepositoryTest {

    private val dao: PlayHistoryDao = mockk()
    private val repo = PlayHistoryRepository(dao)

    // ─── getRecentlyPlayed ───────────────────────────────────────────────────

    @Test
    fun `getRecentlyPlayed maps entity fields to Song`() = runTest {
        every { dao.getRecent(any()) } returns flowOf(listOf(
            makeEntity(songId = "a1", title = "Alpha", artist = "ArtistA", artworkUrl = "http://a.jpg")
        ))

        val result = repo.getRecentlyPlayed().first()

        assertEquals(1, result.size)
        assertEquals("a1", result[0].id)
        assertEquals("Alpha", result[0].title)
        assertEquals("ArtistA", result[0].artist)
        assertEquals("https://a.jpg", result[0].artworkUrl)
    }

    @Test
    fun `getRecentlyPlayed deduplicates repeated plays of the same song`() = runTest {
        every { dao.getRecent(any()) } returns flowOf(listOf(
            makeEntity(songId = "dup", title = "Repeat", artist = "Art"),
            makeEntity(songId = "dup", title = "Repeat", artist = "Art"), // second play
            makeEntity(songId = "other", title = "Other", artist = "Art2"),
        ))

        val result = repo.getRecentlyPlayed().first()

        assertEquals(2, result.size)
        assertEquals(listOf("dup", "other"), result.map { it.id })
    }

    @Test
    fun `getRecentlyPlayed returns empty list when dao emits empty`() = runTest {
        every { dao.getRecent(any()) } returns flowOf(emptyList())

        val result = repo.getRecentlyPlayed().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `getRecentlyPlayed passes limit to dao`() = runTest {
        every { dao.getRecent(5) } returns flowOf(emptyList())

        repo.getRecentlyPlayed(limit = 5).first()

        // Verified by the exact stubbing above (mockk throws if 5 wasn't called)
    }

    // ─── getTopSongs ─────────────────────────────────────────────────────────

    @Test
    fun `getTopSongs maps SongSummary fields to Song`() = runTest {
        every { dao.getMostPlayed(any()) } returns flowOf(listOf(
            SongSummary(songId = "t1", title = "Top Hit", artist = "Big Star", artworkUrl = "http://t.jpg")
        ))

        val result = repo.getTopSongs().first()

        assertEquals(1, result.size)
        assertEquals("t1", result[0].id)
        assertEquals("Top Hit", result[0].title)
        assertEquals("Big Star", result[0].artist)
    }

    @Test
    fun `getTopSongs returns empty list when dao emits empty`() = runTest {
        every { dao.getMostPlayed(any()) } returns flowOf(emptyList())

        val result = repo.getTopSongs().first()

        assertTrue(result.isEmpty())
    }

    // ─── recordPlay ──────────────────────────────────────────────────────────

    @Test
    fun `recordPlay inserts entity with matching songId`() = runTest {
        coEvery { dao.insert(any()) } just Runs

        repo.recordPlay(Song(id = "s1", title = "T", artist = "A", artworkUrl = "u"))

        coVerify {
            dao.insert(match { it.songId == "s1" })
        }
    }

    @Test
    fun `recordPlay copies all song fields into entity`() = runTest {
        val song = Song(id = "s2", title = "My Song", artist = "My Artist", artworkUrl = "art.jpg", duration = 180_000L)
        coEvery { dao.insert(any()) } just Runs

        repo.recordPlay(song)

        coVerify {
            dao.insert(match { entity ->
                entity.songId == "s2" &&
                entity.title == "My Song" &&
                entity.artist == "My Artist" &&
                entity.artworkUrl == "art.jpg" &&
                entity.durationMs == 180_000L
            })
        }
    }

    // ─── helpers ─────────────────────────────────────────────────────────────

    private fun makeEntity(
        songId: String,
        title: String,
        artist: String,
        artworkUrl: String = ""
    ) = PlayHistoryEntity(songId = songId, title = title, artist = artist, artworkUrl = artworkUrl)
}
