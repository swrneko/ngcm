package com.swrneko.glyphmeter.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BootPolicyTest {

    @Test
    fun `starts when glyph works without setup`() {
        assertTrue(shouldStartOnBoot(GlyphAccessState.WORKING))
    }

    @Test
    fun `starts when app manages debug mode itself`() {
        assertTrue(shouldStartOnBoot(GlyphAccessState.MANAGED_BY_APP))
    }

    @Test
    fun `does not start when setup is needed`() {
        assertFalse(shouldStartOnBoot(GlyphAccessState.NEEDS_SETUP))
    }

    @Test
    fun `does not start on unsupported device`() {
        assertFalse(shouldStartOnBoot(GlyphAccessState.UNSUPPORTED_DEVICE))
    }

    @Test
    fun `does not start while access is still being checked`() {
        assertFalse(shouldStartOnBoot(GlyphAccessState.CHECKING))
    }
}
