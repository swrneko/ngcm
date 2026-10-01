package com.swrneko.glyphmeter.access

import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import javax.inject.Inject
import moe.shizuku.server.IRemoteProcess
import moe.shizuku.server.IShizukuService
import rikka.shizuku.Shizuku

/**
 * Fallback for users with no computer to hand.
 *
 * Shizuku is started once over wireless debugging from the phone itself, but it has to
 * be restarted after every reboot, which is why the adb grant is the primary route.
 *
 * `Shizuku.newProcess` is `private static` in this library version, so it cannot be
 * called directly. The AIDL service it forwards to, [IShizukuService], is a different
 * story: `dev.rikka.shizuku:aidl` is pulled in as an `api`-scoped transitive dependency
 * of `dev.rikka.shizuku:api`, and both `IShizukuService.Stub.asInterface` and
 * `IShizukuService.newProcess` on it are public. Going through
 * `IShizukuService.Stub.asInterface(Shizuku.getBinder())` reaches the exact same call
 * the private helper would have made, without touching anything hidden or private.
 */
class ShizukuDebugModeWriter @Inject constructor() : DebugModeWriter {

    override val isAvailable: Boolean
        get() = try {
            Shizuku.pingBinder() &&
                Shizuku.getBinder() != null &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (e: Throwable) {
            // Shizuku may not even be installed, in which case its classes can be
            // missing at runtime (NoClassDefFoundError) rather than merely throwing.
            false
        }

    override fun isDebugModeOn(): Boolean = runShell("settings get global $GLYPH_DEBUG_SETTING")
        ?.trim() == "1"

    override fun enableDebugMode(): Boolean {
        runShell("settings put global $GLYPH_DEBUG_SETTING 1") ?: return false
        return isDebugModeOn()
    }

    private fun runShell(command: String): String? = try {
        val binder = Shizuku.getBinder()
        val service = binder?.let { IShizukuService.Stub.asInterface(it) }
        service?.newProcess(arrayOf("sh", "-c", command), null, null)?.let(::readShellOutput)
    } catch (e: Throwable) {
        Log.w(TAG, "shell command failed: $command", e)
        null
    }
}

private const val TAG = "ShizukuWriter"

/**
 * Reads what a Shizuku process printed and releases every pipe end it handed over.
 *
 * stdin is closed first so the command never waits for input; stderr is drained and logged,
 * since an unread error pipe can block a chatty process and each unclosed end leaks a
 * descriptor on every call.
 */
internal fun readShellOutput(process: IRemoteProcess): String {
    runCatching { process.outputStream?.close() }
    val output = ParcelFileDescriptor.AutoCloseInputStream(process.inputStream).use { stream ->
        stream.bufferedReader().readText()
    }
    val errors = process.errorStream?.let { pipe ->
        ParcelFileDescriptor.AutoCloseInputStream(pipe).use { stream -> stream.bufferedReader().readText() }
    }
    if (!errors.isNullOrBlank()) Log.w(TAG, "shell stderr: ${errors.trim()}")
    process.waitFor()
    return output
}
