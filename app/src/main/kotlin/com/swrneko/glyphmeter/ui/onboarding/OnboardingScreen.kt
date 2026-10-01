package com.swrneko.glyphmeter.ui.onboarding

import android.content.ClipData
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.swrneko.glyphmeter.R
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.swrneko.glyphmeter.access.GlyphAccessState
import com.swrneko.glyphmeter.access.ShizukuStatus
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    state: GlyphAccessState,
    adbCommand: String,
    onCopyCommand: () -> Unit,
    onRecheck: () -> Unit,
    onContinue: () -> Unit,
    shizukuStatus: ShizukuStatus,
    onRequestShizuku: () -> Unit,
) {
    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when (state) {
                GlyphAccessState.CHECKING -> Checking()
                GlyphAccessState.WORKING -> Confirmation(R.string.onboarding_working_body, onContinue)
                GlyphAccessState.MANAGED_BY_APP -> Confirmation(R.string.onboarding_managed_body, onContinue)
                GlyphAccessState.NEEDS_SETUP ->
                    NeedsSetup(adbCommand, onCopyCommand, onRecheck, shizukuStatus, onRequestShizuku)
                GlyphAccessState.UNSUPPORTED_DEVICE -> Unsupported()
            }
        }
    }
}

@Composable
private fun Checking() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(Modifier.testTag("checking"))
    }
}

@Composable
private fun ScreenColumn(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        content()
    }
}

@Composable
private fun Header(titleRes: Int, bodyRes: Int) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Text(
        text = stringResource(bodyRes),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun Confirmation(bodyRes: Int, onContinue: () -> Unit) {
    ScreenColumn {
        Header(R.string.onboarding_ready_title, bodyRes)
        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("continue"),
        ) {
            Text(stringResource(R.string.onboarding_continue))
        }
    }
}

@Composable
private fun Unsupported() {
    ScreenColumn {
        Header(R.string.onboarding_unsupported_title, R.string.onboarding_unsupported_body)
    }
}

@Composable
private fun NeedsSetup(
    adbCommand: String,
    onCopyCommand: () -> Unit,
    onRecheck: () -> Unit,
    shizukuStatus: ShizukuStatus,
    onRequestShizuku: () -> Unit,
) {
    ScreenColumn {
        Header(R.string.onboarding_setup_title, R.string.onboarding_setup_body)

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.onboarding_command_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                SelectionContainer {
                    Text(
                        text = adbCommand,
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("adb_command"),
                    )
                }
                OutlinedButton(
                    onClick = onCopyCommand,
                    modifier = Modifier.testTag("copy_command"),
                ) {
                    Text(stringResource(R.string.onboarding_copy_command))
                }
            }
        }

        Text(
            text = stringResource(R.string.onboarding_once_only),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Button(
            onClick = onRecheck,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("recheck"),
        ) {
            Text(stringResource(R.string.onboarding_recheck))
        }

        ShizukuAlternative(shizukuStatus, onRequestShizuku)
    }
}

@Composable
private fun ShizukuAlternative(status: ShizukuStatus, onRequest: () -> Unit) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.onboarding_shizuku_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .testTag("shizuku_toggle"),
            )
            if (expanded) {
                Text(
                    text = stringResource(R.string.onboarding_shizuku_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("shizuku_body"),
                )
                val statusText = when (status) {
                    ShizukuStatus.NOT_RUNNING -> R.string.onboarding_shizuku_not_running
                    ShizukuStatus.PERMISSION_NEEDED -> R.string.onboarding_shizuku_permission_needed
                    ShizukuStatus.DENIED_PERMANENTLY -> R.string.onboarding_shizuku_denied
                    ShizukuStatus.GRANTED -> R.string.onboarding_shizuku_granted
                }
                Text(
                    text = stringResource(statusText),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.testTag("shizuku_status"),
                )
                if (status == ShizukuStatus.PERMISSION_NEEDED) {
                    OutlinedButton(onClick = onRequest, modifier = Modifier.testTag("shizuku_request")) {
                        Text(stringResource(R.string.onboarding_shizuku_request))
                    }
                }
            }
        }
    }
}

/** Connects [OnboardingScreen] to its ViewModel and to the system clipboard. */
@Composable
fun OnboardingRoute(
    onContinue: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    val command = viewModel.adbCommand
    val shizukuStatus by viewModel.shizukuStatus.collectAsStateWithLifecycle()

    OnboardingScreen(
        state = state,
        adbCommand = command,
        onCopyCommand = {
            scope.launch {
                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("adb command", command)))
            }
        },
        onRecheck = viewModel::onRecheck,
        onContinue = onContinue,
        shizukuStatus = shizukuStatus,
        onRequestShizuku = viewModel::onRequestShizuku,
    )
}
