package com.swrneko.glyphmeter.access

import com.swrneko.glyphmeter.model.DeviceLayout

/**
 * Whether the foreground service should be running right now: the Glyph interface must be
 * usable (see [shouldStartOnBoot]), the user must have the app switched on, and the phone
 * must have a known Glyph layout. Otherwise the service would start, find nothing to draw on
 * and stop again, flashing its notification.
 */
fun shouldStartService(access: GlyphAccessState, enabled: Boolean, layout: DeviceLayout?): Boolean =
    shouldStartOnBoot(access) && enabled && layout != null
