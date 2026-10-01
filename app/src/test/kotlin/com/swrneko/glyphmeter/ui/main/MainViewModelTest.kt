package com.swrneko.glyphmeter.ui.main

import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.charging.FakeChargingStateSource
import com.swrneko.glyphmeter.hardware.FakeGlyphDisplay
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.Light
import com.swrneko.glyphmeter.service.MeterServiceController
import com.swrneko.glyphmeter.settings.FakeSettingsRepository
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MainViewModelTest {

    private class RecordingController : MeterServiceController {
        var starts = 0
        var stops = 0
        override fun start() { starts++ }
        override fun stop() { stops++ }
    }

    private val layout = DeviceLayouts.PHONE_3A
    private val settings = FakeSettingsRepository()
    private val charging = FakeChargingStateSource()
    private val display = FakeGlyphDisplay()
    private val controller = RecordingController()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(layout: com.swrneko.glyphmeter.model.DeviceLayout? = this.layout) = MainViewModel(
        settingsRepository = settings,
        chargingSource = charging,
        display = display,
        accessManager = GlyphAccessManager(emptyList(), { true }, { false }),
        layout = layout,
        serviceController = controller,
    )

    @Test
    fun showing_the_screen_does_not_start_the_service() = runTest(UnconfinedTestDispatcher()) {
        charging.emit(isCharging = true, level = 0.4f)
        val vm = viewModel()

        backgroundScope.launch { vm.state.collect {} }

        assertEquals(0, controller.starts)
        assertEquals(0, controller.stops)
    }

    @Test
    fun the_switch_starts_and_stops_the_service_and_stores_the_choice() = runTest(UnconfinedTestDispatcher()) {
        charging.emit(isCharging = true, level = 0.4f)
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        vm.onEnabledChange(false)
        assertEquals(1, controller.stops)
        assertFalse(settings.settings.first().enabled)

        vm.onEnabledChange(true)
        assertEquals(1, controller.starts)
        assertEquals(true, settings.settings.first().enabled)
    }

    @Test
    fun choosing_a_mode_is_stored() = runTest(UnconfinedTestDispatcher()) {
        charging.emit(isCharging = true, level = 0.4f)
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        vm.onModeChange(MeterMode.ALWAYS_ON)

        assertEquals(MeterMode.ALWAYS_ON, settings.settings.first().meterMode)
    }

    @Test
    fun the_idle_preview_shows_the_current_battery_level() = runTest(UnconfinedTestDispatcher()) {
        charging.emit(isCharging = true, level = 0.63f)
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(MeterRenderer.smooth(0.63f, layout, GlyphSettings.Default.brightness), vm.state.value?.previewFrame)
    }

    @Test
    fun playing_the_animation_replaces_the_idle_frame() = runTest(UnconfinedTestDispatcher()) {
        charging.emit(isCharging = true, level = 0.63f)
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val idle = vm.state.value?.previewFrame

        vm.onPlayPreview()

        assertNotNull(vm.state.value?.previewFrame)
        assertNotEquals(idle, vm.state.value?.previewFrame)
    }

    @Test
    fun a_phone_without_a_known_layout_has_no_preview() = runTest(UnconfinedTestDispatcher()) {
        charging.emit(isCharging = true, level = 0.5f)
        val vm = viewModel(layout = null)
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(null, vm.state.value?.previewFrame)
        assertEquals(Light.MAX, vm.state.value?.settings?.brightness)
    }
}
