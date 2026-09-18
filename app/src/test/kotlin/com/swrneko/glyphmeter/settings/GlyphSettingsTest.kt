package com.swrneko.glyphmeter.settings

import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.model.Light
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphSettingsTest {

    @Test
    fun `the defaults name presets that actually exist`() {
        val defaults = GlyphSettings.Default

        assertNotNull(AnimationPresets.byId(defaults.wiredPresetId))
        assertNotNull(AnimationPresets.byId(defaults.wirelessPresetId))
        assertNotNull(AnimationPresets.byId(defaults.fullPresetId))
    }

    @Test
    fun `the default brightness is legal and visible`() {
        val brightness = GlyphSettings.Default.brightness

        assertTrue(brightness in Light.MIN_VISIBLE..Light.MAX)
    }

    @Test
    fun `the default repeat step is a sane percentage`() {
        assertTrue(GlyphSettings.Default.repeatStepPercent in 1..50)
    }

    @Test
    fun `the fake repository starts at the defaults`() = runTest {
        val repository = FakeSettingsRepository()

        assertEquals(GlyphSettings.Default, repository.settings.first())
    }

    @Test
    fun `an update is visible to the next reader`() = runTest {
        val repository = FakeSettingsRepository()

        repository.update { it.copy(meterMode = MeterMode.ALWAYS_ON, brightness = 2000) }

        val stored = repository.settings.first()
        assertEquals(MeterMode.ALWAYS_ON, stored.meterMode)
        assertEquals(2000, stored.brightness)
    }
}
