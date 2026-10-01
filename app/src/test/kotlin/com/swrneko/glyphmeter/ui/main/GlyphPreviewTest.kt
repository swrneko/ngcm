package com.swrneko.glyphmeter.ui.main

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.Light
import kotlin.math.hypot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** Renders the real [GlyphPreview] under Robolectric. */
@RunWith(RobolectricTestRunner::class)
class GlyphPreviewTest {

    @get:Rule val compose = createComposeRule()

    private val layout = DeviceLayouts.PHONE_3A

    // Ring centre for a 200 x 435 panel, from the geometry notes: 0.49 of width, 0.226 of height.
    private val CENTER_X = 0.49f * 200f
    private val CENTER_Y = 0.226f * 435f

    private fun show(frame: GlyphFrameData) {
        compose.setContent { GlyphPreview(frame = frame, layout = layout, modifier = Modifier) }
    }

    private fun hasLitSegments(text: String) =
        SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, text)

    @Test
    fun it_draws_a_preview_for_a_known_layout() {
        show(MeterRenderer.smooth(0.63f, layout, Light.MAX))

        compose.onNodeWithTag("glyph_preview").assertIsDisplayed()
    }

    @Test
    fun it_reports_how_many_segments_the_frame_lights() {
        // 63 percent of a 20-segment meter: 12 whole segments plus the one still filling.
        show(MeterRenderer.smooth(0.63f, layout, Light.MAX))

        compose.onNodeWithTag("glyph_preview").assert(hasLitSegments("13 of 36 segments lit"))
    }

    @Test
    fun a_dark_frame_lights_nothing() {
        show(GlyphFrameData.off(layout.segmentCount))

        compose.onNodeWithTag("glyph_preview").assert(hasLitSegments("0 of 36 segments lit"))
    }

    @Test
    fun every_segment_of_the_known_layout_has_a_place_on_the_panel() {
        val positions = segmentPositions(layout, width = 200f, height = 400f)

        assertEquals((0 until layout.segmentCount).toSet(), positions.keys)
        assertTrue(positions.values.all { it.x in 0f..200f && it.y in 0f..400f })
    }

    @Test
    fun zone_c_starts_upper_left_of_the_ring_centre_and_ends_above_it() {
        val positions = segmentPositions(layout, width = 200f, height = 435f)
        val first = positions.getValue(0)
        val last = positions.getValue(19)

        assertTrue(first.x < CENTER_X && first.y < CENTER_Y)
        assertTrue(last.y < CENTER_Y)
        assertTrue(last.x > first.x)
    }

    @Test
    fun zone_a_is_right_of_the_ring_centre() {
        val positions = segmentPositions(layout, width = 200f, height = 435f)

        assertTrue((20..30).all { positions.getValue(it).x > CENTER_X })
    }

    @Test
    fun zone_b_is_left_of_and_below_the_ring_centre() {
        val positions = segmentPositions(layout, width = 200f, height = 435f)

        assertTrue((31..35).all { positions.getValue(it).x < CENTER_X && positions.getValue(it).y > CENTER_Y })
    }

    @Test
    fun every_segment_lies_on_the_ring() {
        val positions = segmentPositions(layout, width = 200f, height = 435f)

        positions.values.forEach {
            assertEquals(0.41f * 200f, hypot(it.x - CENTER_X, it.y - CENTER_Y), 0.5f)
        }
    }

    @Test
    fun a_zone_without_geometry_is_skipped_instead_of_crashing() {
        val unknownZone = layout.copy(
            zones = layout.zones + com.swrneko.glyphmeter.model.GlyphZone("Z", listOf(99)),
            segmentCount = 100,
        )

        val positions = segmentPositions(unknownZone, width = 200f, height = 400f)

        assertTrue(99 !in positions.keys)
        assertEquals(36, positions.size)
    }

    @Test
    fun segment_opacity_follows_brightness() {
        assertEquals(0f, segmentAlpha(0), 0f)
        assertEquals(1f, segmentAlpha(Light.MAX), 0f)
        assertEquals(0.5f, segmentAlpha(Light.MAX / 2), 0.001f)
    }
}
