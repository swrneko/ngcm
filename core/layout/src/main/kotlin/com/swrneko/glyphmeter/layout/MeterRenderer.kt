package com.swrneko.glyphmeter.layout

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.Light
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * Turns a battery level into a Glyph frame.
 *
 * The Nothing SDK's own `displayProgress` fills a zone one whole segment at a time,
 * which on Phone (3a) means twenty steps, or five percent of charge per step.
 * [smooth] instead drives per-segment brightness: whole segments burn at full
 * brightness and the next one glows in proportion to the remainder, which makes the
 * scale effectively continuous.
 */
object MeterRenderer {

    fun smooth(level: Float, layout: DeviceLayout, brightness: Int): GlyphFrameData {
        val segments = IntArray(layout.segmentCount)
        fillPath(segments, layout.meterZone.indices, level, brightness)
        return GlyphFrameData(segments)
    }

    /**
     * Lights the first [fraction] of [path] into [segments], the same way [smooth] lights the
     * meter: whole segments at [brightness], the leading one in proportion to the remainder.
     * Segments outside the lit part are left untouched.
     */
    fun fillPath(segments: IntArray, path: List<Int>, fraction: Float, brightness: Int) {
        val clampedFraction = fraction.coerceIn(0f, 1f)
        val clampedBrightness = brightness.coerceIn(0, Light.MAX)

        val exact = clampedFraction * path.size
        val wholeSegments = floor(exact).toInt()
        val remainder = exact - wholeSegments

        for (i in 0 until wholeSegments) {
            segments[path[i]] = clampedBrightness
        }

        // A segment that has only just started filling still has to be visible, so the
        // partial value is mapped onto [MIN_VISIBLE, brightness] rather than onto
        // [0, brightness]. Without this the leading segment would be dark for the first
        // fifth of its range and the scale would look like it lags behind the battery.
        if (wholeSegments < path.size && remainder > 0f) {
            val floorLight = minOf(Light.MIN_VISIBLE, clampedBrightness)
            val span = clampedBrightness - floorLight
            segments[path[wholeSegments]] = floorLight + (span * remainder).roundToInt()
        }
    }

    /**
     * Percentage of the meter zone shown by [frame], for the stepped fallback.
     *
     * The SDK's `displayProgress` only takes a percentage, so the fallback has to read it back
     * out of the frame. A segment that is lit at all counts as a whole step, including the
     * partly glowing leading one: 63% of a twenty-segment meter lights thirteen segments and is
     * reported as 65%. This is the only stepped implementation; an empty meter zone yields 0
     * because this path is the last line of defense and must never throw.
     */
    fun steppedPercent(frame: GlyphFrameData, layout: DeviceLayout): Int {
        val meter = layout.meterZone.indices
        if (meter.isEmpty()) return 0
        return meter.count { frame[it] > 0 } * 100 / meter.size
    }
}
