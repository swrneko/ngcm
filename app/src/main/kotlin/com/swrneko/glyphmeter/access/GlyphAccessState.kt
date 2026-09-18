package com.swrneko.glyphmeter.access

enum class GlyphAccessState {
    /** Not evaluated yet. */
    CHECKING,

    /** The Glyph interface answers without any setup. Nothing OS 4.0 and newer. */
    WORKING,

    /** The app holds WRITE_SECURE_SETTINGS and keeps debug mode on by itself, forever. */
    MANAGED_BY_APP,

    /** One-time setup is needed. Show the instructions. */
    NEEDS_SETUP,

    /** Not a Nothing phone this app knows how to drive. */
    UNSUPPORTED_DEVICE,
}
