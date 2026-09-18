package com.swrneko.glyphmeter.access

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GlyphAccessManagerTest {

    private class StubWriter(
        override val isAvailable: Boolean,
        private var debugOn: Boolean,
        private val canEnable: Boolean,
    ) : DebugModeWriter {
        var enableCalls = 0
            private set

        override fun isDebugModeOn(): Boolean = debugOn

        override fun enableDebugMode(): Boolean {
            enableCalls++
            if (canEnable) debugOn = true
            return canEnable
        }
    }

    private fun manager(
        writers: List<DebugModeWriter>,
        supported: Boolean = true,
        needsDebugMode: Boolean = true,
    ) = GlyphAccessManager(
        writers = writers,
        isDeviceSupported = { supported },
        requiresDebugMode = { needsDebugMode },
    )

    @Test
    fun `an unsupported phone is reported before anything else is checked`() {
        val state = manager(writers = emptyList(), supported = false).evaluate()

        assertEquals(GlyphAccessState.UNSUPPORTED_DEVICE, state)
    }

    @Test
    fun `a phone that does not need debug mode just works`() {
        val state = manager(writers = emptyList(), needsDebugMode = false).evaluate()

        assertEquals(GlyphAccessState.WORKING, state)
    }

    @Test
    fun `debug mode already on without a writer counts as working`() {
        val writer = StubWriter(isAvailable = false, debugOn = true, canEnable = false)

        val state = manager(listOf(writer)).evaluate()

        assertEquals(GlyphAccessState.WORKING, state)
    }

    @Test
    fun `an available writer turns debug mode on by itself`() {
        val writer = StubWriter(isAvailable = true, debugOn = false, canEnable = true)

        val state = manager(listOf(writer)).evaluate()

        assertEquals(GlyphAccessState.MANAGED_BY_APP, state)
        assertEquals(1, writer.enableCalls)
    }

    @Test
    fun `with no usable writer the user is asked to set things up`() {
        val writer = StubWriter(isAvailable = false, debugOn = false, canEnable = false)

        val state = manager(listOf(writer)).evaluate()

        assertEquals(GlyphAccessState.NEEDS_SETUP, state)
        assertEquals(0, writer.enableCalls)
    }

    @Test
    fun `a writer that claims to be available but fails falls through to the next one`() {
        val broken = StubWriter(isAvailable = true, debugOn = false, canEnable = false)
        val working = StubWriter(isAvailable = true, debugOn = false, canEnable = true)

        val state = manager(listOf(broken, working)).evaluate()

        assertEquals(GlyphAccessState.MANAGED_BY_APP, state)
        assertEquals(1, broken.enableCalls)
        assertEquals(1, working.enableCalls)
    }

    @Test
    fun `every writer failing leaves the user with the setup instructions`() {
        val first = StubWriter(isAvailable = true, debugOn = false, canEnable = false)
        val second = StubWriter(isAvailable = true, debugOn = false, canEnable = false)

        val state = manager(listOf(first, second)).evaluate()

        assertEquals(GlyphAccessState.NEEDS_SETUP, state)
    }

    @Test
    fun `the adb command names the real package and permission`() {
        val command = manager(writers = emptyList()).adbGrantCommand

        assertTrue(command, command.contains("com.swrneko.glyphmeter"))
        assertTrue(command, command.contains("android.permission.WRITE_SECURE_SETTINGS"))
        assertTrue(command, command.startsWith("adb shell pm grant "))
    }
}
