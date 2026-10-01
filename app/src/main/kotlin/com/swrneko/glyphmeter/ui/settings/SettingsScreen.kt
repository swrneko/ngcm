package com.swrneko.glyphmeter.ui.settings

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swrneko.glyphmeter.R
import com.swrneko.glyphmeter.animation.AnimationPresets
import com.swrneko.glyphmeter.model.Light
import com.swrneko.glyphmeter.settings.MeterMode
import com.swrneko.glyphmeter.ui.main.GlyphPreview
import kotlin.math.roundToInt

/** Bounds of the on-event controls. */
internal const val MIN_SHOW_SECONDS = 1
internal const val MAX_SHOW_SECONDS = 30
internal const val MIN_REPEAT_STEP_PERCENT = 1
internal const val MAX_REPEAT_STEP_PERCENT = 25

@Composable
fun SettingsRoute(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Scaffold { padding ->
            CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
        }
        return
    }
    SettingsScreen(
        state = current,
        onBack = onBack,
        onBrightnessChange = viewModel::onBrightnessChange,
        onShowDurationChange = viewModel::onShowDurationChange,
        onRepeatStepChange = viewModel::onRepeatStepChange,
        onPresetSelected = viewModel::onPresetSelected,
        onPreviewPreset = viewModel::onPreviewPreset,
        onDimWhenFaceUpChange = viewModel::onDimWhenFaceUpChange,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    state: SettingsUiState,
    onBack: () -> Unit,
    onBrightnessChange: (Int) -> Unit,
    onShowDurationChange: (Long) -> Unit,
    onRepeatStepChange: (Int) -> Unit,
    onPresetSelected: (PresetSlot, String) -> Unit,
    onPreviewPreset: (String) -> Unit,
    onDimWhenFaceUpChange: (Boolean) -> Unit,
) {
    val settings = state.settings
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BrightnessCard(settings.brightness, onBrightnessChange)
            EventCard(
                enabled = settings.meterMode == MeterMode.ON_EVENT,
                showDurationMillis = settings.showDurationMillis,
                repeatStepPercent = settings.repeatStepPercent,
                onShowDurationChange = onShowDurationChange,
                onRepeatStepChange = onRepeatStepChange,
            )
            val layout = state.layout
            val frame = state.previewFrame
            if (layout != null && frame != null) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                ) {
                    Row(Modifier.fillMaxWidth().padding(24.dp), horizontalArrangement = Arrangement.Center) {
                        GlyphPreview(frame = frame, layout = layout, modifier = Modifier.width(120.dp))
                    }
                }
            }
            Text(
                text = stringResource(R.string.settings_animations_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 8.dp),
            )
            PresetCard(PresetSlot.WIRED, R.string.settings_slot_wired, settings.wiredPresetId, onPresetSelected, onPreviewPreset)
            PresetCard(PresetSlot.WIRELESS, R.string.settings_slot_wireless, settings.wirelessPresetId, onPresetSelected, onPreviewPreset)
            PresetCard(PresetSlot.FULL, R.string.settings_slot_full, settings.fullPresetId, onPresetSelected, onPreviewPreset)
            FaceUpCard(settings.dimWhenFaceUp, onDimWhenFaceUpChange)
        }
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun LabeledSlider(
    label: String,
    formatValue: @Composable (Float) -> String,
    sliderValue: Float,
    range: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    tag: String,
    onValueChange: (Float) -> Unit,
) {
    // Follow the finger locally and store only when the gesture ends: every stored change
    // restarts the settings flow and makes the orchestrator re-apply the Glyph.
    var dragValue by remember(sliderValue) { mutableFloatStateOf(sliderValue) }
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            Text(formatValue(dragValue), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
        }
        Slider(
            value = dragValue,
            onValueChange = { dragValue = it },
            onValueChangeFinished = { onValueChange(dragValue) },
            valueRange = range,
            enabled = enabled,
            modifier = Modifier.testTag(tag),
        )
    }
}

