package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData

/**
 * Slides the meter from one battery level to another instead of snapping to it.
 *
 * This is the second, independent layer of smoothness: [MeterRenderer] makes a single
 * level look continuous, and this makes a change of level look continuous too.
 */
class MeterTransition(
    private val fromLevel: Float,
    private val toLevel: Float,
    private val layout: DeviceLayout,
    private val brightness: Int,
    override val durationMillis: Long,
    private val easing: Easing = Easing.EaseOutCubic,
) : FrameSource {

    override fun frameAt(elapsedMillis: Long): GlyphFrameData {
        val progress = when {
            durationMillis <= 0L -> 1f
            else -> (elapsedMillis.toFloat() / durationMillis).coerceIn(0f, 1f)
        }
        val eased = easing.transform(progress)
        val level = fromLevel + (toLevel - fromLevel) * eased

        return MeterRenderer.smooth(level, layout, brightness)
    }
}
