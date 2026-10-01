package com.swrneko.glyphmeter.di

import android.content.Context
import android.os.Build
import com.swrneko.glyphmeter.access.DebugModeWriter
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.SecureSettingsDebugModeWriter
import com.swrneko.glyphmeter.access.ShizukuDebugModeWriter
import com.swrneko.glyphmeter.hardware.GlyphDeviceDetector
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.hardware.NothingGlyphDisplay
import com.swrneko.glyphmeter.model.DeviceLayout
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** Nothing OS 4.0 (Android 16, API 36) dropped the developer-key requirement. */
    private const val FIRST_API_WITHOUT_DEBUG_MODE = 36

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
            requiresDebugMode = { Build.VERSION.SDK_INT < FIRST_API_WITHOUT_DEBUG_MODE },
        )
    }
}
