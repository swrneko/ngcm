package com.swrneko.glyphmeter.hardware

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlinx.coroutines.flow.StateFlow

/**
 * The only thing in the project allowed to know that `com.nothing.ketchum` exists.
 *
 * Everything above this interface deals in frames of brightness values.
 */
interface GlyphDisplay {

    val capability: StateFlow<RenderCapability>

    /** Binds to the Glyph service and opens a session. Safe to call when already connected. */
    suspend fun connect(layout: DeviceLayout): Result<Unit>

    /** Shows one frame. Does nothing when disconnected. */
    fun render(frame: GlyphFrameData)

    fun turnOff()

    fun disconnect()
}
