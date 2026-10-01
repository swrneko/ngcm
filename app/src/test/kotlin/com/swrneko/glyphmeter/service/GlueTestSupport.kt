package com.swrneko.glyphmeter.service

import android.content.Context
import com.swrneko.glyphmeter.access.DebugModeWriter
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.ShizukuPermission
import com.swrneko.glyphmeter.access.ShizukuPermissionClient
import com.swrneko.glyphmeter.charging.ChargingStateSource
import com.swrneko.glyphmeter.charging.FakeChargingStateSource
import com.swrneko.glyphmeter.di.AppModule
import com.swrneko.glyphmeter.di.BindingsModule
import com.swrneko.glyphmeter.di.GlyphDispatcher
import com.swrneko.glyphmeter.di.IoDispatcher
import com.swrneko.glyphmeter.hardware.FakeGlyphDisplay
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.layout.DeviceLayouts
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.orientation.OrientationSource
import com.swrneko.glyphmeter.settings.FakeSettingsRepository
import com.swrneko.glyphmeter.settings.SettingsRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import java.util.concurrent.atomic.AtomicInteger

/** Debug-mode writer that counts how often the app asked it to switch the flag on. */
class CountingDebugModeWriter : DebugModeWriter {
    val enableCalls = AtomicInteger(0)
    override val isAvailable: Boolean = true
    override fun isDebugModeOn(): Boolean = true
    override fun enableDebugMode(): Boolean {
        enableCalls.incrementAndGet()
        return true
    }
}

/**
 * The system boundary of the app for glue tests: the Glyph SDK, the battery, the sensors and
 * the phone model are replaced, everything between them (service, receiver, controller,
 * orchestrator, access manager) is the real code wired by the real Hilt graph.
 * Call [reset] before every test.
 */
object GlueFakes {
    lateinit var display: FakeGlyphDisplay
    lateinit var charging: FakeChargingStateSource
    lateinit var settings: FakeSettingsRepository
    lateinit var writer: CountingDebugModeWriter

    var layout: DeviceLayout? = DeviceLayouts.PHONE_3A

    /** Runs inside [GlyphAccessManager.isDeviceSupported], so a test can make `evaluate()` throw. */
    var onEvaluate: () -> Boolean = { true }

    fun reset() {
        display = FakeGlyphDisplay()
        charging = FakeChargingStateSource()
        settings = FakeSettingsRepository()
        writer = CountingDebugModeWriter()
        layout = DeviceLayouts.PHONE_3A
        onEvaluate = { true }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [AppModule::class, BindingsModule::class])
object GlueTestModule {

    @Provides fun display(): GlyphDisplay = GlueFakes.display

    @Provides fun charging(): ChargingStateSource = GlueFakes.charging

    @Provides fun settings(): SettingsRepository = GlueFakes.settings

    @Provides fun orientation(): OrientationSource = OrientationSource.NeverFaceUp

    @Provides fun layout(): DeviceLayout? = GlueFakes.layout

    @Provides fun accessManager(): GlyphAccessManager = GlyphAccessManager(
        writers = listOf(GlueFakes.writer),
        isDeviceSupported = { GlueFakes.onEvaluate() },
        requiresDebugMode = { true },
    )

    @Provides fun shizuku(): ShizukuPermission = ShizukuPermissionClient()

    @Provides fun controller(@ApplicationContext context: Context): MeterServiceController =
        ContextMeterServiceController(context)

    @Provides @IoDispatcher fun io(): CoroutineDispatcher = Dispatchers.IO

    // One thread, like production: the service depends on the single-writer guarantee.
    private val glyphThread: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)

    @Provides @GlyphDispatcher fun glyph(): CoroutineDispatcher = glyphThread
}

/** Polls a condition from the test thread; the service works on its own threads. */
fun awaitCondition(timeoutMillis: Long = 5_000, condition: () -> Boolean): Boolean {
    val deadline = System.currentTimeMillis() + timeoutMillis
    while (System.currentTimeMillis() < deadline) {
        if (condition()) return true
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        Thread.sleep(10)
    }
    return condition()
}
