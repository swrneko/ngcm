package com.swrneko.glyphmeter.animation

/** How a preset animates. The renderer switches on this; presets themselves are data. */
enum class PresetKind {
    /** Meter zone fills from its first segment to its last, then fades. */
    FILL_UP,

    /** A lit band sweeps across every zone in order. */
    WAVE,

    /** The whole interface breathes up and back down. */
    BREATHE,

    /** A single segment runs around the meter zone twice. */
    CHASE,

    /** Two short full-brightness pulses. */
    FLASH,

    /** Everything lit, fading to dark. Used when power is unplugged. */
    FADE_OUT,
}

data class AnimationPreset(
    val id: String,
    val kind: PresetKind,
    val durationMillis: Long,
)

object AnimationPresets {

    val FILL_UP = AnimationPreset(id = "fill_up", kind = PresetKind.FILL_UP, durationMillis = 1200)
    val WAVE = AnimationPreset(id = "wave", kind = PresetKind.WAVE, durationMillis = 1400)
    val BREATHE = AnimationPreset(id = "breathe", kind = PresetKind.BREATHE, durationMillis = 1600)
    val CHASE = AnimationPreset(id = "chase", kind = PresetKind.CHASE, durationMillis = 1000)
    val FLASH = AnimationPreset(id = "flash", kind = PresetKind.FLASH, durationMillis = 600)
    val FADE_OUT = AnimationPreset(id = "fade_out", kind = PresetKind.FADE_OUT, durationMillis = 700)

    val all: List<AnimationPreset> = listOf(FILL_UP, WAVE, BREATHE, CHASE, FLASH, FADE_OUT)

    /** Presets offered as a plug-in animation. [FADE_OUT] is reserved for unplugging. */
    val selectable: List<AnimationPreset> = listOf(FILL_UP, WAVE, BREATHE, CHASE, FLASH)

    fun byId(id: String): AnimationPreset? = all.firstOrNull { it.id == id }
}
