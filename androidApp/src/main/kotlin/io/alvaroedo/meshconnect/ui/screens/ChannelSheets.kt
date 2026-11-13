package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.storage.ContactEntity
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.size
import androidx.compose.ui.graphics.asImageBitmap
import io.alvaroedo.meshconnect.platform.Qr
import io.github.thatsfguy.meshcore.protocol.ShareUri
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.presentation.ChannelSetup
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import io.alvaroedo.meshconnect.ui.model.ChannelEditRules
import io.github.thatsfguy.meshcore.protocol.ChannelKind

/** The add sheet's choices, in the order Liam's MeshCore app offers them. */
private enum class AddChannelChoice { Public, CreatePrivate, JoinPrivate, Hashtag }

/**
 * Channel add sheet: four named choices, each a short form of its own,
 * plus the community QR scan.
 *
 * It was one form with two hidden rules — a name starting with '#' derived
 * the key, and a blank key field meant "make a random one" — and nothing
 * said that the KEY is the channel. That is how two radios end up holding
 * one channel under two names without anyone knowing. The choices and
 * their one-line descriptions follow the mainstream MeshCore app; the
 * security wording stays ours (obfuscated, not secure).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelAddSheet(
    vm: MeshCoreViewModel,
    onDismiss: () -> Unit,
    /**
     * Given, the sheet is Chats' "New chat" (2026-11-10): a person first, then the channel
     * choices, then one scanner for any code. Settings → Channels leaves it out.
     */
    onMessageContact: ((ContactEntity) -> Unit)? = null,
) {
    var choice by remember { mutableStateOf<AddChannelChoice?>(null) }
    var pickingContact by remember { mutableStateOf(false) }
    val clipboard = io.alvaroedo.meshconnect.ui.rememberTextClipboard()
    val newChat = onMessageContact != null

    val communityScanLauncher = rememberLauncherForActivityResult(io.alvaroedo.meshconnect.platform.ScanQr()) { result ->
        result?.let { vm.importScannedCode(it, openChat = newChat) }
        onDismiss()
    }

    val scanPrompt = stringResource(R.string.scan_prompt)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 24.dp).padding(bottom = 32.dp)) {
            when (val c = choice) {
                null if pickingContact && onMessageContact != null -> ContactPicker(
                    vm,
                    onBack = { pickingContact = false },
                    onPick = { onDismiss(); onMessageContact(it) },
                )
                null -> {
                    Text(
                        stringResource(if (newChat) R.string.newchat_title else R.string.ch_add_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    if (newChat) {
                        SheetHeading(stringResource(R.string.newchat_people))
                        ChoiceRow(R.drawable.ic_person, stringResource(R.string.newchat_contact), stringResource(R.string.newchat_contact_sub)) {
                            pickingContact = true
                        }
                        SheetHeading(stringResource(R.string.newchat_channels))
                    } else {
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(
                        stringResource(R.string.ch_add_intro),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    ChoiceRow(R.drawable.ic_public, stringResource(R.string.ch_public), stringResource(R.string.ch_public_sub)) {
                        choice = AddChannelChoice.Public
                    }
                    ChoiceRow(R.drawable.ic_lock, stringResource(R.string.ch_create_private), stringResource(R.string.ch_create_private_sub)) {
                        choice = AddChannelChoice.CreatePrivate
                    }
                    ChoiceRow(R.drawable.ic_password, stringResource(R.string.ch_join_private), stringResource(R.string.ch_join_private_sub)) {
                        choice = AddChannelChoice.JoinPrivate
                    }
                    ChoiceRow(R.drawable.ic_tag, stringResource(R.string.ch_hashtag), stringResource(R.string.ch_hashtag_sub)) {
                        choice = AddChannelChoice.Hashtag
                    }
                    if (newChat) SheetHeading(stringResource(R.string.newchat_scan))
                    // importScannedCode takes a contact's code as well as a channel's
                    // or a community's, so in New chat this one row scans any of them.
                    ChoiceRow(
                        R.drawable.ic_qr_code_scanner,
                        stringResource(if (newChat) R.string.newchat_scan else R.string.ch_scan),
                        stringResource(if (newChat) R.string.newchat_scan_sub else R.string.ch_scan_sub),
                    ) {
                        communityScanLauncher.launch(scanPrompt)
                    }
                    // A link that arrived somewhere it could not be tapped, copied instead.
                    if (newChat) {
                        ChoiceRow(
                            R.drawable.ic_content_paste,
                            stringResource(R.string.newchat_paste),
                            stringResource(R.string.newchat_paste_sub),
                        ) {
                            clipboard.paste {
                                vm.importPastedText(it, openChat = newChat)
                                onDismiss()
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        stringResource(R.string.ch_add_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                else -> AddChannelForm(vm, c, openChat = newChat, onBack = { choice = null }, onDone = onDismiss)
            }
        }
    }
}

/** People the radio can message, searchable; a tap opens the conversation. */
@Composable
private fun ContactPicker(vm: MeshCoreViewModel, onBack: () -> Unit, onPick: (ContactEntity) -> Unit) {
    var query by remember { mutableStateOf("") }
    val people = vm.dbContacts.collectAsState().value
        .filter { it.type == io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_CHAT }
        .filter { query.isBlank() || it.name.contains(query.trim(), ignoreCase = true) }
        .sortedWith(compareByDescending<ContactEntity> { it.flags and io.github.thatsfguy.meshcore.protocol.Codes.CONTACT_FLAG_FAVORITE != 0 }.thenBy { it.name.lowercase() })
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.material3.IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_arrow_back), stringResource(R.string.back))
        }
        Text(stringResource(R.string.newchat_contact), style = MaterialTheme.typography.titleLarge)
    }
    Spacer(Modifier.height(8.dp))
    MeshTextField(
        value = query,
        onValueChange = { query = it },
        placeholder = stringResource(R.string.newchat_search),
        leadingIcon = { Icon(painterResource(R.drawable.ic_search), null) },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    if (people.isEmpty()) {
        Text(
            stringResource(R.string.newchat_none),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 16.dp),
        )
    }
    for (c in people.take(200)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp))
                .clickable { onPick(c) }
                .padding(horizontal = 4.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            NodeAvatar(seed = c.keyHex, label = c.name.ifBlank { c.keyHex }, type = c.type, size = 40.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(c.name.ifBlank { c.keyHex.take(12) }, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val heard = io.github.thatsfguy.meshcore.presentation.LastHeard.seconds(c)
                if (heard > 0) {
                    Text(relativeAge(heard), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(@androidx.annotation.DrawableRes icon: Int, title: String, description: String, onClick: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
        // The same glyphs the channel's avatar will carry in Chats, so the
        // choice previews what it makes.
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.CircleShape,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp),
        ) {
            androidx.compose.foundation.layout.Box(contentAlignment = androidx.compose.ui.Alignment.Center) {
                androidx.compose.material3.Icon(
                    androidx.compose.ui.res.painterResource(icon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        androidx.compose.material3.Icon(
            androidx.compose.ui.res.painterResource(io.alvaroedo.meshconnect.R.drawable.ic_chevron_right),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AddChannelForm(
    vm: MeshCoreViewModel,
    choice: AddChannelChoice,
    /** From Chats' New chat: open the channel once it is in (NewChatThread). */
    openChat: Boolean,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    var name by remember(choice) { mutableStateOf("") }
    var key by remember(choice) { mutableStateOf("") }

    val title = when (choice) {
        AddChannelChoice.Public -> stringResource(R.string.ch_public)
        AddChannelChoice.CreatePrivate -> stringResource(R.string.ch_create_private)
        AddChannelChoice.JoinPrivate -> stringResource(R.string.ch_join_private)
        AddChannelChoice.Hashtag -> stringResource(R.string.ch_hashtag)
    }
    Text(title, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))

    // Validation runs as you type, but only complains once there is
    // something to complain about.
    val nameResult = when (choice) {
        AddChannelChoice.Hashtag -> ChannelSetup.hashtag(name)
        AddChannelChoice.CreatePrivate, AddChannelChoice.JoinPrivate -> ChannelSetup.privateName(name)
        AddChannelChoice.Public -> null
    }
    val keyResult = if (choice == AddChannelChoice.JoinPrivate) ChannelSetup.privateKey(key) else null
    fun problem(r: ChannelSetup.Result<*>?, typed: String) =
        (r as? ChannelSetup.Result.Problem)?.message?.takeIf { typed.isNotBlank() }

    when (choice) {
        AddChannelChoice.Public -> Explain(stringResource(R.string.ch_public_explain))
        AddChannelChoice.Hashtag -> Explain(stringResource(R.string.ch_hashtag_explain))
        AddChannelChoice.CreatePrivate -> Explain(stringResource(R.string.ch_create_explain))
        AddChannelChoice.JoinPrivate -> Explain(stringResource(R.string.ch_join_explain))
    }

    if (choice != AddChannelChoice.Public) {
        Spacer(Modifier.height(8.dp))
        FormField(
            value = name,
            onValueChange = { name = it },
            label = { Text(stringResource(if (choice == AddChannelChoice.Hashtag) R.string.ch_channel_name else R.string.ch_name)) },
            prefix = if (choice == AddChannelChoice.Hashtag) ({ Text("#") }) else null,
            singleLine = true,
            isError = problem(nameResult, name) != null,
            supportingText = problem(nameResult, name)?.let { { Text(it) } },
            modifier = Modifier.fillMaxWidth(),
        )
    }
    if (choice == AddChannelChoice.JoinPrivate) {
        Spacer(Modifier.height(12.dp))
        FormField(
            value = key,
            onValueChange = { key = it },
            label = { Text(stringResource(R.string.ch_secret_key)) },
            singleLine = true,
            isError = problem(keyResult, key) != null,
            supportingText = problem(keyResult, key)?.let { { Text(it) } },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
    }

    val okName = (nameResult as? ChannelSetup.Result.Ok)?.value
    val okKey = (keyResult as? ChannelSetup.Result.Ok)?.value
    val ready = when (choice) {
        AddChannelChoice.Public -> true
        AddChannelChoice.JoinPrivate -> okName != null && okKey != null
        else -> okName != null
    }
    androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        androidx.compose.material3.OutlinedButton(onClick = onBack) { Text(stringResource(R.string.back)) }
        Spacer(Modifier.weight(1f))
        androidx.compose.material3.Button(
            enabled = ready,
            onClick = {
                when (choice) {
                    AddChannelChoice.Public -> vm.joinPublicChannel(openChat)
                    AddChannelChoice.Hashtag -> vm.joinHashtagChannel(okName!!, openChat)
                    AddChannelChoice.CreatePrivate -> vm.createPrivateChannel(okName!!, openChat)
                    AddChannelChoice.JoinPrivate -> vm.joinPrivateChannel(okName!!, okKey!!, openChat)
                }
                onDone()
            },
        ) { Text(stringResource(if (choice == AddChannelChoice.CreatePrivate) R.string.ch_create else R.string.join_confirm)) }
    }
}

@Composable
private fun Explain(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium)
    Spacer(Modifier.height(8.dp))
}

/** Channel editor (rename / change PSK / remove) for Settings. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelEditSheet(vm: MeshCoreViewModel, channel: ChannelEntity, onDismiss: () -> Unit) {
    // See the confirmation below for why sharing needs one.
    var name by remember { mutableStateOf(channel.name) }
    var pskHex by remember { mutableStateOf("") }
    var pskLoaded by remember { mutableStateOf(false) }
    var shareConfirm by remember { mutableStateOf(false) }
    var shareQr by remember { mutableStateOf(false) }

    LaunchedEffect(channel.idx) {
        vm.channelPskHex(channel)?.let { pskHex = it }
        pskLoaded = true
    }

    val kind = vm.channelKinds.collectAsState().value[channel.idx]
    val keyLocked = ChannelEditRules.keyLocked(kind)
    val nameLocked = ChannelEditRules.nameLocked(kind)

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NodeAvatar(
                    seed = "ch${channel.idx}",
                    label = name.ifBlank { "#" },
                    isChannel = true,
                    channelKind = kind,
                    size = 48.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        name.ifBlank { stringResource(R.string.ch_numbered, channel.idx) },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        listOfNotNull(
                            kind?.let {
                                stringResource(
                                    when (it) {
                                        ChannelKind.Public -> R.string.ch_kind_public
                                        ChannelKind.Hashtag -> R.string.ch_kind_hashtag
                                        ChannelKind.Private -> R.string.ch_kind_private
                                    },
                                )
                            },
                            stringResource(R.string.ch_numbered, channel.idx),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            FormField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.ch_name)) },
                singleLine = true,
                readOnly = nameLocked,
                trailingIcon = if (nameLocked) { { Icon(painterResource(R.drawable.ic_lock), null) } } else null,
                modifier = Modifier.fillMaxWidth(),
            )
            FormField(
                value = pskHex,
                onValueChange = { pskHex = it },
                label = { Text(stringResource(if (pskLoaded) R.string.ch_secret_key else R.string.ch_loading_key)) },
                singleLine = true,
                readOnly = keyLocked,
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                trailingIcon = if (keyLocked) { { Icon(painterResource(R.drawable.ic_lock), null) } } else null,
                supportingText = {
                    Text(
                        stringResource(
                            when (kind) {
                                ChannelKind.Public -> R.string.ch_key_locked_public
                                ChannelKind.Hashtag -> R.string.ch_key_locked_hashtag
                                else -> R.string.ch_key_stored
                            },
                        ),
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            // The region is local-only routing state (PARITY §8): it
            // scopes which repeaters carry this channel's traffic and is
            // never written to the radio's channel slot.
            ChannelRegionPicker(vm, channel.idx)
            Button(
                onClick = {
                    vm.editChannel(channel.idx, (if (nameLocked) channel.name else name).trim(), pskHex.trim())
                    onDismiss()
                },
                enabled = pskLoaded,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.save)) }
            Column {
                SheetActionRow(R.drawable.ic_qr_code, stringResource(R.string.ch_share_qr)) {
                    if (pskLoaded && pskHex.isNotBlank()) shareConfirm = true
                }
                SheetActionRow(R.drawable.ic_delete, stringResource(R.string.ch_remove), destructive = true) {
                    vm.deleteChannel(channel.idx)
                    onDismiss()
                }
            }
        }
    }

    // Sharing a channel hands over its key, and the key IS the channel.
    // Make that a decision, not a tap.
    if (shareConfirm) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { shareConfirm = false },
            title = { Text(stringResource(R.string.ch_share_title)) },
            text = {
                Text(
                    stringResource(R.string.ch_share_body),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = { shareConfirm = false; shareQr = true }) {
                    Text(stringResource(R.string.ch_show_code), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { shareConfirm = false }) { Text(stringResource(R.string.cancel)) }
            },
        )
    }

    if (shareQr) {
        ChannelQrDialog(
            name = name.ifBlank { "#${channel.idx}" },
            pskHex = pskHex.trim(),
            // Share the scope along with the key: a code that omits it
            // hands someone a channel that behaves differently from the
            // one you are running, and neither of you can see why.
            regionScope = vm.prefs.channelRegion(channel.idx).orEmpty(),
            onDismiss = { shareQr = false },
        )
    }
}

/** QR for `meshcore://channel/add?…` — the form other MeshCore apps scan. */
@Composable
private fun ChannelQrDialog(
    name: String,
    pskHex: String,
    regionScope: String,
    onDismiss: () -> Unit,
) {
    val uri = remember(name, pskHex, regionScope) {
        ShareUri.encodeChannel(name, pskHex, regionScope)
    }
    val qr = remember(uri) { runCatching { Qr.encode(uri) }.getOrNull() }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.ch_share_named, name), maxLines = 2) },
        text = {
            Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                qr?.let {
                    androidx.compose.foundation.Image(
                        it.asImageBitmap(),
                        contentDescription = stringResource(R.string.ch_qr),
                        modifier = Modifier.size(280.dp),
                    )
                }
                Text(
                    stringResource(R.string.ch_qr_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_done)) } },
        dismissButton = { QrLinkActions(uri) },
    )
}
