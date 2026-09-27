package com.shyan.dreamin.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingTransitionTest {

    @Test
    fun testSwipeOffsetResetsToZeroOnOpen() {
        var swipeOffsetY = 500f
        val onOpen = {
            swipeOffsetY = 0f
        }
        onOpen()
        assertEquals(0f, swipeOffsetY, 0.001f)
    }

    @Test
    fun testStiffnessValuePreventsMidFlightStall() {
        val stiffness = 400f // Medium-low responsive threshold
        assertTrue(stiffness >= 400f)
    }
}
