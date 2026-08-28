package com.shyan.dreamin.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoEqCatalogTest {

    @Test
    fun testFindMatchingProfileAirPodsPro() {
        val matched = AutoEqCatalog.findMatchingProfile("Shyan's AirPods Pro")
        assertNotNull(matched)
        assertEquals("apple_airpods_pro_2", matched?.id)
        assertTrue(matched!!.frequencyGainsMb.isNotEmpty())
    }

    @Test
    fun testFindMatchingProfileSonyXM5() {
        val matched = AutoEqCatalog.findMatchingProfile("WH-1000XM5")
        assertNotNull(matched)
        assertEquals("sony_wh_1000xm5", matched?.id)
    }

    @Test
    fun testFindMatchingProfileGalaxyBuds() {
        val matched = AutoEqCatalog.findMatchingProfile("Galaxy Buds2 Pro (E3F1)")
        assertNotNull(matched)
        assertEquals("samsung_galaxy_buds2_pro", matched?.id)
    }

    @Test
    fun testFindMatchingProfileMoondropChu() {
        val matched = AutoEqCatalog.findMatchingProfile("Moondrop Chu II")
        assertNotNull(matched)
        assertEquals("moondrop_chu_2", matched?.id)
    }

    @Test
    fun testFindMatchingProfileUnknownReturnsNull() {
        val matched = AutoEqCatalog.findMatchingProfile("Generic Car Bluetooth Audio 99")
        assertNull(matched)
    }
}
