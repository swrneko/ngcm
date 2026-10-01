package com.swrneko.glyphmeter.hardware

import android.content.Context
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphManager
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

private const val TAG = "NothingGlyphDisplay"
private const val SERVICE_CONNECT_TIMEOUT_MS = 5_000L

/**
 * Real [GlyphDisplay], backed by `glyph-matrix-sdk-2.0.aar`.
 *
 * A frame maps onto the SDK one index at a time: `buildChannel(i, light)` performs
 * `channel.set(i, light)` internally, so [GlyphFrameData] needs no translation.
 *
 * The per-segment overload is undocumented. If a Nothing OS update removes it, the
 * first render throws and this class permanently downgrades to
 * [RenderCapability.STEPPED_ONLY] rather than going dark.
 */
class NothingGlyphDisplay(private val context: Context) : GlyphDisplay {

    private val _capability = MutableStateFlow(RenderCapability.UNKNOWN)
    override val capability: StateFlow<RenderCapability> = _capability.asStateFlow()

    // Volatile: the SDK reports a lost service on the main thread, everything else runs on the
    // glyph dispatcher.
    @Volatile private var manager: GlyphManager? = null
    private var layout: DeviceLayout? = null
    @Volatile private var sessionOpen = false

    override suspend fun connect(layout: DeviceLayout): Result<Unit> {
        val usable = _capability.value != RenderCapability.UNAVAILABLE
        if (sessionOpen && this.layout == layout && usable) return Result.success(Unit)
        // Reconnecting with a different layout, or over a session whose rendering has failed
        // completely, must not stack a second session on top of the one already open: close it
        // first so the SDK never sees two openSession calls without a closeSession between them.
        if (sessionOpen) disconnect()

        this.layout = layout
        val deviceId = resolveDeviceId(layout)
            ?: return fail("unsupported device id ${layout.deviceId}")

        val instance = GlyphManager.getInstance(context.applicationContext)
            ?: return fail("GlyphManager.getInstance returned null")

        val bound = withTimeoutOrNull(SERVICE_CONNECT_TIMEOUT_MS) {
            suspendCancellableCoroutine<Boolean> { continuation ->
                instance.init(object : GlyphManager.Callback {
                    override fun onServiceConnected(name: android.content.ComponentName?) {
                        if (continuation.isActive) continuation.resume(true)
                    }

                    override fun onServiceDisconnected(name: android.content.ComponentName?) {
                        onSessionLost(instance)
                    }
                })
                // If the caller's coroutine is cancelled (navigated away) or the timeout
                // below fires, the callback registered above must not stay bound forever:
                // unInit() unregisters it from the GlyphManager singleton.
                continuation.invokeOnCancellation {
                    runCatching { instance.unInit() }
                        .onFailure { Log.w(TAG, "unInit after cancelled connect failed", it) }
                }
            }
        } ?: return fail("timed out waiting for the Glyph service")

        if (!bound) return failAndRelease(instance, "Glyph service refused the connection")

        // From here on the service is bound: every failure must unbind it again, or each failed
        // attempt would leave one more ServiceConnection registered in the GlyphManager singleton.
        if (!instance.register(deviceId)) {
            return failAndRelease(instance, "register($deviceId) was rejected; check the Glyph permission and debug mode")
        }

        return try {
            instance.openSession()
            manager = instance
            sessionOpen = true
            _capability.value = RenderCapability.PER_SEGMENT
            Result.success(Unit)
        } catch (e: Throwable) {
            failAndRelease(instance, "openSession failed: ${e.message}")
        }
    }

    /**
     * The Glyph service went away under an open session. Android would keep the dead binding
     * and silently rebind later, so it is released here and the next [connect] starts clean.
     * Capability is published last, so whoever reacts to [RenderCapability.UNAVAILABLE] by
     * reconnecting never races the unbind.
     */
    private fun onSessionLost(instance: GlyphManager) {
        // Only a live session can be lost. A failed or finished connect has already unbound;
        // reacting again would unbind twice.
        if (manager !== instance) return
        Log.w(TAG, "Glyph service disconnected")
        sessionOpen = false
        manager = null
        runCatching { instance.unInit() }
            .onFailure { Log.w(TAG, "unInit after a lost session failed", it) }
        _capability.value = RenderCapability.UNAVAILABLE
    }

