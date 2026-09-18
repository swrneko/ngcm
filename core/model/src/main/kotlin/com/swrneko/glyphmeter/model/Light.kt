package com.swrneko.glyphmeter.model

/** Brightness limits of a single Glyph segment, taken from the Nothing SDK. */
object Light {
    /** Highest value the SDK accepts. `GlyphManager.DEFAULT_MAX_LIGHT` is 4096, so the top value is 4095. */
    const val MAX: Int = 4095

    /** `GlyphManager.DEFAULT_MIN_LIGHT`. Below this an LED is effectively invisible. */
    const val MIN_VISIBLE: Int = 800
}
