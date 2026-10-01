package com.swrneko.glyphmeter.orchestration

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Carries "play this preset on the Glyph" requests from the UI to [GlyphOrchestrator], the only
 * owner of the display. The UI never touches the display itself, so there is a single writer.
 *
 * Free of Android types. Nothing is replayed: a request made while no orchestrator is listening
 * (the service is not running) is dropped and does not fire later, when the service starts.
 * Only the newest unprocessed request is kept, so rapid taps never build a queue.
 */
@Singleton
class PreviewRequestBus @Inject constructor() {

    private val _requests = MutableSharedFlow<String>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** Preset ids, in the order they were requested. */
    val requests: Flow<String> = _requests.asSharedFlow()

    /** Asks for the preset with [presetId] to be played on the Glyph. Never suspends. */
    fun requestPreview(presetId: String) {
        _requests.tryEmit(presetId)
    }
}
