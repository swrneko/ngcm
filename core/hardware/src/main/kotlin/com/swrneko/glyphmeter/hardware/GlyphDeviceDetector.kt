package com.swrneko.glyphmeter.hardware

import com.nothing.ketchum.Common
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.model.DeviceLayout

/**
 * Figures out which known Glyph layout matches the phone we are running on.
 *
 * Delegates to the manufacturer's own `Common.is24111()` check rather than guessing from
 * `Build.DEVICE` / `Build.MODEL`, since Nothing's own SDK knows this better than a heuristic.
 */
object GlyphDeviceDetector {

    /** Layout of the phone we are running on, or null when the Glyph SDK does not recognise it. */
    fun detect(): DeviceLayout? = when {
        runCatching { Common.is24111() }.getOrDefault(false) -> DeviceLayouts.PHONE_3A
        else -> null
    }
}
