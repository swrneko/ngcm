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
import kotlin.math.cos
import kotlin.math.sin

/** Where the segments of one zone sit on the back panel. Coordinates are fractions of the panel. */
internal sealed interface ZoneShape {

    /** Segments spread evenly from [from] to [to]. */
    data class Line(val from: Offset, val to: Offset) : ZoneShape

    /**
     * Segments spread evenly along an arc. [radius] is a fraction of the panel width;
     * angles are in degrees, 0 pointing right and positive turning clockwise on screen.
     */
    data class Arc(
        val center: Offset,
        val radius: Float,
        val startDegrees: Float,
        val sweepDegrees: Float,
    ) : ZoneShape
}

/**
 * Preview geometry, one entry per zone id. A layout whose zones are all listed here needs
 * no change to the drawing code. Zones missing from the table are simply not drawn.
 */
internal val ZONE_SHAPES: Map<String, ZoneShape> = mapOf(
    "C" to ZoneShape.Line(from = Offset(0.14f, 0.86f), to = Offset(0.14f, 0.14f)),
    "A" to ZoneShape.Arc(center = Offset(0.3f, 0.3f), radius = 0.5f, startDegrees = -70f, sweepDegrees = 140f),
    "B" to ZoneShape.Line(from = Offset(0.4f, 0.9f), to = Offset(0.72f, 0.9f)),
)

/** Width to height ratio of the schematic back panel. */
private const val PANEL_ASPECT = 0.5f

/** Opacity of a segment: its brightness as a share of the maximum. */
internal fun segmentAlpha(brightness: Int): Float =
    (brightness / Light.MAX.toFloat()).coerceIn(0f, 1f)

/** Pixel position of every segment index that [ZONE_SHAPES] knows how to place. */
internal fun segmentPositions(layout: DeviceLayout, width: Float, height: Float): Map<Int, Offset> {
    val positions = mutableMapOf<Int, Offset>()
    for (zone in layout.zones) {
        val shape = ZONE_SHAPES[zone.id] ?: continue
        val count = zone.indices.size
        zone.indices.forEachIndexed { order, segment ->
            val t = if (count <= 1) 0.5f else order / (count - 1).toFloat()
            positions[segment] = when (shape) {
                is ZoneShape.Line -> Offset(
                    x = (shape.from.x + (shape.to.x - shape.from.x) * t) * width,
                    y = (shape.from.y + (shape.to.y - shape.from.y) * t) * height,
                )
                is ZoneShape.Arc -> {
                    val angle = (shape.startDegrees + shape.sweepDegrees * t) * (PI.toFloat() / 180f)
                    Offset(
                        x = (shape.center.x + shape.radius * cos(angle)) * width,
                        y = shape.center.y * height + shape.radius * sin(angle) * width,
                    )
                }
            }
        }
    }
    return positions
}

@Composable
fun GlyphPreview(frame: GlyphFrameData, layout: DeviceLayout, modifier: Modifier = Modifier) {
    val panelColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val idleColor = MaterialTheme.colorScheme.onSurface
    val litColor = MaterialTheme.colorScheme.primary
    val lit = (0 until frame.size).count { frame[it] > 0 }
    val description = stringResource(R.string.preview_state, lit, layout.segmentCount)

    Canvas(
        modifier = modifier
            .aspectRatio(PANEL_ASPECT)
            .testTag("glyph_preview")
            .semantics { stateDescription = description },
    ) {
        drawRoundRect(color = panelColor, size = size, cornerRadius = CornerRadius(28.dp.toPx()))
        val dotRadius = size.width * 0.028f
        for ((segment, position) in segmentPositions(layout, size.width, size.height)) {
            drawCircle(color = idleColor.copy(alpha = 0.12f), radius = dotRadius, center = position)
            val brightness = if (segment < frame.size) frame[segment] else 0
            if (brightness > 0) {
                drawCircle(color = litColor.copy(alpha = segmentAlpha(brightness)), radius = dotRadius, center = position)
            }
        }
    }
}
