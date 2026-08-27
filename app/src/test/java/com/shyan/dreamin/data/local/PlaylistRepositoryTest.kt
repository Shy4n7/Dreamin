package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.local.dao.PlaylistDao
import com.shyan.dreamin.data.local.entity.PlaylistEntity
import com.shyan.dreamin.data.local.entity.PlaylistSongEntity
import com.shyan.dreamin.data.model.Song
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import kotlin.test.assertEquals

class PlaylistRepositoryTest {

    private val dao: PlaylistDao = mockk()
    private val repo = PlaylistRepository(dao)

    @Test
    fun `createPlaylist creates entity with name and custom coverUrl`() = runTest {
        coEvery { dao.insertPlaylist(any()) } returns 101L

        val id = repo.createPlaylist("Rock Hits", "file:///covers/rock.jpg")

        assertEquals(101L, id)
        coVerify {
            dao.insertPlaylist(match {
                it.name == "Rock Hits" && it.coverUrl == "file:///covers/rock.jpg"
            })
        }
    }

    @Test
    fun `updatePlaylistCover calls dao with new cover url`() = runTest {
        coEvery { dao.updatePlaylistCover(any(), any()) } just Runs

        repo.updatePlaylistCover(101L, "file:///covers/new_cover.jpg")

        coVerify { dao.updatePlaylistCover(101L, "file:///covers/new_cover.jpg") }
    }

    @Test
    fun `addSongs batch inserts list of songs at specified position offset`() = runTest {
        coEvery { dao.insertSongs(any()) } just Runs

        val songs = listOf(
            Song(id = "s1", title = "Song 1", artist = "Artist 1", artworkUrl = "art1.jpg"),
            Song(id = "s2", title = "Song 2", artist = "Artist 2", artworkUrl = "art2.jpg")
        )

        repo.addSongs(playlistId = 101L, songs = songs, startPosition = 5)

        coVerify {
            dao.insertSongs(match { entities ->
                entities.size == 2 &&
                entities[0].playlistId == 101L &&
                entities[0].songId == "s1" &&
                entities[0].position == 5 &&
                entities[1].position == 6
            })
        }
    }

    @Test
    fun `observePlaylists maps PlaylistWithCount to Playlist including songCount`() = runTest {
        io.mockk.every { dao.observePlaylists() } returns kotlinx.coroutines.flow.flowOf(
            listOf(
                com.shyan.dreamin.data.local.dao.PlaylistWithCount(
                    id = 1L,
                    name = "Vibes",
                    coverUrl = null,
                    createdAt = 1000L,
                    songCount = 28
                )
            )
        )

        val lists = repo.observePlaylists().first()
        assertEquals(1, lists.size)
        assertEquals(28, lists[0].songCount)
    }
}
