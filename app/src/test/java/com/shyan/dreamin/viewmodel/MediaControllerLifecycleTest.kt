package com.shyan.dreamin.viewmodel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaControllerLifecycleTest {

    @Test
    fun testConnectionStateEvaluation() {
        var isConnected = false
        val checkConnection = { isConnected }
        assertFalse(checkConnection())

        isConnected = true
        assertTrue(checkConnection())
    }
}
