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
import com.swrneko.glyphmeter.hardware.RenderCapability
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import com.swrneko.glyphmeter.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.isActive
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlin.math.abs
import kotlin.math.roundToInt

private const val LEVEL_TRANSITION_MILLIS = 500L
private const val REFRESH_INTERVAL_MILLIS = 500L

/** Pauses before each reconnect attempt after a lost session; their count bounds the attempts. */
val DEFAULT_RECONNECT_DELAYS_MILLIS: List<Long> = listOf(1_000L, 2_000L, 4_000L, 8_000L, 16_000L)

/** A session that survives this long counts as recovered, and the next loss gets a fresh budget. */
private const val STABLE_SESSION_MILLIS = 60_000L

/**
 * Decides what the Glyph shows and when.
 *
 * Deliberately free of Android types so it can be driven by fakes on virtual time.
 * Owns no clock of its own: every wait goes through [delay], which `runTest` controls.
 *
 * [rearmAccess] switches the Glyph debug flag back on when the app is allowed to (it expires
 * after 48 hours on older Nothing OS). It runs before every connect and on every plug-in, so a
 * phone that is never rebooted keeps working. Failures inside it are ignored: connecting is
 * still worth a try.
 *
 * A lost session ([RenderCapability.UNAVAILABLE]) is reconnected with a bounded number of
 * attempts, one after each pause in [reconnectDelaysMillis]. When they are used up [run]
 * returns, so the caller can stop instead of idling as a zombie. A session that drops again
 * within [stableSessionMillis] keeps spending the same budget, so a flapping service cannot
 * make the attempts endless.
 */
class GlyphOrchestrator(
    private val display: GlyphDisplay,
    private val chargingSource: ChargingStateSource,
    private val settingsRepository: SettingsRepository,
    private val layout: DeviceLayout,
    private val rearmAccess: suspend () -> Unit = {},
    private val reconnectDelaysMillis: List<Long> = DEFAULT_RECONNECT_DELAYS_MILLIS,
    private val stableSessionMillis: Long = STABLE_SESSION_MILLIS,
    private val frameIntervalMillis: Long = 16,
) {

    private var shownLevel: Float = 0f
    private var lastTriggerLevel: Float? = null
    private var wasCharging = false

    /** The full-charge preset has played in this charging session; reset on unplug. */
    private var fullPlayed = false
    private var showJob: Job? = null

    /** Frame the always-on refresher re-sends; null whenever something else owns the display. */
    private var heldFrame: GlyphFrameData? = null

    suspend fun run() {
        rearm()
        display.connect(layout).onFailure { return }

        coroutineScope {
            val refresher = launch { refreshHeldFrame() }
            val observer = launch {
                try {
                    observe(this)
                } finally {
                    heldFrame = null
                    stop()
                }
            }
            // Returns only when reconnecting has given up.
            keepConnected()
            observer.cancelAndJoin()
            refresher.cancelAndJoin()
        }
    }

    /** Waits for a lost session and reconnects it, until the attempts for one outage run out. */
    private suspend fun keepConnected() {
        while (true) {
            display.capability.first { it == RenderCapability.UNAVAILABLE }
            if (!reconnect()) return
        }
    }

    /** True once a session is back and has stayed up for [stableSessionMillis]. */
    private suspend fun reconnect(): Boolean {
        for (pause in reconnectDelaysMillis) {
            delay(pause)
            rearm()
            val connected = display.connect(layout).isSuccess &&
                display.capability.value != RenderCapability.UNAVAILABLE
            if (!connected) continue

            val lostAgain = withTimeoutOrNull(stableSessionMillis) {
                display.capability.first { it == RenderCapability.UNAVAILABLE }
            }
            if (lostAgain == null) return true
        }
        return false
    }

    private suspend fun observe(scope: CoroutineScope) {
        combine(chargingSource.state, settingsRepository.settings) { charging, settings ->
            charging to settings
        }.collect { (charging, settings) ->
            handle(scope, charging, settings)
        }
    }

    /** Re-sends the held frame so an always-on meter is not left to the hardware's own timeout. */
    private suspend fun refreshHeldFrame() {
        while (true) {
            delay(REFRESH_INTERVAL_MILLIS)
            heldFrame?.let { display.render(it) }
        }
    }

    private suspend fun handle(
        scope: CoroutineScope,
        charging: ChargingState,
        settings: GlyphSettings,
    ) {
        heldFrame = null

        if (!settings.enabled || !charging.isCharging) {
            val wasOn = wasCharging
            wasCharging = false
            fullPlayed = false
            lastTriggerLevel = null
            shownLevel = 0f
            cancelShow()
            if (!wasOn) return
            if (!settings.enabled) {
                stop()
            } else {
                startShow(scope) {
                    // Darkness is guaranteed even if a newer event cancels the fade.
                    try {
                        play(PresetFrameSource(AnimationPresets.FADE_OUT, layout, settings.brightness))
                    } finally {
                        stop()
                    }
                }
            }
            return
        }

        val justPlugged = !wasCharging
        wasCharging = true

        if (justPlugged) {
            cancelShow()
            shownLevel = 0f
            lastTriggerLevel = charging.level
            // Plugging in at 100% already plays the full-charge preset (see presetFor).
            fullPlayed = charging.level >= 1f
            startShow(scope) {
                rearm()
                play(PresetFrameSource(presetFor(charging, settings), layout, settings.brightness))
                showMeter(charging, settings)
            }
            return
        }

        if (charging.level >= 1f && !fullPlayed) {
            // Reaching 100% while plugged in is an event of its own, in either mode.
            fullPlayed = true
            lastTriggerLevel = charging.level
            cancelShow()
            startShow(scope) {
                play(PresetFrameSource(fullPreset(settings), layout, settings.brightness))
                shownLevel = 0f
                showMeter(charging, settings)
            }
            return
        }

        when (settings.meterMode) {
            MeterMode.ALWAYS_ON -> {
                cancelShow()
                startShow(scope) { showMeter(charging, settings) }
            }

            MeterMode.ON_EVENT -> {
                val since = lastTriggerLevel ?: charging.level
                val gainedPercent = ((charging.level - since) * 100).roundToInt()

                // A small gain must leave a running show alone, otherwise its fade would never happen.
                if (abs(gainedPercent) >= settings.repeatStepPercent) {
                    lastTriggerLevel = charging.level
                    cancelShow()
                    startShow(scope) { showMeter(charging, settings) }
                }
            }
        }
    }

    /** Cancels the running show and waits until it has fully unwound, so it cannot touch the display later. */
    private suspend fun cancelShow() {
        showJob?.cancelAndJoin()
        showJob = null
    }

    private fun startShow(scope: CoroutineScope, block: suspend () -> Unit) {
        showJob = scope.launch { block() }
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
            try {
                play(PresetFrameSource(AnimationPresets.FADE_OUT, layout, settings.brightness))
            } finally {
                stop()
                shownLevel = 0f
            }
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

    private suspend fun rearm() {
        try {
            rearmAccess()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Best effort: without re-arming a connect may still succeed (Nothing OS 4.0+).
        }
    }

    private fun presetFor(charging: ChargingState, settings: GlyphSettings): AnimationPreset {
        val id = when {
            charging.level >= 1f -> return fullPreset(settings)
            charging.source == PowerSource.WIRELESS -> settings.wirelessPresetId
            else -> settings.wiredPresetId
        }
        return AnimationPresets.byId(id) ?: AnimationPresets.FILL_UP
    }

    private fun fullPreset(settings: GlyphSettings): AnimationPreset =
        AnimationPresets.byId(settings.fullPresetId) ?: AnimationPresets.FLASH
}
