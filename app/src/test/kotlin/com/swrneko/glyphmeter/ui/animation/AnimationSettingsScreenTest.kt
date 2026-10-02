package com.swrneko.glyphmeter.ui.animation

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import com.swrneko.glyphmeter.animation.AnimationParams
import com.swrneko.glyphmeter.animation.AnimationPreset
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.FillTarget
import com.swrneko.glyphmeter.animation.SweepDirection
import com.swrneko.glyphmeter.animation.defaultParams
import com.swrneko.glyphmeter.layout.DeviceLayouts
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Renders the real [AnimationSettingsScreen] under Robolectric and asserts through UI nodes. */
@RunWith(RobolectricTestRunner::class)
class AnimationSettingsScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun show(
        preset: AnimationPreset,
        params: AnimationParams = preset.defaultParams,
        selectedZones: Set<String> = setOf("C", "A", "B"),
        zonesEditable: Boolean = true,
        customised: Boolean = false,
        callbacks: AnimationSettingsCallbacks = AnimationSettingsCallbacks(),
    ) {
        compose.setContent {
            AnimationSettingsScreen(
                state = AnimationSettingsUiState(
                    preset = preset,
                    params = params,
                    brightness = 4_000,
                    zones = listOf("C", "A", "B"),
                    selectedZones = selectedZones,
                    zonesEditable = zonesEditable,
                    customised = customised,
                    layout = DeviceLayouts.PHONE_3A,
                    previewFrame = null,
                    glyphPreviewAvailable = true,
                ),
                onBack = {},
                callbacks = callbacks,
            )
        }
    }

    @Test
    fun direction_is_offered_only_for_sweeps() {
        show(AnimationPresets.WAVE)
        compose.onNodeWithTag("direction_counter").performScrollTo().assertExists()
        compose.onNodeWithTag("fill_target_level").assertDoesNotExist()
    }

    @Test
    fun the_fill_is_offered_only_for_fill_up() {
        show(AnimationPresets.FILL_UP, selectedZones = setOf("C"))
        compose.onNodeWithTag("fill_target_level").performScrollTo().assertExists()
        compose.onNodeWithTag("fill_style_stepped").performScrollTo().assertExists()
        compose.onNodeWithTag("direction_counter").assertDoesNotExist()
    }

    @Test
    fun picking_a_direction_reports_it() {
        var direction: SweepDirection? = null
        show(AnimationPresets.CHASE, callbacks = AnimationSettingsCallbacks(onDirectionChange = { direction = it }))

        compose.onNodeWithTag("direction_counter").performScrollTo().performClick()

        assertEquals(SweepDirection.COUNTER_CLOCKWISE, direction)
    }

    @Test
    fun picking_a_fill_target_reports_it() {
        var target: FillTarget? = null
        show(AnimationPresets.FILL_UP, callbacks = AnimationSettingsCallbacks(onFillTargetChange = { target = it }))

        compose.onNodeWithTag("fill_target_level").performScrollTo().performClick()

        assertEquals(FillTarget.CHARGE_LEVEL, target)
    }

    @Test
    fun the_speed_slider_reports_milliseconds_in_tenths_of_a_second() {
        var cycle: Long? = null
        show(AnimationPresets.BREATHE, callbacks = AnimationSettingsCallbacks(onCycleChange = { cycle = it }))

        compose.onNodeWithTag("animation_speed_slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(2.0f) }

        assertEquals(2_000L, cycle)
    }

    @Test
    fun tapping_a_zone_chip_toggles_it() {
        var toggled: String? = null
        show(AnimationPresets.FLASH, selectedZones = setOf("C", "A"), callbacks = AnimationSettingsCallbacks(onZoneToggle = { toggled = it }))

        compose.onNodeWithTag("zone_A").performScrollTo().assertIsSelected().performClick()

        assertEquals("A", toggled)
    }

    @Test
    fun zone_chips_are_locked_for_a_fill_to_the_charge_level() {
        show(
            AnimationPresets.FILL_UP,
            params = AnimationPresets.FILL_UP.defaultParams.copy(fillTarget = FillTarget.CHARGE_LEVEL),
            selectedZones = setOf("C"),
            zonesEditable = false,
        )

        compose.onNodeWithTag("zone_A").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun reset_is_unavailable_for_an_untouched_animation() {
        show(AnimationPresets.WAVE, customised = false)

        compose.onNodeWithTag("animation_reset").assertIsNotEnabled()
    }

    @Test
    fun reset_is_available_after_a_change() {
        show(AnimationPresets.WAVE, customised = true)

        compose.onNodeWithTag("animation_reset").assertIsEnabled()
    }

    @Test
    fun play_asks_for_a_preview() {
        var played = false
        show(AnimationPresets.WAVE, callbacks = AnimationSettingsCallbacks(onPreview = { played = true }))

        compose.onNodeWithTag("animation_play").performClick()

        assertEquals(true, played)
    }
}
