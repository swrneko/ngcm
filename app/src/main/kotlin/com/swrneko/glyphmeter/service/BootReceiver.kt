package com.swrneko.glyphmeter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.shouldStartOnBoot
import android.util.Log
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

private const val TAG = "BootReceiver"

/**
 * Re-arms the app after a reboot.
 *
 * Evaluating access here switches the debug flag back on right after boot when the app holds
 * WRITE_SECURE_SETTINGS. Between reboots the running service does the same before every
 * connect and on every plug-in, which is what keeps the 48-hour expiry invisible.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var accessManager: GlyphAccessManager

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return

        // evaluate() may reach Shizuku over IPC: keep it off the main thread.
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (shouldStartOnBoot(accessManager.evaluate())) GlyphMeterService.start(context)
            } catch (e: Throwable) {
                // Last path to a process crash: a failing access check or a refused foreground
                // start (background start restrictions) must not take the app down at boot.
                Log.e(TAG, "Boot start failed", e)
            } finally {
                pending?.finish()
            }
        }
    }
}
