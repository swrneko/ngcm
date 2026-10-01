package com.swrneko.glyphmeter.access

/**
 * Whether the service may be started after a reboot. Only states in which the Glyph
 * interface answers (or the app can switch it on by itself) qualify; starting in any other
 * state would just show a useless notification.
 */
fun shouldStartOnBoot(state: GlyphAccessState): Boolean = when (state) {
    GlyphAccessState.WORKING, GlyphAccessState.MANAGED_BY_APP -> true
    GlyphAccessState.CHECKING,
    GlyphAccessState.NEEDS_SETUP,
    GlyphAccessState.UNSUPPORTED_DEVICE -> false
}
