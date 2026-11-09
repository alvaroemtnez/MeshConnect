package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.Regions
import kotlinx.coroutines.launch

/**
 * Regions (flood scope) — Settings → Mesh policies. PARITY.md §8.
 *
 * A region is a routing label: the radio hashes the name and only floods
 * packets carrying the matching scope tag. It is NOT a privacy boundary,
 * and the copy here has to keep saying so — a channel scoped to a region
 * is exactly as readable as it was before, just carried by fewer nodes.
 *
 * Region names are shared out of band or discovered from repeaters.
 * Discovered names come off the mesh from nodes we can't authenticate,
 * so nothing is stored automatically: they are shown, and the user adds
 * the ones they mean to use.
 */
@Composable
fun RegionsSection(vm: MeshCoreViewModel) {
    val regions by vm.regions.collectAsState()
    val channelRegions by vm.channelRegions.collectAsState()
    val stuck by vm.floodScopeStuck.collectAsState()
    var newRegion by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf<String?>(null) }
    var discovering by remember { mutableStateOf(false) }
    var discovered by remember { mutableStateOf<MeshCoreViewModel.RegionDiscovery?>(null) }
    val scope = rememberCoroutineScope()

    // "Not privacy" stays in the summary: a user who reads only the
    // first line must not come away thinking a region hides anything.
    ExpandableHint(stringResource(R.string.reg_hint)) {
        DetailText(
            stringResource(R.string.reg_detail),
        )
    }

    stuck?.let {
        Spacer(Modifier.height(4.dp))
        Text(
            stringResource(R.string.reg_stuck, it),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
        )
    }

    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = newRegion,
            onValueChange = { newRegion = it },
            label = { Text(stringResource(R.string.reg_name)) },
            singleLine = true,
            isError = newRegion.isNotBlank() && !Regions.isValid(newRegion),
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = { vm.addRegion(newRegion); newRegion = "" },
            enabled = Regions.isValid(newRegion),
        ) { Text(stringResource(R.string.add)) }
    }

    ButtonFlowRow {
        FilledTonalButton(
            onClick = {
                discovering = true
                scope.launch {
                    discovered = vm.discoverRegions()
                    discovering = false
                }
            },
            enabled = !discovering,
        ) { Text(stringResource(R.string.reg_discover)) }
    }
    if (discovering) SectionSpinner(stringResource(R.string.reg_asking))

    Spacer(Modifier.height(4.dp))
    if (regions.isEmpty()) {
        HintText(stringResource(R.string.reg_none))
    } else {
        for (region in regions) {
            val usedBy = channelRegions.filterValues { it == region }.keys.sorted()
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    // A bullet, not '#': the '#' read as part of the name, and a
                    // repeater lists this same region as a bare "mi".
                    Text("• $region", style = MaterialTheme.typography.bodyLarge)
                    if (usedBy.isNotEmpty()) {
                        HintText(
                            stringResource(R.string.reg_used_by, usedBy.joinToString(", ") { it.toString() }),
                        )
                    }
                }
                FilledTonalButton(onClick = { confirmDelete = region }) {
                    Text(stringResource(R.string.node_remove_confirm), color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }

    confirmDelete?.let { region ->
        val usedBy = channelRegions.filterValues { it == region }.keys.sorted()
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text(stringResource(R.string.reg_forget_title, region)) },
            text = {
                Text(
                    if (usedBy.isEmpty()) {
                        stringResource(R.string.reg_forget_plain)
                    } else {
                        // Silently leaving a channel pointed at a deleted
                        // region would keep scoping its traffic to a name
                        // the user believes is gone.
                        stringResource(R.string.reg_forget_used, usedBy.joinToString(", ") { it.toString() })
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = { vm.removeRegion(region); confirmDelete = null }) {
                    Text(stringResource(R.string.reg_forget), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    discovered?.let { found ->
        DiscoveredRegionsDialog(
            result = found,
            known = regions,
            onAdd = { vm.addRegion(it) },
            onDismiss = { discovered = null },
        )
    }
}

/**
 * What nearby repeaters answered. Nothing here is authenticated — any
 * node in range can claim any region name — so this is a list to choose
 * from, never a list that gets imported.
 */
@Composable
private fun DiscoveredRegionsDialog(
    result: MeshCoreViewModel.RegionDiscovery,
    known: List<String>,
    onAdd: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.reg_heard)) },
        text = {
            Column {
                val found = result.names
                if (found.isEmpty()) {
                    Text(
                        when {
                            result.asked == 0 ->
                                stringResource(R.string.reg_heard_nobody)
                            result.answered > 0 ->
                                stringResource(R.string.reg_heard_global, result.answered, result.asked)
                            else ->
                                stringResource(R.string.reg_heard_silent, result.asked)
                        },
                    )
                } else {
                    Text(
                        stringResource(R.string.reg_heard_found),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    for (region in found) {
                        Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("• $region", Modifier.weight(1f))
                            if (region in known) {
                                HintText(stringResource(R.string.reg_added))
                            } else {
                                FilledTonalButton(onClick = { onAdd(region) }) { Text(stringResource(R.string.add)) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_done)) } },
    )
}

/**
 * Region picker for one channel slot. Tapping the selected region clears
 * it, matching the reference client — and "none" is a real choice, not a
 * missing one, so it is spelled out.
 */
@Composable
fun ChannelRegionPicker(vm: MeshCoreViewModel, channelIndex: Int) {
    val regions by vm.regions.collectAsState()
    val channelRegions by vm.channelRegions.collectAsState()
    val current = channelRegions[channelIndex]

    Text(stringResource(R.string.reg_channel_title), style = MaterialTheme.typography.labelLarge)
    if (regions.isEmpty()) {
        HintText(stringResource(R.string.reg_channel_none))
        return
    }
    HintText(
        if (current == null) {
            stringResource(R.string.reg_channel_global)
        } else {
            stringResource(R.string.reg_channel_scoped, current)
        },
    )
    ChoiceChips(
        options = listOf(stringResource(R.string.reg_none_option)) + regions,
        selected = if (current == null) 0 else regions.indexOf(current) + 1,
    ) { index ->
        vm.setChannelRegion(channelIndex, if (index == 0) null else regions[index - 1])
    }
}
