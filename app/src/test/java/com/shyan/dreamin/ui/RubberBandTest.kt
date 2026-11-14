package com.shyan.dreamin.ui

import com.shyan.dreamin.ui.components.rubberBand
import org.junit.Assert.assertEquals
import org.junit.Test

class RubberBandTest {

    @Test
    fun `offsets within the limit follow the finger exactly`() {
        assertEquals(50f, rubberBand(50f, limit = 100f), 0.001f)
        assertEquals(-100f, rubberBand(-100f, limit = 100f), 0.001f)
    }

    @Test
    fun `offsets past the limit are damped symmetrically`() {
        assertEquals(135f, rubberBand(200f, limit = 100f), 0.001f)
        assertEquals(-135f, rubberBand(-200f, limit = 100f), 0.001f)
    }
}
