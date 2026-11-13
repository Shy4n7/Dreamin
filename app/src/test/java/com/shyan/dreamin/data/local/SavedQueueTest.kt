package com.shyan.dreamin.data.local

import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedQueueTest {

    private fun song(id: String) = Song(id = id, title = "Title $id", artist = "Artist")

    @Test
    fun `resumeIndexFor keeps saved index when it matches the session song`() {
        val saved = UserPreferencesDataStore.SavedQueue(listOf(song("a"), song("b"), song("a")), 2, false, emptyList())
        assertEquals(2, saved.resumeIndexFor("a"))
    }

    @Test
    fun `resumeIndexFor falls back to first match or -1`() {
        val saved = UserPreferencesDataStore.SavedQueue(listOf(song("a"), song("b")), 0, true, emptyList())
        assertEquals(1, saved.resumeIndexFor("b"))
        assertEquals(-1, saved.resumeIndexFor("z"))
    }
}
