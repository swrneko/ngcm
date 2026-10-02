package com.swrneko.glyphmeter.ui.animation

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swrneko.glyphmeter.R
import com.swrneko.glyphmeter.animation.AnimationParams
import com.swrneko.glyphmeter.animation.FillStyle
import com.swrneko.glyphmeter.animation.FillTarget
import com.swrneko.glyphmeter.animation.SweepDirection
import com.swrneko.glyphmeter.animation.hasDirection
import com.swrneko.glyphmeter.animation.hasFill
import com.swrneko.glyphmeter.model.GlyphFrameData
import com.swrneko.glyphmeter.model.Light
import com.swrneko.glyphmeter.ui.main.GlyphPreview
import com.swrneko.glyphmeter.ui.settings.LabeledSlider
import com.swrneko.glyphmeter.ui.settings.SettingsCard
import com.swrneko.glyphmeter.ui.settings.presetLabel
import kotlin.math.roundToInt

/** The speed slider moves in tenths of a second. */
private const val SPEED_STEP_MILLIS = 100L

@Composable
fun AnimationSettingsRoute(
    onBack: () -> Unit,
    viewModel: AnimationSettingsViewModel = hiltViewModel(),
) {
    if (viewModel.preset == null) {
        // Only tunable animations are linked to; anything else has nothing to show.
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Scaffold { padding ->
            CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
        }
        return
    }
    AnimationSettingsScreen(
        state = current,
        onBack = onBack,
        callbacks = AnimationSettingsCallbacks(
            onCycleChange = viewModel::onCycleChange,
            onRepeatsChange = viewModel::onRepeatsChange,
            onBrightnessChange = viewModel::onBrightnessChange,
            onZoneToggle = viewModel::onZoneToggle,
            onDirectionChange = viewModel::onDirectionChange,
            onFillTargetChange = viewModel::onFillTargetChange,
            onFillStyleChange = viewModel::onFillStyleChange,
            onReset = viewModel::onReset,
            onPreview = viewModel::onPreview,
        ),
    )
}

