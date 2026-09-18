package com.swrneko.glyphmeter.settings

import kotlinx.coroutines.flow.Flow

interface SettingsRepository {
    val settings: Flow<GlyphSettings>
    suspend fun update(transform: (GlyphSettings) -> GlyphSettings)
}
