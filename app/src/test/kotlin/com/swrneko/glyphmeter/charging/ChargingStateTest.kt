package com.swrneko.glyphmeter.charging

import android.content.Intent
import android.os.BatteryManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ChargingStateTest {

    private fun batteryIntent(
        level: Int,
        scale: Int = 100,
        status: Int = BatteryManager.BATTERY_STATUS_CHARGING,
        plugged: Int = BatteryManager.BATTERY_PLUGGED_AC,
    ): Intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
        putExtra(BatteryManager.EXTRA_LEVEL, level)
        putExtra(BatteryManager.EXTRA_SCALE, scale)
        putExtra(BatteryManager.EXTRA_STATUS, status)
        putExtra(BatteryManager.EXTRA_PLUGGED, plugged)
    }

    @Test
    fun `it reads the level as a fraction of the scale`() {
        val state = ChargingState.fromBatteryIntent(batteryIntent(level = 63))

        assertEquals(0.63f, state.level, 0.001f)
    }

    @Test
    fun `it copes with a scale that is not one hundred`() {
        val state = ChargingState.fromBatteryIntent(batteryIntent(level = 128, scale = 256))

        assertEquals(0.5f, state.level, 0.001f)
    }

    @Test
    fun `a missing scale does not produce a division by zero`() {
        val intent = Intent(Intent.ACTION_BATTERY_CHANGED).apply {
            putExtra(BatteryManager.EXTRA_LEVEL, 50)
            putExtra(BatteryManager.EXTRA_SCALE, 0)
        }

        val state = ChargingState.fromBatteryIntent(intent)

        assertEquals(0f, state.level, 0.001f)
    }

    @Test
    fun `a full battery on the charger still counts as charging`() {
        val state = ChargingState.fromBatteryIntent(
            batteryIntent(level = 100, status = BatteryManager.BATTERY_STATUS_FULL),
        )

        assertTrue(state.isCharging)
    }

    @Test
    fun `an unplugged battery is not charging and has no source`() {
        val state = ChargingState.fromBatteryIntent(
            batteryIntent(
                level = 50,
                status = BatteryManager.BATTERY_STATUS_DISCHARGING,
                plugged = 0,
            ),
        )

        assertFalse(state.isCharging)
        assertEquals(PowerSource.NONE, state.source)
    }

    @Test
    fun `it tells wired from wireless from dock`() {
        assertEquals(
            PowerSource.WIRED,
            ChargingState.fromBatteryIntent(batteryIntent(50, plugged = BatteryManager.BATTERY_PLUGGED_AC)).source,
        )
        assertEquals(
            PowerSource.WIRED,
            ChargingState.fromBatteryIntent(batteryIntent(50, plugged = BatteryManager.BATTERY_PLUGGED_USB)).source,
        )
        assertEquals(
            PowerSource.WIRELESS,
            ChargingState.fromBatteryIntent(batteryIntent(50, plugged = BatteryManager.BATTERY_PLUGGED_WIRELESS)).source,
        )
        assertEquals(
            PowerSource.DOCK,
            ChargingState.fromBatteryIntent(batteryIntent(50, plugged = BatteryManager.BATTERY_PLUGGED_DOCK)).source,
        )
    }

    @Test
    fun `level is clamped into the valid range`() {
        val state = ChargingState.fromBatteryIntent(batteryIntent(level = 250, scale = 100))

        assertEquals(1f, state.level, 0.001f)
    }
}
