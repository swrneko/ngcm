package com.swrneko.glyphmeter.access

/** Something that can read and set the Glyph debug flag. */
interface DebugModeWriter {
    /** True when this writer currently has the rights it needs. */
    val isAvailable: Boolean

    fun isDebugModeOn(): Boolean

    /** Returns true when debug mode is on afterwards. */
    fun enableDebugMode(): Boolean
}
