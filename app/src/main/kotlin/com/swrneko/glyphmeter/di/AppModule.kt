package com.swrneko.glyphmeter.di

import android.content.Context
import android.os.Build
import com.swrneko.glyphmeter.access.DebugModeWriter
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.SecureSettingsDebugModeWriter
import com.swrneko.glyphmeter.access.ShizukuDebugModeWriter
import com.swrneko.glyphmeter.access.requiresDebugMode
import com.swrneko.glyphmeter.hardware.GlyphDeviceDetector
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.hardware.NothingGlyphDisplay
import com.swrneko.glyphmeter.model.DeviceLayout
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGlyphDisplay(@ApplicationContext context: Context): GlyphDisplay =
        NothingGlyphDisplay(context)

    /**
     * Layout of the phone we are running on, or null when it is not a Glyph device we know.
     * Consumers must handle null; there is intentionally no fallback layout.
     */
    @Provides
    fun provideDeviceLayout(): DeviceLayout? = GlyphDeviceDetector.detect()

    @Provides
    @Singleton
    fun provideAccessManager(
        secureSettingsWriter: SecureSettingsDebugModeWriter,
        shizukuWriter: ShizukuDebugModeWriter,
    ): GlyphAccessManager {
        val writers: List<DebugModeWriter> = listOf(secureSettingsWriter, shizukuWriter)
        return GlyphAccessManager(
            writers = writers,
            isDeviceSupported = { GlyphDeviceDetector.detect() != null },
            requiresDebugMode = { requiresDebugMode(Build.VERSION.SDK_INT) },
        )
    }

    // SINGLE-THREAD REQUIREMENT, DO NOT "OPTIMISE" AWAY, AND DO NOT CREATE ANOTHER ONE:
    // every limitedParallelism(1) call makes an INDEPENDENT one-thread context, so this must
    // stay a single application-wide singleton shared by all service instances. Otherwise a
    // stopping service (turnOff + disconnect) and a freshly started one (connect + render)
    // would enter the Nothing SDK concurrently and the old disconnect would kill the new
    // session. The orchestrator also keeps unguarded state (heldFrame, shownLevel, ...)
    // shared by its two coroutines and its check-then-render is not atomic; on a
    // multi-threaded dispatcher the glyph could stay lit after charging stops.
    @OptIn(ExperimentalCoroutinesApi::class)
    @Provides
    @Singleton
    @GlyphDispatcher
    fun provideGlyphDispatcher(): CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
}
