package com.swrneko.glyphmeter.charging

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SystemChargingStateSource @Inject constructor(
    @ApplicationContext private val context: Context,
) : ChargingStateSource {

    override val state: Flow<ChargingState> = callbackFlow {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                intent ?: return
                trySend(ChargingState.fromBatteryIntent(intent))
            }
        }

        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        // registerReceiver with ACTION_BATTERY_CHANGED returns the sticky current value,
        // so the flow emits the present state without waiting for a change.
        val sticky = context.registerReceiver(receiver, filter)
        if (sticky != null) trySend(ChargingState.fromBatteryIntent(sticky))

        awaitClose { context.unregisterReceiver(receiver) }
    }.distinctUntilChanged()
}
