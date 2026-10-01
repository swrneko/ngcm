package com.swrneko.glyphmeter.ui.settings

import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.orchestration.PreviewRequestBus
import com.swrneko.glyphmeter.settings.FakeSettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private val settings = FakeSettingsRepository()
    private val bus = PreviewRequestBus()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(layout: DeviceLayout? = DeviceLayouts.PHONE_3A) =
        SettingsViewModel(settingsRepository = settings, layout = layout, previewRequestBus = bus)

    @Test
    fun previewing_a_preset_asks_the_service_to_play_exactly_that_preset() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val requested = mutableListOf<String>()
        backgroundScope.launch { bus.requests.collect { requested += it } }

        vm.onPreviewPreset(AnimationPresets.WAVE.id)
        vm.onPreviewPreset(AnimationPresets.FLASH.id)

        assertEquals(listOf(AnimationPresets.WAVE.id, AnimationPresets.FLASH.id), requested)
    }

    @Test
    fun previewing_a_preset_still_plays_it_on_the_screen() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        vm.onPreviewPreset(AnimationPresets.WAVE.id)

        assertNotNull(vm.state.value?.previewFrame)
    }

    @Test
    fun an_unknown_preset_sends_no_request() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }
        val requested = mutableListOf<String>()
        backgroundScope.launch { bus.requests.collect { requested += it } }

        vm.onPreviewPreset("no_such_preset")

        assertEquals(emptyList<String>(), requested)
    }

    @Test
    fun the_glyph_preview_is_unavailable_when_the_app_is_switched_off() = runTest(UnconfinedTestDispatcher()) {
        settings.update { it.copy(enabled = false) }
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(false, vm.state.value?.glyphPreviewAvailable)
    }

    @Test
    fun the_glyph_preview_is_available_when_the_app_is_on() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel()
        backgroundScope.launch { vm.state.collect {} }

        assertEquals(true, vm.state.value?.glyphPreviewAvailable)
    }
}
