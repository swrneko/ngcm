package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.layout.MeterRenderer
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
 * One renderer for all presets, because a preset is data. [params] tune the speed, repeats,
 * brightness, zones, direction and fill; [brightness] is used when [params] has no brightness of
 * its own. Every preset ends fully dark so the meter can take over without a visible seam, except
 * a fill to the charge level: it ends on exactly the frame the meter shows for [chargeLevel], and
 * reports that level as [handoffLevel].
 */
class PresetFrameSource(
    private val preset: AnimationPreset,
    private val layout: DeviceLayout,
    brightness: Int,
    params: AnimationParams = preset.defaultParams,
    chargeLevel: Float = 1f,
) : FrameSource {

    private val params: AnimationParams = params.normalized()
    private val cycleMillis: Long = this.params.cycleMillis
    private val peak: Int = (this.params.brightness ?: brightness).coerceIn(0, Light.MAX)

    /** The meter's brightness, which a fill to the charge level ends on. */
    private val meterBrightness: Int = brightness.coerceIn(0, Light.MAX)

    override val durationMillis: Long = cycleMillis * this.params.repeats

    /** Battery level the last frame shows on the meter, or null when the animation ends dark. */
    val handoffLevel: Float? =
        if (preset.hasFill && this.params.fillTarget == FillTarget.CHARGE_LEVEL) chargeLevel.coerceIn(0f, 1f) else null

    /** Lit zones in clockwise order around the ring, which is the order of `DeviceLayout.zones`. */
    private val zones = preset.zonesFor(this.params, layout)

    /** Segment order used by sweeps and fills, in the chosen direction. */
    private val sweepOrder: List<Int> = zones.flatMap { it.indices }.let { clockwise ->
        if (this.params.direction == SweepDirection.COUNTER_CLOCKWISE && preset.hasDirection) clockwise.reversed() else clockwise
    }

    private val litIndices: List<Int> = zones.flatMap { it.indices }

    override fun frameAt(elapsedMillis: Long): GlyphFrameData {
        if (elapsedMillis >= durationMillis) {
            if (handoffLevel == null) return GlyphFrameData.off(layout.segmentCount)
            return GlyphFrameData(IntArray(layout.segmentCount).also { renderFillUp(it, t = 1f, lastRun = true) })
        }

        val run = (elapsedMillis / cycleMillis).toInt()
        val t = ((elapsedMillis % cycleMillis).toFloat() / cycleMillis).coerceIn(0f, 1f)
        val lastRun = run == params.repeats - 1
        val segments = IntArray(layout.segmentCount)

        when (preset.kind) {
            PresetKind.FILL_UP -> renderFillUp(segments, t, lastRun)
            PresetKind.WAVE -> renderSweep(segments, t, peak, bandWidth = 5f, passes = 1)
            PresetKind.BREATHE -> renderBreathe(segments, t, peak)
            PresetKind.CHASE -> renderSweep(segments, t, peak, bandWidth = 2f, passes = 2)
            PresetKind.FLASH -> renderFlash(segments, t, peak)
            PresetKind.FADE_OUT -> renderFadeOut(segments, t, peak)
        }

        return GlyphFrameData(segments)
    }

    /**
     * Fills over the first 70 percent of a run, then fades out. A fill to the charge level runs
     * along the meter only, and its last run holds the meter frame instead of fading.
     */
    private fun renderFillUp(segments: IntArray, t: Float, lastRun: Boolean) {
        // A fill to the charge level has the meter as its only zone, so this is the meter then.
        val path = sweepOrder
        val target = handoffLevel ?: 1f

        when {
            t <= FILL_PHASE -> fill(segments, path, t / FILL_PHASE * target)
            handoffLevel != null && lastRun -> {
                // The hold eases from the animation's own brightness and from a stepped fill
                // into the exact meter frame, so the meter that follows starts without a jump.
                val progress = ((t - FILL_PHASE) / (1f - FILL_PHASE)).coerceIn(0f, 1f)
                val light = peak + ((meterBrightness - peak) * progress).roundToInt()
                MeterRenderer.fillPath(segments, path, target, light)
            }
            else -> {
                fill(segments, path, target)
                val fade = (1f - (t - FILL_PHASE) / (1f - FILL_PHASE)).coerceIn(0f, 1f)
                for (index in path) segments[index] = (segments[index] * fade).roundToInt()
            }
        }
    }

    private fun fill(segments: IntArray, path: List<Int>, fraction: Float) {
        when (params.fillStyle) {
            FillStyle.SMOOTH -> MeterRenderer.fillPath(segments, path, fraction, peak)
            FillStyle.STEPPED -> {
                val whole = (fraction.coerceIn(0f, 1f) * path.size).toInt().coerceAtMost(path.size)
                for (i in 0 until whole) segments[path[i]] = peak
            }
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

    /** One half sine over a run, applied to every segment at once. */
    private fun renderBreathe(segments: IntArray, t: Float, peak: Int) {
        val level = (peak * sin(t * PI).toFloat()).roundToInt().coerceIn(0, peak)
        for (index in litIndices) segments[index] = level
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
        for (index in litIndices) segments[index] = level
    }

    /** Linear fade from full to dark. */
    private fun renderFadeOut(segments: IntArray, t: Float, peak: Int) {
        val level = (peak * (1f - t)).roundToInt().coerceIn(0, peak)
        segments.fill(level)
    }

    private companion object {
        /** Share of a fill run spent filling; the rest fades out or holds. */
        const val FILL_PHASE = 0.7f
    }
}
