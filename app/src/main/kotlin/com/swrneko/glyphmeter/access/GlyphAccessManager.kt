package com.swrneko.glyphmeter.access

/**
 * Works out whether the Glyph interface is usable, and makes it usable when it can.
 *
 * The order matters. On Nothing OS 4.0 Nothing removed the developer-key restriction
 * entirely, so a phone that already works must never be shown adb instructions.
 */
class GlyphAccessManager(
    private val writers: List<DebugModeWriter>,
    private val isDeviceSupported: () -> Boolean,
    private val requiresDebugMode: () -> Boolean,
) {

    /** Command the user runs once, after which the app keeps debug mode on by itself. */
    val adbGrantCommand: String =
        "adb shell pm grant $PACKAGE_NAME android.permission.WRITE_SECURE_SETTINGS"

    fun evaluate(): GlyphAccessState {
        if (!isDeviceSupported()) return GlyphAccessState.UNSUPPORTED_DEVICE
        if (!requiresDebugMode()) return GlyphAccessState.WORKING

        // Writers with rights go first: a flag that is on right now still expires after 48 hours,
        // so "works directly" is only true when nobody needs to keep it on.
        for (writer in writers) {
            if (!writer.isAvailable) continue
            if (writer.enableDebugMode()) return GlyphAccessState.MANAGED_BY_APP
        }

        if (writers.any { it.isDebugModeOn() }) return GlyphAccessState.WORKING

        return GlyphAccessState.NEEDS_SETUP
    }

    private companion object {
        const val PACKAGE_NAME = "com.swrneko.glyphmeter"
    }
}
