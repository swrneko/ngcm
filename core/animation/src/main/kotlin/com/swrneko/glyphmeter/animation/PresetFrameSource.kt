package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.Light
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Renders an [AnimationPreset] into frames.
 *
 * One renderer for all presets, because a preset is data. Every preset ends fully dark
 * so the meter can take over without a visible seam.
 */
class PresetFrameSource(
    private val preset: AnimationPreset,
    private val layout: DeviceLayout,
    private val brightness: Int,
) : FrameSource {

    override val durationMillis: Long get() = preset.durationMillis

    /** Segment order used by sweeps: the meter zone first, then the remaining zones. */
    private val sweepOrder: List<Int> = buildList {
        addAll(layout.meterZone.indices)
        for (zone in layout.zones) {
            if (zone.id != layout.meterZoneId) addAll(zone.indices)
        }
    }

    override fun frameAt(elapsedMillis: Long): GlyphFrameData {
        if (elapsedMillis >= durationMillis) return GlyphFrameData.off(layout.segmentCount)

        val t = when {
            durationMillis <= 0L -> 1f
            else -> (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        }
        val segments = IntArray(layout.segmentCount)
        val peak = brightness.coerceIn(0, Light.MAX)

        when (preset.kind) {
            PresetKind.FILL_UP -> renderFillUp(segments, t, peak)
            PresetKind.WAVE -> renderSweep(segments, t, peak, bandWidth = 5f, passes = 1)
            PresetKind.BREATHE -> renderBreathe(segments, t, peak)
            PresetKind.CHASE -> renderSweep(segments, t, peak, bandWidth = 2f, passes = 2)
            PresetKind.FLASH -> renderFlash(segments, t, peak)
            PresetKind.FADE_OUT -> renderFadeOut(segments, t, peak)
        }

        return GlyphFrameData(segments)
    }

    /** Fills the meter zone over the first 70 percent, then fades the whole thing out. */
    private fun renderFillUp(segments: IntArray, t: Float, peak: Int) {
        val meter = layout.meterZone.indices
        val fillPhase = 0.7f

        if (t <= fillPhase) {
            val filled = (t / fillPhase * meter.size).toInt().coerceAtMost(meter.size)
            for (i in 0 until filled) segments[meter[i]] = peak
        } else {
            val fade = 1f - (t - fillPhase) / (1f - fillPhase)
            val level = (peak * fade).roundToInt().coerceAtLeast(0)
            for (index in meter) segments[index] = level
        }
    }

    /** Moves a lit band of [bandWidth] segments along [sweepOrder], [passes] times. */
    private fun renderSweep(segments: IntArray, t: Float, peak: Int, bandWidth: Float, passes: Int) {
        val total = sweepOrder.size
        // Travel far enough past the end that the band leaves the strip before time runs out.
        val travel = (total + bandWidth) * passes
        val head = t * travel

        for ((position, index) in sweepOrder.withIndex()) {
            val offsetInPass = ((head - position) % (total + bandWidth) + (total + bandWidth)) % (total + bandWidth)
            if (offsetInPass in 0f..bandWidth) {
                val falloff = 1f - offsetInPass / bandWidth
                segments[index] = (peak * falloff).roundToInt().coerceIn(0, peak)
            }
        }
    }

    /** One half sine over the full duration, applied to every segment at once. */
    private fun renderBreathe(segments: IntArray, t: Float, peak: Int) {
        val level = (peak * sin(t * PI).toFloat()).roundToInt().coerceIn(0, peak)
        segments.fill(level)
    }

    /**
     * Two short pulses (raised-cosine bumps) with a dark gap between and after them.
     *
     * A plain `abs(sin(2*PI*t))` looks like two pulses but is actually continuously lit
     * except at the instants t = 0, 0.5 and 1 - it goes fully dark exactly at the
     * midpoint of the animation, which reads as a single long pulse rather than two
     * short ones. Two explicit windows avoid that and give a real dark gap between them.
     */
    private fun renderFlash(segments: IntArray, t: Float, peak: Int) {
        val pulseCenters = floatArrayOf(0.2f, 0.55f)
        val pulseHalfWidth = 0.15f

        var factor = 0f
        for (center in pulseCenters) {
            val distance = abs(t - center)
            if (distance < pulseHalfWidth) {
                val shaped = cos(distance / pulseHalfWidth * (PI.toFloat() / 2f))
                factor = maxOf(factor, shaped)
            }
        }

        val level = (peak * factor).roundToInt().coerceIn(0, peak)
        segments.fill(level)
    }

    /** Linear fade from full to dark. */
    private fun renderFadeOut(segments: IntArray, t: Float, peak: Int) {
        val level = (peak * (1f - t)).roundToInt().coerceIn(0, peak)
        segments.fill(level)
    }
}