/** Everything the screen can ask for, grouped so the screen takes one parameter for it. */
data class AnimationSettingsCallbacks(
    val onCycleChange: (Long) -> Unit = {},
    val onRepeatsChange: (Int) -> Unit = {},
    val onBrightnessChange: (Int) -> Unit = {},
    val onZoneToggle: (String) -> Unit = {},
    val onDirectionChange: (SweepDirection) -> Unit = {},
    val onFillTargetChange: (FillTarget) -> Unit = {},
    val onFillStyleChange: (FillStyle) -> Unit = {},
    val onReset: () -> Unit = {},
    val onPreview: () -> Unit = {},
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnimationSettingsScreen(
    state: AnimationSettingsUiState,
    onBack: () -> Unit,
    callbacks: AnimationSettingsCallbacks,
) {
    val preset = state.preset
    val params = state.params
    val name = presetLabel(preset.id)?.let { stringResource(it) } ?: preset.id
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(name) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("animation_back")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.settings_back))
                    }
                },
                actions = {
                    TextButton(
                        onClick = callbacks.onReset,
                        enabled = state.customised,
                        modifier = Modifier.testTag("animation_reset"),
                    ) {
                        Text(stringResource(R.string.animation_reset))
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
            PreviewCard(state, callbacks.onPreview)
            SettingsCard {
                LabeledSlider(
                    label = stringResource(R.string.animation_speed),
                    formatValue = { stringResource(R.string.animation_speed_value, it) },
                    sliderValue = params.cycleMillis / 1000f,
                    range = AnimationParams.MIN_CYCLE_MILLIS / 1000f..AnimationParams.MAX_CYCLE_MILLIS / 1000f,
                    steps = ((AnimationParams.MAX_CYCLE_MILLIS - AnimationParams.MIN_CYCLE_MILLIS) / SPEED_STEP_MILLIS - 1).toInt(),
                    enabled = true,
                    tag = "animation_speed_slider",
                    onValueChange = { seconds ->
                        callbacks.onCycleChange((seconds * 1000f / SPEED_STEP_MILLIS).roundToInt() * SPEED_STEP_MILLIS)
                    },
                )
                LabeledSlider(
                    label = stringResource(R.string.animation_brightness),
                    formatValue = { stringResource(R.string.settings_brightness_value, it.roundToInt() * 100 / Light.MAX) },
                    sliderValue = state.brightness.coerceIn(Light.MIN_VISIBLE, Light.MAX).toFloat(),
                    range = Light.MIN_VISIBLE.toFloat()..Light.MAX.toFloat(),
                    enabled = true,
                    tag = "animation_brightness_slider",
                    onValueChange = { callbacks.onBrightnessChange(it.roundToInt()) },
                )
                LabeledSlider(
                    label = stringResource(R.string.animation_repeats),
                    formatValue = { stringResource(R.string.animation_repeats_value, it.roundToInt()) },
                    sliderValue = params.repeats.toFloat(),
                    range = AnimationParams.MIN_REPEATS.toFloat()..AnimationParams.MAX_REPEATS.toFloat(),
                    steps = AnimationParams.MAX_REPEATS - AnimationParams.MIN_REPEATS - 1,
                    enabled = true,
                    tag = "animation_repeats_slider",
                    onValueChange = { callbacks.onRepeatsChange(it.roundToInt()) },
                )
            }
            if (preset.hasFill) {
                SettingsCard {
                    Choice(
                        title = R.string.animation_fill_target,
                        options = listOf(
                            Triple(FillTarget.FULL, R.string.animation_fill_full, "fill_target_full"),
                            Triple(FillTarget.CHARGE_LEVEL, R.string.animation_fill_level, "fill_target_level"),
                        ),
                        selected = params.fillTarget,
                        onSelect = callbacks.onFillTargetChange,
                    )
                    if (params.fillTarget == FillTarget.CHARGE_LEVEL) Hint(stringResource(R.string.animation_fill_level_hint))
                    Choice(
                        title = R.string.animation_fill_style,
                        options = listOf(
                            Triple(FillStyle.SMOOTH, R.string.animation_fill_smooth, "fill_style_smooth"),
                            Triple(FillStyle.STEPPED, R.string.animation_fill_stepped, "fill_style_stepped"),
                        ),
                        selected = params.fillStyle,
                        onSelect = callbacks.onFillStyleChange,
                    )
                }
            }
            if (state.zones.isNotEmpty()) ZonesCard(state, callbacks.onZoneToggle)
            if (preset.hasDirection) {
                SettingsCard {
                    Choice(
                        title = R.string.animation_direction,
                        options = listOf(
                            Triple(SweepDirection.CLOCKWISE, R.string.animation_direction_clockwise, "direction_clockwise"),
                            Triple(SweepDirection.COUNTER_CLOCKWISE, R.string.animation_direction_counter, "direction_counter"),
                        ),
                        selected = params.direction,
                        onSelect = callbacks.onDirectionChange,
                    )
                }
            }
        }
    }
}

@Composable
private fun PreviewCard(state: AnimationSettingsUiState, onPreview: () -> Unit) {
    val layout = state.layout ?: return
    SettingsCard {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            GlyphPreview(
                frame = state.previewFrame ?: GlyphFrameData.off(layout.segmentCount),
                layout = layout,
                modifier = Modifier.width(120.dp),
            )
        }
        FilledTonalButton(
            onClick = onPreview,
            modifier = Modifier.fillMaxWidth().testTag("animation_play"),
            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
            Text(stringResource(R.string.animation_play))
        }
        if (!state.glyphPreviewAvailable) {
            Text(
                text = stringResource(R.string.preview_glyph_unavailable),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("glyph_preview_unavailable"),
            )
        }
    }
}

@Composable
private fun ZonesCard(state: AnimationSettingsUiState, onZoneToggle: (String) -> Unit) {
    SettingsCard {
        Text(stringResource(R.string.animation_zones), style = MaterialTheme.typography.titleMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (zone in state.zones) {
                FilterChip(
                    selected = zone in state.selectedZones,
                    onClick = { onZoneToggle(zone) },
                    enabled = state.zonesEditable,
                    label = { Text(stringResource(R.string.animation_zone, zone)) },
                    modifier = Modifier.testTag("zone_$zone"),
                )
            }
        }
        val meterZone = state.layout?.meterZoneId
        Hint(
            if (!state.zonesEditable && meterZone != null) {
                stringResource(R.string.animation_zones_locked, meterZone)
            } else {
                stringResource(R.string.animation_zones_hint)
            },
        )
    }
}

@Composable
private fun <T> Choice(
    @StringRes title: Int,
    options: List<Triple<T, Int, String>>,
    selected: T,
    onSelect: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleMedium)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (option, label, tag) ->
                SegmentedButton(
                    selected = selected == option,
                    onClick = { onSelect(option) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                    modifier = Modifier.testTag(tag),
                ) {
                    Text(stringResource(label))
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
