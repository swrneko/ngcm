package com.swrneko.glyphmeter.access

import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings
import android.util.Log
import javax.inject.Inject

internal const val GLYPH_DEBUG_SETTING = "nt_glyph_interface_debug_enable"

/**
 * Writes the flag directly, which needs WRITE_SECURE_SETTINGS.
 *
 * Android never grants that permission to an ordinary app, so the user grants it once
 * over adb. After that it is permanent, and the 48-hour expiry of debug mode stops
 * mattering because the app simply turns it back on.
 */
class SecureSettingsDebugModeWriter @Inject constructor(
    private val context: Context,
) : DebugModeWriter {

    override val isAvailable: Boolean
        get() = context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            PackageManager.PERMISSION_GRANTED

    override fun isDebugModeOn(): Boolean =
        Settings.Global.getInt(context.contentResolver, GLYPH_DEBUG_SETTING, 0) == 1

    override fun enableDebugMode(): Boolean = try {
        Settings.Global.putInt(context.contentResolver, GLYPH_DEBUG_SETTING, 1)
        isDebugModeOn()
    } catch (e: SecurityException) {
        Log.w("SecureSettingsWriter", "no permission to write $GLYPH_DEBUG_SETTING", e)
        false
    }
}
