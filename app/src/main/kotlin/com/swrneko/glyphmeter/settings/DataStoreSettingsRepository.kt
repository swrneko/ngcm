package com.swrneko.glyphmeter.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.swrneko.glyphmeter.model.Light
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads and writes [GlyphSettings] through an injected [DataStore], rather than reaching for
 * a context-bound [androidx.datastore.preferences.preferencesDataStore] delegate itself. This
 * keeps the class free of Android context handling and makes it trivial to point at a
 * temporary, disposable store in tests (see [DataStoreSettingsRepositoryTest]). Production
 * wiring of the real on-device store lives in [SettingsModule].
 */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    private object Keys {
        val Enabled = booleanPreferencesKey("enabled")
        val MeterModeName = stringPreferencesKey("meter_mode")
        val Brightness = intPreferencesKey("brightness")
        val ShowDuration = longPreferencesKey("show_duration_ms")
        val RepeatStep = intPreferencesKey("repeat_step_percent")
        val WiredPreset = stringPreferencesKey("wired_preset")
        val WirelessPreset = stringPreferencesKey("wireless_preset")
        val FullPreset = stringPreferencesKey("full_preset")
        val DimWhenFaceUp = booleanPreferencesKey("dim_when_face_up")
    }

    override val settings: Flow<GlyphSettings> =
        dataStore.data.map { it.toSettings() }

    override suspend fun update(transform: (GlyphSettings) -> GlyphSettings) {
        dataStore.edit { preferences ->
            val updated = transform(preferences.toSettings())

            preferences[Keys.Enabled] = updated.enabled
            preferences[Keys.MeterModeName] = updated.meterMode.name
            preferences[Keys.Brightness] = updated.brightness.coerceIn(0, Light.MAX)
            preferences[Keys.ShowDuration] = updated.showDurationMillis.coerceAtLeast(0)
            preferences[Keys.RepeatStep] = updated.repeatStepPercent.coerceIn(1, 50)
            preferences[Keys.WiredPreset] = updated.wiredPresetId
            preferences[Keys.WirelessPreset] = updated.wirelessPresetId
            preferences[Keys.FullPreset] = updated.fullPresetId
            preferences[Keys.DimWhenFaceUp] = updated.dimWhenFaceUp
        }
    }

    private fun Preferences.toSettings(): GlyphSettings {
        val defaults = GlyphSettings.Default
        val modeName = this[Keys.MeterModeName]

        return GlyphSettings(
            enabled = this[Keys.Enabled] ?: defaults.enabled,
            meterMode = MeterMode.entries.firstOrNull { it.name == modeName } ?: defaults.meterMode,
            brightness = this[Keys.Brightness] ?: defaults.brightness,
            showDurationMillis = this[Keys.ShowDuration] ?: defaults.showDurationMillis,
            repeatStepPercent = this[Keys.RepeatStep] ?: defaults.repeatStepPercent,
            wiredPresetId = this[Keys.WiredPreset] ?: defaults.wiredPresetId,
            wirelessPresetId = this[Keys.WirelessPreset] ?: defaults.wirelessPresetId,
            fullPresetId = this[Keys.FullPreset] ?: defaults.fullPresetId,
            dimWhenFaceUp = this[Keys.DimWhenFaceUp] ?: defaults.dimWhenFaceUp,
        )
    }
}
