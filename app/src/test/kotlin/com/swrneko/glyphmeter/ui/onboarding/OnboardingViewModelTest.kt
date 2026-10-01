package com.swrneko.glyphmeter.ui.onboarding

import com.swrneko.glyphmeter.access.DebugModeWriter
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.GlyphAccessState
import com.swrneko.glyphmeter.access.ShizukuPermission
import com.swrneko.glyphmeter.access.ShizukuStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class OnboardingViewModelTest {

    /** Stands in for Shizuku: the user answers the permission dialog through [answer]. */
    private class FakeShizuku(var status: ShizukuStatus) : ShizukuPermission {
        var answer = CompletableDeferred<Boolean>()
        var requests = 0

        override fun status() = status

        override suspend fun request(): Boolean {
            requests++
            val granted = answer.await()
            status = if (granted) ShizukuStatus.GRANTED else ShizukuStatus.DENIED_PERMANENTLY
            return granted
        }
    }

    /** Writes the flag through "Shizuku", so it works exactly when the fake has been granted. */
    private class ShizukuBackedWriter(private val shizuku: FakeShizuku) : DebugModeWriter {
        private var on = false
        override val isAvailable get() = shizuku.status == ShizukuStatus.GRANTED
        override fun isDebugModeOn() = on
        override fun enableDebugMode(): Boolean {
            on = isAvailable
            return on
        }
    }

    @Before fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After fun tearDown() = Dispatchers.resetMain()

    private fun viewModel(shizuku: FakeShizuku) = OnboardingViewModel(
        accessManager = GlyphAccessManager(listOf(ShizukuBackedWriter(shizuku)), { true }, { true }),
        shizuku = shizuku,
        ioDispatcher = UnconfinedTestDispatcher(),
    )

    @Test
    fun the_shizuku_status_is_shown_from_the_start() = runTest {
        val vm = viewModel(FakeShizuku(ShizukuStatus.PERMISSION_NEEDED))

        assertEquals(ShizukuStatus.PERMISSION_NEEDED, vm.shizukuStatus.value)
        assertEquals(GlyphAccessState.NEEDS_SETUP, vm.state.value)
    }

    @Test
    fun granting_shizuku_access_makes_the_app_manage_debug_mode() = runTest {
        val shizuku = FakeShizuku(ShizukuStatus.PERMISSION_NEEDED)
        val vm = viewModel(shizuku)

        vm.onRequestShizuku()
        shizuku.answer.complete(true)

        assertEquals(1, shizuku.requests)
        assertEquals(ShizukuStatus.GRANTED, vm.shizukuStatus.value)
        assertEquals(GlyphAccessState.MANAGED_BY_APP, vm.state.value)
    }

    @Test
    fun denying_shizuku_access_keeps_the_setup_instructions() = runTest {
        val shizuku = FakeShizuku(ShizukuStatus.PERMISSION_NEEDED)
        val vm = viewModel(shizuku)

        vm.onRequestShizuku()
        shizuku.answer.complete(false)

        assertEquals(ShizukuStatus.DENIED_PERMANENTLY, vm.shizukuStatus.value)
        assertEquals(GlyphAccessState.NEEDS_SETUP, vm.state.value)
    }

    @Test
    fun rechecking_refreshes_the_shizuku_status() = runTest {
        val shizuku = FakeShizuku(ShizukuStatus.NOT_RUNNING)
        val vm = viewModel(shizuku)

        shizuku.status = ShizukuStatus.PERMISSION_NEEDED
        vm.onRecheck()

        assertEquals(ShizukuStatus.PERMISSION_NEEDED, vm.shizukuStatus.value)
    }
}
