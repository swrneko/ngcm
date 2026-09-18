package com.swrneko.glyphmeter.hardware

import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * In-memory [GlyphDisplay] for tests.
 *
 * Lives in main rather than test sources because the app module's tests need it too.
 */
class FakeGlyphDisplay : GlyphDisplay {

    private val _capability = MutableStateFlow(RenderCapability.UNKNOWN)
    override val capability: StateFlow<RenderCapability> = _capability.asStateFlow()

    private val _rendered = mutableListOf<GlyphFrameData>()
    val rendered: List<GlyphFrameData> get() = _rendered.toList()

    var isConnected: Boolean = false
        private set

    var turnOffCount: Int = 0
        private set

    /** Result the next [connect] should produce. */
    var connectResult: Result<Unit> = Result.success(Unit)

    /** When true, a successful connect reports [RenderCapability.STEPPED_ONLY]. */
    var failPerSegment: Boolean = false

    fun clearRendered() = _rendered.clear()

    override suspend fun connect(layout: DeviceLayout): Result<Unit> {
        if (connectResult.isFailure) {
            isConnected = false
            _capability.value = RenderCapability.UNAVAILABLE
            return connectResult
        }
        isConnected = true
        _capability.value =
            if (failPerSegment) RenderCapability.STEPPED_ONLY else RenderCapability.PER_SEGMENT
        return connectResult
    }

    override fun render(frame: GlyphFrameData) {
        if (isConnected) _rendered += frame
    }

    override fun turnOff() {
        turnOffCount++
        _rendered.clear()
    }

    override fun disconnect() {
        isConnected = false
        _capability.value = RenderCapability.UNKNOWN
    }
}
