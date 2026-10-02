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
import com.swrneko.glyphmeter.orientation.OrientationSource
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import com.swrneko.glyphmeter.settings.SettingsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
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

/** How long a dark, idle session is kept before it is let go; a quick replug reuses it. */
private const val IDLE_RELEASE_MILLIS = 2_000L

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
 *
 * With [GlyphSettings.dimWhenFaceUp] on, the Glyph stays dark while [orientationSource] says the
 * phone lies screen up. Orientation is collected only while charging with that setting on, so
 * the sensor behind it never runs otherwise.
 *
 * The Glyph session is held only while something is shown. An open session keeps Nothing's own
 * Glyph features, such as the Essential notification light, from lighting up, so once the glyph
 * has gone dark and stayed idle for [idleReleaseMillis] the session is closed, and the next show
 * opens it again (re-arming access first). An always-on meter holds it for the whole charge.
 * The first connect in [run] stays: it is what tells the user early that access is missing.
 *
 * [previewRequests] carries preset ids the user asked to see on the Glyph. They are handled in
 * the same sequence as battery and settings events, so the UI never writes to the display. A
 * preview is an explicit action: it is not suppressed by [GlyphSettings.dimWhenFaceUp] and plays
 * without charging too. It cancels the running show (or an earlier preview), plays the preset as
 * tuned in [GlyphSettings.animationParams] and then goes back to the meter if charging, or to
 * darkness if not.
 * It is ignored while the app is switched off.
 */
