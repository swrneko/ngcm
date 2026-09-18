package com.swrneko.glyphmeter.hardware

import android.content.Context
import android.util.Log
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphException
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

    private var manager: GlyphManager? = null
    private var layout: DeviceLayout? = null
    private var sessionOpen = false

    override suspend fun connect(layout: DeviceLayout): Result<Unit> {
        if (sessionOpen && this.layout == layout) return Result.success(Unit)

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
                        sessionOpen = false
                        _capability.value = RenderCapability.UNAVAILABLE
                    }
                })
            }
        } ?: return fail("timed out waiting for the Glyph service")

        if (!bound) return fail("Glyph service refused the connection")

        if (!instance.register(deviceId)) {
            return fail("register($deviceId) was rejected; check the Glyph permission and debug mode")
        }

        return try {
            instance.openSession()
            manager = instance
            sessionOpen = true
            _capability.value = RenderCapability.PER_SEGMENT
            Result.success(Unit)
        } catch (e: GlyphException) {
            fail("openSession failed: ${e.message}")
        }
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
        val lit = meter.count { frame[it] > 0 }
        val percent = (lit * 100) / meter.size

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
        runCatching {
            if (sessionOpen) manager?.closeSession()
            manager?.unInit()
        }.onFailure { Log.w(TAG, "disconnect failed", it) }

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
