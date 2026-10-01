package com.swrneko.glyphmeter.layout

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceLayoutsTest {

    @Test
    fun `phone 3a has thirty six segments`() {
        assertEquals(36, DeviceLayouts.PHONE_3A.segmentCount)
    }

    @Test
    fun `phone 3a zones match the SDK constant table`() {
        val layout = DeviceLayouts.PHONE_3A

        assertEquals((0..19).toList(), layout.zone("C").indices)
        assertEquals((20..30).toList(), layout.zone("A").indices)
        assertEquals((31..35).toList(), layout.zone("B").indices)
    }

    @Test
    fun `phone 3a uses the twenty segment strip as its meter`() {
        val layout = DeviceLayouts.PHONE_3A

        assertEquals("C", layout.meterZoneId)
        assertEquals(20, layout.meterZone.indices.size)
    }

    @Test
    fun `every layout covers each segment index exactly once`() {
        for (layout in DeviceLayouts.all) {
            val covered = layout.zones.flatMap { it.indices }

            assertEquals(
                "layout ${layout.deviceId} has duplicate or missing indices",
                (0 until layout.segmentCount).toList(),
                covered.sorted(),
            )
        }
    }

    @Test
    fun `every layout declares a meter zone that exists`() {
        for (layout in DeviceLayouts.all) {
            assertTrue(
                "layout ${layout.deviceId} points at a missing meter zone",
                layout.zones.any { it.id == layout.meterZoneId },
            )
        }
    }

    @Test
    fun `lookup by device id finds a known device and rejects an unknown one`() {
        assertNotNull(DeviceLayouts.byDeviceId("24111"))
        assertNull(DeviceLayouts.byDeviceId("not-a-nothing-phone"))
    }

    @Test
    fun `no layout claims hardware verification before it was checked on a device`() {
        for (layout in DeviceLayouts.all) {
            assertFalse("layout ${layout.deviceId} was never run on hardware", layout.hardwareVerified)
        }
    }
}
