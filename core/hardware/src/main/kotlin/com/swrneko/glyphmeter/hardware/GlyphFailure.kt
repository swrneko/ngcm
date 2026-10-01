package com.swrneko.glyphmeter.hardware

/** Why the Glyph last stopped working. Kept apart from [RenderCapability], which a disconnect resets. */
enum class GlyphFailure {
    /** The Glyph service could not be reached, or would not open a session. */
    SERVICE_UNREACHABLE,

    /** The service refused to register the app: no Glyph permission, or debug mode is off. */
    REGISTRATION_REJECTED,

    /** An open session was dropped by the system. */
    SESSION_LOST,

    /** Even the stepped fallback could not draw. */
    RENDERING_FAILED,
}
