package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.BlockList

/**
 * Blocked senders and hidden channel names — Settings → App.
 * PARITY.md §3 (BlockedChannelSendersScreen).
 *
 * These are presented as two different things because they ARE two
 * different things, and the difference is the point:
 *
 *  - Blocking a contact matches their public key. It holds.
 *  - Hiding a channel name matches text anyone can type. It doesn't.
 *
 * PARITY §3 asks for block-by-key rather than by name. For direct
 * messages that is what this does. For channels it is not possible: a
 * MeshCore group message carries no sender key at all — the name is
 * inside the ciphertext and any holder of the channel PSK can write any
 * name (MESHCORE_PROTOCOL §9/§10). Rather than ship name-matching under
 * the word "block", the two are named for what they can actually
 * promise.
 */
@Composable
fun BlockingSection(vm: MeshCoreViewModel) {
    val blocked by vm.blockedKeys.collectAsState()
    val filtered by vm.filteredChannelNames.collectAsState()
    val contacts by vm.dbContacts.collectAsState()
    var newName by remember { mutableStateOf("") }

    Text(stringResource(R.string.block_contacts), style = MaterialTheme.typography.labelLarge)
    ExpandableHint(stringResource(R.string.block_by_key)) {
        DetailText(
            stringResource(R.string.block_dropped),
        )
    }
    if (blocked.isEmpty()) {
        HintText(stringResource(R.string.block_none))
    } else {
        for (key in blocked) {
            val name = contacts.firstOrNull { it.keyHex == key }?.name
            Row(
                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(name?.ifBlank { null } ?: stringResource(R.string.block_not_contact))
                    Text(
                        key.take(16) + "…",
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                FilledTonalButton(onClick = { vm.setBlocked(key, false) }) { Text(stringResource(R.string.node_unblock)) }
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Text(stringResource(R.string.block_hidden_names), style = MaterialTheme.typography.labelLarge)
    // The caveat lives in shared next to the matching code so the two
    // can't drift apart.
    Text(
        BlockList.CHANNEL_FILTER_CAVEAT,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.error,
    )
    Row(verticalAlignment = Alignment.CenterVertically) {
        FormField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text(stringResource(R.string.block_name_to_hide)) },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        FilledTonalButton(
            onClick = { vm.setChannelNameFiltered(newName.trim(), true); newName = "" },
            enabled = BlockList.canonicalName(newName) != null,
        ) { Text(stringResource(R.string.heard_hide)) }
    }
    for (name in filtered) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(name, Modifier.weight(1f))
            FilledTonalButton(onClick = { vm.setChannelNameFiltered(name, false) }) { Text(stringResource(R.string.show)) }
        }
    }
}
