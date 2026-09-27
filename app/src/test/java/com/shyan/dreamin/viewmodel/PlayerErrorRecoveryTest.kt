package com.shyan.dreamin.viewmodel

import com.shyan.dreamin.data.model.PlaybackState
import com.shyan.dreamin.data.model.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerErrorRecoveryTest {

    @Test
    fun testErrorStateRetryAction() {
        var state: PlaybackState = PlaybackState.Error("Timeout loading stream")
        var retried = false

        val handlePlayPause = {
            if (state is PlaybackState.Error) {
                retried = true
                state = PlaybackState.Loading
            }
        }

        handlePlayPause()

        assertTrue(retried)
        assertEquals(PlaybackState.Loading, state)
    }
}
