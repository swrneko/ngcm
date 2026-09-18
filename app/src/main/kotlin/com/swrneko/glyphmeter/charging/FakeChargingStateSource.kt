package com.swrneko.glyphmeter.charging

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow

/** Test double letting a test drive battery events by hand. */
class FakeChargingStateSource : ChargingStateSource {

    private val _state = MutableSharedFlow<ChargingState>(replay = 1, extraBufferCapacity = 16)
    override val state: Flow<ChargingState> = _state

    suspend fun emit(state: ChargingState) = _state.emit(state)

    suspend fun emit(isCharging: Boolean, level: Float, source: PowerSource = PowerSource.WIRED) =
        emit(ChargingState(isCharging = isCharging, level = level, source = source))
}
