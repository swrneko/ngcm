package com.swrneko.glyphmeter.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class FakeSettingsRepository(
    initial: GlyphSettings = GlyphSettings.Default,
) : SettingsRepository {

    private val _settings = MutableStateFlow(initial)
    override val settings: Flow<GlyphSettings> = _settings.asStateFlow()

    override suspend fun update(transform: (GlyphSettings) -> GlyphSettings) {
        _settings.update(transform)
    }
}
