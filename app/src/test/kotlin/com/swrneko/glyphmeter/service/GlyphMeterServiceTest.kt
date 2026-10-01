package com.swrneko.glyphmeter.service

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.charging.PowerSource
import com.swrneko.glyphmeter.hardware.GlyphFailure
import com.swrneko.glyphmeter.orchestration.PreviewRequestBus
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * The service, the controller and the orchestrator wired by the real Hilt graph. Each part has
 * its own tests; these catch the failures that only exist in the seams between them.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class GlyphMeterServiceTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    @Inject lateinit var controller: MeterServiceController
    @Inject lateinit var previewBus: PreviewRequestBus

    private val application: Application get() = ApplicationProvider.getApplicationContext()

    @Before fun setUp() {
        GlueFakes.reset()
        hilt.inject()
    }

    private fun createService() = Robolectric.buildService(GlyphMeterService::class.java).create()

    @Test
    fun `the controller starts and stops the glyph service`() {
        controller.start()
        val started = shadowOf(application).nextStartedService
        assertEquals(GlyphMeterService::class.java.name, started.component?.className)

        controller.stop()
        val stopped = shadowOf(application).nextStoppedService
        assertEquals(GlyphMeterService::class.java.name, stopped.component?.className)
    }

    @Test
    fun `a started service connects the glyph and draws the charge`() {
        val service = createService()

        assertTrue(awaitCondition { GlueFakes.display.isConnected })
        runBlocking { GlueFakes.charging.emit(isCharging = true, level = 0.5f) }

        assertTrue("nothing was drawn while charging", awaitCondition { GlueFakes.display.rendered.isNotEmpty() })
        assertFalse(shadowOf(service.get()).isStoppedBySelf)
    }

    @Test
    fun `a preview request on the shared bus is drawn on the glyph by the running service`() {
        createService()
        assertTrue(awaitCondition { GlueFakes.display.isConnected })
        runBlocking { GlueFakes.charging.emit(isCharging = false, level = 0.5f, source = PowerSource.NONE) }

        // The request is dropped if the service is not listening yet, so ask until it answers.
        val drawn = awaitCondition {
            previewBus.requestPreview(AnimationPresets.CHASE.id)
            GlueFakes.display.rendered.isNotEmpty()
        }

        assertTrue("the preview never reached the glyph", drawn)
    }

    @Test
    fun `the service re-arms the debug flag through the access manager before it connects`() {
        createService()

        assertTrue(awaitCondition { GlueFakes.display.isConnected })
        assertTrue("debug mode was never re-armed", GlueFakes.writer.enableCalls.get() >= 1)
    }

    @Test
    fun `destroying the service switches the glyph off and releases it`() {
        val controller = createService()
        assertTrue(awaitCondition { GlueFakes.display.isConnected })

        controller.destroy()

        assertTrue("glyph stayed connected", awaitCondition { !GlueFakes.display.isConnected })
        assertTrue(GlueFakes.display.turnOffCount >= 1)
    }

    @Test
    fun `when the connection fails the work ends and the service stops itself`() {
        GlueFakes.display.connectResult = Result.failure(IllegalStateException("refused"))
        GlueFakes.display.failureReason = GlyphFailure.REGISTRATION_REJECTED

        val service = createService().get()

        assertTrue(
            "service kept running after a failed connection",
            awaitCondition { shadowOf(service).isStoppedBySelf },
        )
        assertEquals(1, GlueFakes.display.connectCount)
        assertEquals(GlyphFailure.REGISTRATION_REJECTED, GlueFakes.display.lastFailure.value)
    }

    @Test
    fun `on a phone the app does not know the service never touches the glyph and stops`() {
        GlueFakes.layout = null

        val service = createService().get()

        assertTrue(awaitCondition { shadowOf(service).isStoppedBySelf })
        assertEquals(0, GlueFakes.display.connectCount)
    }
}
