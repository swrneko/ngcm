package com.swrneko.glyphmeter.layout

import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.GlyphZone
import com.swrneko.glyphmeter.model.Light
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MeterRendererTest {

    private val layout = DeviceLayouts.PHONE_3A
    private val meter = layout.meterZone.indices
    private val full = Light.MAX

    @Test
    fun `an empty battery leaves every segment dark`() {
        val frame = MeterRenderer.smooth(level = 0f, layout = layout, brightness = full)

        assertEquals(List(36) { 0 }, frame.segments.toList())
    }

    @Test
    fun `a full battery lights the whole meter zone and nothing else`() {
        val frame = MeterRenderer.smooth(level = 1f, layout = layout, brightness = full)

        for (index in meter) {
            assertEquals("segment $index", full, frame[index])
        }
        for (index in layout.zone("A").indices + layout.zone("B").indices) {
            assertEquals("segment $index", 0, frame[index])
        }
    }

    @Test
    fun `a level on an exact segment boundary lights whole segments only`() {
        // 20 segments, so 25% is exactly 5 of them.
        val frame = MeterRenderer.smooth(level = 0.25f, layout = layout, brightness = full)

        assertEquals(listOf(full, full, full, full, full), meter.take(5).map { frame[it] })
        assertEquals(List(15) { 0 }, meter.drop(5).map { frame[it] })
    }

    @Test
    fun `a level between segments lights the next segment partially`() {
        // 63% of 20 segments is 12.6: twelve full, the thirteenth at 60 percent.
        val frame = MeterRenderer.smooth(level = 0.63f, layout = layout, brightness = full)

        assertEquals(List(12) { full }, meter.take(12).map { frame[it] })

        val partial = frame[meter[12]]
        val expected = Light.MIN_VISIBLE + ((full - Light.MIN_VISIBLE) * 0.6f).toInt()
        assertTrue("partial was $partial, expected near $expected", kotlin.math.abs(partial - expected) <= 2)

        assertEquals(List(7) { 0 }, meter.drop(13).map { frame[it] })
    }

    @Test
    fun `a barely started segment is still bright enough to see`() {
        // One percent into a segment must not produce an invisible glow.
        val frame = MeterRenderer.smooth(level = 0.0005f, layout = layout, brightness = full)

        assertTrue(frame[meter[0]] >= Light.MIN_VISIBLE)
    }

    @Test
    fun `the meter never decreases as the battery rises`() {
        var previous = List(36) { 0 }

        for (percent in 0..100) {
            val frame = MeterRenderer.smooth(percent / 100f, layout, full)
            val current = frame.segments.toList()

            for (index in meter) {
                assertTrue(
                    "segment $index dropped at $percent percent",
                    current[index] >= previous[index],
                )
            }
            previous = current
        }
    }

    @Test
    fun `brightness scales the whole meter`() {
        val frame = MeterRenderer.smooth(level = 0.25f, layout = layout, brightness = 1000)

        assertEquals(List(5) { 1000 }, meter.take(5).map { frame[it] })
    }

    @Test
    fun `a level outside the valid range is clamped`() {
        assertEquals(
            MeterRenderer.smooth(0f, layout, full),
            MeterRenderer.smooth(-5f, layout, full),
        )
        assertEquals(
            MeterRenderer.smooth(1f, layout, full),
            MeterRenderer.smooth(5f, layout, full),
        )
    }

    @Test
    fun `the stepped percentage counts a partly lit leading segment as a whole step`() {
        val frame = MeterRenderer.smooth(level = 0.63f, layout = layout, brightness = full)

        assertEquals(65, MeterRenderer.steppedPercent(frame, layout))
    }

    @Test
    fun `the stepped percentage is zero for a dark frame and hundred for a full one`() {
        assertEquals(0, MeterRenderer.steppedPercent(MeterRenderer.smooth(0f, layout, full), layout))
        assertEquals(100, MeterRenderer.steppedPercent(MeterRenderer.smooth(1f, layout, full), layout))
    }

    @Test
    fun `the stepped percentage ignores segments outside the meter zone`() {
        val segments = IntArray(layout.segmentCount)
        for (index in layout.zone("A").indices) segments[index] = full

        assertEquals(0, MeterRenderer.steppedPercent(GlyphFrameData(segments), layout))
    }

    @Test
    fun `a meter zone without segments yields zero instead of dividing by zero`() {
        val empty = layout.copy(zones = listOf(GlyphZone(id = "C", indices = emptyList())))

        assertEquals(0, MeterRenderer.steppedPercent(GlyphFrameData(IntArray(layout.segmentCount)), empty))
    }
}
