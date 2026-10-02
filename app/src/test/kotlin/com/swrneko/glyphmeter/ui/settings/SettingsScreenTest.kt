package com.swrneko.glyphmeter.ui.settings

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import com.swrneko.glyphmeter.animation.AnimationParams
import com.swrneko.glyphmeter.model.Light
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.math.roundToInt

/** Renders the real [SettingsScreen] under Robolectric and asserts through UI nodes. */
@RunWith(RobolectricTestRunner::class)
class SettingsScreenTest {

    @get:Rule val compose = createComposeRule()

    private fun show(
        settings: GlyphSettings = GlyphSettings.Default,
        onBrightnessChange: (Int) -> Unit = {},
        onShowDurationChange: (Long) -> Unit = {},
        onRepeatStepChange: (Int) -> Unit = {},
        onPresetSelected: (PresetSlot, String) -> Unit = { _, _ -> },
        onPreviewPreset: (String) -> Unit = {},
        onDimWhenFaceUpChange: (Boolean) -> Unit = {},
        onOpenAnimation: (String) -> Unit = {},
        glyphPreviewAvailable: Boolean = true,
    ) {
        compose.setContent {
            SettingsScreen(
                state = SettingsUiState(
                    settings = settings,
                    layout = null,
                    previewFrame = null,
                    glyphPreviewAvailable = glyphPreviewAvailable,
                ),
                onBack = {},
                onBrightnessChange = onBrightnessChange,
                onShowDurationChange = onShowDurationChange,
                onRepeatStepChange = onRepeatStepChange,
                onPresetSelected = onPresetSelected,
                onPreviewPreset = onPreviewPreset,
                onDimWhenFaceUpChange = onDimWhenFaceUpChange,
                onOpenAnimation = onOpenAnimation,
            )
        }
    }

    @Test
    fun the_screen_explains_that_the_glyph_preview_needs_the_app_on() {
        show(glyphPreviewAvailable = false)

        compose.onNodeWithTag("glyph_preview_unavailable").performScrollTo().assertExists()
    }

    @Test
    fun the_screen_stays_quiet_when_the_glyph_preview_is_available() {
        show(glyphPreviewAvailable = true)

        compose.onNodeWithTag("glyph_preview_unavailable").assertDoesNotExist()
    }

    @Test
    fun duration_and_repeat_step_are_unavailable_in_always_on_mode() {
        show(GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON))

        compose.onNodeWithTag("show_duration_slider").performScrollTo().assertIsNotEnabled()
        compose.onNodeWithTag("repeat_step_slider").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun duration_and_repeat_step_are_available_in_on_event_mode() {
        show(GlyphSettings.Default.copy(meterMode = MeterMode.ON_EVENT))

        compose.onNodeWithTag("show_duration_slider").performScrollTo().assertIsEnabled()
        compose.onNodeWithTag("repeat_step_slider").performScrollTo().assertIsEnabled()
    }

    @Test
    fun changing_the_duration_reports_milliseconds() {
        var reported: Long? = null
        show(onShowDurationChange = { reported = it })

        compose.onNodeWithTag("show_duration_slider").performScrollTo()
            .performSemanticsAction(SemanticsActions.SetProgress) { it(12f) }

        assertEquals(12_000L, reported)
    }

    @Test
    fun the_brightness_slider_cannot_go_below_the_visibility_threshold() {
        val slider = compose.also { show(GlyphSettings.Default.copy(brightness = 2000)) }
            .onNodeWithTag("brightness_slider")

        val range = slider.fetchSemanticsNode().config[SemanticsProperties.ProgressBarRangeInfo].range
        assertEquals(Light.MIN_VISIBLE.toFloat(), range.start, 0f)
        assertEquals(Light.MAX.toFloat(), range.endInclusive, 0f)
    }

    @Test
    fun dragging_the_brightness_slider_to_zero_reports_the_visibility_threshold() {
        var reported: Int? = null
        show(GlyphSettings.Default.copy(brightness = 2000), onBrightnessChange = { reported = it })

        compose.onNodeWithTag("brightness_slider")
            .performSemanticsAction(SemanticsActions.SetProgress) { it(0f) }

        assertEquals(Light.MIN_VISIBLE, reported)
    }

