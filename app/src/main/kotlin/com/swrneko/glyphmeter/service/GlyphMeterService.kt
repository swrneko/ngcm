package com.swrneko.glyphmeter.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.swrneko.glyphmeter.R
import com.swrneko.glyphmeter.charging.ChargingStateSource
import com.swrneko.glyphmeter.di.GlyphDispatcher
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.orchestration.GlyphOrchestrator
import com.swrneko.glyphmeter.settings.SettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Provider

private const val CHANNEL_ID = "glyph_meter_service"
private const val NOTIFICATION_ID = 1
private const val TAG = "GlyphMeterService"

@AndroidEntryPoint
class GlyphMeterService : Service() {

    @Inject lateinit var display: GlyphDisplay
    @Inject lateinit var chargingSource: ChargingStateSource
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var layoutProvider: Provider<DeviceLayout?>

    /** Application-wide single thread, see AppModule.provideGlyphDispatcher. Never create one here. */
    @Inject @GlyphDispatcher lateinit var glyphDispatcher: CoroutineDispatcher

    /**
     * Deliberately not cancelled with the service: the cleanup in [launchOrchestrator] must be
     * allowed to finish after [onDestroy] returns.
     */
    private val scope = CoroutineScope(
        SupervisorJob() + CoroutineExceptionHandler { _, error ->
            // Runs after the job's finally block, so the glyph is already off and released.
            Log.e(TAG, "Orchestrator failed, stopping service", error)
            stopSelf()
        },
    )
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        // Must happen before any early stopSelf(): startForegroundService() requires it.
        startForegroundCompat()

        val layout = layoutProvider.get()
        if (layout == null) {
            // Unknown phone: never drive hardware we cannot describe, and never guess a layout.
            stopSelf()
            return
        }
        launchOrchestrator(layout)
    }

    private fun launchOrchestrator(layout: DeviceLayout) {
        val orchestrator = GlyphOrchestrator(
            display = display,
            chargingSource = chargingSource,
            settingsRepository = settingsRepository,
            layout = layout,
        )

        // ATOMIC: the body runs (and its finally with it) even if the job is cancelled before
        // it gets its first turn, so the glyph is always switched off and released.
        job = scope.launch(glyphDispatcher, start = CoroutineStart.ATOMIC) {
            try {
                orchestrator.run()
                // Finished by itself (e.g. connection failed): nothing left to do, do not idle.
                stopSelf()
            } finally {
                display.turnOff()
                display.disconnect()
            }
        }
    }

    override fun onDestroy() {
        // Cleanup lives in the job's finally block and runs on the same single thread.
        job?.cancel()
        super.onDestroy()
    }

    private fun startForegroundCompat() {
        val notification: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(getString(R.string.service_notification_text))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.service_channel_name),
            NotificationManager.IMPORTANCE_MIN,
        )
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    companion object {
        fun start(context: Context) {
            context.startForegroundService(Intent(context, GlyphMeterService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GlyphMeterService::class.java))
        }
    }
}
