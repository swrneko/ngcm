package com.swrneko.glyphmeter.ui.main

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.swrneko.glyphmeter.access.GlyphAccessState
import com.swrneko.glyphmeter.hardware.GlyphFailure
import com.swrneko.glyphmeter.hardware.RenderCapability
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.Light
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Renders the real [MainScreen] under Robolectric and asserts through UI nodes. */
@RunWith(RobolectricTestRunner::class)
class MainScreenTest {

    @get:Rule val compose = createComposeRule()

    private val layout = DeviceLayouts.PHONE_3A

    private fun state(
        settings: GlyphSettings = GlyphSettings.Default,
        capability: RenderCapability = RenderCapability.PER_SEGMENT,
        access: GlyphAccessState = GlyphAccessState.WORKING,
        failure: GlyphFailure? = null,
    ) = MainUiState(
        settings = settings,
        level = 0.5f,
        capability = capability,
        failure = failure,
        access = access,
        layout = layout,
        previewFrame = MeterRenderer.smooth(0.5f, layout, Light.MAX),
    )

    private fun show(
        state: MainUiState,
        onEnabledChange: (Boolean) -> Unit = {},
        onModeChange: (MeterMode) -> Unit = {},
        onPlayPreview: () -> Unit = {},
        onOpenSettings: () -> Unit = {},
        onOpenOnboarding: () -> Unit = {},
        onRetryGlyph: () -> Unit = {},
    ) {
        compose.setContent {
            MainScreen(state, onEnabledChange, onModeChange, onPlayPreview, onOpenSettings, onOpenOnboarding, onRetryGlyph)
        }
    }

    @Test
    fun the_reduced_smoothness_warning_shows_when_the_hardware_layer_says_stepped_only() {
        show(state(capability = RenderCapability.STEPPED_ONLY))

        compose.onNodeWithTag("reduced_smoothness_warning").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun the_warning_is_hidden_while_capability_is_unknown() {
        show(state(capability = RenderCapability.UNKNOWN))

        compose.onNodeWithTag("reduced_smoothness_warning").assertDoesNotExist()
    }

    @Test
    fun the_warning_is_hidden_when_per_segment_brightness_works() {
        show(state(capability = RenderCapability.PER_SEGMENT))

        compose.onNodeWithTag("reduced_smoothness_warning").assertDoesNotExist()
    }

    // Inverted from the_warning_is_hidden_when_the_glyph_service_is_unavailable, which pinned the
    // silent failure the spec (7.4) forbids: an unavailable Glyph must be explained, not hidden.
    @Test
    fun a_warning_is_shown_when_the_glyph_service_is_unavailable() {
        show(state(capability = RenderCapability.UNAVAILABLE))

        compose.onNodeWithTag("glyph_failure_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun a_remembered_failure_is_shown_even_after_the_service_has_disconnected() {
        show(state(capability = RenderCapability.UNKNOWN, failure = GlyphFailure.REGISTRATION_REJECTED))

        compose.onNodeWithTag("glyph_failure_card").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun no_failure_card_while_the_glyph_works() {
        show(state(capability = RenderCapability.PER_SEGMENT, failure = null))

        compose.onNodeWithTag("glyph_failure_card").assertDoesNotExist()
    }

    @Test
    fun the_failure_card_offers_a_new_check() {
        var retried = false
        show(state(failure = GlyphFailure.SESSION_LOST), onRetryGlyph = { retried = true })

        compose.onNodeWithTag("glyph_failure_retry").performScrollTo().performClick()

        assertTrue(retried)
    }

    @Test
    fun the_switch_shows_the_current_state_and_reports_changes() {
        var reported: Boolean? = null
        show(state(settings = GlyphSettings.Default.copy(enabled = true)), onEnabledChange = { reported = it })

        compose.onNodeWithTag("enabled_switch").assertIsOn().performClick()

        assertEquals(false, reported)
    }

    @Test
    fun the_selected_mode_is_marked_and_choosing_the_other_reports_it() {
        var reported: MeterMode? = null
        show(
            state(settings = GlyphSettings.Default.copy(meterMode = MeterMode.ALWAYS_ON)),
            onModeChange = { reported = it },
        )

        compose.onNodeWithTag("mode_always_on").performScrollTo().assertIsSelected()
        compose.onNodeWithTag("mode_on_event").performScrollTo().performClick()

        assertEquals(MeterMode.ON_EVENT, reported)
    }

    @Test
    fun a_working_phone_has_no_link_to_onboarding() {
        show(state(access = GlyphAccessState.WORKING))

        compose.onNodeWithTag("access_status").performScrollTo().assertIsDisplayed()
        compose.onNodeWithTag("open_onboarding").assertDoesNotExist()
    }

    @Test
    fun a_phone_needing_setup_links_to_onboarding() {
        var opened = false
        show(state(access = GlyphAccessState.NEEDS_SETUP), onOpenOnboarding = { opened = true })

        compose.onNodeWithTag("open_onboarding").performScrollTo().performClick()

        assertTrue(opened)
    }

    @Test
    fun playing_the_animation_and_opening_settings_report_back() {
        var played = false
        var settingsOpened = false
        show(state(), onPlayPreview = { played = true }, onOpenSettings = { settingsOpened = true })

        compose.onNodeWithTag("play_preview").performClick()
        compose.onNodeWithTag("open_settings").performClick()

        assertTrue(played)
        assertTrue(settingsOpened)
    }

    @Test
    fun the_preview_is_drawn_for_a_known_layout() {
        show(state())

        compose.onNodeWithTag("glyph_preview").assertIsDisplayed()
    }

    @Test
    fun there_is_no_preview_on_a_phone_without_a_known_layout() {
        show(state().copy(layout = null, previewFrame = null))

        compose.onNodeWithTag("glyph_preview").assertDoesNotExist()
    }
}
