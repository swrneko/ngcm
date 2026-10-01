package com.swrneko.glyphmeter.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.orchestration.PreviewRequestBus
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.SettingsRepository
import com.swrneko.glyphmeter.ui.preview.PresetPreviewPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** The three situations a plug-in animation can be chosen for. */
enum class PresetSlot { WIRED, WIRELESS, FULL }

data class SettingsUiState(
    val settings: GlyphSettings,
    val layout: DeviceLayout?,
    /** The animation being previewed, or null when none is playing. */
    val previewFrame: GlyphFrameData?,
    /** The Glyph itself plays previews only while the app is on and the phone has a known layout. */
    val glyphPreviewAvailable: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    private val layout: DeviceLayout?,
    private val previewRequestBus: PreviewRequestBus,
) : ViewModel() {

    private val player = PresetPreviewPlayer(viewModelScope, nowMillis = { System.nanoTime() / 1_000_000 })

    val state: StateFlow<SettingsUiState?> = combine(
        settingsRepository.settings,
        player.frame,
    ) { settings, playing ->
        SettingsUiState(
            settings = settings,
            layout = layout,
            previewFrame = playing,
            glyphPreviewAvailable = settings.enabled && layout != null,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onBrightnessChange(value: Int) = update { it.copy(brightness = value) }

    fun onShowDurationChange(millis: Long) = update { it.copy(showDurationMillis = millis) }

    fun onRepeatStepChange(percent: Int) = update { it.copy(repeatStepPercent = percent) }

    fun onDimWhenFaceUpChange(value: Boolean) = update { it.copy(dimWhenFaceUp = value) }

    fun onPresetSelected(slot: PresetSlot, presetId: String) = update {
        when (slot) {
            PresetSlot.WIRED -> it.copy(wiredPresetId = presetId)
            PresetSlot.WIRELESS -> it.copy(wirelessPresetId = presetId)
            PresetSlot.FULL -> it.copy(fullPresetId = presetId)
        }
    }

    fun onPreviewPreset(presetId: String) {
        val current = state.value ?: return
        val layout = current.layout ?: return
        val preset = AnimationPresets.byId(presetId) ?: return
        player.play(preset, layout, current.settings.brightness)
        previewRequestBus.requestPreview(preset.id)
    }

    private fun update(transform: (GlyphSettings) -> GlyphSettings) {
        viewModelScope.launch { settingsRepository.update(transform) }
    }
}
