package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
fun ChatsScreen(vm: MeshCoreViewModel, nav: NavController, archivedView: Boolean = false) {
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
    val listRevision by vm.prefs.threadListRevision.collectAsState()
    val pinned = remember(listRevision) { vm.prefs.pinnedThreads }
    val archivedKeys = remember(listRevision) { vm.prefs.archivedThreads }
    var nicknameFor by remember { mutableStateOf<ConversationRow?>(null) }
    var editChannel by remember { mutableStateOf<Int?>(null) }
    // Multi-select, by thread key; null when off.
    var selection by remember { mutableStateOf<Set<String>?>(null) }
    var confirmDelete by remember { mutableStateOf<List<ConversationRow>?>(null) }
    androidx.activity.compose.BackHandler(enabled = selection != null) { selection = null }
    fun toggle(k: String) {
        selection = selection?.let { if (k in it) it - k else it + k }?.ifEmpty { null }
    }
    val channels by vm.dbChannels.collectAsState()

    val communityScanLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        io.alvaroedo.meshconnect.platform.ScanQr(),
    ) { result ->
        result?.let { vm.importScannedCode(it) }
    }

    Scaffold(
        topBar = {
            val picked = selection
            if (picked != null) {
                val rows = conversations.filter { io.alvaroedo.meshconnect.ui.model.ChatListModel.key(it) in picked }
                SelectionTopBar(count = picked.size, onClose = { selection = null }) {
                    androidx.compose.material3.IconButton(onClick = {
                        rows.forEach { vm.markRead(it.kind, it.key) }
                        selection = null
                    }) { Icon(painterResource(R.drawable.ic_mark_read), stringResource(R.string.chats_mark_read)) }
                    androidx.compose.material3.IconButton(onClick = {
                        rows.forEach { vm.prefs.setThreadArchived(io.alvaroedo.meshconnect.ui.model.ChatListModel.key(it), !archivedView) }
                        selection = null
                    }) {
                        Icon(
                            painterResource(if (archivedView) R.drawable.ic_unarchive else R.drawable.ic_archive),
                            stringResource(if (archivedView) R.string.chats_unarchive else R.string.chats_archive),
                        )
                    }
                    SelectionDeleteAction { confirmDelete = rows }
                }
            } else AppTopBar(
                title = stringResource(if (archivedView) R.string.chats_archived_title else R.string.nav_chats),
                vm = vm,
                nav = if (archivedView) nav else null,
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
                    MenuAction(stringResource(R.string.chats_advert_flood), icon = R.drawable.ic_campaign) {
                        vm.sendSelfAdvert(flood = true)
                    },
                    MenuAction(stringResource(R.string.chats_new_chat), icon = R.drawable.ic_edit_square) { showChannelSheet = true },
                    MenuAction(stringResource(R.string.nodes_scan_qr), icon = R.drawable.ic_qr_code_scanner) {
                        communityScanLauncher.launch(scanPrompt)
                    },
                    MenuAction(stringResource(R.string.my_qr), icon = R.drawable.ic_qr_code) { showSelfQr = true },
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showChannelSheet = true }) {
                Icon(painterResource(R.drawable.ic_edit_square), stringResource(R.string.chats_new_chat))
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
            val split = io.alvaroedo.meshconnect.ui.model.ChatListModel.split(filtered, pinned, archivedKeys)
            val pinnedRows = if (archivedView) emptyList() else split.pinned
            val rest = if (archivedView) split.archived else split.recent
            val kinds by vm.channelKinds.collectAsState()

            @Composable
            fun rowItem(row: ConversationRow, isPinned: Boolean) {
                val k = io.alvaroedo.meshconnect.ui.model.ChatListModel.key(row)
                val isArchived = k in archivedKeys
                // Right: read/unread. Left: archive/unarchive (Skypost's archive pattern).
                SwipeRow(
                    onStartSwipe = {
                        if (row.unread > 0 || row.markedUnread) vm.markRead(row.kind, row.key) else vm.markUnread(row.kind, row.key)
                    },
                    onEndSwipe = { vm.prefs.setThreadArchived(k, !isArchived) },
                    startIcon = if (row.unread > 0 || row.markedUnread) R.drawable.ic_mark_read else R.drawable.ic_mark_unread,
                    endIcon = if (isArchived) R.drawable.ic_unarchive else R.drawable.ic_archive,
                ) {
                    ConversationRowItem(
                        row,
                        channelKind = if (row.isChannel) kinds[row.key.toIntOrNull()] else null,
                        pinned = isPinned,
                        archived = isArchived,
                        selecting = selection != null,
                        selected = selection?.contains(k) == true,
                        onClick = {
                            if (selection != null) toggle(k) else nav.navigate(conversationRoute(row.kind, row.key))
                        },
                        onTogglePin = { vm.prefs.setThreadPinned(k, !isPinned) },
                        onToggleArchive = { vm.prefs.setThreadArchived(k, !isArchived) },
                        onToggleRead = {
                            if (row.unread > 0 || row.markedUnread) vm.markRead(row.kind, row.key) else vm.markUnread(row.kind, row.key)
                        },
                        onEdit = { if (row.isChannel) editChannel = row.key.toIntOrNull() else nicknameFor = row },
                        onSelect = { selection = setOf(k) },
                        onToggleSelected = { toggle(k) },
                        onDelete = { confirmDelete = listOf(row) },
                    )
                }
            }

            Column(Modifier.fillMaxSize().padding(padding)) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (pinnedRows.isNotEmpty()) {
                        item("pinned_header") { SectionHeader(stringResource(R.string.chats_pinned)) }
                        items(pinnedRows, key = { "p|${it.kind}|${it.key}" }) { row -> rowItem(row, true) }
                        item("recent_header") { SectionHeader(stringResource(R.string.chats_recent)) }
                    }
                    items(rest, key = { "${it.kind}|${it.key}" }) { row -> rowItem(row, false) }
                    if (!archivedView && split.archived.isNotEmpty()) {
                        item("archived") {
                            androidx.compose.material3.ListItem(
                                modifier = Modifier.clickable { nav.navigate("chats/archived") },
                                leadingContent = { Icon(painterResource(R.drawable.ic_archive), null) },
                                headlineContent = { Text(stringResource(R.string.chats_archived_count, split.archived.size)) },
                                trailingContent = if (split.archivedUnread) {
                                    { Badge(containerColor = MaterialTheme.colorScheme.primary) {} }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSelfQr) {
        SelfQrDialog(vm, onDismiss = { showSelfQr = false })
    }
    if (showChannelSheet) {
        ChannelAddSheet(
            vm,
            onDismiss = { showChannelSheet = false },
            onMessageContact = { nav.navigate(conversationRoute(io.alvaroedo.meshconnect.storage.MessageRepository.KIND_DM, it.keyHex)) },
        )
    }

    editChannel?.let { idx ->
        channels.firstOrNull { it.idx == idx }?.let { ch ->
            ChannelEditSheet(vm, ch, onDismiss = { editChannel = null })
        } ?: run { editChannel = null }
    }

    confirmDelete?.let { rows ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { confirmDelete = null },
            icon = { Icon(painterResource(R.drawable.ic_delete), null) },
            title = {
                Text(androidx.compose.ui.res.pluralStringResource(R.plurals.chats_delete_title, rows.size, rows.size))
            },
            text = {
                Text(
                    stringResource(
                        if (rows.any { it.isChannel }) R.string.chats_delete_body_channels else R.string.chats_delete_body,
                    ),
                )
            },
            confirmButton = {
                androidx.compose.material3.Button(
                    onClick = {
                        vm.deleteChats(rows)
                        confirmDelete = null
                        selection = null
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { confirmDelete = null }) { Text(stringResource(R.string.cancel)) }
            },
        )
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
    archived: Boolean = false,
    selecting: Boolean = false,
    selected: Boolean = false,
    onClick: () -> Unit,
    onTogglePin: () -> Unit = {},
    onToggleArchive: () -> Unit = {},
    onToggleRead: () -> Unit = {},
    onEdit: () -> Unit = {},
    onSelect: () -> Unit = {},
    onToggleSelected: () -> Unit = {},
    onDelete: () -> Unit = {},
) {
    var menuOpen by remember { mutableStateOf(false) }
    val markedLabel = stringResource(R.string.vm_marked_unread)
    val tint = MaterialTheme.colorScheme.primary
    Row(
        Modifier
            .fillMaxWidth()
            .background(if (selected) tint.copy(alpha = 0.12f) else androidx.compose.ui.graphics.Color.Transparent)
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (selecting) onToggleSelected() else menuOpen = true },
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.animation.AnimatedVisibility(
            visible = selecting,
            enter = androidx.compose.animation.expandHorizontally() + androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.shrinkHorizontally() + androidx.compose.animation.fadeOut(),
        ) {
            Icon(
                painterResource(if (selected) R.drawable.ic_check_circle else R.drawable.ic_circle),
                null,
                tint = if (selected) tint else MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(end = 12.dp).size(24.dp),
            )
        }
        NodeAvatar(
            // Channels seed from a stable per-slot tag (PSKs stay in the
            // vault); DMs seed from the contact pubkey.
            seed = if (row.isChannel) "channel|${row.key}|${row.title}" else row.key,
            label = row.title,
            type = row.contactType,
            isChannel = row.isChannel,
            channelKind = channelKind,
            size = LIST_AVATAR_SIZE,
        )
        androidx.compose.foundation.layout.Spacer(
            Modifier.padding(start = 16.dp),
        )
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                    fontWeight = if (row.unread > 0 || row.markedUnread) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }
            Text(
                row.subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
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
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (row.unread > 0) {
                Badge(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ) { Text(row.unread.toString()) }
            } else if (row.markedUnread) {
                // Marked by hand: the badge's circle with no number in it,
                // since there is no unread message to count.
                Box(
                    Modifier
                        .size(16.dp)
                        .background(MaterialTheme.colorScheme.primary, androidx.compose.foundation.shape.CircleShape)
                        .semantics { contentDescription = markedLabel },
                )
            }
        }
        // The message long-press card's look (rounded, raised), as a plain popup:
        // no blur here, the list stays readable behind it.
        MeshDropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.widthIn(min = 232.dp),
        ) {
            val unread = row.unread > 0 || row.markedUnread
            ChatMenuRow(
                if (unread) R.drawable.ic_mark_read else R.drawable.ic_mark_unread,
                stringResource(if (unread) R.string.chats_mark_read else R.string.conv_mark_unread),
            ) { menuOpen = false; onToggleRead() }
            if (!archived) {
                ChatMenuRow(
                    if (pinned) R.drawable.ic_keep_off else R.drawable.ic_push_pin,
                    stringResource(if (pinned) R.string.chats_unpin else R.string.chats_pin),
                ) { menuOpen = false; onTogglePin() }
            }
            ChatMenuRow(
                if (archived) R.drawable.ic_unarchive else R.drawable.ic_archive,
                stringResource(if (archived) R.string.chats_unarchive else R.string.chats_archive),
            ) { menuOpen = false; onToggleArchive() }
            // A channel edits the way Settings → Channels does; a DM has only its
            // private nickname to edit.
            ChatMenuRow(
                R.drawable.ic_edit,
                stringResource(if (row.isChannel) R.string.conv_channel_settings else R.string.chats_nickname_menu),
            ) { menuOpen = false; onEdit() }
            ChatMenuRow(R.drawable.ic_select, stringResource(R.string.conv_select)) { menuOpen = false; onSelect() }
            ChatMenuRow(R.drawable.ic_delete, stringResource(R.string.delete), destructive = true) {
                menuOpen = false
                onDelete()
            }
        }
    }
}

@Composable
private fun ChatMenuRow(
    @androidx.annotation.DrawableRes icon: Int,
    label: String,
    destructive: Boolean = false,
    onClick: () -> Unit,
) {
    val color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    DropdownMenuItem(
        text = { Text(label, style = MaterialTheme.typography.bodyLarge, color = color) },
        leadingIcon = { Icon(painterResource(icon), null, tint = color, modifier = Modifier.size(22.dp)) },
        onClick = onClick,
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 18.dp, vertical = 4.dp),
    )
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
