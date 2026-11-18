package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.alvaroedo.meshconnect.ui.model.InternalChannel

/**
 * Requests waiting for an answer, as one row at the top of Chats (2026-11-18): only while
 * there are some, like the archived row at the bottom. Not a conversation each: there is no
 * contact yet and nothing to type, and they would count in unread totals, the widget and Simple
 * mode's list. The row opens [DmRequestListSheet]; a request there opens its prompt
 * (DmRequestPrompt), the one place a request is answered, with its name check. It is where a
 * request put off with Not now comes back to.
 */
@Composable
fun DmRequestsRow(count: Int, onClick: () -> Unit) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Box(Modifier.size(LIST_AVATAR_SIZE), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_person_add), null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        headlineContent = { Text(stringResource(R.string.dmreq_list_title), fontWeight = FontWeight.Bold) },
        supportingContent = { Text(stringResource(R.string.dmreq_list_row_hint)) },
        trailingContent = {
            Badge(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Text(count.toString()) }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DmRequestListSheet(vm: MeshCoreViewModel, onDismiss: () -> Unit) {
    val pending by vm.dmRequests.collectAsState()
    val contacts by vm.dbContacts.collectAsState()
    val heard by vm.discovered.collectAsState()
    val channels by vm.dbChannels.collectAsState()
    val context = LocalContext.current
    val known = contacts.map { it.keyHex to it.name } + heard.map { it.keyHex to it.name }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 16.dp),
        ) {
            Text(
                stringResource(R.string.dmreq_list_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            Text(
                stringResource(R.string.dmreq_list_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            // Answered from the prompt, the list empties itself; nothing left, it closes.
            if (pending.isEmpty()) {
                androidx.compose.runtime.LaunchedEffect(Unit) { onDismiss() }
            }
            // Newest first, like Chats; the prompt goes oldest first.
            for (request in pending.asReversed()) {
                val name = request.name.ifBlank { stringResource(R.string.dm_someone) }
                val via = channels.firstOrNull { it.idx == request.channelIndex }?.name?.ifBlank { null }
                val ago = io.alvaroedo.meshconnect.ui.model.Ago.of(context, request.receivedAtMs, System.currentTimeMillis())
                val confirmed = InternalChannel.nameConfirmed(request.keyHex, request.name, known)
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            vm.showDmRequest(request)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Greyed: not a contact yet.
                    Box(Modifier.alpha(0.45f)) {
                        NodeAvatar(seed = request.keyHex, label = name, size = LIST_AVATAR_SIZE)
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                name,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            if (!confirmed) {
                                Spacer(Modifier.width(6.dp))
                                Icon(
                                    painterResource(R.drawable.ic_warning),
                                    stringResource(R.string.dmreq_list_unconfirmed),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                        Text(
                            via?.let { stringResource(R.string.dmreq_list_via, it, ago) } ?: ago,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Icon(painterResource(R.drawable.ic_chevron_right), null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
