package com.swrneko.glyphmeter.animation

fun interface Easing {
    /** Maps normalised time in 0..1 onto normalised progress in 0..1. */
    fun transform(t: Float): Float

    companion object {
        val Linear = Easing { t -> t.coerceIn(0f, 1f) }

        /** Decelerating curve: quick to react, gentle to settle. */
        val EaseOutCubic = Easing { t ->
            val clamped = t.coerceIn(0f, 1f)
            val inverted = 1f - clamped
            1f - inverted * inverted * inverted
        }
    }
}
