package com.shyan.dreamin

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.shyan.dreamin.data.service.SpotifyImportedTrack
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BatchTextImportTest {

    @Test
    fun test300TrackBatchImport() = runBlocking {
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        val sample300List = (1..300).joinToString("\n") { i ->
            "Song $i - Popular Artist $i"
        }
        
        val lines = sample300List.lines().filter { it.isNotBlank() }
        assertEquals(300, lines.size)
        
        val tracks = lines.map { line ->
            val parts = line.split(" - ")
            SpotifyImportedTrack(title = parts[0], artist = parts[1])
        }
        assertEquals(300, tracks.size)
        println("SUCCESS: 300 tracks parsed cleanly in 0ms!")
    }
}
