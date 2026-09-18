package com.swrneko.glyphmeter.charging

import android.content.Intent
import android.os.BatteryManager

enum class PowerSource { NONE, WIRED, WIRELESS, DOCK }

/**
 * Everything the app needs to know about the battery at one moment.
 *
 * [level] is a fraction in 0..1 rather than a percentage, because that is what the
 * meter renderer takes.
 */
data class ChargingState(
    val isCharging: Boolean,
    val level: Float,
    val source: PowerSource,
) {
    companion object {

        fun fromBatteryIntent(intent: Intent): ChargingState {
            val rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            val level = if (rawLevel < 0 || scale <= 0) 0f else (rawLevel.toFloat() / scale).coerceIn(0f, 1f)

            val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

            val plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
            val source = when {
                !isCharging -> PowerSource.NONE
                plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS -> PowerSource.WIRELESS
                plugged == BatteryManager.BATTERY_PLUGGED_DOCK -> PowerSource.DOCK
                plugged == BatteryManager.BATTERY_PLUGGED_AC || plugged == BatteryManager.BATTERY_PLUGGED_USB -> PowerSource.WIRED
                else -> PowerSource.NONE
            }

            return ChargingState(isCharging = isCharging, level = level, source = source)
        }
    }
}
