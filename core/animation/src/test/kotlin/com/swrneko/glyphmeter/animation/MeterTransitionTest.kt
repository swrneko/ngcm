package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterTransitionTest {

    private val layout = DeviceLayouts.PHONE_3A
    private val meter = layout.meterZone.indices

    private fun transition(from: Float, to: Float) = MeterTransition(
        fromLevel = from,
        toLevel = to,
        layout = layout,
        brightness = Light.MAX,
        durationMillis = 500,
        easing = Easing.EaseOutCubic,
    )

    @Test
    fun `it starts at the old level`() {
        val subject = transition(from = 0.5f, to = 0.6f)

        assertEquals(MeterRenderer.smooth(0.5f, layout, Light.MAX), subject.frameAt(0))
    }

    @Test
    fun `it ends at the new level`() {
        val subject = transition(from = 0.5f, to = 0.6f)

        assertEquals(MeterRenderer.smooth(0.6f, layout, Light.MAX), subject.frameAt(500))
    }

    @Test
    fun `time past the end stays at the new level`() {
        val subject = transition(from = 0.5f, to = 0.6f)

        assertEquals(subject.frameAt(500), subject.frameAt(10_000))
    }

    @Test
    fun `it passes through intermediate levels instead of jumping`() {
        val subject = transition(from = 0f, to = 1f)

        val midpoint = subject.frameAt(250)
        val litSegments = meter.count { midpoint[it] > 0 }

        assertTrue("expected a partly filled meter, got $litSegments segments", litSegments in 1..19)
    }

    @Test
    fun `a rising transition never shows the meter going backwards`() {
        val subject = transition(from = 0.2f, to = 0.8f)
        var previous = subject.frameAt(0)

        for (elapsed in 0L..500L step 10L) {
            val current = subject.frameAt(elapsed)
            for (index in meter) {
                assertTrue("segment $index dropped at ${elapsed}ms", current[index] >= previous[index])
            }
            previous = current
        }
    }

    @Test
    fun `a zero length transition reports the new level immediately`() {
        val subject = MeterTransition(
            fromLevel = 0.2f,
            toLevel = 0.9f,
            layout = layout,
            brightness = Light.MAX,
            durationMillis = 0,
            easing = Easing.EaseOutCubic,
        )

        assertEquals(MeterRenderer.smooth(0.9f, layout, Light.MAX), subject.frameAt(0))
    }
}