@Composable
private fun BrightnessCard(brightness: Int, onBrightnessChange: (Int) -> Unit) {
    // Stored brightness may be 0 (a dark segment); the slider only offers visible values.
    val shown = brightness.coerceIn(Light.MIN_VISIBLE, Light.MAX)
    SettingsCard {
        LabeledSlider(
            label = stringResource(R.string.settings_brightness_title),
            formatValue = { stringResource(R.string.settings_brightness_value, it.roundToInt() * 100 / Light.MAX) },
            sliderValue = shown.toFloat(),
            range = Light.MIN_VISIBLE.toFloat()..Light.MAX.toFloat(),
            enabled = true,
            tag = "brightness_slider",
            onValueChange = { onBrightnessChange(it.roundToInt()) },
        )
    }
}

@Composable
private fun EventCard(
    enabled: Boolean,
    showDurationMillis: Long,
    repeatStepPercent: Int,
    onShowDurationChange: (Long) -> Unit,
    onRepeatStepChange: (Int) -> Unit,
) {
    val seconds = (showDurationMillis / 1000f).roundToInt().coerceIn(MIN_SHOW_SECONDS, MAX_SHOW_SECONDS)
    val step = repeatStepPercent.coerceIn(MIN_REPEAT_STEP_PERCENT, MAX_REPEAT_STEP_PERCENT)
    SettingsCard {
        Text(stringResource(R.string.settings_event_title), style = MaterialTheme.typography.titleLarge)
        if (!enabled) {
            Text(
                text = stringResource(R.string.settings_event_hint_disabled),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LabeledSlider(
            label = stringResource(R.string.settings_show_duration),
            formatValue = { stringResource(R.string.settings_show_duration_value, it.roundToInt()) },
            sliderValue = seconds.toFloat(),
            range = MIN_SHOW_SECONDS.toFloat()..MAX_SHOW_SECONDS.toFloat(),
            enabled = enabled,
            tag = "show_duration_slider",
            onValueChange = { onShowDurationChange(it.roundToInt() * 1000L) },
        )
        LabeledSlider(
            label = stringResource(R.string.settings_repeat_step),
            formatValue = { stringResource(R.string.settings_repeat_step_value, it.roundToInt()) },
            sliderValue = step.toFloat(),
            range = MIN_REPEAT_STEP_PERCENT.toFloat()..MAX_REPEAT_STEP_PERCENT.toFloat(),
            enabled = enabled,
            tag = "repeat_step_slider",
            onValueChange = { onRepeatStepChange(it.roundToInt()) },
        )
    }
}

@StringRes
private fun presetLabel(id: String): Int? = when (id) {
    AnimationPresets.FILL_UP.id -> R.string.preset_fill_up
    AnimationPresets.WAVE.id -> R.string.preset_wave
    AnimationPresets.BREATHE.id -> R.string.preset_breathe
    AnimationPresets.CHASE.id -> R.string.preset_chase
    AnimationPresets.FLASH.id -> R.string.preset_flash
    else -> null
}

@Composable
private fun PresetCard(
    slot: PresetSlot,
    @StringRes title: Int,
    selectedId: String,
    onPresetSelected: (PresetSlot, String) -> Unit,
    onPreviewPreset: (String) -> Unit,
) {
    SettingsCard {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        Column {
            for (preset in AnimationPresets.selectable) {
                val name = presetLabel(preset.id)?.let { stringResource(it) } ?: preset.id
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = preset.id == selectedId,
                            onClick = { onPresetSelected(slot, preset.id) },
                            role = Role.RadioButton,
                        )
                        .testTag("preset_${slot.name.lowercase()}_${preset.id}"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = preset.id == selectedId, onClick = null, modifier = Modifier.padding(end = 16.dp))
                    Text(name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = { onPreviewPreset(preset.id) },
                        modifier = Modifier.testTag("preview_${slot.name.lowercase()}_${preset.id}"),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = stringResource(R.string.settings_preset_preview, name))
                    }
                }
            }
        }
    }
}

@Composable
private fun FaceUpCard(dimWhenFaceUp: Boolean, onChange: (Boolean) -> Unit) {
    SettingsCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.settings_face_up_title), style = MaterialTheme.typography.titleMedium)
                Text(
                    text = stringResource(R.string.settings_face_up_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = dimWhenFaceUp, onCheckedChange = onChange, modifier = Modifier.testTag("face_up_switch"))
        }
    }
}
