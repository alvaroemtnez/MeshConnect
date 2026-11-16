package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.storage.MessageRepository
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.alvaroedo.meshconnect.ui.model.SharedText
import io.alvaroedo.meshconnect.ui.model.SimpleMode
import io.github.thatsfguy.meshcore.presentation.LastHeard
import io.github.thatsfguy.meshcore.protocol.BlockList
import io.github.thatsfguy.meshcore.protocol.Codes

/**
 * A contact's or a room's card in Simple mode (2026-11-16): who it is, whether they hear you,
 * and the few things a person does with a contact. The full card (ContactDetailSheet) has
 * routing, paths, telemetry, permissions and recovery, which Simple mode leaves to the full app.
 *
 * Laid out like the group card: a header, then a list that starts with the QR code.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SimpleContactSheet(vm: MeshCoreViewModel, contact: ContactEntity, onDismiss: () -> Unit) {
    // The row as the database has it now: a favourite or a rename shows at once.
    @Suppress("NAME_SHADOWING")
    val contact = vm.dbContacts.collectAsState().value.firstOrNull { it.keyHex == contact.keyHex } ?: contact
    var renameOpen by remember { mutableStateOf(false) }
    var removeConfirm by remember { mutableStateOf(false) }
    var shareQrOpen by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Who, as on the full card, with the favourite star beside it.
            val savedFav = contact.flags and Codes.CONTACT_FLAG_FAVORITE != 0
            var favPending by remember(contact.keyHex) { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(savedFav) { favPending = null }
            val isFav = favPending ?: savedFav
            Row(verticalAlignment = Alignment.CenterVertically) {
                NodeAvatar(
                    seed = contact.keyHex,
                    label = contact.name.ifBlank { contact.keyHex },
                    type = contact.type,
                    typeIcon = true,
                    size = 56.dp,
                )
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        contact.name.ifBlank { stringResource(R.string.node_unnamed) },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        SharedText.kind(androidx.compose.ui.platform.LocalResources.current, contact.type) +
                            (if (LastHeard.seconds(contact) > 0) " · " + relativeAge(LastHeard.seconds(contact)) else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = {
                    favPending = !isFav
                    vm.setFavourite(contact.keyHex, !isFav)
                }) {
                    Icon(
                        painterResource(if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star),
                        stringResource(R.string.node_qa_favourite),
                        tint = if (isFav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- Can they hear you: the full card's own section, Check included. People
            // only; a room is answered by signing in.
            if (contact.type == Codes.ADV_TYPE_CHAT) ReachSection(vm, contact.keyHex, contact.type)

            HorizontalDivider(Modifier.padding(top = 16.dp, bottom = 4.dp))
            SheetActionRow(R.drawable.ic_qr_code, stringResource(R.string.simple_show_qr)) { shareQrOpen = true }
            SheetActionRow(R.drawable.ic_edit, stringResource(R.string.node_rename)) { renameOpen = true }
            val blocked = vm.isBlocked(contact.keyHex)
            if (BlockList.isBlockableNodeType(contact.type) || blocked) {
                SheetActionRow(
                    R.drawable.ic_block,
                    stringResource(if (blocked) R.string.node_unblock else R.string.node_block),
                    destructive = !blocked,
                ) { vm.setBlocked(contact.keyHex, !blocked) }
            }
            SheetActionRow(R.drawable.ic_delete, stringResource(R.string.node_remove), destructive = true) {
                removeConfirm = true
            }
        }
    }

    if (shareQrOpen) ContactQrDialog(vm, contact, onDismiss = { shareQrOpen = false })

    if (renameOpen) {
        var name by remember { mutableStateOf(contact.name) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(R.string.node_rename_title)) },
            text = { FormField(value = name, onValueChange = { name = it }, singleLine = true) },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameContact(contact, name.trim())
                    renameOpen = false
                }) { Text(stringResource(R.string.save)) }
            },
            dismissButton = {
                TextButton(onClick = { renameOpen = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (removeConfirm) {
        AlertDialog(
            onDismissRequest = { removeConfirm = false },
            title = { Text(stringResource(R.string.node_remove_title)) },
            text = { Text(stringResource(R.string.node_remove_body, contact.name.ifBlank { contact.keyHex.take(12) })) },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeContact(contact.keyHex)
                    removeConfirm = false
                    onDismiss()
                }) { Text(stringResource(R.string.node_remove_confirm), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { removeConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }
}

/**
 * A contact's card for whichever mode is on: the simple one, or the full ContactDetailSheet
 * with its actions wired to navigation. Used wherever a card opens outside the Nodes tab.
 */
@Composable
fun ContactCard(
    vm: MeshCoreViewModel,
    nav: NavController,
    contact: ContactEntity,
    openAdmin: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    if (SimpleMode.isSimple(LocalAppMode.current)) {
        SimpleContactSheet(vm, contact, onDismiss)
        return
    }
    ContactDetailSheet(
        vm = vm,
        contact = contact,
        onDismiss = onDismiss,
        // A room's conversation asks for its sign-in itself (the banner).
        onOpenChat = {
            onDismiss()
            nav.navigate(conversationRoute(MessageRepository.KIND_DM, contact.keyHex))
        },
        onOpenAdmin = {
            onDismiss()
            openAdmin(contact.keyHex)
        },
        onTrace = {
            onDismiss()
            nav.navigate(traceRoute(contact.keyHex))
        },
        onShowOnMap = {
            onDismiss()
            showOnMap(vm, nav, contact.keyHex)
        },
        onFlashFirmware = {
            onDismiss()
            val role = if (contact.type == Codes.ADV_TYPE_ROOM) "room" else "repeater"
            nav.navigate(
                "firmware/node?role=$role&node=${contact.keyHex}" +
                    (contact.otaAddress?.let { "&mac=$it" } ?: "") +
                    (contact.boardName?.let { "&board=${io.github.thatsfguy.meshcore.presentation.encodePrefill(it)}" } ?: ""),
            )
        },
    )
}

/**
 * The card a scanned code or a held New chat row asked for (vm.openCardRequest), hosted once
 * at the app root like ScanConfirmations, so it opens over whichever screen is showing. A
 * contact just added may not be in the database yet: wait for it, for a while.
 */
@Composable
fun NodeCardHost(vm: MeshCoreViewModel, nav: NavController) {
    // Here, not under the early returns: its sign-in dialog must outlive the card.
    val openAdmin = rememberAdminOpener(vm, nav)
    val key by vm.openCardRequest.collectAsState()
    val contacts by vm.dbContacts.collectAsState()
    val wanted = key ?: return
    val contact = contacts.firstOrNull { it.keyHex == wanted }
    LaunchedEffect(wanted, contact == null) {
        if (contact == null) {
            // An add the radio refused never appears; stop waiting for it.
            kotlinx.coroutines.delay(30_000)
            if (vm.openCardRequest.value == wanted) vm.openCardRequest.value = null
        }
    }
    contact ?: return
    ContactCard(vm, nav, contact, openAdmin, onDismiss = { vm.openCardRequest.value = null })
}
