package com.swrneko.glyphmeter.orchestration

import app.cash.turbine.test
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.PresetFrameSource
import com.swrneko.glyphmeter.charging.FakeChargingStateSource
import com.swrneko.glyphmeter.charging.PowerSource
import com.swrneko.glyphmeter.hardware.FakeGlyphDisplay
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.orientation.FakeOrientationSource
import com.swrneko.glyphmeter.orientation.OrientationSource
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
        orientation: OrientationSource = OrientationSource.NeverFaceUp,
        rearmAccess: suspend () -> Unit = {},
    ) = GlyphOrchestrator(
        display = display,
        chargingSource = charging,
        settingsRepository = settings,
        layout = layout,
        orientationSource = orientation,
        rearmAccess = rearmAccess,
        reconnectDelaysMillis = reconnectDelays,
        stableSessionMillis = stableSession,
        frameIntervalMillis = 16,
    )

    private val reconnectDelays = listOf(100L, 200L, 400L)
    private val stableSession = 1_000L

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

    @Test
    fun `access is re armed before the first connect`() = runTest {
        val display = FakeGlyphDisplay()
        val connectsSeenByRearm = mutableListOf<Int>()
        val job = launch {
            orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()) {
                connectsSeenByRearm += display.connectCount
            }.run()
        }
        runCurrent()

        assertEquals("re-arm must run once, before any connect", listOf(0), connectsSeenByRearm)
        assertEquals(1, display.connectCount)

        job.cancelAndJoin()
    }

    @Test
    fun `a failed re arm does not stop the orchestrator from connecting`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch {
            orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()) {
                error("secure settings unavailable")
            }.run()
        }
        runCurrent()

        assertTrue(display.isConnected)

        job.cancelAndJoin()
    }

    @Test
    fun `every plug in re arms access`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        var rearms = 0
        val job = launch {
            orchestrator(display, charging, FakeSettingsRepository()) { rearms++ }.run()
        }
        runCurrent()
        val afterConnect = rearms

        charging.emit(isCharging = true, level = 0.5f)
        advanceTimeBy(3_000)
        charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE)
        advanceTimeBy(3_000)
        charging.emit(isCharging = true, level = 0.5f)
        advanceTimeBy(3_000)
        runCurrent()

        assertEquals("one re-arm per plug-in", afterConnect + 2, rearms)

        job.cancelAndJoin()
    }

    @Test
    fun `a lost session is reconnected`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch { orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()).run() }
        runCurrent()

        display.loseConnection()
        advanceTimeBy(reconnectDelays.first() + 1)
        runCurrent()

        assertTrue("the display must be connected again", display.isConnected)
        assertEquals(2, display.connectCount)
        assertTrue("the orchestrator must keep running", job.isActive)

        job.cancelAndJoin()
    }

    @Test
    fun `access is re armed before every reconnect attempt`() = runTest {
        val display = FakeGlyphDisplay()
        val connectsSeenByRearm = mutableListOf<Int>()
        val job = launch {
            orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()) {
                connectsSeenByRearm += display.connectCount
            }.run()
        }
        runCurrent()

        display.connectResult = Result.failure(IllegalStateException("register rejected"))
        display.loseConnection()
        advanceTimeBy(reconnectDelays.sum() + 1)
        runCurrent()

        assertEquals("one re-arm before each of the connects", listOf(0, 1, 2, 3), connectsSeenByRearm)

        job.cancelAndJoin()
    }

    @Test
    fun `reconnect attempts wait longer each time`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch { orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()).run() }
        runCurrent()
        display.connectResult = Result.failure(IllegalStateException("register rejected"))

        display.loseConnection()
        advanceTimeBy(99)
        runCurrent()
        assertEquals("no attempt before the first pause", 1, display.connectCount)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(2, display.connectCount)

        advanceTimeBy(198)
        runCurrent()
        assertEquals("the second pause is longer", 2, display.connectCount)

        advanceTimeBy(2)
        runCurrent()
        assertEquals(3, display.connectCount)

        job.cancelAndJoin()
    }

    @Test
    fun `reconnecting gives up after a bounded number of attempts and the run ends`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch { orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()).run() }
        runCurrent()
        display.connectResult = Result.failure(IllegalStateException("register rejected"))

        display.loseConnection()
        advanceTimeBy(60_000)
        runCurrent()

        assertEquals("one initial connect plus one per pause", 1 + reconnectDelays.size, display.connectCount)
        assertTrue("run() must return once it has given up", job.isCompleted)
    }

    @Test
    fun `a reconnect that succeeds on a later attempt keeps the orchestrator running`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch { orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()).run() }
        runCurrent()
        display.connectResult = Result.failure(IllegalStateException("register rejected"))

        display.loseConnection()
        advanceTimeBy(reconnectDelays[0] + 1)
        runCurrent()
        display.connectResult = Result.success(Unit)
        advanceTimeBy(reconnectDelays[1])
        runCurrent()

        assertTrue(display.isConnected)
        assertTrue(job.isActive)

        job.cancelAndJoin()
    }

    @Test
    fun `after a reconnect the always on meter is drawn again`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))
        val job = launch { orchestrator(display, charging, settings).run() }
        runCurrent()
        charging.emit(isCharging = true, level = 0.5f)
        advanceTimeBy(5_000)
        runCurrent()

        display.loseConnection()
        display.clearRendered()
        advanceTimeBy(reconnectDelays.first() + 1_000)
        runCurrent()

        assertTrue("the held meter must come back after reconnecting", display.rendered.isNotEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `every new loss gets a fresh budget of attempts`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch { orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()).run() }
        runCurrent()

        repeat(reconnectDelays.size + 2) {
            display.loseConnection()
            advanceTimeBy(reconnectDelays.first() + stableSession + 1)
            runCurrent()
        }

        assertTrue(display.isConnected)
        assertTrue(job.isActive)

        job.cancelAndJoin()
    }

    @Test
    fun `a session that keeps dropping right after reconnecting still runs out of attempts`() = runTest {
        val display = FakeGlyphDisplay()
        val job = launch { orchestrator(display, FakeChargingStateSource(), FakeSettingsRepository()).run() }
        runCurrent()

        repeat(reconnectDelays.size + 1) {
            display.loseConnection()
            advanceTimeBy(reconnectDelays.last() + 1)
            runCurrent()
        }

        assertTrue("a flapping service must not keep the orchestrator alive forever", job.isCompleted)
    }

    /** The frames the full-charge preset draws, sampled the way the orchestrator plays them. */
    private fun fullPresetFrames(settings: GlyphSettings, count: Int) =
        PresetFrameSource(AnimationPresets.byId(settings.fullPresetId)!!, layout, settings.brightness)
            .let { source -> (0 until count).map { source.frameAt(it * 16L) } }

    @Test
    fun `reaching full charge while plugged in plays the full charge preset`() = runTest {
        for (mode in MeterMode.entries) {
            val display = FakeGlyphDisplay()
            val charging = FakeChargingStateSource()
            val initial = GlyphSettings.Default.copy(meterMode = mode, fullPresetId = AnimationPresets.CHASE.id)
            val job = launch { orchestrator(display, charging, FakeSettingsRepository(initial)).run() }
            runCurrent()
            charging.emit(isCharging = true, level = 0.99f)
            advanceTimeBy(20_000)
            runCurrent()
            display.clearRendered()

            charging.emit(isCharging = true, level = 1f)
            advanceTimeBy(200)
            runCurrent()

            assertEquals("$mode: the full-charge preset must play", fullPresetFrames(initial, 10), display.rendered.take(10))

            job.cancelAndJoin()
        }
    }

    @Test
    fun `the full charge preset plays only once per charging session`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val initial = GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON, fullPresetId = AnimationPresets.CHASE.id)
        val job = launch { orchestrator(display, charging, FakeSettingsRepository(initial)).run() }
        runCurrent()
        charging.emit(isCharging = true, level = 0.99f)
        advanceTimeBy(20_000)
        charging.emit(isCharging = true, level = 1f)
        advanceTimeBy(20_000)
        charging.emit(isCharging = true, level = 0.99f)
        advanceTimeBy(20_000)
        runCurrent()
        display.clearRendered()

        charging.emit(isCharging = true, level = 1f)
        advanceTimeBy(200)
        runCurrent()

        assertNotEquals(fullPresetFrames(initial, 10), display.rendered.take(10))

        job.cancelAndJoin()
    }

    @Test
    fun `a new charging session can play the full charge preset again`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val initial = GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON, fullPresetId = AnimationPresets.CHASE.id)
        val job = launch { orchestrator(display, charging, FakeSettingsRepository(initial)).run() }
        runCurrent()
        charging.emit(isCharging = true, level = 0.99f)
        advanceTimeBy(20_000)
        charging.emit(isCharging = true, level = 1f)
        advanceTimeBy(20_000)
        charging.emit(isCharging = false, level = 1f, source = PowerSource.NONE)
        advanceTimeBy(5_000)
        charging.emit(isCharging = true, level = 0.99f)
        advanceTimeBy(20_000)
        runCurrent()
        display.clearRendered()

        charging.emit(isCharging = true, level = 1f)
        advanceTimeBy(200)
        runCurrent()

        assertEquals(fullPresetFrames(initial, 10), display.rendered.take(10))

        job.cancelAndJoin()
    }

    private val alwaysOnDimming = GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON, dimWhenFaceUp = true)

    @Test
    fun `with dimming on a face up phone keeps the glyph dark while charging`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val orientation = FakeOrientationSource(faceUp = true)
        val job = launch {
            orchestrator(display, charging, FakeSettingsRepository(alwaysOnDimming), orientation = orientation).run()
        }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f)
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue("nothing may be drawn face up: ${display.rendered.size} frames", display.rendered.isEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `turning a lit phone face up darkens the glyph`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val orientation = FakeOrientationSource(faceUp = false)
        val job = launch {
            orchestrator(display, charging, FakeSettingsRepository(alwaysOnDimming), orientation = orientation).run()
        }
        runCurrent()
        charging.emit(isCharging = true, level = 0.5f)
        advanceTimeBy(5_000)
        runCurrent()
        val before = display.turnOffCount

        orientation.set(true)
        runCurrent()
        display.clearRendered()
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue("turning face up must switch the glyph off", display.turnOffCount > before)
        assertTrue("and the held meter must stop being redrawn", display.rendered.isEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `with dimming off a face up phone still shows the meter`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(alwaysOnDimming.copy(dimWhenFaceUp = false))
        val job = launch {
            orchestrator(display, charging, settings, orientation = FakeOrientationSource(faceUp = true)).run()
        }
        runCurrent()

        charging.emit(isCharging = true, level = 0.5f)
        advanceTimeBy(5_000)
        runCurrent()

        assertTrue(display.rendered.isNotEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `turning the phone back over brings the meter back`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val orientation = FakeOrientationSource(faceUp = true)
        val job = launch {
            orchestrator(display, charging, FakeSettingsRepository(alwaysOnDimming), orientation = orientation).run()
        }
        runCurrent()
        charging.emit(isCharging = true, level = 0.5f)
        advanceTimeBy(5_000)
        runCurrent()

        orientation.set(false)
        advanceTimeBy(5_000)
        runCurrent()

        val last = display.rendered.last()
        assertTrue("the meter must show the charge again", meter.take(10).all { last[it] > 0 })

        job.cancelAndJoin()
    }

    @Test
    fun `unplugging a dimmed phone does not light it for the fade out`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val job = launch {
            orchestrator(
                display, charging, FakeSettingsRepository(alwaysOnDimming),
                orientation = FakeOrientationSource(faceUp = true),
            ).run()
        }
        runCurrent()
        charging.emit(isCharging = true, level = 0.5f)
        advanceTimeBy(5_000)
        runCurrent()

        charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE)
        advanceTimeBy(3_000)
        runCurrent()

        assertTrue(display.rendered.isEmpty())

        job.cancelAndJoin()
    }

    @Test
    fun `the orientation sensor runs only while charging with dimming on`() = runTest {
        val display = FakeGlyphDisplay()
        val charging = FakeChargingStateSource()
        val settings = FakeSettingsRepository(alwaysOnDimming)
        val orientation = FakeOrientationSource()
        val job = launch { orchestrator(display, charging, settings, orientation = orientation).run() }
        runCurrent()

        charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE)
        runCurrent()
        assertEquals("not charging: sensor off", 0, orientation.listenerCount)

        charging.emit(isCharging = true, level = 0.5f)
        runCurrent()
        assertEquals("charging: sensor on", 1, orientation.listenerCount)

        settings.update { it.copy(dimWhenFaceUp = false) }
        runCurrent()
        assertEquals("dimming off: sensor off", 0, orientation.listenerCount)

        settings.update { it.copy(dimWhenFaceUp = true) }
        runCurrent()
        charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE)
        runCurrent()
        assertEquals("unplugged: sensor off", 0, orientation.listenerCount)

        job.cancelAndJoin()
    }
}
