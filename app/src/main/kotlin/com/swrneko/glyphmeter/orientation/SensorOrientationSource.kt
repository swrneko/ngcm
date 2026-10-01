package com.swrneko.glyphmeter.orientation

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

/** Gravity along the screen normal, m/s², above which a flat phone counts as screen up. */
private const val FACE_UP_ENTER = 8.0f

/** Below this the phone is no longer screen up. The gap keeps a wobbling phone from flickering. */
private const val FACE_UP_LEAVE = 6.0f

/**
 * [OrientationSource] on the gravity sensor, falling back to the accelerometer.
 *
 * The sensor is registered only while the flow is collected, which the orchestrator does only
 * while charging with face-up dimming on. Without either sensor the phone is never face up, so
 * the Glyph behaves as if the setting were off.
 */
class SensorOrientationSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : OrientationSource {

    override val isFaceUp: Flow<Boolean> = callbackFlow {
        val manager = context.getSystemService(SensorManager::class.java)
        val sensor = manager?.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: manager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (manager == null || sensor == null) {
            trySend(false)
            awaitClose()
            return@callbackFlow
        }

        var faceUp = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                faceUp = faceUpAfter(screenNormalGravity = event.values[2], wasFaceUp = faceUp)
                trySend(faceUp)
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { manager.unregisterListener(listener) }
    }.distinctUntilChanged()
}

private fun faceUpAfter(screenNormalGravity: Float, wasFaceUp: Boolean): Boolean =
    if (wasFaceUp) screenNormalGravity >= FACE_UP_LEAVE else screenNormalGravity >= FACE_UP_ENTER
