package com.swrneko.glyphmeter.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import com.swrneko.glyphmeter.access.GlyphAccessState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Renders the real [GlyphMeterNavHost]; each screen is a tagged stand-in with working buttons. */
@RunWith(RobolectricTestRunner::class)
class GlyphMeterNavHostTest {

    @get:Rule val compose = createComposeRule()

    private fun show(access: GlyphAccessState) {
        compose.setContent {
            GlyphMeterNavHost(
                access = access,
                onboarding = { onContinue ->
                    Column(Modifier.testTag("screen_onboarding")) {
                        Button(onClick = onContinue, modifier = Modifier.testTag("onboarding_continue")) { Text("c") }
                    }
                },
                main = { onOpenSettings, onOpenOnboarding ->
                    Column(Modifier.testTag("screen_main")) {
                        Button(onClick = onOpenSettings, modifier = Modifier.testTag("main_to_settings")) { Text("s") }
                        Button(onClick = onOpenOnboarding, modifier = Modifier.testTag("main_to_onboarding")) { Text("o") }
                    }
                },
                settings = { onBack, onOpenAnimation ->
                    Column(Modifier.testTag("screen_settings")) {
                        Button(onClick = onBack, modifier = Modifier.testTag("settings_back")) { Text("b") }
                        Button(onClick = { onOpenAnimation("wave") }, modifier = Modifier.testTag("settings_to_wave")) { Text("w") }
                    }
                },
                animation = { presetId, onBack ->
                    Column(Modifier.testTag("screen_animation_$presetId")) {
                        Button(onClick = onBack, modifier = Modifier.testTag("animation_back")) { Text("b") }
                    }
                },
            )
        }
    }

    @Test
    fun a_working_phone_starts_on_the_main_screen() {
        show(GlyphAccessState.WORKING)

        compose.onNodeWithTag("screen_main").assertExists()
        compose.onNodeWithTag("screen_onboarding").assertDoesNotExist()
    }

    @Test
    fun a_phone_managed_by_the_app_starts_on_the_main_screen() {
        show(GlyphAccessState.MANAGED_BY_APP)

        compose.onNodeWithTag("screen_main").assertExists()
    }

    @Test
    fun a_phone_needing_setup_starts_on_onboarding() {
        show(GlyphAccessState.NEEDS_SETUP)

        compose.onNodeWithTag("screen_onboarding").assertExists()
        compose.onNodeWithTag("screen_main").assertDoesNotExist()
    }

    @Test
    fun an_unsupported_phone_starts_on_onboarding() {
        show(GlyphAccessState.UNSUPPORTED_DEVICE)

        compose.onNodeWithTag("screen_onboarding").assertExists()
    }

    @Test
    fun no_screen_is_shown_while_access_is_still_being_checked() {
        show(GlyphAccessState.CHECKING)

        compose.onNodeWithTag("nav_checking").assertExists()
        compose.onNodeWithTag("screen_main").assertDoesNotExist()
        compose.onNodeWithTag("screen_onboarding").assertDoesNotExist()
    }

    @Test
    fun continuing_from_onboarding_leads_to_the_main_screen() {
        show(GlyphAccessState.NEEDS_SETUP)

        compose.onNodeWithTag("onboarding_continue").performClick()

        compose.onNodeWithTag("screen_main").assertExists()
        compose.onNodeWithTag("screen_onboarding").assertDoesNotExist()
    }

    @Test
    fun main_opens_settings_and_back_returns_to_main() {
        show(GlyphAccessState.WORKING)

        compose.onNodeWithTag("main_to_settings").performClick()
        compose.onNodeWithTag("screen_settings").assertExists()

        compose.onNodeWithTag("settings_back").performClick()
        compose.onNodeWithTag("screen_main").assertExists()
    }

    @Test
    fun onboarding_reached_from_main_continues_to_a_fresh_main() {
        show(GlyphAccessState.WORKING)

        compose.onNodeWithTag("main_to_onboarding").performClick()
        compose.onNodeWithTag("screen_onboarding").assertExists()

        compose.onNodeWithTag("onboarding_continue").performClick()
        compose.onNodeWithTag("screen_main").assertExists()
    }

    @Test
    fun settings_opens_one_animation_and_back_returns_to_settings() {
        show(GlyphAccessState.WORKING)
        compose.onNodeWithTag("main_to_settings").performClick()

        compose.onNodeWithTag("settings_to_wave").performClick()
        compose.onNodeWithTag("screen_animation_wave").assertExists()

        compose.onNodeWithTag("animation_back").performClick()
        compose.onNodeWithTag("screen_settings").assertExists()
    }
}
