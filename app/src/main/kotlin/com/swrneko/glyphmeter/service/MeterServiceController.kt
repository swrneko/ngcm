package com.swrneko.glyphmeter.service

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Starts and stops the foreground service that drives the Glyph. A seam so view models stay testable. */
interface MeterServiceController {
    fun start()
    fun stop()
}

class ContextMeterServiceController @Inject constructor(
    @ApplicationContext private val context: Context,
) : MeterServiceController {
    override fun start() = GlyphMeterService.start(context)
    override fun stop() = GlyphMeterService.stop(context)
}
