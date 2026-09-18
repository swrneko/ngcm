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
        val meter = layout.meterZone.indices
        val clampedLevel = level.coerceIn(0f, 1f)
        val clampedBrightness = brightness.coerceIn(0, Light.MAX)

        val exact = clampedLevel * meter.size
        val wholeSegments = floor(exact).toInt()
        val remainder = exact - wholeSegments

        for (i in 0 until wholeSegments) {
            segments[meter[i]] = clampedBrightness
        }

        // A segment that has only just started filling still has to be visible, so the
        // partial value is mapped onto [MIN_VISIBLE, brightness] rather than onto
        // [0, brightness]. Without this the leading segment would be dark for the first
        // fifth of its range and the scale would look like it lags behind the battery.
        if (wholeSegments < meter.size && remainder > 0f) {
            val floorLight = minOf(Light.MIN_VISIBLE, clampedBrightness)
            val span = clampedBrightness - floorLight
            segments[meter[wholeSegments]] = floorLight + (span * remainder).roundToInt()
        }

        return GlyphFrameData(segments)
    }

    /**
     * All-or-nothing fallback, used when per-segment brightness is unavailable.
     *
     * Also lights [DeviceLayout.progressAnchorIndex], because `displayProgress` throws
     * a `GlyphException` unless that segment is present in the frame.
     */
    fun stepped(level: Float, layout: DeviceLayout, brightness: Int): GlyphFrameData {
        val segments = IntArray(layout.segmentCount)
        val meter = layout.meterZone.indices
        val clampedLevel = level.coerceIn(0f, 1f)
        val clampedBrightness = brightness.coerceIn(0, Light.MAX)

        val wholeSegments = floor(clampedLevel * meter.size).toInt()

        for (i in 0 until wholeSegments) {
            segments[meter[i]] = clampedBrightness
        }
        if (wholeSegments > 0) {
            segments[layout.progressAnchorIndex] = clampedBrightness
        }

        return GlyphFrameData(segments)
    }
}
