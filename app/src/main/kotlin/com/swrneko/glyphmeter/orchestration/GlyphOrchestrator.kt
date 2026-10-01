package com.swrneko.glyphmeter.orchestration

import com.swrneko.glyphmeter.animation.AnimationPreset
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.Easing
import com.swrneko.glyphmeter.animation.MeterTransition
import com.swrneko.glyphmeter.animation.PresetFrameSource
import com.swrneko.glyphmeter.charging.ChargingState
import com.swrneko.glyphmeter.charging.ChargingStateSource
import com.swrneko.glyphmeter.charging.PowerSource
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import com.swrneko.glyphmeter.settings.SettingsRepository
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LEVEL_TRANSITION_MILLIS = 500L
private const val REFRESH_INTERVAL_MILLIS = 500L

/**
 * Decides what the Glyph shows and when.
 *
 * Deliberately free of Android types so it can be driven by fakes on virtual time.
 * Owns no clock of its own: every wait goes through [delay], which `runTest` controls.
 */
class GlyphOrchestrator(
    private val display: GlyphDisplay,
    private val chargingSource: ChargingStateSource,
    private val settingsRepository: SettingsRepository,
    private val layout: DeviceLayout,
    private val frameIntervalMillis: Long = 16,
) {

    private var shownLevel: Float = 0f
    private var lastTriggerLevel: Float? = null
    private var wasCharging = false

    /** Frame the always-on refresher re-sends; null whenever something else owns the display. */
    private var heldFrame: GlyphFrameData? = null

    suspend fun run() {
        display.connect(layout).onFailure { return }

        coroutineScope {
            launch { refreshHeldFrame() }
            observe()
        }
    }

    private suspend fun observe() {
        combine(chargingSource.state, settingsRepository.settings) { charging, settings ->
            charging to settings
        }.collect { (charging, settings) ->
            handle(charging, settings)
        }
    }

    /** Re-sends the held frame so an always-on meter is not left to the hardware's own timeout. */
    private suspend fun refreshHeldFrame() {
        while (true) {
            delay(REFRESH_INTERVAL_MILLIS)
            heldFrame?.let { display.render(it) }
        }
    }

    private suspend fun handle(charging: ChargingState, settings: GlyphSettings) {
        heldFrame = null
        if (!settings.enabled) {
            val wasOn = wasCharging
            wasCharging = false
            lastTriggerLevel = null
            shownLevel = 0f
            if (wasOn) stop()
            return
        }

        if (!charging.isCharging) {
            val fade = wasCharging
            wasCharging = false
            lastTriggerLevel = null
            shownLevel = 0f
            if (fade) {
                play(PresetFrameSource(AnimationPresets.FADE_OUT, layout, settings.brightness))
                stop()
            }
            return
        }

        val justPlugged = !wasCharging
        wasCharging = true

        if (justPlugged) {
            shownLevel = 0f
            lastTriggerLevel = charging.level
            play(PresetFrameSource(presetFor(charging, settings), layout, settings.brightness))
            showMeter(charging, settings)
            return
        }

        when (settings.meterMode) {
            MeterMode.ALWAYS_ON -> showMeter(charging, settings)

            MeterMode.ON_EVENT -> {
                val since = lastTriggerLevel ?: charging.level
                val gainedPercent = ((charging.level - since) * 100).roundToInt()

                if (abs(gainedPercent) >= settings.repeatStepPercent) {
                    lastTriggerLevel = charging.level
                    showMeter(charging, settings)
                }
            }
        }
    }

    /** Animates the meter to [charging]'s level, then holds or fades depending on the mode. */
    private suspend fun showMeter(charging: ChargingState, settings: GlyphSettings) {
        val transition = MeterTransition(
            fromLevel = shownLevel,
            toLevel = charging.level,
            layout = layout,
            brightness = settings.brightness,
            durationMillis = LEVEL_TRANSITION_MILLIS,
            easing = Easing.EaseOutCubic,
        )
        play(transition)
        shownLevel = charging.level

        if (settings.meterMode == MeterMode.ALWAYS_ON) {
            heldFrame = transition.frameAt(transition.durationMillis)
        } else {
            hold(settings.showDurationMillis, transition)
            play(PresetFrameSource(AnimationPresets.FADE_OUT, layout, settings.brightness))
            stop()
            shownLevel = 0f
        }
    }

    /** Keeps the final frame on screen without spinning the CPU. */
    private suspend fun hold(durationMillis: Long, source: MeterTransition) {
        display.render(source.frameAt(source.durationMillis))
        delay(durationMillis)
    }

    private suspend fun play(source: com.swrneko.glyphmeter.animation.FrameSource) {
        var elapsed = 0L
        while (elapsed <= source.durationMillis && currentCoroutineContext().isActive) {
            display.render(source.frameAt(elapsed))
            delay(frameIntervalMillis)
            elapsed += frameIntervalMillis
        }
    }

    private fun stop() {
        display.turnOff()
    }

    private fun presetFor(charging: ChargingState, settings: GlyphSettings): AnimationPreset {
        val id = when {
            charging.level >= 1f -> settings.fullPresetId
            charging.source == PowerSource.WIRELESS -> settings.wirelessPresetId
            else -> settings.wiredPresetId
        }
        return AnimationPresets.byId(id) ?: AnimationPresets.FILL_UP
    }
}
