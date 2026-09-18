package com.swrneko.glyphmeter.charging

import android.app.Application
import android.content.Intent
import android.os.BatteryManager
import android.os.Looper
import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

/**
 * Exercises [SystemChargingStateSource] through its public [ChargingStateSource.state] flow,
 * driving the real Android battery broadcast machinery via Robolectric instead of mocking
 * anything inside the class under test.
 */
@RunWith(RobolectricTestRunner::class)
class SystemChargingStateSourceTest {

    private val context: Application = RuntimeEnvironment.getApplication()

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
    fun `it emits the current state immediately on subscription`() = runTest {
        context.sendStickyBroadcast(batteryIntent(level = 50))
        val source = SystemChargingStateSource(context)

        source.state.test {
            val first = awaitItem()

            assertEquals(0.5f, first.level, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `it does not deliver the same state twice`() = runTest {
        context.sendStickyBroadcast(batteryIntent(level = 50))
        val source = SystemChargingStateSource(context)

        source.state.test {
            awaitItem()

            // The exact same battery event fired again after subscription must not be
            // reported as a second, distinct change.
            context.sendBroadcast(batteryIntent(level = 50))
            shadowOf(Looper.getMainLooper()).idle()

            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a level change is delivered as a new state`() = runTest {
        context.sendStickyBroadcast(batteryIntent(level = 50))
        val source = SystemChargingStateSource(context)

        source.state.test {
            awaitItem()

            context.sendBroadcast(batteryIntent(level = 60))
            shadowOf(Looper.getMainLooper()).idle()

            val next = awaitItem()
            assertEquals(0.6f, next.level, 0.001f)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `switching from charging to discharging is delivered as a new state`() = runTest {
        context.sendStickyBroadcast(batteryIntent(level = 50, status = BatteryManager.BATTERY_STATUS_CHARGING))
        val source = SystemChargingStateSource(context)

        source.state.test {
            val first = awaitItem()
            assertTrue(first.isCharging)

            context.sendBroadcast(
                batteryIntent(level = 50, status = BatteryManager.BATTERY_STATUS_DISCHARGING, plugged = 0),
            )
            shadowOf(Looper.getMainLooper()).idle()

            val next = awaitItem()
            assertFalse(next.isCharging)
            assertEquals(PowerSource.NONE, next.source)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the receiver is unregistered once the flow is closed`() = runTest {
        context.sendStickyBroadcast(batteryIntent(level = 50))
        val source = SystemChargingStateSource(context)
        val batteryChangedFilter = Intent(Intent.ACTION_BATTERY_CHANGED)

        source.state.test {
            awaitItem()
            assertTrue(shadowOf(context).hasReceiverForIntent(batteryChangedFilter))
            cancelAndIgnoreRemainingEvents()
        }

        assertFalse(shadowOf(context).hasReceiverForIntent(batteryChangedFilter))

        // A stray system event after the flow is gone must not crash anything, because
        // there is no receiver left to deliver it to.
        context.sendBroadcast(batteryIntent(level = 90))
        shadowOf(Looper.getMainLooper()).idle()
    }
}