    private fun failAndRelease(instance: GlyphManager, reason: String): Result<Unit> {
        runCatching { instance.unInit() }
            .onFailure { Log.w(TAG, "unInit after a failed connect failed", it) }
        return fail(reason)
    }

    override fun render(frame: GlyphFrameData) {
        val manager = manager ?: return
        val layout = layout ?: return
        if (!sessionOpen) return
        if (frame.size != layout.segmentCount) {
            Log.w(TAG, "frame of ${frame.size} segments does not fit ${layout.segmentCount}")
            return
        }

        when (_capability.value) {
            RenderCapability.PER_SEGMENT -> renderPerSegment(manager, frame, layout)
            RenderCapability.STEPPED_ONLY -> renderStepped(manager, frame, layout)
            else -> Unit
        }
    }

    private fun renderPerSegment(manager: GlyphManager, frame: GlyphFrameData, layout: DeviceLayout) {
        try {
            val builder = manager.glyphFrameBuilder
            for (index in 0 until layout.segmentCount) {
                builder.buildChannel(index, frame[index])
            }
            manager.toggle(builder.build())
        } catch (e: Throwable) {
            // The undocumented overload is gone, or the channel list is a different size.
            Log.w(TAG, "per-segment rendering failed, falling back to stepped output", e)
            _capability.value = RenderCapability.STEPPED_ONLY
            renderStepped(manager, frame, layout)
        }
    }

    private fun renderStepped(manager: GlyphManager, frame: GlyphFrameData, layout: DeviceLayout) {
        val meter = layout.meterZone.indices
        val percent = steppedMeterPercent(meterSize = meter.size, litCount = meter.count { frame[it] > 0 })

        try {
            val builder = manager.glyphFrameBuilder
            builder.buildChannel(layout.progressAnchorIndex)
            manager.displayProgress(builder.build(), percent)
        } catch (e: Throwable) {
            Log.e(TAG, "stepped rendering failed too", e)
            _capability.value = RenderCapability.UNAVAILABLE
        }
    }

    override fun turnOff() {
        runCatching { manager?.turnOff() }
            .onFailure { Log.w(TAG, "turnOff failed", it) }
    }

    override fun disconnect() {
        // unInit() must run even when closeSession() throws (it declares GlyphException):
        // otherwise a failed close would leave the service binding and callback registered
        // forever. Each step gets its own runCatching so a failure in one never skips the other.
        if (sessionOpen) {
            runCatching { manager?.closeSession() }
                .onFailure { Log.w(TAG, "closeSession failed", it) }
        }
        runCatching { manager?.unInit() }
            .onFailure { Log.w(TAG, "unInit failed", it) }

        sessionOpen = false
        manager = null
        _capability.value = RenderCapability.UNKNOWN
    }

    private fun fail(reason: String): Result<Unit> {
        Log.w(TAG, reason)
        sessionOpen = false
        _capability.value = RenderCapability.UNAVAILABLE
        return Result.failure(IllegalStateException(reason))
    }

    private fun resolveDeviceId(layout: DeviceLayout): String? = when (layout.deviceId) {
        "24111" -> Glyph.DEVICE_24111
        else -> null
    }
}

/**
 * Percentage of the meter zone considered lit, used by the stepped fallback renderer.
 *
 * Pulled out as a plain top-level function (no Glyph SDK types involved) so it stays
 * unit-testable even though [NothingGlyphDisplay] itself is not: an empty meter zone
 * must yield 0 rather than divide by zero, since this path is the last line of defense
 * and must never throw.
 */
internal fun steppedMeterPercent(meterSize: Int, litCount: Int): Int =
    if (meterSize == 0) 0 else (litCount * 100) / meterSize
