package com.swrneko.glyphmeter.ui.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swrneko.glyphmeter.R
import com.swrneko.glyphmeter.access.GlyphAccessState
import com.swrneko.glyphmeter.hardware.GlyphFailure
import com.swrneko.glyphmeter.hardware.RenderCapability
import com.swrneko.glyphmeter.settings.MeterMode
import kotlin.math.roundToInt

@Composable
fun MainRoute(
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    viewModel: MainViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val current = state
    if (current == null) {
        Scaffold { padding ->
            CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
        }
        return
    }
    MainScreen(
        state = current,
        onEnabledChange = viewModel::onEnabledChange,
        onModeChange = viewModel::onModeChange,
        onPlayPreview = viewModel::onPlayPreview,
        onOpenSettings = onOpenSettings,
        onOpenOnboarding = onOpenOnboarding,
        onRetryGlyph = viewModel::onRetryGlyph,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    state: MainUiState,
    onEnabledChange: (Boolean) -> Unit,
    onModeChange: (MeterMode) -> Unit,
    onPlayPreview: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOnboarding: () -> Unit,
    onRetryGlyph: () -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.main_title)) },
                actions = {
                    IconButton(onClick = onOpenSettings, modifier = Modifier.testTag("open_settings")) {
                        Icon(Icons.Filled.Settings, contentDescription = stringResource(R.string.main_open_settings))
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
            // First, so a broken Glyph is the first thing the user sees (spec 7.4: no silent failure).
            if (state.failure != null || state.capability == RenderCapability.UNAVAILABLE) {
                GlyphFailureCard(state.failure, onRetryGlyph)
            }
            EnabledCard(state.settings.enabled, onEnabledChange)
            PreviewCard(state, onPlayPreview)
            ModeCard(state.settings.meterMode, onModeChange)
            AccessCard(state.access, onOpenOnboarding)
            if (state.capability == RenderCapability.STEPPED_ONLY) {
                ReducedSmoothnessWarning()
            }
        }
    }
}

@Composable
private fun EnabledCard(enabled: Boolean, onEnabledChange: (Boolean) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.main_enabled_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                Text(
                    text = stringResource(if (enabled) R.string.main_enabled_on else R.string.main_enabled_off),
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = onEnabledChange,
                modifier = Modifier.testTag("enabled_switch"),
            )
        }
    }
}

@Composable
private fun PreviewCard(state: MainUiState, onPlayPreview: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.main_preview_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val layout = state.layout
            val frame = state.previewFrame
            if (layout != null && frame != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    GlyphPreview(frame = frame, layout = layout, modifier = Modifier.width(120.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            text = stringResource(R.string.main_preview_battery, (state.level * 100).roundToInt()),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        FilledTonalButton(onClick = onPlayPreview, modifier = Modifier.testTag("play_preview")) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Text(
                                text = stringResource(R.string.main_preview_play),
                                modifier = Modifier.padding(start = 8.dp),
                            )
                        }
                    }
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
    }
}

@Composable
private fun ModeCard(mode: MeterMode, onModeChange: (MeterMode) -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.main_mode_title),
                style = MaterialTheme.typography.titleMedium,
            )
            val options = listOf(
                Triple(MeterMode.ALWAYS_ON, R.string.main_mode_always_on, "mode_always_on"),
                Triple(MeterMode.ON_EVENT, R.string.main_mode_on_event, "mode_on_event"),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                options.forEachIndexed { index, (option, label, tag) ->
                    SegmentedButton(
                        selected = mode == option,
                        onClick = { onModeChange(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, options.size),
                        modifier = Modifier.testTag(tag),
                    ) {
                        Text(stringResource(label))
                    }
                }
            }
            Text(
                text = stringResource(
                    if (mode == MeterMode.ALWAYS_ON) R.string.main_mode_always_on_hint else R.string.main_mode_on_event_hint,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AccessCard(access: GlyphAccessState, onOpenOnboarding: () -> Unit) {
    val label = when (access) {
        GlyphAccessState.CHECKING -> R.string.main_access_checking
        GlyphAccessState.WORKING -> R.string.main_access_working
        GlyphAccessState.MANAGED_BY_APP -> R.string.main_access_managed
        GlyphAccessState.NEEDS_SETUP -> R.string.main_access_needs_setup
        GlyphAccessState.UNSUPPORTED_DEVICE -> R.string.main_access_unsupported
    }
    val hasProblem = access == GlyphAccessState.NEEDS_SETUP || access == GlyphAccessState.UNSUPPORTED_DEVICE
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .weight(1f)
                    .testTag("access_status"),
            )
            if (hasProblem) {
                TextButton(onClick = onOpenOnboarding, modifier = Modifier.testTag("open_onboarding")) {
                    Text(stringResource(R.string.main_access_open_onboarding))
                }
            }
        }
    }
}

@Composable
private fun GlyphFailureCard(failure: GlyphFailure?, onRetry: () -> Unit) {
    val explanation = when (failure) {
        GlyphFailure.REGISTRATION_REJECTED -> R.string.main_failure_rejected
        GlyphFailure.SERVICE_UNREACHABLE -> R.string.main_failure_unreachable
        GlyphFailure.SESSION_LOST -> R.string.main_failure_session_lost
        GlyphFailure.RENDERING_FAILED -> R.string.main_failure_rendering
        null -> R.string.main_failure_unknown
    }
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("glyph_failure_card"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Warning, contentDescription = null)
                Text(
                    text = stringResource(R.string.main_failure_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(explanation),
                style = MaterialTheme.typography.bodyMedium,
            )
            FilledTonalButton(onClick = onRetry, modifier = Modifier.testTag("glyph_failure_retry")) {
                Text(stringResource(R.string.main_failure_retry))
            }
        }
    }
}

@Composable
private fun ReducedSmoothnessWarning() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("reduced_smoothness_warning"),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
    ) {
        Row(
            modifier = Modifier.padding(24.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.Warning, contentDescription = null)
            Text(
                text = stringResource(R.string.main_reduced_smoothness),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
