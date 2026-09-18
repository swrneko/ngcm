package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.model.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PresetFrameSourceTest {

    private val layout = DeviceLayouts.PHONE_3A

    private fun source(preset: AnimationPreset) =
        PresetFrameSource(preset = preset, layout = layout, brightness = Light.MAX)

    @Test
    fun `every preset is registered under a unique id`() {
        val ids = AnimationPresets.all.map { it.id }

        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `lookup finds a known preset and rejects an unknown one`() {
        assertNotNull(AnimationPresets.byId("fill_up"))
        assertNull(AnimationPresets.byId("no_such_preset"))
    }

    @Test
    fun `every preset produces frames sized for the device`() {
        for (preset in AnimationPresets.all) {
            val frame = source(preset).frameAt(preset.durationMillis / 2)

            assertEquals("preset ${preset.id}", 36, frame.size)
        }
    }

    @Test
    fun `every preset keeps brightness inside the legal range`() {
        for (preset in AnimationPresets.all) {
            val subject = source(preset)

            for (elapsed in 0L..preset.durationMillis step 10L) {
                for (value in subject.frameAt(elapsed).segments) {
                    assertTrue("preset ${preset.id} produced $value", value in 0..Light.MAX)
                }
            }
        }
    }

    @Test
    fun `every preset ends dark so the meter can take over cleanly`() {
        for (preset in AnimationPresets.all) {
            val last = source(preset).frameAt(preset.durationMillis)

            assertEquals(
                "preset ${preset.id} left segments lit",
                List(36) { 0 },
                last.segments.toList(),
            )
        }
    }

    @Test
    fun `every preset lights something in the middle`() {
        for (preset in AnimationPresets.all) {
            val middle = source(preset).frameAt(preset.durationMillis / 2)

            assertTrue("preset ${preset.id} is dark all the way through", middle.segments.any { it > 0 })
        }
    }

    @Test
    fun `fill up lights the meter from the bottom upward`() {
        val preset = AnimationPresets.byId("fill_up")!!
        val subject = source(preset)
        val meter = layout.meterZone.indices

        val early = subject.frameAt(preset.durationMillis / 5)
        val lit = meter.filter { early[it] > 0 }

        assertTrue("expected the low end lit first, got $lit", lit.isNotEmpty())
        assertEquals(meter.take(lit.size), lit)
    }

    @Test
    fun `wave touches every zone at some point`() {
        val preset = AnimationPresets.byId("wave")!!
        val subject = source(preset)
        val touched = mutableSetOf<Int>()

        for (elapsed in 0L..preset.durationMillis step 10L) {
            val frame = subject.frameAt(elapsed)
            for (index in 0 until frame.size) {
                if (frame[index] > 0) touched += index
            }
        }

        assertEquals((0 until 36).toSet(), touched)
    }

    @Test
    fun `time past the end is dark rather than an error`() {
        for (preset in AnimationPresets.all) {
            val frame = source(preset).frameAt(preset.durationMillis + 100_000)

            assertEquals("preset ${preset.id}", List(36) { 0 }, frame.segments.toList())
        }
    }
}
