package com.swrneko.glyphmeter.orchestration

import app.cash.turbine.test
import com.swrneko.glyphmeter.charging.FakeChargingStateSource
import com.swrneko.glyphmeter.charging.PowerSource
import com.swrneko.glyphmeter.hardware.FakeGlyphDisplay
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.settings.FakeSettingsRepository
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class GlyphOrchestratorTest {

    private val layout = DeviceLayouts.PHONE_3A
    private val meter = layout.meterZone.indices

    private fun orchestrator(
        display: FakeGlyphDisplay,
        charging: FakeChargingStateSource,
        settings: FakeSettingsRepository,
    ) = GlyphOrchestrator(
        display = display,
        chargingSource = charging,
        settingsRepository = settings,
        layout = layout,
        frameIntervalMillis = 16,
    )

    @Test
    fun `it connects to the display when it starts`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch { orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()).run() }
        runCurrent()

        assertTrue(display.isConnected)

        job.cancelAndJoin()
    }

    @Test
    fun `plugging in plays the wired preset`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ON_EVENT))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(300)
        runCurrent()

        assertTrue("expected frames during the plug-in animation", display.rendered.isNotEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `in on event mode the meter goes dark after the show duration`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(meterMode = MeterMode.ON_EVENT, showDurationMillis = 2_000),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(30_000)
        runCurrent()

        assertTrue("meter should have been turned off", display.turnOffCount >= 1)

        job.cancelAndJoin()
    }

    @Test
    fun `in always on mode the meter keeps being redrawn`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(10_000)
        runCurrent()
        display.clearRendered()
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue("always-on mode must keep drawing", display.rendered.isNotEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `unplugging turns the glyph off`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(3_000)
        runCurrent()

        val before = display.turnOffCount
        charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE)
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue("unplugging must end in darkness", display.turnOffCount > before)

        job.cancelAndJoin()
    }

    @Test
    fun `a disabled app draws nothing`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(enabled = false))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue(display.rendered.isEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `in on event mode a small charge gain does not retrigger the meter`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(
                meterMode = MeterMode.ON_EVENT,
                showDurationMillis = 1_000,
                repeatStepPercent = 5,
            ),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.50f, source = PowerSource.WIRED)
        advanceTimeBy(20_000)
        runCurrent()
        display.clearRendered()

        // Observe inside the show window: a retriggered meter would be on screen here,
        // while after the whole show cycle turnOff() would wipe the evidence.
        charging.emit(isCharging = true, level = 0.52f, source = PowerSource.WIRED)
        advanceTimeBy(800)
        runCurrent()

        assertTrue("a two percent gain must stay quiet", display.rendered.isEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `in on event mode a full step of charge retriggers the meter`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(
                meterMode = MeterMode.ON_EVENT,
                showDurationMillis = 1_000,
                repeatStepPercent = 5,
            ),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.50f, source = PowerSource.WIRED)
        advanceTimeBy(20_000)
        runCurrent()
        display.clearRendered()

        charging.emit(isCharging = true, level = 0.56f, source = PowerSource.WIRED)
        advanceTimeBy(1_000)
        runCurrent()

        assertTrue("a six percent gain must wake the meter", display.rendered.isNotEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `the last frame of a rise matches the new level`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 1f, source = PowerSource.WIRED)
        advanceTimeBy(10_000)
        runCurrent()

        val last = display.rendered.last()
        for (index in meter) {
            assertTrue("segment $index should be lit at full charge", last[index] > 0)
        }

        job.cancelAndJoin()
    }

    @Test
    fun `changing a setting while charging does not replay the plug in animation`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 1f, source = PowerSource.WIRED)
        advanceTimeBy(10_000)
        runCurrent()
        display.clearRendered()

        settings.update { it.copy(brightness = 2_000) }
        advanceTimeBy(100)
        runCurrent()

        // A replayed plug-in animation would start from a dark meter.
        val first = display.rendered.first()
        for (index in meter) {
            assertTrue("segment $index should stay lit", first[index] > 0)
        }

        job.cancelAndJoin()
    }

    @Test
    fun `unplugging during a long hold darkens the glyph at once`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(meterMode = MeterMode.ON_EVENT, showDurationMillis = 30_000),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(5_000)
        runCurrent()
        val before = display.turnOffCount

        charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE)
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue("glyph must go dark long before the 30 s hold ends", display.turnOffCount > before)

        job.cancelAndJoin()
    }

    @Test
    fun `disabling the app during a long hold darkens the glyph at once`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(meterMode = MeterMode.ON_EVENT, showDurationMillis = 30_000),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(5_000)
        runCurrent()
        val before = display.turnOffCount

        settings.update { it.copy(enabled = false) }
        advanceTimeBy(100)
        runCurrent()

        assertTrue("disabling must darken immediately", display.turnOffCount > before)

        job.cancelAndJoin()
    }

    @Test
    fun `a small gain during a hold does not cut the show short`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(
            GlyphSettings.Default.copy(
                meterMode = MeterMode.ON_EVENT,
                showDurationMillis = 2_000,
                repeatStepPercent = 5,
            ),
        )
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.50f, source = PowerSource.WIRED)
        advanceTimeBy(3_000)
        runCurrent()
        charging.emit(isCharging = true, level = 0.52f, source = PowerSource.WIRED)
        advanceTimeBy(10_000)
        runCurrent()

        assertTrue("the meter must still go dark afterwards", display.turnOffCount >= 1)

        job.cancelAndJoin()
    }

    @Test
    fun `replugging in the middle of the fade out still darkens the glyph`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        advanceTimeBy(3_000)
        runCurrent()
        charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE)
        advanceTimeBy(50)
        runCurrent()
        val before = display.turnOffCount

        charging.emit(isCharging = true, level = 0.5f, source = PowerSource.WIRED)
        runCurrent()

        assertTrue("an interrupted fade must still end in darkness", display.turnOffCount > before)

        job.cancelAndJoin()
    }

    @Test
    fun `wired and wireless power play different animations`() = runTest {
        suspend fun firstFrames(source: PowerSource): List<List<Int>> {
            val display = FakeGlyphDisplay()
            val charging = FakeChargingStateSource()
            val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ON_EVENT))
            val job = launch { orchestrator(display, charging, settings).run() }
            runCurrent()
            charging.emit(isCharging = true, level = 0.5f, source = source)
            advanceTimeBy(400)
            runCurrent()
            val frames = display.rendered.map { frame -> meter.map { frame[it] } }
            job.cancelAndJoin()
            return frames
        }

        val wired = firstFrames(PowerSource.WIRED)
        val wireless = firstFrames(PowerSource.WIRELESS)

        assertTrue(wired.isNotEmpty() && wireless.isNotEmpty())
        assertNotEquals(wired, wireless)
    }
}
