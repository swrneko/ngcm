package com.swrneko.glyphmeter.settings

import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.model.Light

enum class MeterMode {
    /** Meter stays lit for as long as the phone is charging. */
    ALWAYS_ON,

    /** Meter appears on plug-in and after each step of charge, then fades. */
    ON_EVENT,
}

data class GlyphSettings(
    val enabled: Boolean,
    val meterMode: MeterMode,
    /** Peak segment brightness, 0..Light.MAX. */
    val brightness: Int,
    /** How long the meter stays up in [MeterMode.ON_EVENT]. */
    val showDurationMillis: Long,
    /** Charge gain, in percent, that re-triggers the meter in [MeterMode.ON_EVENT]. */
    val repeatStepPercent: Int,
    val wiredPresetId: String,
    val wirelessPresetId: String,
    val fullPresetId: String,
    /** Skip lighting up while the phone lies screen-up. */
    val dimWhenFaceUp: Boolean,
) {
    companion object {
        val Default = GlyphSettings(
            enabled = true,
            meterMode = MeterMode.ON_EVENT,
            brightness = Light.MAX,
            showDurationMillis = 5_000,
            repeatStepPercent = 5,
            wiredPresetId = AnimationPresets.FILL_UP.id,
            wirelessPresetId = AnimationPresets.WAVE.id,
            fullPresetId = AnimationPresets.FLASH.id,
            dimWhenFaceUp = false,
        )
    }
}
