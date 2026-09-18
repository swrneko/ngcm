package com.swrneko.glyphmeter.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class GlyphFrameDataTest {

    @Test
    fun `off produces a frame of the requested size with all segments dark`() {
        val frame = GlyphFrameData.off(36)

        assertEquals(36, frame.size)
        assertEquals(List(36) { 0 }, frame.segments.toList())
    }

    @Test
    fun `frames with identical segment values are equal`() {
        val a = GlyphFrameData(intArrayOf(0, 100, 4095))
        val b = GlyphFrameData(intArrayOf(0, 100, 4095))

        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun `frames with different segment values are not equal`() {
        val a = GlyphFrameData(intArrayOf(0, 100, 4095))
        val b = GlyphFrameData(intArrayOf(0, 101, 4095))

        assertNotEquals(a, b)
    }
}
