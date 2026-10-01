package com.swrneko.glyphmeter.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.GlyphAccessState
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val accessManager: GlyphAccessManager,
) : ViewModel() {

    private val _state = MutableStateFlow(GlyphAccessState.CHECKING)
    val state: StateFlow<GlyphAccessState> = _state.asStateFlow()

    val adbCommand: String get() = accessManager.adbGrantCommand

    init {
        evaluate()
    }

    fun onRecheck() {
        _state.value = GlyphAccessState.CHECKING
        evaluate()
    }

    private fun evaluate() {
        viewModelScope.launch {
            // Evaluation may talk to Shizuku or secure settings, keep it off the main thread.
            _state.value = withContext(Dispatchers.IO) { accessManager.evaluate() }
        }
    }
}
