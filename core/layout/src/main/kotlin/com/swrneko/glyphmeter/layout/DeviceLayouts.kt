package com.swrneko.glyphmeter.layout

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphZone

/**
 * Table of known Glyph layouts.
 *
 * Index values are taken from the `Glyph.Code_*` classes inside
 * `glyph-matrix-sdk-2.0.aar`, read out of the bytecode rather than the README.
 */
object DeviceLayouts {

    /**
     * Nothing Phone (3a) and (3a) Pro. Both report the same device id.
     *
     * Verified: A_1..A_11 = 20..30, B_1..B_5 = 31..35, C_1..C_20 = 0..19.
     * Zone C is the long strip and runs bottom-left to top-right, which makes it
     * the natural battery meter.
     */
    val PHONE_3A: DeviceLayout = DeviceLayout(
        deviceId = "24111",
        displayName = "Phone (3a) / (3a) Pro",
        segmentCount = 36,
        zones = listOf(
            GlyphZone(id = "C", indices = (0..19).toList()),
            GlyphZone(id = "A", indices = (20..30).toList()),
            GlyphZone(id = "B", indices = (31..35).toList()),
        ),
        meterZoneId = "C",
        progressAnchorIndex = 0,
        hardwareVerified = true,
    )

    val all: List<DeviceLayout> = listOf(PHONE_3A)

    fun byDeviceId(deviceId: String): DeviceLayout? =
        all.firstOrNull { it.deviceId == deviceId }
}
