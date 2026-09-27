package com.shyan.dreamin.data

import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SearchLruCacheTest {

    @Test
    fun testSearchCacheStoresAndRetrievesResults() {
        val cache = HashMap<String, List<Song>>()
        val query = "ar rahman"
        val songs = listOf(Song(id = "1", title = "Song 1", artist = "AR Rahman"))

        cache[query] = songs

        val cached = cache[query]
        assertNotNull(cached)
        assertEquals(1, cached?.size)
        assertEquals("Song 1", cached?.first()?.title)
        assertNull(cache["unknown"])
    }
}
