package com.swrneko.glyphmeter.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.swrneko.glyphmeter.animation.AnimationParams
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.FillStyle
import com.swrneko.glyphmeter.animation.FillTarget
import com.swrneko.glyphmeter.animation.SweepDirection
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

    /**
     * Keys of one animation's tuning. The cycle key marks that an entry exists at all; the other
     * keys are optional and read back as the default when absent or unreadable.
     */
    private class AnimationKeys(presetId: String) {
        val Cycle = longPreferencesKey("anim_${presetId}_cycle_ms")
        val Repeats = intPreferencesKey("anim_${presetId}_repeats")
        val Brightness = intPreferencesKey("anim_${presetId}_brightness")
        val Zones = stringSetPreferencesKey("anim_${presetId}_zones")
        val Direction = stringPreferencesKey("anim_${presetId}_direction")
        val Target = stringPreferencesKey("anim_${presetId}_fill_target")
        val Style = stringPreferencesKey("anim_${presetId}_fill_style")
    }

    /** Only the presets a user can pick are tunable; anything else in the map is not stored. */
    private val animationKeys: Map<String, AnimationKeys> =
        AnimationPresets.selectable.associate { it.id to AnimationKeys(it.id) }

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
            for ((presetId, keys) in animationKeys) {
                preferences.writeAnimation(keys, updated.animationParams[presetId])
            }
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
            animationParams = animationKeys.mapNotNull { (presetId, keys) ->
                readAnimation(keys)?.let { presetId to it }
            }.toMap(),
        )
    }

    private fun MutablePreferences.writeAnimation(keys: AnimationKeys, params: AnimationParams?) {
        if (params == null) {
            listOf(keys.Cycle, keys.Repeats, keys.Brightness, keys.Zones, keys.Direction, keys.Target, keys.Style)
                .forEach { remove(it) }
            return
        }
        val legal = params.normalized()
        this[keys.Cycle] = legal.cycleMillis
        this[keys.Repeats] = legal.repeats
        legal.brightness?.let { this[keys.Brightness] = it } ?: remove(keys.Brightness)
        legal.zoneIds?.let { this[keys.Zones] = it } ?: remove(keys.Zones)
        this[keys.Direction] = legal.direction.name
        this[keys.Target] = legal.fillTarget.name
        this[keys.Style] = legal.fillStyle.name
    }

    private fun Preferences.readAnimation(keys: AnimationKeys): AnimationParams? {
        val cycle = this[keys.Cycle] ?: return null
        val defaults = AnimationParams(cycleMillis = cycle)
        return AnimationParams(
            cycleMillis = cycle,
            repeats = this[keys.Repeats] ?: defaults.repeats,
            brightness = this[keys.Brightness],
            zoneIds = this[keys.Zones],
            direction = enumOrNull<SweepDirection>(this[keys.Direction]) ?: defaults.direction,
            fillTarget = enumOrNull<FillTarget>(this[keys.Target]) ?: defaults.fillTarget,
            fillStyle = enumOrNull<FillStyle>(this[keys.Style]) ?: defaults.fillStyle,
        ).normalized()
    }

    private inline fun <reified E : Enum<E>> enumOrNull(name: String?): E? =
        enumValues<E>().firstOrNull { it.name == name }
}
