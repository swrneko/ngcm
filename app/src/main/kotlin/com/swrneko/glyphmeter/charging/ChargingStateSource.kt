package com.swrneko.glyphmeter.charging

import kotlinx.coroutines.flow.Flow

/** Stream of battery states. Emits the current state immediately on collection. */
interface ChargingStateSource {
    val state: Flow<ChargingState>
}
