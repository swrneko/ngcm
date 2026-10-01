package com.swrneko.glyphmeter.ui.preview

import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.model.Light
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PresetPreviewPlayerTest {

    private val layout = DeviceLayouts.PHONE_3A

    @Test
    fun nothing_is_playing_until_asked() = runTest {
        val player = PresetPreviewPlayer(backgroundScope, nowMillis = { testScheduler.currentTime })

        assertNull(player.frame.value)
    }

    @Test
    fun a_preset_plays_frames_for_its_duration_and_then_clears() = runTest {
        val player = PresetPreviewPlayer(backgroundScope, nowMillis = { testScheduler.currentTime })
        val preset = AnimationPresets.FLASH

        player.play(preset, layout, Light.MAX)
        runCurrent()
        advanceTimeBy(preset.durationMillis / 4)
        assertNotNull(player.frame.value)

        // The flash peaks around a fifth of the way in, so some frame in the first half is lit.
        var sawLight = false
        repeat(40) {
            advanceTimeBy(8)
            val frame = player.frame.value
            if (frame != null && (0 until frame.size).any { frame[it] > 0 }) sawLight = true
        }
        assertTrue(sawLight)

        advanceTimeBy(preset.durationMillis)
        runCurrent()
        assertNull(player.frame.value)
    }

    @Test
    fun starting_another_preset_replaces_the_running_one() = runTest {
        val player = PresetPreviewPlayer(backgroundScope, nowMillis = { testScheduler.currentTime })

        player.play(AnimationPresets.BREATHE, layout, Light.MAX)
        runCurrent()
        advanceTimeBy(400)
        player.play(AnimationPresets.FLASH, layout, Light.MAX)
        runCurrent()
        advanceTimeBy(AnimationPresets.FLASH.durationMillis + 50)
        runCurrent()

        // Had breathe kept running it would still be playing at 1050 ms of its 1600 ms.
        assertNull(player.frame.value)
    }
}
