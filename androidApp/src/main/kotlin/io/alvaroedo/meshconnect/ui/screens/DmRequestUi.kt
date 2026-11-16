package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.alvaroedo.meshconnect.ui.model.InternalChannel
import io.github.thatsfguy.meshcore.presentation.DmRequest
import io.github.thatsfguy.meshcore.protocol.ChannelKind

/**
 * Ask [name] (the name they advertise) to add us, so direct messages reach them (2026-11-16).
 * On #mcn-internal by default; or in a private group the two share, which keeps it among its
 * members. Said plainly, without alarm: all it shows anyone is that you asked.
 */
@Composable
fun AskToBeAddedDialog(vm: MeshCoreViewModel, name: String, shownName: String = name, onDismiss: () -> Unit) {
    val channels by vm.dbChannels.collectAsState()
    val kinds by vm.channelKinds.collectAsState()
    // Groups only: a hashtag or Public channel is as open as ours and has more people on it.
    val groups = channels.filter { kinds[it.idx] == ChannelKind.Private && it.name.isNotBlank() }
    var via by remember { mutableStateOf<Int?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_person_add), null) },
        title = { Text(stringResource(R.string.dmreq_ask_title, shownName)) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(stringResource(R.string.dmreq_ask_body, shownName))
                if (groups.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(stringResource(R.string.dmreq_ask_where), style = MaterialTheme.typography.titleSmall)
                    Column(Modifier.selectableGroup()) {
                        ViaRow(stringResource(R.string.dmreq_via_internal), via == null) { via = null }
                        for (g in groups) ViaRow(stringResource(R.string.dmreq_via_group, g.name), via == g.idx) { via = g.idx }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                vm.askToBeAdded(name, via)
                onDismiss()
            }) { Text(stringResource(R.string.dm_ask_send)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

@Composable
private fun ViaRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

/**
 * A request that came on #mcn-internal, which shows nowhere else (InternalChannel). Hosted at
 * the app root, one at a time, oldest first. Accept adds them and answers; Decline answers
 * nothing; Later leaves it for the next time the app opens.
 *
 * The name in a request is typed text; only the key is theirs. When our radio holds, or has
 * heard a signed advert from, that key under that name, the prompt says so; otherwise it says
 * to check with them, and shows the start of the key to compare.
 */
@Composable
fun InternalDmRequestPrompt(vm: MeshCoreViewModel) {
    val pending by vm.internalDmRequests.collectAsState()
    var later by remember { mutableStateOf(emptySet<String>()) }
    val request = pending.firstOrNull { it.keyHex !in later } ?: return
    val contacts by vm.dbContacts.collectAsState()
    val heard by vm.discovered.collectAsState()
    val confirmed = InternalChannel.nameConfirmed(
        request.keyHex,
        request.name,
        contacts.map { it.keyHex to it.name } + heard.map { it.keyHex to it.name },
    )
    val name = request.name.ifBlank { stringResource(R.string.dm_someone) }
    AlertDialog(
        onDismissRequest = { later = later + request.keyHex },
        icon = { Icon(painterResource(R.drawable.ic_chat), null) },
        title = { Text(stringResource(R.string.dmreq_incoming_title, name)) },
        text = {
            Column {
                Text(stringResource(R.string.dmreq_incoming_body))
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(if (confirmed) R.string.dmreq_confirmed else R.string.dmreq_unconfirmed),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.dm_key_starts, request.keyHex.take(DmRequest.PREFIX_HEX)),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            Button(onClick = { vm.acceptInternalDmRequest(request) }) { Text(stringResource(R.string.dm_accept)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { later = later + request.keyHex }) { Text(stringResource(R.string.onb_later)) }
                TextButton(onClick = { vm.declineDmRequest(request.keyHex) }) { Text(stringResource(R.string.dm_decline)) }
            }
        },
    )
}
