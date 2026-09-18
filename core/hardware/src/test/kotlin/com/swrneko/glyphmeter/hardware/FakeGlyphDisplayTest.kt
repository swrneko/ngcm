package com.swrneko.glyphmeter.hardware

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.GlyphZone
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeGlyphDisplayTest {

    private val layout = DeviceLayout(
        deviceId = "test",
        displayName = "Test",
        segmentCount = 4,
        zones = listOf(GlyphZone(id = "C", indices = listOf(0, 1, 2, 3))),
        meterZoneId = "C",
        progressAnchorIndex = 0,
        hardwareVerified = false,
    )

    @Test
    fun `it records the frames it was asked to show`() = runTest {
        val display = FakeGlyphDisplay()
        display.connect(layout)

        display.render(GlyphFrameData(intArrayOf(1, 2, 3, 4)))
        display.render(GlyphFrameData(intArrayOf(5, 6, 7, 8)))

        assertEquals(
            listOf(
                GlyphFrameData(intArrayOf(1, 2, 3, 4)),
                GlyphFrameData(intArrayOf(5, 6, 7, 8)),
            ),
            display.rendered,
        )
    }

    @Test
    fun `it ignores frames while disconnected`() {
        val display = FakeGlyphDisplay()

        display.render(GlyphFrameData(intArrayOf(1, 2, 3, 4)))

        assertTrue(display.rendered.isEmpty())
    }

    @Test
    fun `a successful connect reports per segment support`() = runTest {
        val display = FakeGlyphDisplay()

        val result = display.connect(layout)

        assertTrue(result.isSuccess)
        assertTrue(display.isConnected)
        assertEquals(RenderCapability.PER_SEGMENT, display.capability.value)
    }

    @Test
    fun `losing per segment support downgrades the capability`() = runTest {
        val display = FakeGlyphDisplay()
        display.failPerSegment = true

        display.connect(layout)

        assertEquals(RenderCapability.STEPPED_ONLY, display.capability.value)
    }

    @Test
    fun `a refused connect reports the failure and stays unavailable`() = runTest {
        val display = FakeGlyphDisplay()
        display.connectResult = Result.failure(IllegalStateException("no glyph service"))

        val result = display.connect(layout)

        assertTrue(result.isFailure)
        assertFalse(display.isConnected)
        assertEquals(RenderCapability.UNAVAILABLE, display.capability.value)
    }

    @Test
    fun `disconnect stops recording and counts a turn off`() = runTest {
        val display = FakeGlyphDisplay()
        display.connect(layout)

        display.turnOff()
        display.disconnect()
        display.render(GlyphFrameData(intArrayOf(1, 1, 1, 1)))

        assertEquals(1, display.turnOffCount)
        assertFalse(display.isConnected)
        assertTrue(display.rendered.isEmpty())
    }
}
