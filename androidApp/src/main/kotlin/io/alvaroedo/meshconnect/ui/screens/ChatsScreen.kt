package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.combinedClickable
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.layout.size
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.ConversationRow
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.engine.EngineState
import io.github.thatsfguy.meshcore.protocol.Codes
import java.text.DateFormat
import java.util.Date

/**
 * Merged conversation list: direct-message threads and channels, newest
 * first, with unread badges. FAB opens the channel-add / community-join
 * sheet; DM threads start from the Nodes tab.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(vm: MeshCoreViewModel, nav: NavController) {
    val conversations by vm.conversations.collectAsState()
    val engineState by vm.engineState.collectAsState()
    var showChannelSheet by remember { mutableStateOf(false) }
    // Saveable, not remember: opening a node or a conversation takes this
    // screen off the composition, and coming back used to start the search
    // over — the term typed to find it was gone (reported 2026-09-25).
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var showSelfQr by remember { mutableStateOf(false) }
    val scanPrompt = stringResource(R.string.scan_prompt)
    var pinned by remember { mutableStateOf(vm.prefs.pinnedThreads) }
    var nicknameFor by remember { mutableStateOf<ConversationRow?>(null) }

    val communityScanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        com.journeyapps.barcodescanner.ScanContract(),
    ) { result ->
        result.contents?.let { vm.importScannedCode(it) }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_chats),
                vm = vm,
                search = TopBarSearch(
                    query = query,
                    onQueryChange = { query = it },
                    active = searching,
                    onActiveChange = { searching = it },
                    hint = stringResource(R.string.chats_search_hint),
                ),
                menuActions = listOf(
                    // Adverts live here as well as on Settings -> Identity.
                    // Announcing yourself is a frequent, situational thing
                    // ("nobody can see me, say hello again"), and the tidy
                    // answer to "which screen does this belong on" had put
                    // it three taps deep behind a settings page. Being
                    // reachable from the screen you are already on beats
                    // being filed correctly.
                    MenuAction(stringResource(R.string.chats_advert_zero_hop), icon = R.drawable.ic_sensors) {
                        vm.sendSelfAdvert(flood = false)
                    },
                    MenuAction(stringResource(R.string.chats_advert_flood), icon = R.drawable.ic_radar) {
                        vm.sendSelfAdvert(flood = true)
                    },
                    MenuAction(stringResource(R.string.chats_add_channel), icon = R.drawable.ic_tag) { showChannelSheet = true },
                    MenuAction(stringResource(R.string.nodes_scan_qr), icon = R.drawable.ic_qr_code_scanner) {
                        communityScanLauncher.launch(meshScanOptions(scanPrompt))
                    },
                    MenuAction(stringResource(R.string.my_qr), icon = R.drawable.ic_qr_code) { showSelfQr = true },
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showChannelSheet = true }) {
                Icon(painterResource(R.drawable.ic_add), stringResource(R.string.chats_add_channel))
            }
        },
    ) { padding ->
        if (conversations.isEmpty() && engineState != EngineState.Ready) {
            NoRadioState(vm, Modifier.fillMaxSize().padding(padding))
        } else if (conversations.isEmpty()) {
            EmptyHint(
                modifier = Modifier.padding(padding),
                text = stringResource(R.string.chats_empty),
            )
        } else {
            val filtered = conversations.filter { row ->
                query.isBlank() ||
                    row.title.contains(query, ignoreCase = true) ||
                    row.subtitle.contains(query, ignoreCase = true)
            }
            val pinnedRows = filtered.filter { "${it.kind}|${it.key}" in pinned }
            val kinds by vm.channelKinds.collectAsState()
            val rest = filtered.filter { "${it.kind}|${it.key}" !in pinned }

            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (pinnedRows.isNotEmpty()) {
                        item("pinned_header") { SectionHeader(stringResource(R.string.chats_pinned)) }
                        items(pinnedRows, key = { "p|${it.kind}|${it.key}" }) { row ->
                            ConversationRowItem(
                                row,
                                channelKind = if (row.isChannel) kinds[row.key.toIntOrNull()] else null,
                                pinned = true,
                                onClick = { nav.navigate(conversationRoute(row.kind, row.key)) },
                                onTogglePin = {
                                    vm.prefs.setThreadPinned("${row.kind}|${row.key}", false)
                                    pinned = vm.prefs.pinnedThreads
                                },
                                onNickname = { nicknameFor = row },
                            )
                        }
                        item("recent_header") { SectionHeader(stringResource(R.string.chats_recent)) }
                    }
                    items(rest, key = { "${it.kind}|${it.key}" }) { row ->
                        ConversationRowItem(
                            row,
                            channelKind = if (row.isChannel) kinds[row.key.toIntOrNull()] else null,
                            pinned = false,
                            onClick = { nav.navigate(conversationRoute(row.kind, row.key)) },
                            onTogglePin = {
                                vm.prefs.setThreadPinned("${row.kind}|${row.key}", true)
                                pinned = vm.prefs.pinnedThreads
                            },
                            onNickname = { nicknameFor = row },
                        )
                    }
                }
            }
        }
    }

    if (showSelfQr) {
        SelfQrDialog(vm, onDismiss = { showSelfQr = false })
    }
    if (showChannelSheet) {
        ChannelAddSheet(vm, onDismiss = { showChannelSheet = false })
    }

    nicknameFor?.let { row ->
        NicknameDialog(
            current = vm.nicknameFor(row.key).orEmpty(),
            subject = row.title,
            onSave = { name ->
                vm.setNickname(row.key, name)
                nicknameFor = null
            },
            onDismiss = { nicknameFor = null },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ConversationRowItem(
    row: ConversationRow,
    channelKind: io.github.thatsfguy.meshcore.protocol.ChannelKind? = null,
    pinned: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: () -> Unit = {},
    onNickname: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeAvatar(
            // Channels seed from a stable per-slot tag (PSKs stay in the
            // vault); DMs seed from the contact pubkey.
            seed = if (row.isChannel) "channel|${row.key}|${row.title}" else row.key,
            label = row.title,
            type = row.contactType,
            isChannel = row.isChannel,
            channelKind = channelKind,
        )
        androidx.compose.foundation.layout.Spacer(
            Modifier.padding(start = 12.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (row.unread > 0) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            if (pinned) {
                Icon(
                    painterResource(R.drawable.ic_push_pin),
                    stringResource(R.string.chats_pinned),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp),
                )
            }
            if (row.timestamp > 0) {
                Text(
                    DateFormat.getTimeInstance(DateFormat.SHORT)
                        .format(Date(row.timestamp * 1000)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.unread > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text(row.unread.toString()) }
            }
        }
        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(if (pinned) R.string.chats_unpin else R.string.chats_pin)) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_push_pin), null) },
                onClick = { menuOpen = false; onTogglePin() },
            )
            if (!row.isChannel) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.chats_nickname_menu)) },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_edit), null) },
                    onClick = { menuOpen = false; onNickname() },
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
    )
}

/**
 * Local-only rename. Distinct from the contact Rename action, which
 * rewrites the record on the radio and is visible to the mesh; this
 * never leaves the phone.
 */
@Composable
private fun NicknameDialog(
    current: String,
    subject: String,
    onSave: (String?) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(current) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.chats_nickname_title)) },
        text = {
            Column {
                Text(
                    stringResource(R.string.chats_nickname_body, subject),
                    style = MaterialTheme.typography.bodySmall,
                )
                FormField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.chats_nickname)) },
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                onSave(text.trim().ifBlank { null })
            }) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            androidx.compose.material3.TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}

@Composable
fun EmptyHint(modifier: Modifier = Modifier, text: String) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(32.dp),
        ) {
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
