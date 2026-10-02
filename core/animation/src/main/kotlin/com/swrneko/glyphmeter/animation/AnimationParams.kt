package com.swrneko.glyphmeter.animation

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphZone
import com.swrneko.glyphmeter.model.Light

/** Which way a sweep runs around the ring. Clockwise follows the order of `DeviceLayout.zones`. */
enum class SweepDirection { CLOCKWISE, COUNTER_CLOCKWISE }

/** How far [PresetKind.FILL_UP] fills. */
enum class FillTarget {
    /** The whole strip lights up, then fades out. */
    FULL,

    /** The meter fills up to the battery level and hands over to the meter without fading. */
    CHARGE_LEVEL,
}

/** How [PresetKind.FILL_UP] advances. */
enum class FillStyle {
    /** Continuous, with the leading segment glowing in proportion, like the meter itself. */
    SMOOTH,

    /** One whole segment at a time. */
    STEPPED,
}

/**
 * What the user tuned for one animation. The defaults keep the preset's own timing, brightness,
 * zones and direction; the only visible change from 0.1 is that a fill is smooth by default.
 */
data class AnimationParams(
    /** Length of one run of the animation. */
    val cycleMillis: Long,
    /** How many times the animation runs back to back. */
    val repeats: Int = 1,
    /** Peak brightness, 0..Light.MAX; null follows the brightness the caller passes in. */
    val brightness: Int? = null,
    /** Zones the animation lights; null, or no zone the layout knows, means the preset's own. */
    val zoneIds: Set<String>? = null,
    /** Used by [PresetKind.WAVE] and [PresetKind.CHASE]. */
    val direction: SweepDirection = SweepDirection.CLOCKWISE,
    /** Used by [PresetKind.FILL_UP]. */
    val fillTarget: FillTarget = FillTarget.FULL,
    /** Used by [PresetKind.FILL_UP]. */
    val fillStyle: FillStyle = FillStyle.SMOOTH,
) {
    /** The same parameters with every number pulled into its legal range. */
    fun normalized(): AnimationParams = copy(
        cycleMillis = cycleMillis.coerceIn(MIN_CYCLE_MILLIS, MAX_CYCLE_MILLIS),
        repeats = repeats.coerceIn(MIN_REPEATS, MAX_REPEATS),
        brightness = brightness?.coerceIn(0, Light.MAX),
    )

    companion object {
        const val MIN_CYCLE_MILLIS = 500L
        const val MAX_CYCLE_MILLIS = 3_000L
        const val MIN_REPEATS = 1
        const val MAX_REPEATS = 5
    }
}

/** Parameters that reproduce this preset as shipped. */
val AnimationPreset.defaultParams: AnimationParams
    get() = AnimationParams(cycleMillis = durationMillis)

/** The zones lit when the user has not picked any: the meter for a fill, every zone otherwise. */
fun AnimationPreset.defaultZoneIds(layout: DeviceLayout): Set<String> = when (kind) {
    PresetKind.FILL_UP -> setOf(layout.meterZoneId)
    else -> layout.zones.map { it.id }.toSet()
}

/**
 * Zones this preset lights with [params] on [layout], clockwise. Picked ids the layout does not
 * know are dropped, and nothing left means [defaultZoneIds]. A fill to the charge level always
 * runs along the meter, whatever was picked.
 */
fun AnimationPreset.zonesFor(params: AnimationParams, layout: DeviceLayout): List<GlyphZone> {
    if (hasFill && params.fillTarget == FillTarget.CHARGE_LEVEL) return listOf(layout.meterZone)
    val known = params.zoneIds?.filter { id -> layout.zones.any { it.id == id } }?.toSet()
    val chosen = if (known.isNullOrEmpty()) defaultZoneIds(layout) else known
    return layout.zones.filter { it.id in chosen }
}

/** True when [AnimationParams.direction] changes how this preset looks. */
val AnimationPreset.hasDirection: Boolean
    get() = kind == PresetKind.WAVE || kind == PresetKind.CHASE

/** True when [AnimationParams.fillTarget] and [AnimationParams.fillStyle] apply to this preset. */
val AnimationPreset.hasFill: Boolean
    get() = kind == PresetKind.FILL_UP
