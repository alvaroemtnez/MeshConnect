package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.engine.EngineState

/**
 * First-run setup (PARITY.md §1, `SetupScreen`).
 *
 * The gap this closes is that the app used to drop a new user into an
 * empty Chats list with no indication that a radio was required, let
 * alone which four numbers had to match everyone else's.
 *
 * It is a checklist, not a wizard: each step reports whether it is done
 * and offers the one action that does it. A wizard that must be
 * completed in order is the wrong shape here — plenty of users arrive
 * with a radio already configured by someone else, and should be able
 * to skip straight past.
 */
@Composable
fun SetupScreen(vm: MeshCoreViewModel, onDone: () -> Unit) {
    val engineState by vm.engineState.collectAsState()
    val self by vm.selfInfo.collectAsState()
    val connected = engineState == EngineState.Ready
    var presetSheet by remember { mutableStateOf(false) }
    var name by remember(self?.name) { mutableStateOf(self?.name.orEmpty()) }

    val matches = remember(self?.freqKhz, self?.bwHz, self?.sf, self?.cr) {
        val info = self
        if (info == null) {
            emptyList()
        } else {
            io.github.thatsfguy.meshcore.protocol.RadioPresets.matching(
                info.freqKhz, info.bwHz, info.sf, info.cr,
            )
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            // No top bar here, so this screen pads for the system bars itself.
            .safeDrawingPadding()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
    ) {
        Text(stringResource(R.string.setup_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        HintText(stringResource(R.string.setup_intro))

        Spacer(Modifier.height(20.dp))
        SetupStep(
            number = 1,
            title = stringResource(R.string.setup_step_connect),
            done = connected,
            detail = if (connected) {
                stringResource(R.string.setup_connected_to, vm.connectionLabel.collectAsState().value ?: "")
            } else {
                stringResource(R.string.setup_connect_detail)
            },
        ) {
            HintText(stringResource(R.string.setup_connect_hint))
        }

        SetupStep(
            number = 2,
            title = stringResource(R.string.setup_step_name),
            done = connected && !self?.name.isNullOrBlank(),
            detail = stringResource(R.string.setup_name_detail),
        ) {
            if (!connected) {
                HintText(stringResource(R.string.setup_connect_first))
            } else {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.setup_node_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                ButtonFlowRow {
                    TextButton(
                        onClick = { vm.setAdvertName(name.trim()) },
                        enabled = name.isNotBlank() && name.trim() != self?.name,
                    ) { Text(stringResource(R.string.setup_set_name)) }
                }
            }
        }

        SetupStep(
            number = 3,
            title = stringResource(R.string.setup_step_radio),
            done = connected && matches.isNotEmpty(),
            detail = if (matches.isNotEmpty()) {
                stringResource(R.string.setup_radio_current, matches.joinToString(", ") { it.name })
            } else {
                stringResource(R.string.setup_radio_detail)
            },
        ) {
            if (!connected) {
                HintText(stringResource(R.string.setup_connect_first))
            } else {
                ButtonFlowRow {
                    TextButton(onClick = { presetSheet = true }) { Text(stringResource(R.string.setup_pick_preset)) }
                }
                HintText(io.github.thatsfguy.meshcore.protocol.RadioPresets.REGULATORY_CAVEAT)
            }
        }

        SetupStep(
            number = 4,
            title = stringResource(R.string.setup_step_protects),
            done = false,
            detail = stringResource(R.string.setup_protects_detail),
        ) {
            HintText(stringResource(R.string.setup_protects_hint))
        }

        Spacer(Modifier.height(24.dp))
        ButtonFlowRow {
            OutlinedButton(onClick = {
                vm.prefs.setupComplete = true
                onDone()
            }) { Text(stringResource(if (connected) R.string.setup_done else R.string.setup_skip)) }
        }
        Spacer(Modifier.height(32.dp))
    }

    if (presetSheet) {
        RadioPresetSheet(vm, onDismiss = { presetSheet = false })
    }
}

@Composable
private fun SetupStep(
    number: Int,
    title: String,
    done: Boolean,
    detail: String,
    content: @Composable () -> Unit,
) {
    Spacer(Modifier.height(16.dp))
    Text(
        (if (done) "✓ " else "$number. ") + title,
        style = MaterialTheme.typography.titleSmall,
        color = if (done) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurface
        },
    )
    HintText(detail)
    if (!done) content()
}
