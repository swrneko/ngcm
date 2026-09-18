package com.swrneko.glyphmeter.hardware

enum class RenderCapability {
    /** Not probed yet. */
    UNKNOWN,

    /** `buildChannel(index, light)` works: full sub-segment smoothness. */
    PER_SEGMENT,

    /** Per-segment brightness is gone; only whole-segment steps are possible. */
    STEPPED_ONLY,

    /** The Glyph service refused us. Nothing can be drawn. */
    UNAVAILABLE,
}
