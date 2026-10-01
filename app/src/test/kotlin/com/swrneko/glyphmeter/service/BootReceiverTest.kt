package com.swrneko.glyphmeter.service

import android.app.Application
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger

/** The receiver wired through the real Hilt graph; only the phone's system boundary is replaced. */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class BootReceiverTest {

    @get:Rule val hilt = HiltAndroidRule(this)

    private val application: Application get() = ApplicationProvider.getApplicationContext()
    private val uncaught = CopyOnWriteArrayList<Throwable>()
    private var previousHandler: Thread.UncaughtExceptionHandler? = null

    @Before fun setUp() {
        GlueFakes.reset()
        hilt.inject()
        previousHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { _, error -> uncaught += error }
    }

    @After fun tearDown() = Thread.setDefaultUncaughtExceptionHandler(previousHandler)

    private fun bootCompleted() = application.sendBroadcast(Intent(Intent.ACTION_BOOT_COMPLETED))

    private fun startedService() = shadowOf(application).nextStartedService

    @Test
    fun `after boot the service starts when access works`() {
        bootCompleted()

        assertTrue(awaitCondition { shadowOf(application).peekNextStartedService() != null })
        assertEquals(GlyphMeterService::class.java.name, startedService().component?.className)
    }

    @Test
    fun `after boot the app switches the debug flag back on`() {
        bootCompleted()

        assertTrue(awaitCondition { GlueFakes.writer.enableCalls.get() >= 1 })
    }

    @Test
    fun `after boot nothing starts on a phone the app cannot drive`() {
        GlueFakes.onEvaluate = { false }

        bootCompleted()
        Thread.sleep(300)

        assertNull(shadowOf(application).peekNextStartedService())
    }

    @Test
    fun `a failing access check does not crash the process`() {
        val attempts = AtomicInteger(0)
        GlueFakes.onEvaluate = {
            attempts.incrementAndGet()
            throw IllegalStateException("access check blew up")
        }

        bootCompleted()
        assertTrue(awaitCondition { attempts.get() >= 1 })
        Thread.sleep(500)

        assertTrue("uncaught: $uncaught", uncaught.isEmpty())
        assertNull(shadowOf(application).peekNextStartedService())
    }
}
