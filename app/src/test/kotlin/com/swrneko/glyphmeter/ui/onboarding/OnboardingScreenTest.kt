package com.swrneko.glyphmeter.ui.onboarding

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.swrneko.glyphmeter.access.GlyphAccessState
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Renders the real [OnboardingScreen] under Robolectric and asserts through UI nodes. */
@RunWith(RobolectricTestRunner::class)
class OnboardingScreenTest {

    @get:Rule val compose = createComposeRule()

    private val command = "adb shell pm grant com.swrneko.glyphmeter android.permission.WRITE_SECURE_SETTINGS"

    private fun show(
        state: GlyphAccessState,
        onCopyCommand: () -> Unit = {},
        onRecheck: () -> Unit = {},
        onContinue: () -> Unit = {},
    ) {
        compose.setContent {
            OnboardingScreen(
                state = state,
                adbCommand = command,
                onCopyCommand = onCopyCommand,
                onRecheck = onRecheck,
                onContinue = onContinue,
            )
        }
    }

    @Test
    fun a_working_phone_is_never_shown_the_adb_command() {
        show(GlyphAccessState.WORKING)

        compose.onNodeWithTag("adb_command").assertDoesNotExist()
        compose.onNodeWithText(command, substring = true).assertDoesNotExist()
        compose.onNodeWithText("adb", substring = true, ignoreCase = true).assertDoesNotExist()
    }

    @Test
    fun a_working_phone_can_continue() {
        var continued = false
        show(GlyphAccessState.WORKING, onContinue = { continued = true })

        compose.onNodeWithTag("continue").assertIsDisplayed().performClick()

        assertTrue(continued)
    }

    @Test
    fun a_phone_managed_by_the_app_is_not_shown_the_adb_command_and_can_continue() {
        show(GlyphAccessState.MANAGED_BY_APP)

        compose.onNodeWithTag("adb_command").assertDoesNotExist()
        compose.onNodeWithTag("copy_command").assertDoesNotExist()
        compose.onNodeWithText("adb", substring = true, ignoreCase = true).assertDoesNotExist()
        compose.onNodeWithTag("continue").assertIsDisplayed()
    }

    @Test
    fun a_phone_needing_setup_is_shown_the_adb_command() {
        show(GlyphAccessState.NEEDS_SETUP)

        compose.onNodeWithTag("adb_command").assertIsDisplayed()
        compose.onNodeWithText(command).assertIsDisplayed()
        compose.onNodeWithTag("continue").assertDoesNotExist()
    }

    @Test
    fun a_phone_needing_setup_is_told_the_command_is_run_only_once() {
        show(GlyphAccessState.NEEDS_SETUP)

        compose.onNodeWithText("only once", substring = true).assertIsDisplayed()
    }

    @Test
    fun copying_the_command_reports_back() {
        var copied = false
        show(GlyphAccessState.NEEDS_SETUP, onCopyCommand = { copied = true })

        compose.onNodeWithTag("copy_command").performClick()

        assertTrue(copied)
    }

    @Test
    fun rechecking_reports_back() {
        var rechecked = false
        show(GlyphAccessState.NEEDS_SETUP, onRecheck = { rechecked = true })

        compose.onNodeWithTag("recheck").performClick()

        assertTrue(rechecked)
    }

    @Test
    fun the_shizuku_alternative_is_collapsed_until_opened() {
        show(GlyphAccessState.NEEDS_SETUP)

        compose.onNodeWithTag("shizuku_body").assertDoesNotExist()
        compose.onNodeWithTag("shizuku_toggle").performClick()
        compose.onNodeWithTag("shizuku_body").assertExists()
    }

    @Test
    fun an_unsupported_phone_offers_no_way_forward() {
        show(GlyphAccessState.UNSUPPORTED_DEVICE)

        compose.onNodeWithTag("continue").assertDoesNotExist()
        compose.onNodeWithTag("adb_command").assertDoesNotExist()
        compose.onNodeWithTag("copy_command").assertDoesNotExist()
        compose.onNodeWithText("Device not supported").assertIsDisplayed()
    }

    @Test
    fun a_phone_being_checked_shows_only_a_progress_indicator() {
        show(GlyphAccessState.CHECKING)

        compose.onNodeWithTag("checking").assertIsDisplayed()
        compose.onNodeWithTag("continue").assertDoesNotExist()
        compose.onNodeWithTag("adb_command").assertDoesNotExist()
    }
}
