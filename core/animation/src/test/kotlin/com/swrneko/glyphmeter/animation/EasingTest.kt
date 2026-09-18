package com.swrneko.glyphmeter.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EasingTest {

    @Test
    fun `linear easing returns its input`() {
        assertEquals(0f, Easing.Linear.transform(0f), 0.0001f)
        assertEquals(0.5f, Easing.Linear.transform(0.5f), 0.0001f)
        assertEquals(1f, Easing.Linear.transform(1f), 0.0001f)
    }

    @Test
    fun `ease out cubic starts and ends where linear does`() {
        assertEquals(0f, Easing.EaseOutCubic.transform(0f), 0.0001f)
        assertEquals(1f, Easing.EaseOutCubic.transform(1f), 0.0001f)
    }

    @Test
    fun `ease out cubic moves fast early and slow late`() {
        assertTrue(Easing.EaseOutCubic.transform(0.25f) > 0.25f)
        assertTrue(Easing.EaseOutCubic.transform(0.9f) > 0.9f)
    }

    @Test
    fun `ease out cubic never goes backwards`() {
        var previous = -1f

        for (step in 0..100) {
            val value = Easing.EaseOutCubic.transform(step / 100f)
            assertTrue("dropped at $step", value >= previous)
            previous = value
        }
    }
}
