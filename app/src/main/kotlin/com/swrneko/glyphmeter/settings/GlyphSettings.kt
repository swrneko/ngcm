package com.swrneko.glyphmeter.settings

import com.swrneko.glyphmeter.animation.AnimationParams
import com.swrneko.glyphmeter.animation.AnimationPreset
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.PresetFrameSource
import com.swrneko.glyphmeter.animation.defaultParams
import com.swrneko.glyphmeter.model.DeviceLayout
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
    /** Tuning per animation, keyed by preset id. A preset without an entry plays as shipped. */
    val animationParams: Map<String, AnimationParams> = emptyMap(),
) {
    fun paramsFor(preset: AnimationPreset): AnimationParams =
        animationParams[preset.id] ?: preset.defaultParams

    /** [preset] as tuned here; its brightness falls back to [brightness], the meter's. */
    fun frameSource(preset: AnimationPreset, layout: DeviceLayout, chargeLevel: Float): PresetFrameSource =
        PresetFrameSource(preset, layout, brightness, paramsFor(preset), chargeLevel)

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
