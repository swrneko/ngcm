package com.swrneko.glyphmeter.ui.preview

import com.swrneko.glyphmeter.animation.AnimationPreset
import com.swrneko.glyphmeter.animation.PresetFrameSource
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Plays an animation preset on the on-screen preview only; it never touches the hardware.
 *
 * [frame] is null while nothing is playing.
 */
class PresetPreviewPlayer(
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long,
    private val frameIntervalMillis: Long = 16,
) {
    private val _frame = MutableStateFlow<GlyphFrameData?>(null)
    val frame: StateFlow<GlyphFrameData?> = _frame.asStateFlow()

    private var job: Job? = null

    /** Starts [preset], replacing any animation that is still running. */
    fun play(preset: AnimationPreset, layout: DeviceLayout, brightness: Int) {
        job?.cancel()
        val source = PresetFrameSource(preset, layout, brightness)
        job = scope.launch {
            val start = nowMillis()
            try {
                while (true) {
                    val elapsed = nowMillis() - start
                    if (elapsed >= source.durationMillis) break
                    _frame.value = source.frameAt(elapsed)
                    delay(frameIntervalMillis)
                }
            } finally {
                _frame.value = null
            }
        }
    }
}
