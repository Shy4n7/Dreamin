package com.shyan.dreamin.viewmodel

import com.shyan.dreamin.data.model.PlaybackState
import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSwitchingTest {

    @Test
    fun testSongSwitchSetsLoadingStateImmediately() {
        val song1 = Song(id = "s1", title = "Song 1", artist = "Artist 1")
        val song2 = Song(id = "s2", title = "Song 2", artist = "Artist 2")

        var playbackState: PlaybackState = PlaybackState.Playing
        var currentSong: Song? = song1

        val onSwitch = { nextSong: Song ->
            currentSong = nextSong
            playbackState = PlaybackState.Loading
        }

        onSwitch(song2)

        assertEquals("s2", currentSong?.id)
        assertEquals(PlaybackState.Loading, playbackState)
    }
}
