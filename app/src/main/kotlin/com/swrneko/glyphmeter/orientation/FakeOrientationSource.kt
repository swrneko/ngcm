package com.swrneko.glyphmeter.orientation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/** Test double: flip the phone by hand and see whether anyone is listening. */
class FakeOrientationSource(faceUp: Boolean = false) : OrientationSource {

    private val state = MutableStateFlow(faceUp)
    override val isFaceUp: Flow<Boolean> = state

    /** Number of active collectors, i.e. whether the "sensor" is running. */
    val listenerCount: Int get() = state.subscriptionCount.value

    fun set(faceUp: Boolean) {
        state.value = faceUp
    }
}
