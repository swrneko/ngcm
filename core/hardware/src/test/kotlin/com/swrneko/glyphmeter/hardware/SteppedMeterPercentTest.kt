package com.swrneko.glyphmeter.hardware

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * [steppedMeterPercent] is the pure fallback-percentage calculation pulled out of
 * [NothingGlyphDisplay], which cannot itself be unit-tested because the Glyph SDK is
 * not on this module's test classpath (see [FakeGlyphDisplayTest] for the rest of the
 * hardware layer's test coverage).
 */
class SteppedMeterPercentTest {

    @Test
    fun `a fully lit meter is one hundred percent`() {
        assertEquals(100, steppedMeterPercent(meterSize = 20, litCount = 20))
    }

    @Test
    fun `a half lit meter is fifty percent`() {
        assertEquals(50, steppedMeterPercent(meterSize = 20, litCount = 10))
    }

    @Test
    fun `an unlit meter is zero percent`() {
        assertEquals(0, steppedMeterPercent(meterSize = 20, litCount = 0))
    }

    @Test
    fun `an empty meter zone is zero percent instead of dividing by zero`() {
        assertEquals(0, steppedMeterPercent(meterSize = 0, litCount = 0))
    }
}
