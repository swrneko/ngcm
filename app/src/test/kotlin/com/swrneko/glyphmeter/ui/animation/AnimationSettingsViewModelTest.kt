package com.swrneko.glyphmeter.ui.animation

import androidx.lifecycle.SavedStateHandle
import com.swrneko.glyphmeter.animation.AnimationParams
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.FillTarget
import com.swrneko.glyphmeter.animation.SweepDirection
import com.swrneko.glyphmeter.animation.defaultParams
import com.swrneko.glyphmeter.charging.FakeChargingStateSource
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.orchestration.PreviewRequestBus
import com.swrneko.glyphmeter.settings.FakeSettingsRepository
import com.swrneko.glyphmeter.settings.GlyphSettings
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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnimationSettingsViewModelTest {

    private val settings = FakeSettingsRepository(GlyphSettings.Default.copy(brightness = 3_000))
    private val bus = PreviewRequestBus()
    private val charging = FakeChargingStateSource()

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(presetId: String) = AnimationSettingsViewModel(
        savedStateHandle = SavedStateHandle(mapOf(AnimationSettingsViewModel.PRESET_ID to presetId)),
        settingsRepository = settings,
        chargingSource = charging,
        layout = DeviceLayouts.PHONE_3A,
        previewRequestBus = bus,
    )

    private suspend fun stored(presetId: String): AnimationParams? = settings.settings.first().animationParams[presetId]

    @Test
    fun an_untouched_animation_shows_the_preset_as_shipped() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(AnimationPresets.WAVE.id)
        backgroundScope.launch { vm.state.collect {} }

        val state = vm.state.value!!
        assertEquals(AnimationPresets.WAVE.defaultParams, state.params)
        assertFalse(state.customised)
        assertEquals("brightness follows the meter", 3_000, state.brightness)
        assertEquals(setOf("C", "A", "B"), state.selectedZones)
    }

    @Test
    fun a_change_is_stored_for_that_animation_only() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(AnimationPresets.CHASE.id)
        backgroundScope.launch { vm.state.collect {} }

        vm.onCycleChange(2_200)
        vm.onRepeatsChange(3)
        vm.onDirectionChange(SweepDirection.COUNTER_CLOCKWISE)

        assertEquals(
            AnimationParams(cycleMillis = 2_200, repeats = 3, direction = SweepDirection.COUNTER_CLOCKWISE),
            stored(AnimationPresets.CHASE.id),
        )
        assertNull(stored(AnimationPresets.WAVE.id))
        assertTrue(vm.state.value!!.customised)
    }

    @Test
    fun brightness_once_moved_no_longer_follows_the_meter() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(AnimationPresets.FLASH.id)
        backgroundScope.launch { vm.state.collect {} }

        vm.onBrightnessChange(1_000)
        settings.update { it.copy(brightness = 4_000) }

        assertEquals(1_000, vm.state.value!!.brightness)
    }

    @Test
    fun the_last_zone_cannot_be_switched_off() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(AnimationPresets.BREATHE.id)
        backgroundScope.launch { vm.state.collect {} }

        vm.onZoneToggle("A")
        vm.onZoneToggle("B")
        assertEquals(setOf("C"), vm.state.value!!.selectedZones)

        vm.onZoneToggle("C")
        assertEquals(setOf("C"), vm.state.value!!.selectedZones)

        vm.onZoneToggle("A")
        assertEquals(setOf("C", "A"), stored(AnimationPresets.BREATHE.id)?.zoneIds)
    }

    @Test
    fun a_fill_to_the_charge_level_locks_the_zones_to_the_meter() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(AnimationPresets.FILL_UP.id)
        backgroundScope.launch { vm.state.collect {} }
        vm.onZoneToggle("A")
        assertTrue(vm.state.value!!.zonesEditable)

        vm.onFillTargetChange(FillTarget.CHARGE_LEVEL)

        val state = vm.state.value!!
        assertFalse(state.zonesEditable)
        assertEquals(setOf("C"), state.selectedZones)
    }

    @Test
    fun reset_brings_the_preset_back_as_shipped() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(AnimationPresets.WAVE.id)
        backgroundScope.launch { vm.state.collect {} }
        vm.onCycleChange(2_000)

        vm.onReset()

        assertNull(stored(AnimationPresets.WAVE.id))
        assertEquals(AnimationPresets.WAVE.defaultParams, vm.state.value!!.params)
    }

    @Test
    fun preview_plays_on_screen_and_asks_the_service_for_the_same_animation() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(AnimationPresets.BREATHE.id)
        backgroundScope.launch { vm.state.collect {} }
        val requested = mutableListOf<String>()
        backgroundScope.launch { bus.requests.collect { requested += it } }

        vm.onPreview()

        assertEquals(listOf(AnimationPresets.BREATHE.id), requested)
        assertNotNull(vm.state.value!!.previewFrame)
    }

    @Test
    fun the_unplug_fade_and_unknown_ids_are_not_tunable() {
        for (id in listOf(AnimationPresets.FADE_OUT.id, "no_such_preset")) {
            assertNull(id, viewModel(id).preset)
        }
    }

    @Test
    fun touching_a_control_without_changing_it_keeps_the_animation_untouched() = runTest(UnconfinedTestDispatcher()) {
        val vm = viewModel(AnimationPresets.WAVE.id)
        backgroundScope.launch { vm.state.collect {} }

        vm.onBrightnessChange(3_000)
        vm.onCycleChange(AnimationPresets.WAVE.durationMillis)

        assertNull(stored(AnimationPresets.WAVE.id))
        assertFalse(vm.state.value!!.customised)
    }
}
