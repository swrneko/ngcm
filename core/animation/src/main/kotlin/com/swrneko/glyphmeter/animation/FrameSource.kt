package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.model.GlyphFrameData

/**
 * A finite sequence of Glyph frames expressed as a function of elapsed time.
 *
 * Time is passed in rather than read from a clock, so every animation is testable
 * without waiting for anything.
 */
interface FrameSource {
    val durationMillis: Long

    /** Frame to show [elapsedMillis] after the animation started. Values past the end clamp. */
    fun frameAt(elapsedMillis: Long): GlyphFrameData
}
