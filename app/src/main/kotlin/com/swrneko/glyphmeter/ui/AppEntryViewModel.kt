package com.swrneko.glyphmeter.ui

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

/** Works out Glyph access once at launch, so navigation knows where to start. */
@HiltViewModel
class AppEntryViewModel @Inject constructor(
    private val accessManager: GlyphAccessManager,
) : ViewModel() {

    private val _access = MutableStateFlow(GlyphAccessState.CHECKING)
    val access: StateFlow<GlyphAccessState> = _access.asStateFlow()

    init {
        viewModelScope.launch {
            _access.value = withContext(Dispatchers.IO) { accessManager.evaluate() }
        }
    }
}
