package com.swrneko.glyphmeter.access

import android.os.ParcelFileDescriptor
import moe.shizuku.server.IRemoteProcess
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * A process started through Shizuku hands the app three pipe ends. [readShellOutput] must use
 * stdout and release all of them, including stderr, or every shell call leaks descriptors.
 */
@RunWith(RobolectricTestRunner::class)
class ShizukuProcessOutputTest {

    /** Stands in for the remote process on the Shizuku side: real pipes, canned output. */
    private class FakeRemoteProcess(stdout: String, stderr: String) : IRemoteProcess.Stub() {
        private fun pipeWith(text: String): ParcelFileDescriptor {
            val (read, write) = ParcelFileDescriptor.createPipe()
            ParcelFileDescriptor.AutoCloseOutputStream(write).use { it.write(text.toByteArray()) }
            return read
        }

        val stdoutEnd = pipeWith(stdout)
        val stderrEnd = pipeWith(stderr)
        private val stdinPipe = ParcelFileDescriptor.createPipe()
        val stdinEnd: ParcelFileDescriptor = stdinPipe[1]
        var waited = false

        override fun getInputStream() = stdoutEnd
        override fun getErrorStream() = stderrEnd
        override fun getOutputStream() = stdinEnd
        override fun waitFor(): Int {
            waited = true
            return 0
        }
        override fun exitValue() = 0
        override fun destroy() = Unit
        override fun alive() = false
        override fun waitForTimeout(timeout: Long, unit: String?) = true
    }

    private fun ParcelFileDescriptor.isClosed(): Boolean =
        runCatching { fd }.isFailure

    @Test
    fun it_returns_what_the_command_printed() {
        val process = FakeRemoteProcess(stdout = "1\n", stderr = "")

        assertEquals("1\n", readShellOutput(process))
    }

    @Test
    fun it_releases_stdout_stderr_and_stdin() {
        val process = FakeRemoteProcess(stdout = "1\n", stderr = "warning: something\n")

        readShellOutput(process)

        assertTrue("stdout left open", process.stdoutEnd.isClosed())
        assertTrue("stderr left open", process.stderrEnd.isClosed())
        assertTrue("stdin left open", process.stdinEnd.isClosed())
        assertTrue(process.waited)
    }
}
