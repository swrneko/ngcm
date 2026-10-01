package com.swrneko.glyphmeter.ui.main

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.swrneko.glyphmeter.R
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.Light
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * Where the segments of one zone sit on the back panel: an arc of the ring around the camera.
 * [center] is a fraction of panel width and height, [radius] a fraction of panel width.
 * Angles are in degrees in the mathematical convention (0 points right, counter-clockwise
 * is positive, Y up), as in the geometry notes. Segments go from [startDegrees] to
 * [endDegrees] in index order.
 */
internal data class ZoneShape(
    val center: Offset,
    val radius: Float,
    val startDegrees: Float,
    val endDegrees: Float,
)

/** Centre of the ring around the camera module, shared by all three arcs. */
private val RING_CENTER = Offset(0.49f, 0.226f)

/** Radius of the ring as a fraction of panel width. */
private const val RING_RADIUS = 0.41f

/** Radius of the camera module as a fraction of panel width. */
private const val CAMERA_RADIUS = 0.36f

/**
 * Preview geometry, one entry per zone id. A layout whose zones are all listed here needs
 * no change to the drawing code. Zones missing from the table are simply not drawn.
 */
internal val ZONE_SHAPES: Map<String, ZoneShape> = mapOf(
    "C" to ZoneShape(RING_CENTER, RING_RADIUS, startDegrees = 162.6f, endDegrees = 113.7f),
    "A" to ZoneShape(RING_CENTER, RING_RADIUS, startDegrees = 16.5f, endDegrees = -31.9f),
    "B" to ZoneShape(RING_CENTER, RING_RADIUS, startDegrees = 227.9f, endDegrees = 205.3f),
)

/** Width to height ratio of the schematic back panel. */
private const val PANEL_ASPECT = 0.46f

/** Opacity of a segment: its brightness as a share of the maximum. */
internal fun segmentAlpha(brightness: Int): Float =
    (brightness / Light.MAX.toFloat()).coerceIn(0f, 1f)

/**
 * Pixel point on [shape]'s circle at [degrees] (mathematical convention, Y up).
 * Screen Y points down, so the sine term is subtracted.
 */
internal fun arcPoint(shape: ZoneShape, degrees: Float, width: Float, height: Float): Offset {
    val angle = degrees * (PI.toFloat() / 180f)
    return Offset(
        x = shape.center.x * width + shape.radius * width * cos(angle),
        y = shape.center.y * height - shape.radius * width * sin(angle),
    )
}

/** Angle in degrees of segment number [order] out of [count] along [shape]. */
internal fun segmentAngle(shape: ZoneShape, order: Int, count: Int): Float {
    val t = if (count <= 1) 0.5f else order / (count - 1).toFloat()
    return shape.startDegrees + (shape.endDegrees - shape.startDegrees) * t
}

/** Pixel position of every segment index that [ZONE_SHAPES] knows how to place. */
internal fun segmentPositions(layout: DeviceLayout, width: Float, height: Float): Map<Int, Offset> =
    segmentAngles(layout).mapValues { (_, placed) ->
        arcPoint(placed.first, placed.second, width, height)
    }

/** Shape and angle of every placeable segment index. */
private fun segmentAngles(layout: DeviceLayout): Map<Int, Pair<ZoneShape, Float>> {
    val placed = mutableMapOf<Int, Pair<ZoneShape, Float>>()
    for (zone in layout.zones) {
        val shape = ZONE_SHAPES[zone.id] ?: continue
        val count = zone.indices.size
        zone.indices.forEachIndexed { order, segment ->
            placed[segment] = shape to segmentAngle(shape, order, count)
        }
    }
    return placed
}

@Composable
fun GlyphPreview(frame: GlyphFrameData, layout: DeviceLayout, modifier: Modifier = Modifier) {
    val panelColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val idleColor = MaterialTheme.colorScheme.onSurface
    val litColor = MaterialTheme.colorScheme.primary
    val cameraColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.22f)
    val lit = (0 until frame.size).count { frame[it] > 0 }
    val description = stringResource(R.string.preview_state, lit, layout.segmentCount)

    Canvas(
        modifier = modifier
            .aspectRatio(PANEL_ASPECT)
            .testTag("glyph_preview")
            .semantics { stateDescription = description },
    ) {
        drawRoundRect(color = panelColor, size = size, cornerRadius = CornerRadius(size.width * 0.14f))
        drawCircle(
            color = cameraColor,
            radius = CAMERA_RADIUS * size.width,
            center = Offset(RING_CENTER.x * size.width, RING_CENTER.y * size.height),
        )
        val strokeWidth = size.width * 0.035f
        for ((segment, placed) in segmentAngles(layout)) {
            val (shape, angle) = placed
            val zoneCount = layout.zones.first { segment in it.indices }.indices.size
            val step = abs(shape.endDegrees - shape.startDegrees) / max(zoneCount - 1, 1)
            // A tangent stroke covers most of the angular step, leaving a hairline gap.
            val half = step * 0.42f
            val from = arcPoint(shape, angle + half, size.width, size.height)
            val to = arcPoint(shape, angle - half, size.width, size.height)
            drawLine(idleColor.copy(alpha = 0.12f), from, to, strokeWidth)
            val brightness = if (segment < frame.size) frame[segment] else 0
            if (brightness > 0) {
                drawLine(litColor.copy(alpha = segmentAlpha(brightness)), from, to, strokeWidth)
            }
        }
    }
}
