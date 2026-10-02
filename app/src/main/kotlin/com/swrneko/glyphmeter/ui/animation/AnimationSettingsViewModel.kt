package com.swrneko.glyphmeter.ui.animation

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.swrneko.glyphmeter.animation.AnimationParams
import com.swrneko.glyphmeter.animation.AnimationPreset
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.animation.FillStyle
import com.swrneko.glyphmeter.animation.FillTarget
import com.swrneko.glyphmeter.animation.SweepDirection
import com.swrneko.glyphmeter.animation.zonesFor
import com.swrneko.glyphmeter.charging.ChargingStateSource
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AnimationSettingsUiState(
    val preset: AnimationPreset,
    /** The tuning in effect: the stored one, or the preset as shipped. */
    val params: AnimationParams,
    /** Peak brightness in effect; follows the meter's until the user moves it. */
    val brightness: Int,
    /** Every zone of the phone, clockwise. Empty on a phone with no known layout. */
    val zones: List<String>,
    val selectedZones: Set<String>,
    /** A fill to the charge level always runs along the meter, so its zones cannot be picked. */
    val zonesEditable: Boolean,
    /** Something differs from the preset as shipped, so there is something to reset. */
    val customised: Boolean,
    val layout: DeviceLayout?,
    /** The animation being previewed on screen, or null when none is playing. */
    val previewFrame: GlyphFrameData?,
    /** The Glyph itself plays previews only while the app is on and the phone has a known layout. */
    val glyphPreviewAvailable: Boolean,
)

/**
 * Tunes one plug-in animation, picked by the [PRESET_ID] navigation argument. Every change is
 * stored right away for that animation only; the service picks it up from the settings.
 */
@HiltViewModel
class AnimationSettingsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val settingsRepository: SettingsRepository,
    chargingSource: ChargingStateSource,
    private val layout: DeviceLayout?,
    private val previewRequestBus: PreviewRequestBus,
) : ViewModel() {

    /** The animation being tuned; null for an id the user cannot tune, such as the unplug fade. */
    val preset: AnimationPreset? = savedStateHandle.get<String>(PRESET_ID)
        ?.let { id -> AnimationPresets.selectable.firstOrNull { it.id == id } }

    private val player = PresetPreviewPlayer(viewModelScope, nowMillis = { System.nanoTime() / 1_000_000 })

    /** Battery level a fill to the charge level previews with; full until the first reading. */
    private val level: StateFlow<Float> = chargingSource.state.map { it.level }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1f)

    val state: StateFlow<AnimationSettingsUiState?> = if (preset == null) {
        flowOf(null).stateIn(viewModelScope, SharingStarted.Eagerly, null)
    } else {
        combine(settingsRepository.settings, player.frame) { settings, playing ->
            val params = settings.paramsFor(preset)
            AnimationSettingsUiState(
                preset = preset,
                params = params,
                brightness = params.brightness ?: settings.brightness,
                zones = layout?.zones?.map { it.id }.orEmpty(),
                selectedZones = layout?.let { selectedZones(params, it) }.orEmpty(),
                zonesEditable = params.fillTarget != FillTarget.CHARGE_LEVEL,
                customised = preset.id in settings.animationParams,
                layout = layout,
                previewFrame = playing,
                glyphPreviewAvailable = settings.enabled && layout != null,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    }

    fun onCycleChange(millis: Long) = tune { it.copy(cycleMillis = millis) }

    fun onRepeatsChange(repeats: Int) = tune { it.copy(repeats = repeats) }

    /** Leaving the slider where it was keeps the brightness following the meter's. */
    fun onBrightnessChange(value: Int) = tune { params, settings ->
        if (params.brightness == null && value == settings.brightness) params else params.copy(brightness = value)
    }

    fun onDirectionChange(direction: SweepDirection) = tune { it.copy(direction = direction) }

    fun onFillTargetChange(target: FillTarget) = tune { it.copy(fillTarget = target) }

    fun onFillStyleChange(style: FillStyle) = tune { it.copy(fillStyle = style) }

    /** Switches a zone on or off. The last lit zone stays on: an animation needs somewhere to play. */
    fun onZoneToggle(zoneId: String) {
        val layout = layout ?: return
        tune { params ->
            val current = selectedZones(params, layout)
            val toggled = if (zoneId in current) current - zoneId else current + zoneId
            if (toggled.isEmpty()) params else params.copy(zoneIds = toggled)
        }
    }

    /** Forgets the tuning, so the animation plays as shipped again. */
    fun onReset() {
        val preset = preset ?: return
        viewModelScope.launch {
            settingsRepository.update { it.copy(animationParams = it.animationParams - preset.id) }
        }
    }

    /** Plays the animation as tuned on screen and asks the service to play it on the Glyph too. */
    fun onPreview() {
        val current = state.value ?: return
        val layout = current.layout ?: return
        viewModelScope.launch {
            // Read the stored settings, not the screen state, so a change made a moment ago is in.
            val settings = settingsRepository.settings.first()
            player.play(settings.frameSource(current.preset, layout, level.value))
            previewRequestBus.requestPreview(current.preset.id)
        }
    }

    private fun tune(transform: (AnimationParams) -> AnimationParams) = tune { params, _ -> transform(params) }

    private fun tune(transform: (AnimationParams, GlyphSettings) -> AnimationParams) {
        val preset = preset ?: return
        viewModelScope.launch {
            settingsRepository.update { settings ->
                val current = settings.paramsFor(preset)
                val tuned = transform(current, settings)
                // A touch that changes nothing must not turn an untouched animation into a tuned one.
                if (tuned == current) settings else settings.withParams(preset, tuned)
            }
        }
    }

    private fun GlyphSettings.withParams(preset: AnimationPreset, params: AnimationParams) =
        copy(animationParams = animationParams + (preset.id to params))

    private fun selectedZones(params: AnimationParams, layout: DeviceLayout): Set<String> =
        preset?.zonesFor(params, layout)?.map { it.id }?.toSet().orEmpty()

    companion object {
        /** Navigation argument with the id of the animation to tune. */
        const val PRESET_ID = "presetId"
    }
}
