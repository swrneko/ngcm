package com.swrneko.glyphmeter.access

import com.swrneko.glyphmeter.layout.DeviceLayouts
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceStartPolicyTest {

    private val layout = DeviceLayouts.PHONE_3A

    @Test
    fun `starts only for usable access, enabled app and known layout`() {
        for (state in GlyphAccessState.entries) {
            assertEquals(
                "state $state",
                shouldStartOnBoot(state),
                shouldStartService(state, enabled = true, layout = layout),
            )
        }
    }

    @Test
    fun `does not start when the app is switched off`() {
        assertEquals(false, shouldStartService(GlyphAccessState.WORKING, enabled = false, layout = layout))
    }

    @Test
    fun `does not start without a known layout`() {
        assertEquals(false, shouldStartService(GlyphAccessState.WORKING, enabled = true, layout = null))
        assertEquals(false, shouldStartService(GlyphAccessState.MANAGED_BY_APP, enabled = true, layout = null))
    }
}
