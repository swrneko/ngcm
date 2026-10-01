package com.swrneko.glyphmeter.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.shouldStartOnBoot
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-arms the app after a reboot.
 *
 * Evaluating access here is what makes the 48-hour debug-mode expiry invisible: if the
 * app holds WRITE_SECURE_SETTINGS it simply switches the flag back on.
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
            } finally {
                pending.finish()
            }
        }
    }
}