    @Test
    fun a_slider_reports_only_when_the_gesture_ends() {
        val reported = mutableListOf<Int>()
        show(GlyphSettings.Default.copy(brightness = 2000), onBrightnessChange = { reported += it })

        compose.onNodeWithTag("brightness_slider").performTouchInput {
            down(center)
            moveBy(Offset(40f, 0f))
            moveBy(Offset(40f, 0f))
        }
        compose.waitForIdle()
        assertEquals(emptyList<Int>(), reported)

        compose.onNodeWithTag("brightness_slider").performTouchInput { up() }
        compose.waitForIdle()
        assertEquals(1, reported.size)
        assertTrue(reported.single() > 2000)
    }

    @Test
    fun the_label_follows_the_slider_during_a_drag_before_anything_is_stored() {
        val reported = mutableListOf<Int>()
        show(GlyphSettings.Default.copy(brightness = Light.MAX), onBrightnessChange = { reported += it })
        compose.onNodeWithText("100%").assertExists()

        compose.onNodeWithTag("brightness_slider").performTouchInput {
            down(center)
            moveBy(Offset(-40f, 0f))
            moveBy(Offset(-40f, 0f))
        }
        compose.waitForIdle()

        assertEquals(emptyList<Int>(), reported)
        val current = compose.onNodeWithTag("brightness_slider").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo].current
        assertTrue("current=$current", current < Light.MAX)
        compose.onNodeWithText("100%").assertDoesNotExist()
        compose.onNodeWithText("${current.roundToInt() * 100 / Light.MAX}%").assertExists()
    }

    @Test
    fun a_stored_brightness_of_zero_is_shown_at_the_threshold() {
        show(GlyphSettings.Default.copy(brightness = 0))

        val info = compose.onNodeWithTag("brightness_slider").fetchSemanticsNode()
            .config[SemanticsProperties.ProgressBarRangeInfo]
        assertEquals(Light.MIN_VISIBLE.toFloat(), info.current, 0f)
    }

    @Test
    fun each_slot_marks_its_current_preset_and_reports_a_new_choice() {
        var slot: PresetSlot? = null
        var preset: String? = null
        show(
            GlyphSettings.Default,
            onPresetSelected = { s, p -> slot = s; preset = p },
        )

        compose.onNodeWithTag("preset_wired_fill_up").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("preset_wireless_wave").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("preset_full_flash").performScrollTo().assertIsSelected()

        compose.onNodeWithTag("preset_wireless_chase").performScrollTo().performClick()

        assertEquals(PresetSlot.WIRELESS, slot)
        assertEquals("chase", preset)
    }

    @Test
    fun every_selectable_preset_has_a_preview_button() {
        val previewed = mutableListOf<String>()
        show(onPreviewPreset = { previewed += it })

        for (id in listOf("fill_up", "wave", "breathe", "chase", "flash")) {
            compose.onNodeWithTag("preview_wired_$id").performScrollTo().performClick()
        }

        assertEquals(listOf("fill_up", "wave", "breathe", "chase", "flash"), previewed)
    }

    @Test
    fun the_reserved_fade_out_preset_is_not_offered() {
        show()

        compose.onNodeWithTag("preset_wired_fade_out").assertDoesNotExist()
    }

    @Test
    fun the_face_up_switch_shows_its_state_and_reports_changes() {
        var reported: Boolean? = null
        show(GlyphSettings.Default.copy(dimWhenFaceUp = true), onDimWhenFaceUpChange = { reported = it })

        compose.onNodeWithTag("face_up_switch").performScrollTo().assertIsOn().performClick()

        assertEquals(false, reported)
    }

    @Test
    fun tapping_an_animation_opens_its_settings() {
        var opened: String? = null
        show(onOpenAnimation = { opened = it })

        compose.onNodeWithTag("tune_chase").performScrollTo().performClick()

        assertEquals("chase", opened)
    }

    @Test
    fun a_tuned_animation_is_marked_as_customised() {
        show(
            GlyphSettings.Default.copy(
                animationParams = mapOf("wave" to AnimationParams(cycleMillis = 2_000)),
            ),
        )

        compose.onNodeWithTag("tune_wave").performScrollTo()
        compose.onNodeWithText("Customised").assertExists()
    }
}
