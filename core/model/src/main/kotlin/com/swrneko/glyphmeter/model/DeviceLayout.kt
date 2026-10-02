package com.swrneko.glyphmeter.model

/**
 * A contiguous, ordered group of Glyph segments.
 *
 * [indices] is in visual order: the first entry is where an animation or a meter
 * starts filling from.
 */
data class GlyphZone(
    val id: String,
    val indices: List<Int>,
)

/**
 * Physical description of one phone's Glyph interface.
 *
 * Adding a new phone means adding one entry to `DeviceLayouts.all`; no logic changes.
 */
data class DeviceLayout(
    /** Value of the matching `Glyph.DEVICE_*` constant, without the `DEVICE_` prefix. */
    val deviceId: String,
    val displayName: String,
    val segmentCount: Int,
    /** Listed clockwise around the ring; sweeps and fills follow this order. */
    val zones: List<GlyphZone>,
    /** Zone used as the battery meter. */
    val meterZoneId: String,
    /**
     * Segment that `GlyphManager.displayProgress` insists on having lit for this device.
     * Used only by the stepped fallback renderer.
     */
    val progressAnchorIndex: Int,
    /** True when this layout was verified against real hardware. */
    val hardwareVerified: Boolean,
) {
    fun zone(id: String): GlyphZone =
        zones.firstOrNull { it.id == id }
            ?: error("layout $deviceId has no zone $id")

    val meterZone: GlyphZone get() = zone(meterZoneId)
}
