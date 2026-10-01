package com.swrneko.glyphmeter.access

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DebugModePolicyTest {

    @Test
    fun `debug mode is required just below API 36`() {
        assertTrue(requiresDebugMode(35))
    }

    @Test
    fun `debug mode is not required on API 36`() {
        assertFalse(requiresDebugMode(36))
    }

    @Test
    fun `debug mode is not required above API 36`() {
        assertFalse(requiresDebugMode(37))
    }

    @Test
    fun `debug mode is required on the minimum supported API`() {
        assertTrue(requiresDebugMode(33))
    }
}
