package com.swrneko.glyphmeter.model

/**
 * One frame of the Glyph interface: a brightness value per addressable segment.
 *
 * Index i maps directly onto the Nothing SDK channel index, so a frame can be
 * handed to `GlyphFrame.Builder.buildChannel(i, segments[i])` without translation.
 */
class GlyphFrameData(val segments: IntArray) {

    val size: Int get() = segments.size

    operator fun get(index: Int): Int = segments[index]

    override fun equals(other: Any?): Boolean =
        this === other || (other is GlyphFrameData && segments.contentEquals(other.segments))

    override fun hashCode(): Int = segments.contentHashCode()

    override fun toString(): String = "GlyphFrameData(${segments.joinToString()})"

    companion object {
        fun off(size: Int): GlyphFrameData = GlyphFrameData(IntArray(size))
    }
}
