package com.swrneko.glyphmeter.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.swrneko.glyphmeter.animation.AnimationParams
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.FillStyle
import com.swrneko.glyphmeter.animation.FillTarget
import com.swrneko.glyphmeter.animation.SweepDirection
import com.swrneko.glyphmeter.animation.defaultParams
import com.swrneko.glyphmeter.model.Light
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Exercises [DataStoreSettingsRepository] through its public [SettingsRepository] interface
 * (`settings`, `update`), backed by a real [DataStore] rooted in a temporary file per test.
 * Nothing here is mocked or duplicated from the class under test.
 */
class DataStoreSettingsRepositoryTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private var nextFileId = 0

    /** Every call gets its own backing file: the [preferencesDataStore] delegate this mirrors
     * cannot be recreated for the same file within one process, and a JUnit [TemporaryFolder]
     * only clears itself between tests, not between calls made within one test. */
    private fun newDataStore(): DataStore<Preferences> {
        val file = File(tempFolder.root, "settings_${nextFileId++}.preferences_pb")
        return PreferenceDataStoreFactory.create { file }
    }

    private fun newRepository(dataStore: DataStore<Preferences> = newDataStore()) =
        DataStoreSettingsRepository(dataStore)

    @Test
    fun `brightness above max is clamped to max on write`() = runTest {
        val repository = newRepository()

        repository.update { it.copy(brightness = Light.MAX + 1000) }

        assertEquals(Light.MAX, repository.settings.first().brightness)
    }

    @Test
    fun `brightness below zero is clamped to zero, not to the visibility floor`() = runTest {
        val repository = newRepository()

        repository.update { it.copy(brightness = -100) }

        // Zero at the storage layer means "segment off". Clamping user input to the
        // visible minimum is the settings screen's job, not the repository's.
        assertEquals(0, repository.settings.first().brightness)
    }

    @Test
    fun `negative show duration is clamped to zero on write`() = runTest {
        val repository = newRepository()

        repository.update { it.copy(showDurationMillis = -5000) }

        assertEquals(0L, repository.settings.first().showDurationMillis)
    }

    @Test
    fun `repeat step is clamped to the legal range on both sides`() = runTest {
        val belowRange = newRepository()
        val aboveRange = newRepository()

        belowRange.update { it.copy(repeatStepPercent = 0) }
        aboveRange.update { it.copy(repeatStepPercent = 100) }

        assertEquals(1, belowRange.settings.first().repeatStepPercent)
        assertEquals(50, aboveRange.settings.first().repeatStepPercent)
    }

    @Test
    fun `written values are read back without corruption`() = runTest {
        val repository = newRepository()
        val original = GlyphSettings(
            enabled = false,
            meterMode = MeterMode.ALWAYS_ON,
            brightness = 2500,
            showDurationMillis = 3000,
            repeatStepPercent = 10,
            wiredPresetId = "custom_wired",
            wirelessPresetId = "custom_wireless",
            fullPresetId = "custom_full",
            dimWhenFaceUp = true,
            animationParams = mapOf(
                "fill_up" to AnimationParams(
                    cycleMillis = 2_500,
                    repeats = 3,
                    brightness = 1_500,
                    zoneIds = setOf("C", "A"),
                    fillTarget = FillTarget.CHARGE_LEVEL,
                    fillStyle = FillStyle.STEPPED,
                ),
                "chase" to AnimationParams(cycleMillis = 800, direction = SweepDirection.COUNTER_CLOCKWISE),
            ),
        )

        repository.update { original }

        assertEquals(original, repository.settings.first())
    }

    @Test
    fun `an empty store reads back as the default settings`() = runTest {
        val repository = newRepository()

        assertEquals(GlyphSettings.Default, repository.settings.first())
    }

    @Test
    fun `an unknown persisted meter mode name reads back as the default mode`() = runTest {
        // Writing an invalid mode through update() is impossible by construction, since it only
        // ever accepts a MeterMode enum value. A future rename of an enum entry would still
        // leave this exact situation on an existing user's disk, so the corruption has to be
        // injected below the repository's own API, directly into the store it reads from.
        val dataStore = newDataStore()
        val meterModeKey = stringPreferencesKey("meter_mode")
        dataStore.edit { preferences -> preferences[meterModeKey] = "NO_SUCH_MODE" }

        val repository = newRepository(dataStore)

        assertEquals(GlyphSettings.Default.meterMode, repository.settings.first().meterMode)
    }

    @Test
    fun `animation numbers out of range are clamped on write`() = runTest {
        val repository = newRepository()

        repository.update {
            it.copy(animationParams = mapOf("wave" to AnimationParams(cycleMillis = 1, repeats = 50, brightness = -3)))
        }

        val stored = repository.settings.first().animationParams.getValue("wave")
        assertEquals(AnimationParams.MIN_CYCLE_MILLIS, stored.cycleMillis)
        assertEquals(AnimationParams.MAX_REPEATS, stored.repeats)
        assertEquals(0, stored.brightness)
    }

    @Test
    fun `dropping an animation's entry resets it to the preset as shipped`() = runTest {
        val repository = newRepository()
        repository.update {
            it.copy(animationParams = mapOf("flash" to AnimationParams(cycleMillis = 2_000, brightness = 900)))
        }

        repository.update { it.copy(animationParams = it.animationParams - "flash") }

        val settings = repository.settings.first()
        assertEquals(emptyMap<String, AnimationParams>(), settings.animationParams)
        assertEquals(AnimationPresets.FLASH.defaultParams, settings.paramsFor(AnimationPresets.FLASH))
    }

    @Test
    fun `an unknown persisted direction reads back as clockwise`() = runTest {
        val dataStore = newDataStore()
        dataStore.edit { preferences ->
            preferences[androidx.datastore.preferences.core.longPreferencesKey("anim_wave_cycle_ms")] = 900L
            preferences[stringPreferencesKey("anim_wave_direction")] = "SIDEWAYS"
        }

        val stored = newRepository(dataStore).settings.first().animationParams.getValue("wave")

        assertEquals(AnimationParams(cycleMillis = 900), stored)
    }
}
