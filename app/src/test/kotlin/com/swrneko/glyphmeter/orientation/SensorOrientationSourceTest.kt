package com.swrneko.glyphmeter.orientation

import android.app.Application
import android.hardware.Sensor
import android.hardware.SensorManager
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
import org.robolectric.shadows.SensorEventBuilder
import org.robolectric.shadows.ShadowSensor

/** Drives the real [SensorOrientationSource] through Robolectric's sensor manager. */
@RunWith(RobolectricTestRunner::class)
class SensorOrientationSourceTest {

    private val context: Application = RuntimeEnvironment.getApplication()
    private val sensorManager = context.getSystemService(SensorManager::class.java)

    private fun installGravitySensor(): Sensor =
        ShadowSensor.newInstance(Sensor.TYPE_GRAVITY).also { shadowOf(sensorManager).addSensor(it) }

    private fun send(sensor: Sensor, z: Float, x: Float = 0f) {
        shadowOf(sensorManager).sendSensorEventToListeners(
            SensorEventBuilder.newBuilder().setSensor(sensor).setValues(floatArrayOf(x, 0f, z)).build(),
        )
    }

    @Test
    fun `lying screen up is face up and screen down is not`() = runTest {
        val sensor = installGravitySensor()

        SensorOrientationSource(context).isFaceUp.test {
            send(sensor, z = 9.8f)
            assertTrue(awaitItem())
            send(sensor, z = -9.8f)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `small wobbles around flat do not flip the answer`() = runTest {
        val sensor = installGravitySensor()

        SensorOrientationSource(context).isFaceUp.test {
            send(sensor, z = 9.8f)
            assertTrue(awaitItem())
            send(sensor, z = 9.0f)
            send(sensor, z = 7.0f)
            send(sensor, z = 9.6f)
            expectNoEvents()
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `a phone standing upright is not face up`() = runTest {
        val sensor = installGravitySensor()

        SensorOrientationSource(context).isFaceUp.test {
            send(sensor, z = 0.5f, x = 9.7f)
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `the sensor is released when nobody listens any more`() = runTest {
        installGravitySensor()
        val source = SensorOrientationSource(context)

        source.isFaceUp.test {
            assertEquals(1, shadowOf(sensorManager).listeners.size)
            cancelAndIgnoreRemainingEvents()
        }

        assertTrue(shadowOf(sensorManager).listeners.isEmpty())
    }

    @Test
    fun `without a usable sensor the phone is never face up`() = runTest {
        SensorOrientationSource(context).isFaceUp.test {
            assertFalse(awaitItem())
            cancelAndIgnoreRemainingEvents()
        }
    }
}
