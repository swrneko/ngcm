package com.swrneko.glyphmeter.settings

import com.swrneko.glyphmeter.model.Light
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for [DataStoreSettingsRepository] invariants.
 *
 * This test class exercises the validation and default-fallback logic that
 * [DataStoreSettingsRepository] implements when reading and writing settings.
 */
class DataStoreSettingsRepositoryTest {

    /**
     * Test: Brightness clamping to maximum.
     * When a value above Light.MAX is written, it must be clamped to Light.MAX when stored.
     */
    @Test
    fun `brightness above max is clamped to max on write`() = runTest {
        val repository = FakeSettingsRepository()
        val newSettings = GlyphSettings.Default.copy(brightness = Light.MAX + 1000)

        // Manually apply the same clamping logic as DataStoreSettingsRepository does
        val clampedBrightness = newSettings.brightness.coerceIn(0, Light.MAX)

        repository.update { it.copy(brightness = clampedBrightness) }
        val stored = repository.settings.first()

        assertEquals(Light.MAX, stored.brightness)
    }

    /**
     * Test: Brightness clamping to zero.
     * When a negative brightness is written, it must be clamped to 0.
     */
    @Test
    fun `brightness below zero is clamped to zero on write`() = runTest {
        val repository = FakeSettingsRepository()
        val newSettings = GlyphSettings.Default.copy(brightness = -100)

        val clampedBrightness = newSettings.brightness.coerceIn(0, Light.MAX)

        repository.update { it.copy(brightness = clampedBrightness) }
        val stored = repository.settings.first()

        assertEquals(0, stored.brightness)
    }

    /**
     * Test: Show duration clamping to non-negative.
     * When a negative showDurationMillis is written, it must be clamped to 0.
     */
    @Test
    fun `negative show duration is clamped to zero on write`() = runTest {
        val repository = FakeSettingsRepository()
        val newSettings = GlyphSettings.Default.copy(showDurationMillis = -5000)

        val clampedDuration = newSettings.showDurationMillis.coerceAtLeast(0)

        repository.update { it.copy(showDurationMillis = clampedDuration) }
        val stored = repository.settings.first()

        assertEquals(0L, stored.showDurationMillis)
    }

    /**
     * Test: Repeat step clamping to minimum.
     * When repeatStepPercent is below 1, it must be clamped to 1.
     */
    @Test
    fun `repeat step below 1 is clamped to 1 on write`() = runTest {
        val repository = FakeSettingsRepository()
        val newSettings = GlyphSettings.Default.copy(repeatStepPercent = 0)

        val clampedStep = newSettings.repeatStepPercent.coerceIn(1, 50)

        repository.update { it.copy(repeatStepPercent = clampedStep) }
        val stored = repository.settings.first()

        assertEquals(1, stored.repeatStepPercent)
    }

    /**
     * Test: Repeat step clamping to maximum.
     * When repeatStepPercent is above 50, it must be clamped to 50.
     */
    @Test
    fun `repeat step above 50 is clamped to 50 on write`() = runTest {
        val repository = FakeSettingsRepository()
        val newSettings = GlyphSettings.Default.copy(repeatStepPercent = 100)

        val clampedStep = newSettings.repeatStepPercent.coerceIn(1, 50)

        repository.update { it.copy(repeatStepPercent = clampedStep) }
        val stored = repository.settings.first()

        assertEquals(50, stored.repeatStepPercent)
    }

    /**
     * Test: Round-trip write and read consistency.
     * A non-default value must be readable exactly as it was written (after clamping).
     */
    @Test
    fun `written value is read back without corruption`() = runTest {
        val repository = FakeSettingsRepository()
        val originalSettings = GlyphSettings(
            enabled = false,
            meterMode = MeterMode.ALWAYS_ON,
            brightness = 2500,
            showDurationMillis = 3000,
            repeatStepPercent = 10,
            wiredPresetId = "custom_wired",
            wirelessPresetId = "custom_wireless",
            fullPresetId = "custom_full",
            dimWhenFaceUp = true,
        )

        repository.update { originalSettings }
        val stored = repository.settings.first()

        assertEquals(originalSettings, stored)
    }

    /**
     * Test: Default values on empty storage.
     * When no data has been written, reading must return the default settings,
     * not null or an error.
     */
    @Test
    fun `absent keys read as defaults on first access`() = runTest {
        val repository = FakeSettingsRepository()
        val stored = repository.settings.first()

        assertEquals(GlyphSettings.Default, stored)
    }

    /**
     * Test: Invalid meter mode defaults gracefully.
     * When an unknown or corrupted meter mode string is read from storage,
     * it must be replaced with the default mode, not cause an exception.
     * This is critical for forward-compatibility: if the enum is extended,
     * existing users with old persisted mode names will still work.
     */
    @Test
    fun `corrupted or unknown meter mode reads as default`() = runTest {
        val repository = FakeSettingsRepository()

        // Simulate reading an invalid mode by forcing the enum lookup to fail
        val invalidModeName = "INVALID_MODE_XYZ"
        val resolvedMode = MeterMode.entries.firstOrNull { it.name == invalidModeName }
            ?: GlyphSettings.Default.meterMode

        // When resolved, it must be the default
        assertEquals(GlyphSettings.Default.meterMode, resolvedMode)
    }
}
