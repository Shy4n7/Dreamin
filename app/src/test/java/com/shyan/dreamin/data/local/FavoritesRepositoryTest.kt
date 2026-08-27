package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.local.dao.FavoriteDao
import com.shyan.dreamin.data.local.entity.FavoriteEntity
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
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FavoritesRepositoryTest {

    private val dao: FavoriteDao = mockk()
    private val repo = FavoritesRepository(dao)

    // ─── addFavorite ─────────────────────────────────────────────────────────

    @Test
    fun `addFavorite inserts entity with correct songId`() = runTest {
        coEvery { dao.insert(any()) } just Runs

        repo.addFavorite(Song(id = "fav1", title = "T", artist = "A", artworkUrl = "u"))

        coVerify { dao.insert(match { it.songId == "fav1" }) }
    }

    @Test
    fun `addFavorite copies all song fields into entity`() = runTest {
        val song = Song(id = "fav2", title = "Fav Song", artist = "Fav Artist", artworkUrl = "fav.jpg")
        coEvery { dao.insert(any()) } just Runs

        repo.addFavorite(song)

        coVerify {
            dao.insert(match { entity ->
                entity.songId == "fav2" &&
                entity.title == "Fav Song" &&
                entity.artist == "Fav Artist" &&
                entity.artworkUrl == "fav.jpg"
            })
        }
    }

    // ─── removeFavorite ──────────────────────────────────────────────────────

    @Test
    fun `removeFavorite calls dao delete with the provided songId`() = runTest {
        coEvery { dao.delete(any()) } just Runs

        repo.removeFavorite("song123")

        coVerify { dao.delete("song123") }
    }

    @Test
    fun `removeFavorite passes songId unchanged to dao`() = runTest {
        coEvery { dao.delete(any()) } just Runs

        repo.removeFavorite("exact-id-99")

        coVerify { dao.delete("exact-id-99") }
    }

    // ─── observeAll ──────────────────────────────────────────────────────────

    @Test
    fun `observeAll maps FavoriteEntity fields to Song`() = runTest {
        every { dao.observeAll() } returns flowOf(listOf(
            FavoriteEntity(songId = "f1", title = "Fav", artist = "Art", artworkUrl = "http://f.jpg")
        ))

        val result = repo.observeAll().first()

        assertEquals(1, result.size)
        assertEquals("f1", result[0].id)
        assertEquals("Fav", result[0].title)
        assertEquals("Art", result[0].artist)
        assertEquals("https://f.jpg", result[0].artworkUrl)
    }

    @Test
    fun `observeAll returns empty list when no favorites exist`() = runTest {
        every { dao.observeAll() } returns flowOf(emptyList())

        val result = repo.observeAll().first()

        assertTrue(result.isEmpty())
    }

    @Test
    fun `observeAll preserves ordering from dao`() = runTest {
        val entities = listOf(
            FavoriteEntity(songId = "first", title = "First", artist = "A", artworkUrl = ""),
            FavoriteEntity(songId = "second", title = "Second", artist = "B", artworkUrl = ""),
        )
        every { dao.observeAll() } returns flowOf(entities)

        val result = repo.observeAll().first()

        assertEquals(listOf("first", "second"), result.map { it.id })
    }

    // ─── isFavorite ──────────────────────────────────────────────────────────

    @Test
    fun `isFavorite returns true when dao emits true`() = runTest {
        every { dao.isFavorite("song1") } returns flowOf(true)

        val result = repo.isFavorite("song1").first()

        assertTrue(result)
    }

    @Test
    fun `isFavorite returns false when dao emits false`() = runTest {
        every { dao.isFavorite("song2") } returns flowOf(false)

        val result = repo.isFavorite("song2").first()

        assertFalse(result)
    }

    @Test
    fun `isFavorite passes songId to dao unchanged`() = runTest {
        every { dao.isFavorite("exact-song-id") } returns flowOf(true)

        repo.isFavorite("exact-song-id").first()

        // Verified by the exact stubbing above
    }
}
