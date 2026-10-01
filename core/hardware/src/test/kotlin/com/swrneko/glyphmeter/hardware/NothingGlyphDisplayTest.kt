package com.swrneko.glyphmeter.hardware

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import android.os.Looper
import com.nothing.ketchum.GlyphManager
import com.nothing.thirdparty.IGlyphService
import com.swrneko.glyphmeter.layout.DeviceLayouts
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Drives the real [NothingGlyphDisplay] and the real `GlyphManager` against a stand-in for the
 * Glyph system service, which is the boundary of this layer. Bindings are observed through
 * Robolectric's record of bound service connections.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class NothingGlyphDisplayTest {

    private class StubGlyphService : IGlyphService.Stub() {
        var acceptRegistration = true
        var failOpenSession = false
        var openedSessions = 0

        override fun registerSDK(key: String?, device: String?): Boolean = acceptRegistration
        override fun openSession() {
            if (failOpenSession) throw IllegalStateException("session refused")
            openedSessions++
        }
        override fun closeSession() = Unit
        override fun setFrameColors(colors: IntArray?) = Unit
        override fun register(key: String?): Boolean = acceptRegistration
        override fun registerMatrixSDK(key: String?): Boolean = false
        override fun setMatrixColors(colors: IntArray?) = Unit
        override fun setGlyphMatrixTimeout(enabled: Boolean) = Unit
        override fun setAppMatrixColors(colors: IntArray?) = Unit
        override fun closeAppMatrix() = Unit
    }

    private val glyphService = ComponentName("com.nothing.thirdparty", "com.nothing.thirdparty.GlyphService")
    private val layout = DeviceLayouts.PHONE_3A
    private val service = StubGlyphService()
    private lateinit var app: Application

    @Before
    fun setUp() {
        // GlyphManager is a process-wide singleton; Robolectric hands every test a fresh
        // Application, so the instance bound to the previous test's context must go.
        GlyphManager::class.java.getDeclaredField("mInstance").apply { isAccessible = true }.set(null, null)

        app = RuntimeEnvironment.getApplication()
        shadowOf(app).setComponentNameAndServiceForBindService(glyphService, service)
        // Real Android never calls onServiceDisconnected for an unbind the app asked for.
        shadowOf(app).setUnbindServiceCallsOnServiceDisconnected(false)
        // The SDK reads the app key from manifest metadata before registering.
        shadowOf(app.packageManager).getInternalMutablePackageInfo(app.packageName)
            .applicationInfo!!.metaData = Bundle().apply { putString("NothingKey", "test") }
    }

    private val boundConnections get() = shadowOf(app).boundServiceConnections

    private suspend fun TestScope.connect(display: NothingGlyphDisplay): Result<Unit> {
        val result = async { display.connect(layout) }
        runCurrent()
        // Delivers onServiceConnected, which Robolectric posts to the main looper.
        shadowOf(Looper.getMainLooper()).idle()
        runCurrent()
        return result.await()
    }

    @Test
    fun `a successful connect keeps one binding and opens a per segment session`() = runTest {
        val display = NothingGlyphDisplay(app)

        val result = connect(display)

        assertTrue(result.isSuccess)
        assertEquals(1, boundConnections.size)
        assertEquals(1, service.openedSessions)
        assertEquals(RenderCapability.PER_SEGMENT, display.capability.value)
    }

    @Test
    fun `a rejected registration does not leave the service bound`() = runTest {
        service.acceptRegistration = false
        val display = NothingGlyphDisplay(app)

        val result = connect(display)

        assertTrue(result.isFailure)
        assertEquals(RenderCapability.UNAVAILABLE, display.capability.value)
        assertTrue("binding leaked: $boundConnections", boundConnections.isEmpty())
    }

    @Test
    fun `a failing open session does not leave the service bound`() = runTest {
        service.failOpenSession = true
        val display = NothingGlyphDisplay(app)

        val result = connect(display)

        assertTrue(result.isFailure)
        assertTrue("binding leaked: $boundConnections", boundConnections.isEmpty())
    }

    @Test
    fun `repeated failed attempts do not pile up bindings`() = runTest {
        service.acceptRegistration = false
        val display = NothingGlyphDisplay(app)

        repeat(3) { connect(display) }

        assertTrue("bindings leaked: $boundConnections", boundConnections.isEmpty())
    }

    @Test
    fun `a lost session is released and reported as unavailable`() = runTest {
        val display = NothingGlyphDisplay(app)
        connect(display)
        val connection = boundConnections.single()

        connection.onServiceDisconnected(glyphService)

        assertEquals(RenderCapability.UNAVAILABLE, display.capability.value)
        assertTrue("the dead binding must be released", boundConnections.isEmpty())
    }

    @Test
    fun `after a lost session a new connect opens a fresh session`() = runTest {
        val display = NothingGlyphDisplay(app)
        connect(display)
        boundConnections.single().onServiceDisconnected(glyphService)

        val result = connect(display)

        assertTrue(result.isSuccess)
        assertEquals(2, service.openedSessions)
        assertEquals(1, boundConnections.size)
        assertEquals(RenderCapability.PER_SEGMENT, display.capability.value)
    }

    @Test
    fun `disconnect releases the binding`() = runTest {
        val display = NothingGlyphDisplay(app)
        connect(display)

        display.disconnect()

        assertTrue(boundConnections.isEmpty())
        assertEquals(RenderCapability.UNKNOWN, display.capability.value)
    }

    @Test
    fun `a rejected registration is remembered as the reason`() = runTest {
        service.acceptRegistration = false
        val display = NothingGlyphDisplay(app)

        connect(display)

        assertEquals(GlyphFailure.REGISTRATION_REJECTED, display.lastFailure.value)
    }

    @Test
    fun `the reason survives the disconnect that follows a failure`() = runTest {
        service.acceptRegistration = false
        val display = NothingGlyphDisplay(app)
        connect(display)

        display.disconnect()

        assertEquals(RenderCapability.UNKNOWN, display.capability.value)
        assertEquals(GlyphFailure.REGISTRATION_REJECTED, display.lastFailure.value)
    }

    @Test
    fun `a lost session is remembered as the reason`() = runTest {
        val display = NothingGlyphDisplay(app)
        connect(display)

        boundConnections.single().onServiceDisconnected(glyphService)
        display.disconnect()

        assertEquals(GlyphFailure.SESSION_LOST, display.lastFailure.value)
    }

    @Test
    fun `a successful connect clears the remembered reason`() = runTest {
        service.acceptRegistration = false
        val display = NothingGlyphDisplay(app)
        connect(display)

        service.acceptRegistration = true
        connect(display)

        assertEquals(null, display.lastFailure.value)
    }
}
