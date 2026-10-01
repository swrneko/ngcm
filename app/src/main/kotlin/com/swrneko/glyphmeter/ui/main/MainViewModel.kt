package com.swrneko.glyphmeter.ui.main

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swrneko.glyphmeter.access.GlyphAccessManager
import com.swrneko.glyphmeter.access.GlyphAccessState
import com.swrneko.glyphmeter.access.shouldStartService
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.charging.ChargingStateSource
import com.swrneko.glyphmeter.di.IoDispatcher
import com.swrneko.glyphmeter.hardware.GlyphDisplay
import com.swrneko.glyphmeter.hardware.GlyphFailure
import com.swrneko.glyphmeter.hardware.RenderCapability
import com.swrneko.glyphmeter.layout.MeterRenderer
import com.swrneko.glyphmeter.model.DeviceLayout
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.service.MeterServiceController
import com.swrneko.glyphmeter.settings.GlyphSettings
import com.swrneko.glyphmeter.settings.MeterMode
import com.swrneko.glyphmeter.settings.SettingsRepository
import com.swrneko.glyphmeter.ui.preview.PresetPreviewPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class MainUiState(
    val settings: GlyphSettings,
    /** Battery level, 0..1. */
    val level: Float,
    val capability: RenderCapability,
    /** Why the Glyph last failed; survives the disconnect that follows a failure. Null when fine. */
    val failure: GlyphFailure?,
    val access: GlyphAccessState,
    /** Null on a phone with no known Glyph layout; the preview is then hidden. */
    val layout: DeviceLayout?,
    /** What the preview shows: a playing animation, or else the current battery level. */
    val previewFrame: GlyphFrameData?,
)

@HiltViewModel
class MainViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    chargingSource: ChargingStateSource,
    display: GlyphDisplay,
    private val accessManager: GlyphAccessManager,
    private val layout: DeviceLayout?,
    private val serviceController: MeterServiceController,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher,
) : ViewModel() {

    private val access = MutableStateFlow(GlyphAccessState.CHECKING)

    private val player = PresetPreviewPlayer(viewModelScope, nowMillis = { System.nanoTime() / 1_000_000 })

    /** Null until the first settings and battery reading have arrived. */
    val state: StateFlow<MainUiState?> = combine(
        settingsRepository.settings,
        chargingSource.state,
        combine(display.capability, display.lastFailure, ::Pair),
        access,
        player.frame,
    ) { settings, charging, (capability, failure), accessState, playing ->
        val idleFrame = layout?.let { MeterRenderer.smooth(charging.level, it, settings.brightness) }
        MainUiState(
            settings = settings,
            level = charging.level,
            capability = capability,
            failure = failure,
            access = accessState,
            layout = layout,
            previewFrame = playing ?: idleFrame,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            // Evaluation may talk to Shizuku or secure settings, keep it off the main thread.
            access.value = withContext(ioDispatcher) { accessManager.evaluate() }
            // The app is on by default, so a fresh install must start the service without
            // waiting for the user to flip the switch. Doing it here (not on every resume)
            // keeps rotation and trips to settings from repeating it.
            val enabled = settingsRepository.settings.first().enabled
            if (shouldStartService(access.value, enabled, layout)) serviceController.start()
        }
    }

    /** Turns the whole app on or off. The service follows once the choice is stored. */
    fun onEnabledChange(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.update { it.copy(enabled = enabled) }
            if (shouldStartService(access.value, enabled, layout)) {
                serviceController.start()
            } else if (!enabled) {
                serviceController.stop()
            }
        }
    }

    /**
     * "Check again" on the failure card: re-evaluates access (which re-arms debug mode when the
     * app may) and starts the service anew, which reconnects to the Glyph.
     */
    fun onRetryGlyph() {
        viewModelScope.launch {
            access.value = withContext(ioDispatcher) { accessManager.evaluate() }
            val enabled = settingsRepository.settings.first().enabled
            if (shouldStartService(access.value, enabled, layout)) serviceController.start()
        }
    }

    fun onModeChange(mode: MeterMode) {
        viewModelScope.launch { settingsRepository.update { it.copy(meterMode = mode) } }
    }

    /** Plays the wired-charging animation on the preview. */
    fun onPlayPreview() {
        val current = state.value ?: return
        val layout = current.layout ?: return
        val preset = AnimationPresets.byId(current.settings.wiredPresetId) ?: AnimationPresets.FILL_UP
        player.play(preset, layout, current.settings.brightness)
    }
}
