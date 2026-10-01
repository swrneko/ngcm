package com.swrneko.glyphmeter.access

import android.content.pm.PackageManager
import javax.inject.Inject
import kotlinx.coroutines.suspendCancellableCoroutine
import rikka.shizuku.Shizuku
import kotlin.coroutines.resume

enum class ShizukuStatus {
    /** Shizuku is not installed, not started, or too old (pre-v11) to ask. */
    NOT_RUNNING,

    /** Shizuku runs; the app may ask for its permission. */
    PERMISSION_NEEDED,

    /** The user chose "deny and don't ask again"; only the Shizuku app can undo that. */
    DENIED_PERMANENTLY,

    GRANTED,
}

/** The app's side of the Shizuku permission dialog. A seam: Shizuku itself is the system boundary. */
interface ShizukuPermission {
    fun status(): ShizukuStatus

    /** Shows Shizuku's own permission dialog and suspends until the user answers. True when granted. */
    suspend fun request(): Boolean
}

private const val REQUEST_CODE = 0x6C79

class ShizukuPermissionClient @Inject constructor() : ShizukuPermission {

    override fun status(): ShizukuStatus = try {
        when {
            !Shizuku.pingBinder() || Shizuku.isPreV11() -> ShizukuStatus.NOT_RUNNING
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> ShizukuStatus.GRANTED
            Shizuku.shouldShowRequestPermissionRationale() -> ShizukuStatus.DENIED_PERMANENTLY
            else -> ShizukuStatus.PERMISSION_NEEDED
        }
    } catch (e: Throwable) {
        // Shizuku classes may be unusable when the manager app is absent.
        ShizukuStatus.NOT_RUNNING
    }

    override suspend fun request(): Boolean {
        if (status() != ShizukuStatus.PERMISSION_NEEDED) return status() == ShizukuStatus.GRANTED
        return suspendCancellableCoroutine { continuation ->
            val listener = object : Shizuku.OnRequestPermissionResultListener {
                override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                    if (requestCode != REQUEST_CODE) return
                    Shizuku.removeRequestPermissionResultListener(this)
                    if (continuation.isActive) {
                        continuation.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                    }
                }
            }
            continuation.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
            try {
                Shizuku.addRequestPermissionResultListener(listener)
                Shizuku.requestPermission(REQUEST_CODE)
            } catch (e: Throwable) {
                Shizuku.removeRequestPermissionResultListener(listener)
                if (continuation.isActive) continuation.resume(false)
            }
        }
    }
}
