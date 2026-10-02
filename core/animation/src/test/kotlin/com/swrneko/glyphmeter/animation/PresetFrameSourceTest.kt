package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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

    private fun tuned(preset: AnimationPreset, chargeLevel: Float = 1f, tune: (AnimationParams) -> AnimationParams) =
        PresetFrameSource(
            preset = preset,
            layout = layout,
            brightness = Light.MAX,
            params = tune(preset.defaultParams),
            chargeLevel = chargeLevel,
        )

    private fun litIndices(frame: com.swrneko.glyphmeter.model.GlyphFrameData): Set<Int> =
        (0 until frame.size).filter { frame[it] > 0 }.toSet()

    private fun zone(id: String): Set<Int> = layout.zone(id).indices.toSet()

    @Test
    fun `repeats run the cycle back to back`() {
        val subject = tuned(AnimationPresets.BREATHE) { it.copy(cycleMillis = 1_000, repeats = 3) }

        assertEquals(3_000L, subject.durationMillis)
        assertEquals(subject.frameAt(500), subject.frameAt(2_500))
        assertTrue(subject.frameAt(1_500).segments.any { it > 0 })
    }

    @Test
    fun `cycle length sets the speed`() {
        val slow = tuned(AnimationPresets.BREATHE) { it.copy(cycleMillis = 2_000) }
        val fast = tuned(AnimationPresets.BREATHE) { it.copy(cycleMillis = 1_000) }

        assertEquals(slow.frameAt(1_000), fast.frameAt(500))
    }

    @Test
    fun `own brightness overrides the one passed in`() {
        val subject = tuned(AnimationPresets.BREATHE) { it.copy(brightness = 2_000) }

        val peak = subject.frameAt(AnimationPresets.BREATHE.durationMillis / 2)

        assertEquals(2_000, peak.segments.max())
    }

    @Test
    fun `chosen zones are the only ones lit`() {
        for (preset in AnimationPresets.selectable) {
            val subject = tuned(preset) { it.copy(zoneIds = setOf("A")) }
            val touched = mutableSetOf<Int>()
            for (elapsed in 0L..subject.durationMillis step 10L) touched += litIndices(subject.frameAt(elapsed))

            assertEquals("preset ${preset.id}", zone("A"), touched)
        }
    }

    @Test
    fun `zones the layout does not know fall back to the preset's own`() {
        val unknown = tuned(AnimationPresets.FLASH) { it.copy(zoneIds = setOf("Z")) }
        val plain = source(AnimationPresets.FLASH)

        assertEquals(plain.frameAt(120), unknown.frameAt(120))
    }

    @Test
    fun `a counter-clockwise sweep starts at the far end of the ring`() {
        for (preset in listOf(AnimationPresets.WAVE, AnimationPresets.CHASE)) {
            val clockwise = source(preset).frameAt(10)
            val counter = tuned(preset) { it.copy(direction = SweepDirection.COUNTER_CLOCKWISE) }.frameAt(10)

            assertTrue("preset ${preset.id}", clockwise[0] > 0 && clockwise[35] == 0)
            assertTrue("preset ${preset.id}", counter[35] > 0 && counter[0] == 0)
        }
    }

    @Test
    fun `a full fill over two zones lights both and nothing else`() {
        val subject = tuned(AnimationPresets.FILL_UP) { it.copy(zoneIds = setOf("C", "A")) }

        // The fill phase is the first 70 percent of the cycle.
        val filled = subject.frameAt(subject.durationMillis * 7 / 10)

        assertEquals(zone("C") + zone("A"), litIndices(filled))
    }

    @Test
    fun `a fill to the charge level hands the meter over without a seam`() {
        val subject = tuned(AnimationPresets.FILL_UP, chargeLevel = 0.63f) { it.copy(fillTarget = FillTarget.CHARGE_LEVEL) }

        val expected = MeterRenderer.smooth(0.63f, layout, Light.MAX)
        assertEquals(0.63f, subject.handoffLevel)
        assertEquals(expected, subject.frameAt(subject.durationMillis - 1))
        assertEquals(expected, subject.frameAt(subject.durationMillis))
        assertEquals(expected, subject.frameAt(subject.durationMillis + 10_000))
    }

    @Test
    fun `a fill to the charge level stays on the meter whatever zones were picked`() {
        val subject = tuned(AnimationPresets.FILL_UP, chargeLevel = 1f) {
            it.copy(fillTarget = FillTarget.CHARGE_LEVEL, zoneIds = setOf("A", "B"))
        }

        assertEquals(zone("C"), litIndices(subject.frameAt(subject.durationMillis)))
    }

    @Test
    fun `a full fill hands nothing over`() {
        assertNull(source(AnimationPresets.FILL_UP).handoffLevel)
        assertNull(tuned(AnimationPresets.WAVE) { it.copy(fillTarget = FillTarget.CHARGE_LEVEL) }.handoffLevel)
    }

    @Test
    fun `only the last run of a repeated fill to the charge level holds`() {
        val subject = tuned(AnimationPresets.FILL_UP, chargeLevel = 0.5f) {
            it.copy(cycleMillis = 1_000, repeats = 2, fillTarget = FillTarget.CHARGE_LEVEL)
        }

        // 95 percent into the first run is deep in its fade; the same point of the second run holds.
        assertTrue(subject.frameAt(950).segments.max() < Light.MAX / 2)
        assertEquals(MeterRenderer.smooth(0.5f, layout, Light.MAX), subject.frameAt(1_950))
    }

    @Test
    fun `a stepped fill lights whole segments only`() {
        val subject = tuned(AnimationPresets.FILL_UP) { it.copy(fillStyle = FillStyle.STEPPED) }
        val fillPhase = subject.durationMillis * 7 / 10

        for (elapsed in 0L until fillPhase step 7L) {
            for (value in subject.frameAt(elapsed).segments) {
                assertTrue("partial value $value at $elapsed", value == 0 || value == Light.MAX)
            }
        }
    }

    @Test
    fun `a smooth fill glows the leading segment part way`() {
        val subject = tuned(AnimationPresets.FILL_UP) { it.copy(fillStyle = FillStyle.SMOOTH) }
        val fillPhase = subject.durationMillis * 7 / 10

        val partial = (0L until fillPhase step 7L).any { elapsed ->
            subject.frameAt(elapsed).segments.any { it in 1 until Light.MAX }
        }

        assertTrue(partial)
    }

    @Test
    fun `out of range numbers are pulled into range`() {
        val params = AnimationParams(cycleMillis = 10, repeats = 99, brightness = 99_999).normalized()

        assertEquals(AnimationParams.MIN_CYCLE_MILLIS, params.cycleMillis)
        assertEquals(AnimationParams.MAX_REPEATS, params.repeats)
        assertEquals(Light.MAX, params.brightness)
        assertNotEquals(params, params.copy(direction = SweepDirection.COUNTER_CLOCKWISE))
        assertFalse(AnimationPresets.FLASH.hasDirection)
    }

    @Test
    fun `a fill to the charge level with its own brightness still ends on the meter frame`() {
        val subject = PresetFrameSource(
            preset = AnimationPresets.FILL_UP,
            layout = layout,
            brightness = 1_600,
            params = AnimationPresets.FILL_UP.defaultParams.copy(fillTarget = FillTarget.CHARGE_LEVEL, brightness = Light.MAX),
            chargeLevel = 0.4f,
        )

        assertEquals(MeterRenderer.smooth(0.4f, layout, 1_600), subject.frameAt(subject.durationMillis))
        assertEquals("the fill itself runs at its own brightness", Light.MAX, subject.frameAt(subject.durationMillis * 6 / 10).segments.max())
    }
}
