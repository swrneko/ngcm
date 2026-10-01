package com.swrneko.glyphmeter.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.GlyphAccessState
import com.swrneko.glyphmeter.access.ShizukuPermission
import com.swrneko.glyphmeter.access.ShizukuStatus
import com.swrneko.glyphmeter.di.IoDispatcher
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val accessManager: GlyphAccessManager,
    private val shizuku: ShizukuPermission,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val _state = MutableStateFlow(GlyphAccessState.CHECKING)
    val state: StateFlow<GlyphAccessState> = _state.asStateFlow()

    private val _shizukuStatus = MutableStateFlow(ShizukuStatus.NOT_RUNNING)
    val shizukuStatus: StateFlow<ShizukuStatus> = _shizukuStatus.asStateFlow()

    val adbCommand: String get() = accessManager.adbGrantCommand

    init {
        evaluate()
    }

    fun onRecheck() {
        _state.value = GlyphAccessState.CHECKING
        evaluate()
    }

    /** Asks Shizuku for its permission; once granted, the Shizuku writer can switch debug mode on. */
    fun onRequestShizuku() {
        viewModelScope.launch {
            val granted = shizuku.request()
            _shizukuStatus.value = withContext(ioDispatcher) { shizuku.status() }
            if (granted) onRecheck()
        }
    }

    private fun evaluate() {
        viewModelScope.launch {
            // Evaluation may talk to Shizuku or secure settings, keep it off the main thread.
            _state.value = withContext(ioDispatcher) { accessManager.evaluate() }
            _shizukuStatus.value = withContext(ioDispatcher) { shizuku.status() }
        }
    }
}
