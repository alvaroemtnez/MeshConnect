package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.Spacer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel

/**
 * The confirmations for anything scanned, hosted once at the app root.
 *
 * These lived inside NodesScreen, which meant they only existed while
 * that screen was composed. A code scanned from the Chats button set the
 * pending state and nothing ever drew it — the scan silently did
 * nothing, for contact cards, channel shares and settings codes alike.
 * Same failure as the old "Show route on map": a flag set for a screen
 * that was not listening.
 *
 * Nothing here applies anything on its own. Each dialog states what will
 * change and waits, because none of these codes is authenticated and one
 * of them retunes the radio.
 */
@Composable
fun ScanConfirmations(vm: MeshCoreViewModel) {
    val pendingChannel by vm.pendingChannelShare.collectAsState()
    pendingChannel?.let { share ->
        AlertDialog(
            onDismissRequest = { vm.dismissChannelShare() },
            icon = { Icon(painterResource(R.drawable.ic_tag), null) },
            title = { Text(stringResource(R.string.join_title)) },
            text = {
                Column {
                    Text(
                        share.name.ifBlank { stringResource(R.string.join_unnamed) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    // A scope is a routing decision, so it is stated
                    // BEFORE the join rather than reported after it —
                    // joining may add a region the user does not have,
                    // and that is not something to discover in a
                    // snackbar.
                    if (share.regionScope.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.join_scoped, share.regionScope),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else if (share.rawRegionScope.isNotBlank()) {
                        // The code asked for a scope this build can't
                        // use. Saying nothing here is what made the
                        // dropped scope invisible: the dialog looked
                        // exactly like an unscoped channel's, and the
                        // join reported plain success.
                        Spacer(Modifier.height(4.dp))
                        Text(
                            stringResource(R.string.join_bad_scope, share.rawRegionScope),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.join_body),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                Button(onClick = { vm.confirmChannelShare(share) }) { Text(stringResource(R.string.join_confirm)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.dismissChannelShare() }) { Text(stringResource(R.string.back)) }
            },
        )
    }

    val pendingCard by vm.pendingContactCard.collectAsState()
    pendingCard?.let { card ->
        ContactCardConfirmDialog(
            card = card,
            onConfirm = { vm.confirmContactCard(card) },
            onDismiss = { vm.dismissContactCard() },
        )
    }

    // A scanned settings code. This dialog is the whole safety story:
    // nothing about the code is authenticated, and applying it decides
    // whether this radio is on a mesh at all — and what frequency it
    // transmits on, which is a legal question wherever you are standing.
    // So every value is shown, and the regulatory caveat comes with it.
    val pendingRadio by vm.pendingRadioConfig.collectAsState()
    pendingRadio?.let { config ->
        // Ticked by default: the code's author chose to include it, and
        // one scan putting a newcomer on the right frequency AND in the
        // right region is the point of the code. It stays a separate,
        // visible choice because it fails differently (see below).
        var applyRegion by remember(config) { mutableStateOf(config.region != null) }
        // A tree offers every level; the code's own default is preselected,
        // else the most local one.
        val choices = config.regionChoices()
        var picked by remember(config) { mutableStateOf(config.region ?: choices.lastOrNull()) }
        AlertDialog(
            onDismissRequest = { vm.pendingRadioConfig.value = null },
            icon = { Icon(painterResource(R.drawable.ic_sensors), null) },
            title = { Text(stringResource(R.string.radio_apply_title)) },
            text = {
                // Scrolls: a region tree adds a row per level.
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        config.name.ifBlank { stringResource(R.string.rsf_unnamed_mesh) },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(config.summary(), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        stringResource(R.string.scan_retunes),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.scan_unsigned) + " " +
                            stringResource(R.string.regulatory_caveat),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    picked?.takeIf { choices.isNotEmpty() }?.let { current ->
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.scan_region), style = MaterialTheme.typography.titleSmall)
                        config.regionHeadline()?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .toggleable(
                                    value = applyRegion,
                                    role = Role.Checkbox,
                                    onValueChange = { v -> applyRegion = v },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(checked = applyRegion, onCheckedChange = null)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                if (choices.size > 1) stringResource(R.string.scan_set_region) else stringResource(R.string.scan_also_region, current),
                            )
                        }
                        if (choices.size > 1) {
                            // The whole tree, widest first, each level
                            // indented under the one above — the same
                            // picture the web generator draws.
                            choices.forEachIndexed { depth, choice ->
                                Row(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(start = (12 + 14 * depth).dp)
                                        .selectable(
                                            selected = current == choice,
                                            enabled = applyRegion,
                                            role = Role.RadioButton,
                                            onClick = { picked = choice },
                                        ),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    RadioButton(
                                        selected = current == choice,
                                        onClick = null,
                                        enabled = applyRegion,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        (if (depth > 0) "└ " else "") + "#$choice",
                                        fontFamily = FontFamily.Monospace,
                                    )
                                    if (choice == config.region) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.rr_tag_default),
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                            }
                        }
                        // Named separately because it is a different
                        // failure: wrong radio values make you deaf,
                        // a wrong region leaves you audible but unable
                        // to propagate. Each level stands alone on the
                        // air — a repeater holding only #grr does not
                        // relay #mi — which is why the tree is offered
                        // rather than assumed.
                        Text(
                            stringResource(R.string.scan_region_effect, current),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    config.rejectedRegionTree?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.scan_bad_tree, it, io.github.thatsfguy.meshcore.protocol.ShareUri.MAX_REGION_TREE),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    config.rejectedRegion?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.scan_bad_region, it),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                // Error-coloured: this retunes the radio.
                Button(
                    onClick = { vm.confirmRadioConfig(config, picked.takeIf { applyRegion }) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.radio_apply)) }
            },
            dismissButton = {
                OutlinedButton(onClick = { vm.pendingRadioConfig.value = null }) { Text(stringResource(R.string.back)) }
            },
        )
    }
}
