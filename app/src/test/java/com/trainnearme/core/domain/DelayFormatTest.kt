package com.trainnearme.core.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DelayFormatTest {
    @Test
    fun `delay is shown as hours and minutes`() {
        assertEquals("0:05", formatDelay(5))
        assertEquals("0:12", formatDelay(12))
        assertEquals("1:00", formatDelay(60))
        assertEquals("1:21", formatDelay(81))
        assertEquals("10:07", formatDelay(607))
    }
}