class GlyphOrchestrator(
    private val display: GlyphDisplay,
    private val chargingSource: ChargingStateSource,
    private val settingsRepository: SettingsRepository,
    private val layout: DeviceLayout,
    private val orientationSource: OrientationSource = OrientationSource.NeverFaceUp,
    private val rearmAccess: suspend () -> Unit = {},
    private val previewRequests: Flow<String> = emptyFlow(),
    private val reconnectDelaysMillis: List<Long> = DEFAULT_RECONNECT_DELAYS_MILLIS,
    private val stableSessionMillis: Long = STABLE_SESSION_MILLIS,
    private val idleReleaseMillis: Long = IDLE_RELEASE_MILLIS,
    private val frameIntervalMillis: Long = 16,
) {

    private var shownLevel: Float = 0f
    private var lastTriggerLevel: Float? = null
    private var wasCharging = false

    /** The glyph is held dark because the phone lies face up. */
    private var dimmed = false

    /** The full-charge preset has played in this charging session; reset on unplug. */
    private var fullPlayed = false
    private var showJob: Job? = null

    /** A preview started by a request is playing, so non-charging events must not cut it short. */
    private var previewActive = false

    /** Latest battery and settings seen by [observe]; null until the first event. */
    private var latest: Pair<ChargingState, GlyphSettings>? = null

    private sealed interface Event {
        data class State(val charging: ChargingState, val settings: GlyphSettings, val faceUp: Boolean) : Event
        data class Preview(val presetId: String) : Event
    }

    /** Frame the always-on refresher re-sends; null whenever something else owns the display. */
    private var heldFrame: GlyphFrameData? = null

    /** Scope of [run]; idle releases are launched in it. */
    private lateinit var sessionScope: CoroutineScope

    /** Pending close of an idle session; cancelled as soon as something is to be shown. */
    private var releaseJob: Job? = null

    suspend fun run() {
        rearm()
        display.connect(layout).onFailure { return }

        coroutineScope {
            sessionScope = this
            scheduleRelease()
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
            releaseJob?.cancel()
        }
    }

    /** Waits for a lost session and reconnects it, until the attempts for one outage run out. */
    private suspend fun keepConnected() {
        while (true) {
            display.capability.first { it == RenderCapability.UNAVAILABLE }
            if (!reconnect()) return
            // Nothing may need the session the reconnect brought back.
            scheduleRelease()
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

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observe(scope: CoroutineScope) {
        val states: Flow<Event> = combine(chargingSource.state, settingsRepository.settings) { charging, settings ->
            charging to settings
        }.flatMapLatest { (charging, settings) ->
            if (charging.isCharging && settings.enabled && settings.dimWhenFaceUp) {
                // Nothing is decided until the sensor has spoken, so a phone plugged in face up
                // never flashes the plug-in animation first.
                orientationSource.isFaceUp.distinctUntilChanged().map { Triple(charging, settings, it) }
            } else {
                flowOf(Triple(charging, settings, false))
            }
        }.map { (charging, settings, faceUp) -> Event.State(charging, settings, faceUp) }

        // One collector for both, so a preview and a battery event are never handled concurrently.
        merge(states, previewRequests.map { Event.Preview(it) }).collect { event ->
            when (event) {
                is Event.State -> {
                    latest = event.charging to event.settings
                    handle(scope, event.charging, event.settings, event.faceUp)
                }

                is Event.Preview -> startPreview(scope, event.presetId)
            }
        }
    }

    private suspend fun startPreview(scope: CoroutineScope, presetId: String) {
        val (state, settings) = latest ?: return
        if (!settings.enabled) return
        val preset = AnimationPresets.byId(presetId) ?: return

        heldFrame = null
        cancelShow()
        previewActive = true
        startShow(scope) {
            try {
                val source = settings.frameSource(preset, layout, state.level)
                play(source)
                previewActive = false
                val (charging, current) = latest ?: return@startShow
                if (wasCharging && !dimmed) {
                    shownLevel = source.handoffLevel ?: 0f
                    showMeter(charging, current)
                }
            } finally {
                previewActive = false
                // While charging, whatever takes over (the meter, a plug-in animation) owns the
                // display from here; otherwise nothing may be left lit.
                if (!wasCharging || dimmed) stop()
            }
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
        faceUp: Boolean,
    ) {
        heldFrame = null

        if (!settings.enabled || !charging.isCharging) {
            // A preview on an idle phone is not a charging session: settings or battery noise
            // must not cut it short. Switching the app off still does.
            if (!wasCharging && previewActive && settings.enabled) return
            val wasOn = wasCharging
            val wasDimmed = dimmed
            wasCharging = false
            dimmed = false
            fullPlayed = false
            lastTriggerLevel = null
            shownLevel = 0f
            cancelShow()
            if (!wasOn) return
            if (!settings.enabled || wasDimmed) {
                // A dimmed glyph is already dark; a fade-out would light it up just to fade.
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

        if (settings.dimWhenFaceUp && faceUp) {
            if (!wasCharging) {
                // Plugged in face up: the session starts, silently, but still re-arms access.
                wasCharging = true
                lastTriggerLevel = charging.level
                fullPlayed = charging.level >= 1f
                scope.launch { rearm() }
            }
            if (!dimmed) {
                dimmed = true
                cancelShow()
                shownLevel = 0f
                stop()
            }
            return
        }

        if (dimmed) {
            // Turned back over: show where the charge is now, without replaying the plug-in.
            dimmed = false
            cancelShow()
            lastTriggerLevel = charging.level
            startShow(scope) { showMeter(charging, settings) }
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
            startShow(scope) { opened ->
                // Every plug-in re-arms access once; opening the session has done it already.
                if (!opened) rearm()
                playThenHandOver(settings.frameSource(presetFor(charging, settings), layout, charging.level))
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
                playThenHandOver(settings.frameSource(fullPreset(settings), layout, charging.level))
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

    /**
     * Starts a show once the session is open; a session that will not open skips the show.
     * [block] learns whether the session had to be opened for it, which re-armed access already.
     */
    private fun startShow(scope: CoroutineScope, block: suspend (opened: Boolean) -> Unit) {
        releaseJob?.cancel()
        releaseJob = null
        showJob = scope.launch {
            val wasOpen = isOpen()
            if (ensureConnected()) block(!wasOpen)
        }
    }

    private fun isOpen(): Boolean =
        display.capability.value.let { it == RenderCapability.PER_SEGMENT || it == RenderCapability.STEPPED_ONLY }

    /**
     * Opens the session for a show unless it is already open. A refused connect is left to
     * [keepConnected], which sees [RenderCapability.UNAVAILABLE] and spends the bounded attempts.
     */
    private suspend fun ensureConnected(): Boolean {
        if (isOpen()) return true
        rearm()
        return display.connect(layout).isSuccess
    }

    /** Closes the session after [idleReleaseMillis], unless something is shown by then. */
    private fun scheduleRelease() {
        releaseJob?.cancel()
        releaseJob = sessionScope.launch {
            delay(idleReleaseMillis)
            val busy = heldFrame != null || showJob?.isActive == true
            if (!busy) display.disconnect()
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
            try {
                play(PresetFrameSource(AnimationPresets.FADE_OUT, layout, settings.brightness))
            } finally {
                stop()
                shownLevel = 0f
            }
        }
    }

    /**
     * Plays a plug-in animation and leaves [shownLevel] where it ended, so the meter that follows
     * rises from darkness, or carries on from the frame a fill to the charge level stopped on.
     */
    private suspend fun playThenHandOver(source: PresetFrameSource) {
        play(source)
        shownLevel = source.handoffLevel ?: 0f
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
        scheduleRelease()
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
