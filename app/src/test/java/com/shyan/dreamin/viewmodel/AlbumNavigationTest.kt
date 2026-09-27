package com.shyan.dreamin.viewmodel

import com.shyan.dreamin.data.model.AlbumItem
import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class AlbumNavigationTest {

    /**
     * Mirrors the resolution logic in [MusicPlayerViewModel.openAlbumForSong].
     */
    private fun resolveTargetAlbumName(song: Song): String {
        val movieFromAlbum = song.album.substringAfter("(From \"", "").substringBefore("\")").trim()
        val movieFromTitle = song.title.substringAfter("(From \"", "").substringBefore("\")").trim()
        return when {
            movieFromAlbum.isNotBlank() -> movieFromAlbum
            song.album.isNotBlank() -> song.album.trim()
            movieFromTitle.isNotBlank() -> movieFromTitle
            else -> song.title.trim()
        }
    }

    private fun findMatchingAlbum(targetName: String, albums: List<AlbumItem>): AlbumItem? {
        return albums.firstOrNull {
            it.title.equals(targetName, ignoreCase = true) ||
            it.title.contains(targetName, ignoreCase = true) ||
            targetName.contains(it.title, ignoreCase = true)
        }
    }

    /** song.album = "Magale (From "Baththa")" should resolve to "Baththa", not the raw album string. */
    @Test
    fun testAlbumFieldWithFromPatternExtractsMovieName() {
        val song = Song(id = "s0", title = "Magale", artist = "Sai Abhyankkar", album = "Magale (From \"Baththa\")")
        val albums = listOf(
            AlbumItem(id = "a0", title = "Baththa"),
            AlbumItem(id = "a1", title = "Magale (From \"Baththa\")")
        )

        val target = resolveTargetAlbumName(song)
        val matched = findMatchingAlbum(target, albums)

        assertEquals("Baththa", target)
        assertEquals("a0", matched?.id)
    }

    @Test
    fun testDirectAlbumPropertyMatch() {
        val song = Song(id = "s1", title = "Pattampoochi", artist = "GV Prakash", album = "Vishwanath & Sons")
        val albums = listOf(
            AlbumItem(id = "a1", title = "Vishwanath & Sons"),
            AlbumItem(id = "a2", title = "Amaran")
        )

        val target = resolveTargetAlbumName(song)
        val matched = findMatchingAlbum(target, albums)

        assertEquals("Vishwanath & Sons", target)
        assertEquals("a1", matched?.id)
    }

    @Test
    fun testParsedMovieFromTitleMatch() {
        val song = Song(id = "s2", title = "Magale (From \"Baththa\")", artist = "Sai Abhyankkar", album = "")
        val albums = listOf(
            AlbumItem(id = "a10", title = "Baththa"),
            AlbumItem(id = "a11", title = "Chella Magale")
        )

        val target = resolveTargetAlbumName(song)
        val matched = findMatchingAlbum(target, albums)

        assertEquals("Baththa", target)
        assertEquals("a10", matched?.id)
    }

    @Test
    fun testFallbackToTitleWhenNoAlbumSpecified() {
        val song = Song(id = "s3", title = "Dheema", artist = "Anirudh", album = "")
        val albums = listOf(
            AlbumItem(id = "a20", title = "Dheema"),
            AlbumItem(id = "a21", title = "Dragon")
        )

        val target = resolveTargetAlbumName(song)
        val matched = findMatchingAlbum(target, albums)

        assertEquals("Dheema", target)
        assertEquals("a20", matched?.id)
    }
}
