package com.swrneko.glyphmeter.orientation

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Whether the phone lies screen up, which is when the Glyph on its back faces the table.
 *
 * The underlying sensor runs only while the flow is collected, so collectors decide how long
 * it draws power.
 */
interface OrientationSource {
    val isFaceUp: Flow<Boolean>

    /** For callers that do not care about orientation. */
    object NeverFaceUp : OrientationSource {
        override val isFaceUp: Flow<Boolean> = flowOf(false)
    }
}
