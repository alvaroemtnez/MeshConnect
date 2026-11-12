package io.alvaroedo.meshconnect.ui.screens

import io.github.thatsfguy.meshcore.presentation.AdminSession
import android.graphics.Bitmap
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.model.NodeKinds
import io.alvaroedo.meshconnect.ui.model.SharedText
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.width
import io.alvaroedo.meshconnect.ui.model.LinkMeter
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.platform.Qr
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.storage.MessageRepository
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.presentation.BatteryLevel
import io.github.thatsfguy.meshcore.presentation.TelemetryFetch
import io.github.thatsfguy.meshcore.presentation.LastHeard
import io.github.thatsfguy.meshcore.presentation.NodeListModel
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import io.github.thatsfguy.meshcore.presentation.NodeTab
import io.github.thatsfguy.meshcore.presentation.NodeTabsModel
import io.github.thatsfguy.meshcore.presentation.encodePrefill
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import io.github.thatsfguy.meshcore.util.RelativeTime
import io.github.thatsfguy.meshcore.presentation.Units
import io.github.thatsfguy.meshcore.util.haversineMetres
import io.github.thatsfguy.meshcore.util.isPlausiblePosition
import io.github.thatsfguy.meshcore.protocol.BlockList
import io.github.thatsfguy.meshcore.protocol.PathCodec
import io.github.thatsfguy.meshcore.protocol.Codes
import java.text.DateFormat
import java.util.Date

/**
 * Contacts/nodes list with kind filter chips (All, Contacts, Repeaters,
 * Rooms, Sensors), search in the top bar, QR import from the scan FAB,
 * and the contact detail sheet.
 *
 * Nodes heard but not added sit above the list and stay hidden until the
 * list is pulled down: on a busy mesh they were a wall of rows in front of
 * the contacts you came for. The chips carry their counts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NodesScreen(vm: MeshCoreViewModel, nav: NavController) {
    val openAdmin = rememberAdminOpener(vm, nav)
    val contacts by vm.dbContacts.collectAsState()
    var detail by remember { mutableStateOf<ContactEntity?>(null) }
    // A room being signed into on the way to its conversation.
    var roomSignIn by remember { mutableStateOf<ContactEntity?>(null) }
    // A room is one thing — sign in, then read and post — so it opens
    // its conversation, through the sign-in when this app has no
    // session (RoomModel.kt says why the sign-in is not optional).
    fun openRoom(room: ContactEntity) {
        if ((vm.adminSessions.value[room.keyHex] ?: AdminSession.None).signedIn) {
            nav.navigate(conversationRoute(MessageRepository.KIND_DM, room.keyHex))
        } else {
            roomSignIn = room
        }
    }
    // A heard-but-not-added node's sheet, and the key of one just added
    // from it: when the radio's contact list gains that key, its full
    // contact sheet opens in the heard sheet's place.
    var heardDetail by remember {
        mutableStateOf<io.alvaroedo.meshconnect.storage.DiscoveredEntity?>(null)
    }
    var openWhenAdded by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(contacts, openWhenAdded) {
        val key = openWhenAdded ?: return@LaunchedEffect
        contacts.firstOrNull { it.keyHex == key }?.let {
            detail = it
            openWhenAdded = null
        }
    }
    LaunchedEffect(openWhenAdded) {
        // An Add the radio refused never appears; stop waiting for it.
        if (openWhenAdded != null) {
            kotlinx.coroutines.delay(30_000)
            openWhenAdded = null
        }
    }
    var staleOpen by remember { mutableStateOf(false) }

    val scanLauncher = rememberLauncherForActivityResult(io.alvaroedo.meshconnect.platform.ScanQr()) { result ->
        result?.let { vm.importScannedCode(it) }
    }
    val scanPrompt = stringResource(R.string.scan_prompt)
    var showSelfQr by remember { mutableStateOf(false) }
    var discovering by remember { mutableStateOf(false) }
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current

    // Saveable: opening a node or a conversation takes this screen off the
    // composition, and the search term must survive the round trip.
    var query by rememberSaveable { mutableStateOf("") }
    var searching by rememberSaveable { mutableStateOf(false) }
    var kind by remember { mutableStateOf(NodeKinds.fromSaved(vm.prefs.nodesKind)) }
    var sort by remember { mutableStateOf(vm.prefs.nodesSort) }
    var filters by remember { mutableStateOf(vm.prefs.nodesFilters) }
    var hideHeard by remember { mutableStateOf(vm.prefs.nodesHideHeard) }
    var sortMenu by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_nodes),
                vm = vm,
                search = TopBarSearch(
                    query = query,
                    onQueryChange = { query = it },
                    active = searching,
                    onActiveChange = { searching = it },
                    hint = stringResource(R.string.nodes_search_hint),
                ),
                actions = {
                    Box {
                        IconButton(onClick = { sortMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_sort),
                                stringResource(R.string.nodes_sort_filter),
                                // Tinted while something is hidden, so the
                                // state shows without opening the menu.
                                tint = if (filters.isEmpty() && !hideHeard) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        SortFilterMenu(
                            open = sortMenu,
                            onDismiss = { sortMenu = false },
                            sort = sort,
                            filters = filters,
                            hideHeard = hideHeard,
                            onSortChange = { sort = it; vm.prefs.nodesSort = it },
                            onToggleFilter = { f ->
                                filters = if (f in filters) filters - f else filters + f
                                vm.prefs.nodesFilters = filters
                            },
                            onClearFilters = { filters = emptySet(); vm.prefs.nodesFilters = filters },
                            onHideHeardChange = { hideHeard = it; vm.prefs.nodesHideHeard = it },
                        )
                    }
                },
                menuActions = listOf(
                    // The camera is not the only way a code arrives: contacts
                    // shared between clients usually come as a copied link.
                    MenuAction(stringResource(R.string.nodes_paste_code), icon = R.drawable.ic_content_copy) {
                        vm.importPastedText(clipboard.getText()?.text)
                    },
                    MenuAction(stringResource(R.string.my_qr), icon = R.drawable.ic_qr_code) { showSelfQr = true },
                    // Active discovery: a broadcast asking nearby repeaters
                    // to speak up, as opposed to the passive advert inbox.
                    MenuAction(stringResource(R.string.nodes_discover), icon = R.drawable.ic_radar) { discovering = true },
                    // Not who is out there, but who is carrying MY traffic.
                    MenuAction(stringResource(R.string.nodes_who_repeats), icon = R.drawable.ic_cell_tower) {
                        nav.navigate(HEARD_REPEATS_ROUTE)
                    },
                    // The only bulk-destructive action in the app; it has
                    // its own preview-then-confirm dialog.
                    MenuAction(stringResource(R.string.nodes_remove_stale), icon = R.drawable.ic_delete_sweep) {
                        staleOpen = true
                    },
                ),
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { scanLauncher.launch(scanPrompt) }) {
                Icon(painterResource(R.drawable.ic_qr_code_scanner), stringResource(R.string.nodes_scan_qr))
            }
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            val discovered by vm.discovered.collectAsState()
            val ofThisKind = contacts.filter { NodeKinds.matches(kind, it.type) }
            // Searching, narrowing and ordering are NodeListModel's, so the
            // rules are pinned by tests rather than by driving a phone.
            val listed = NodeListModel.arrange(
                items = ofThisKind,
                query = query,
                sort = sort,
                filters = filters,
                nowSeconds = vm.nowSeconds(),
            )
            val newCounts = NodeTabsModel.counts(discovered, hidden = hideHeard)
            val signals by vm.heardSignals.collectAsState()
            // Useful ones first — relayed your traffic or heard direct —
            // then the rest, each group newest first (the sort is stable).
            val heard = NodeKinds.heard(
                kind = kind,
                heard = discovered,
                query = query,
                filtersActive = filters.isNotEmpty(),
                hidden = hideHeard,
            ).sortedByDescending { d ->
                signals[d.keyHex]?.let { it.provenRelay || it.heardDirect } == true
            }

            KindChips(
                selected = kind,
                newCounts = newCounts,
                onSelect = {
                    kind = it
                    vm.prefs.nodesKind = NodeKinds.toSaved(it)
                },
            )
            ActiveFilterNotes(
                filters = filters,
                hiddenNote = if (hideHeard) stringResource(R.string.hiding_not_added) else null,
                onShowHeard = { hideHeard = false; vm.prefs.nodesHideHeard = false },
            )

            // The heard section is the list's "-1" row: a fold-out header that sits just
            // above the top when the list opens, so it is there when you scroll up and out
            // of the way otherwise. It was a pull-to-refresh, whose spinner promised a
            // refresh that never happened. Kept across navigation, so opening a node and
            // coming back doesn't fold it away again.
            var heardRevealed by rememberSaveable { mutableStateOf(false) }
            var confirmDismiss by remember { mutableStateOf<List<String>?>(null) }
            val radioFull by vm.contactsFull.collectAsState()
            val listState = rememberLazyListState(initialFirstVisibleItemIndex = if (heard.isNotEmpty()) 1 else 0)
            val hasHeard = heard.isNotEmpty()
            // Heard nodes can arrive after the list is first drawn; tuck the row away then too.
            LaunchedEffect(hasHeard) {
                if (hasHeard && !heardRevealed && listState.firstVisibleItemIndex == 0 &&
                    listState.firstVisibleItemScrollOffset == 0
                ) {
                    listState.scrollToItem(1)
                }
            }

            Box(Modifier.fillMaxSize()) {
                LazyColumn(Modifier.fillMaxSize(), state = listState) {
                    if (hasHeard) {
                        item(key = "heard_header") {
                            HeardHeader(
                                count = heard.size,
                                expanded = heardRevealed,
                                radioFull = radioFull,
                                onToggle = { heardRevealed = !heardRevealed },
                                onDismissAll = { confirmDismiss = heard.map { it.keyHex } },
                            )
                        }
                    }
                    if (hasHeard && heardRevealed) {
                        items(heard, key = { "heard_${it.keyHex}" }) { d ->
                            DiscoveredRow(
                                node = d,
                                signals = signals[d.keyHex],
                                onOpen = { heardDetail = d },
                                onAdd = { vm.addDiscovered(d.keyHex) },
                                onDismiss = { vm.dismissDiscovered(d.keyHex) },
                            )
                        }
                        item(key = "heard_divider") { HorizontalDivider() }
                    }
                    if (listed.isEmpty() && (heard.isEmpty() || heardRevealed)) {
                        item(key = "empty") {
                            val joined = filters.map { filterLabel(it).lowercase() }
                                .joinToString(stringResource(R.string.nodes_and))
                            EmptyHint(
                                modifier = Modifier.height(360.dp),
                                // An empty list with an active filter looks
                                // exactly like a broken app. Say which it is.
                                text = when {
                                    filters.isNotEmpty() && ofThisKind.isNotEmpty() ->
                                        stringResource(R.string.nodes_empty_filtered, joined)
                                    query.isNotBlank() && ofThisKind.isNotEmpty() ->
                                        stringResource(R.string.nodes_empty_query, query)
                                    kind == NodeTab.Repeaters -> stringResource(R.string.nodes_empty_repeaters)
                                    kind == NodeTab.Rooms -> stringResource(R.string.nodes_empty_rooms)
                                    kind == NodeTab.Sensors -> stringResource(R.string.nodes_empty_sensors)
                                    else -> stringResource(R.string.nodes_empty)
                                },
                            )
                        }
                    }
                    items(listed, key = { it.keyHex }) { c ->
                        // Every kind opens its card, tap or hold (2026-11-10): a repeater
                        // went straight to sign-in, which is one of several things you
                        // might want from it. Manage, Room and Message are one tap on the card.
                        ContactRow(c = c, onClick = { detail = c }, onLongClick = { detail = c })
                    }
                    item(key = "fab_clearance") { Spacer(Modifier.height(88.dp)) }
                }
            }

            confirmDismiss?.let { keys ->
                AlertDialog(
                    onDismissRequest = { confirmDismiss = null },
                    icon = { Icon(painterResource(R.drawable.ic_delete_sweep), null) },
                    title = { Text(pluralStringResource(R.plurals.dismiss_all_title, keys.size, keys.size)) },
                    text = { Text(stringResource(R.string.dismiss_all_body)) },
                    confirmButton = {
                        Button(
                            onClick = {
                                keys.forEach { vm.dismissDiscovered(it) }
                                confirmDismiss = null
                                heardRevealed = false
                            },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) { Text(stringResource(R.string.dismiss_all)) }
                    },
                    dismissButton = {
                        TextButton(onClick = { confirmDismiss = null }) { Text(stringResource(R.string.cancel)) }
                    },
                )
            }
        }
    }

    if (showSelfQr) {
        SelfQrDialog(vm, onDismiss = { showSelfQr = false })
    }
    if (discovering) {
        DiscoverNodesDialog(
            vm,
            onDismiss = { discovering = false },
            onOpenContact = { discovering = false; detail = it },
            onOpenHeard = { discovering = false; heardDetail = it },
        )
    }
    if (staleOpen) {
        StaleNodesDialog(vm, onDismiss = { staleOpen = false })
    }

    heardDetail?.let { node ->
        HeardDetailSheet(
            vm = vm,
            node = node,
            signals = vm.heardSignals.collectAsState().value[node.keyHex],
            onDismiss = { heardDetail = null },
            onAdd = {
                heardDetail = null
                openWhenAdded = node.keyHex
                vm.addDiscovered(node.keyHex)
            },
            onForget = {
                heardDetail = null
                vm.dismissDiscovered(node.keyHex)
            },
        )
    }

    roomSignIn?.let { room ->
        RepeaterLoginDialog(
            vm = vm,
            keyHex = room.keyHex,
            nodeName = room.name.ifBlank { room.keyHex.take(12) },
            isRoom = true,
            onDismiss = {
                roomSignIn = null
                // Called on success and on cancel alike; only a session
                // the room actually granted goes on into it.
                if ((vm.adminSessions.value[room.keyHex] ?: AdminSession.None).signedIn) {
                    nav.navigate(conversationRoute(MessageRepository.KIND_DM, room.keyHex))
                }
            },
        )
    }

    detail?.let { contact ->
        ContactDetailSheet(
            vm = vm,
            contact = contact,
            onDismiss = { detail = null },
            onOpenChat = {
                detail = null
                if (contact.type == Codes.ADV_TYPE_ROOM) {
                    openRoom(contact)
                } else {
                    nav.navigate(conversationRoute(MessageRepository.KIND_DM, contact.keyHex))
                }
            },
            onOpenAdmin = {
                detail = null
                openAdmin(contact.keyHex)
            },
            onTrace = {
                detail = null
                nav.navigate(traceRoute(contact.keyHex))
            },
            onShowOnMap = {
                detail = null
                showOnMap(vm, nav, contact.keyHex)
            },
            // Straight to the transfer, using the address the node
            // announced: a node in update mode has left the mesh.
            onFlashFirmware = {
                detail = null
                val role = if (contact.type == Codes.ADV_TYPE_ROOM) "room" else "repeater"
                nav.navigate(
                    "firmware/node?role=$role&node=${contact.keyHex}" +
                        (contact.otaAddress?.let { "&mac=$it" } ?: "") +
                        (contact.boardName?.let { "&board=${encodePrefill(it)}" } ?: ""),
                )
            },
        )
    }
}

@DrawableRes
internal fun kindIcon(kind: NodeTab?): Int = when (kind) {
    null -> R.drawable.ic_apps
    NodeTab.Contacts -> R.drawable.ic_person
    NodeTab.Repeaters -> R.drawable.ic_cell_tower
    NodeTab.Rooms -> R.drawable.ic_forum
    NodeTab.Sensors -> R.drawable.ic_sensors
}

@StringRes
internal fun kindLabel(kind: NodeTab?): Int = when (kind) {
    null -> R.string.kind_all
    NodeTab.Contacts -> R.string.kind_contacts
    NodeTab.Repeaters -> R.string.kind_repeaters
    NodeTab.Rooms -> R.string.kind_rooms
    NodeTab.Sensors -> R.string.kind_sensors
}

@Composable
private fun filterLabel(f: NodeListModel.Filter): String = stringResource(
    when (f) {
        NodeListModel.Filter.Favorites -> R.string.filter_favorites
        NodeListModel.Filter.Unread -> R.string.filter_unread
        NodeListModel.Filter.ActiveDay -> R.string.filter_active_day
    },
)

@Composable
private fun sortLabel(s: NodeListModel.Sort): String = stringResource(
    when (s) {
        NodeListModel.Sort.Activity -> R.string.sort_activity
        NodeListModel.Sort.LastHeard -> R.string.sort_last_heard
        NodeListModel.Sort.Name -> R.string.sort_name
        NodeListModel.Sort.Hops -> R.string.sort_hops
    },
)

/**
 * All / Contacts / Repeaters / Rooms / Sensors, as filter chips with icons.
 * A chip whose kind has newcomers heard but not added carries their count.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KindChips(selected: NodeTab?, newCounts: Map<NodeTab, Int>, onSelect: (NodeTab?) -> Unit) {
    val kinds: List<NodeTab?> = listOf(null) + NodeTab.entries
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(kinds.size) { i ->
            val k = kinds[i]
            val count = if (k == null) newCounts.values.sum() else newCounts[k] ?: 0
            val label = stringResource(kindLabel(k))
            FilterChip(
                selected = selected == k,
                onClick = { onSelect(k) },
                label = { Text(label, maxLines = 1) },
                leadingIcon = {
                    Icon(painterResource(kindIcon(k)), null, Modifier.size(FilterChipDefaults.IconSize))
                },
                trailingIcon = if (count > 0) {
                    { Badge(containerColor = MaterialTheme.colorScheme.primary) { Text("$count") } }
                } else {
                    null
                },
            )
        }
    }
}

/**
 * Active filters and muted newcomers are hidden state that changes what
 * the app appears to contain, so each is named in a line under the chips.
 */
@Composable
private fun ActiveFilterNotes(
    filters: Set<NodeListModel.Filter>,
    hiddenNote: String?,
    onShowHeard: () -> Unit,
) {
    if (filters.isNotEmpty()) {
        val joined = filters.map { filterLabel(it).lowercase() }.joinToString(stringResource(R.string.nodes_and))
        Text(
            stringResource(R.string.nodes_showing, joined),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 4.dp),
        )
    }
    hiddenNote?.let { note ->
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                note,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onShowHeard) { Text(stringResource(R.string.show)) }
        }
    }
}

/**
 * Orders and narrows the list. A top-bar icon rather than the overflow,
 * which holds actions on the mesh: "what do I see" and "what do I do" are
 * kept apart.
 */
@Composable
private fun SortFilterMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    sort: NodeListModel.Sort,
    filters: Set<NodeListModel.Filter>,
    hideHeard: Boolean,
    onSortChange: (NodeListModel.Sort) -> Unit,
    onToggleFilter: (NodeListModel.Filter) -> Unit,
    onClearFilters: () -> Unit,
    onHideHeardChange: (Boolean) -> Unit,
) {
    MeshDropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        MenuHeading(stringResource(R.string.nodes_sort_by))
        for (option in NodeListModel.Sort.entries) {
            DropdownMenuItem(
                text = { Text(sortLabel(option)) },
                onClick = { onSortChange(option); onDismiss() },
                trailingIcon = { if (option == sort) SelectedTick() },
            )
        }
        HorizontalDivider()
        MenuHeading(stringResource(R.string.nodes_show_only))
        for (option in NodeListModel.Filter.entries) {
            DropdownMenuItem(
                text = { Text(filterLabel(option)) },
                // Filters stack, so the menu stays open.
                onClick = { onToggleFilter(option) },
                trailingIcon = { if (option in filters) SelectedTick() },
            )
        }
        HorizontalDivider()
        MenuHeading(stringResource(R.string.nodes_new_nodes))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.nodes_hide_not_added)) },
            onClick = { onHideHeardChange(!hideHeard) },
            trailingIcon = { if (hideHeard) SelectedTick() },
        )
        if (filters.isNotEmpty()) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nodes_clear_filters)) },
                onClick = { onClearFilters(); onDismiss() },
            )
        }
    }
}

@Composable
private fun MenuHeading(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun SelectedTick() {
    Icon(painterResource(R.drawable.ic_check), null, modifier = Modifier.size(18.dp))
}

@Composable
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun DiscoveredRow(
    node: io.alvaroedo.meshconnect.storage.DiscoveredEntity,
    signals: io.github.thatsfguy.meshcore.presentation.RepeaterSignals.Signals? = null,
    onOpen: () -> Unit,
    onAdd: () -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            // Tap and long-press both open the node's sheet: it can't be
            // administered until it is added.
            .combinedClickable(onClick = onOpen, onLongClick = onOpen)
            .padding(start = 16.dp, end = 4.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeAvatar(
            seed = node.keyHex,
            label = node.name.ifBlank { node.keyHex },
            type = node.type,
            typeIcon = true,
            size = LIST_AVATAR_SIZE,
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name.ifBlank { node.keyHex.take(12) },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Hops, not the latest copy's SNR: after a relay that is the
            // last repeater's signal, not the node's (HeardReach).
            Text(
                listOfNotNull(
                    stringResource(singleKindLabel(node.type)),
                    SharedText.heardReach(LocalContext.current, node.minHops, node.directSnr),
                    DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(node.lastHeardAt)),
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            signals?.let {
                (if (node.type == Codes.ADV_TYPE_REPEATER) it else it.copy(relayed = 0))
                    .copy(heardDirect = false)
            }?.let { SharedText.signals(LocalContext.current, it) }?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
        }
        IconButton(onClick = onAdd) {
            Icon(painterResource(R.drawable.ic_person_add), stringResource(R.string.add), tint = MaterialTheme.colorScheme.primary)
        }
        IconButton(onClick = onDismiss) {
            Icon(painterResource(R.drawable.ic_close), stringResource(R.string.dismiss))
        }
    }
}

@StringRes
internal fun singleKindLabel(type: Int): Int = when (type) {
    Codes.ADV_TYPE_REPEATER -> R.string.kind_repeater
    Codes.ADV_TYPE_ROOM -> R.string.kind_room
    Codes.ADV_TYPE_SENSOR -> R.string.kind_sensor
    else -> R.string.kind_contact
}

/**
 * The top of the revealed "heard, not added" section: how many, a way to
 * fold it away, and Dismiss all, which asks first (it is easy to hit). The
 * full-radio warning lives here because a full radio is why most of these
 * are here.
 */
@Composable
private fun HeardHeader(
    count: Int,
    expanded: Boolean,
    radioFull: Boolean,
    onToggle: () -> Unit,
    onDismissAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surfaceContainer, FieldShape)
                .clip(FieldShape)
                .clickable(onClick = onToggle)
                .padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(painterResource(R.drawable.ic_radar), null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(12.dp))
            Text(
                stringResource(R.string.heard_header, count),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            if (expanded) {
                TextButton(onClick = onDismissAll) { Text(stringResource(R.string.dismiss_all)) }
            }
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painterResource(if (expanded) R.drawable.ic_expand_less else R.drawable.ic_expand_more),
                    stringResource(if (expanded) R.string.heard_hide else R.string.show),
                )
            }
        }
        if (radioFull && expanded) {
            Text(
                stringResource(R.string.radio_full),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }
    }
}

internal fun typeLabel(type: Int): String = when (type) {
    Codes.ADV_TYPE_CHAT -> "Contacts"
    Codes.ADV_TYPE_REPEATER -> "Repeaters"
    Codes.ADV_TYPE_ROOM -> "Room servers"
    Codes.ADV_TYPE_SENSOR -> "Sensors"
    else -> "Other"
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    c: ContactEntity,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeAvatar(
            seed = c.keyHex,
            label = c.name.ifBlank { c.keyHex },
            type = c.type,
            typeIcon = true,
            size = LIST_AVATAR_SIZE,
        )
        Spacer(Modifier.size(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (c.flags and Codes.CONTACT_FLAG_FAVORITE != 0) "★ " else "") +
                    c.name.ifBlank { c.keyHex.take(12) },
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                c.keyHex.take(12) + " · " + PathCodec.decodePathLen(c.pathLen).let { info ->
                    if (info.isFlood) stringResource(R.string.flood) else stringResource(R.string.hops_path, info.hops)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
        if (LastHeard.seconds(c) > 0) {
            // "14 min ago", not "8/19/26". A date answers a question
            // nobody asks of a mesh node: what matters is whether it was
            // heard recently enough to be worth sending to, and a date
            // makes the reader do that subtraction. Today's adverts all
            // rendered as the same date, which is the case where the
            // column said nothing at all.
            //
            // From OUR radio's clock, not the node's own — a repeater
            // heard this morning read "20688 days ago" here because its
            // RTC is unset (LastHeard).
            Text(
                relativeAge(LastHeard.seconds(c)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

/**
 * How far a node is, for every sheet that shows one — null when it has no real position.
 * `isPlausiblePosition`, not a null check: a node a few microdegrees from Null Island is
 * non-null, and two sheets once disagreed about it (2026-09-07).
 */
@Composable
internal fun nodeDistance(
    lat: Double?,
    lon: Double?,
    selfLat: Double?,
    selfLon: Double?,
    units: io.github.thatsfguy.meshcore.presentation.UnitSystem,
): String? {
    if (!isPlausiblePosition(lat, lon)) return null
    return if (isPlausiblePosition(selfLat, selfLon)) {
        Units.distance(haversineMetres(selfLat!!, selfLon!!, lat!!, lon!!), units)
    } else {
        stringResource(R.string.node_unknown)
    }
}

/**
 * A node heard over the air but not in the radio's contacts: what is
 * known about it, laid out as a contact's sheet is, and Add.
 *
 * Messaging, administration, routes, favourites, permissions and rename
 * all act on a contact the RADIO holds (CMD_SEND_LOGIN, CMD_SEND_TXT_MSG
 * and the rest look it up by key), so they can't be offered here. The
 * sheet says so, and Add opens the full contact sheet once the radio has
 * the node — the one step between this and every other option.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HeardDetailSheet(
    vm: MeshCoreViewModel,
    node: io.alvaroedo.meshconnect.storage.DiscoveredEntity,
    signals: io.github.thatsfguy.meshcore.presentation.RepeaterSignals.Signals?,
    onDismiss: () -> Unit,
    onAdd: () -> Unit,
    onForget: () -> Unit,
) {
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val context = LocalContext.current
    val self = vm.selfInfo.collectAsState().value
    val units by vm.unitSystem.collectAsState()
    val stamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    // The contact card's layout (2026-11-10), with what a heard node has: no stored
    // path, no favourite, nothing the radio would have to look up by key.
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                NodeAvatar(
                    seed = node.keyHex,
                    label = node.name.ifBlank { node.keyHex },
                    type = node.type,
                    typeIcon = true,
                    size = 56.dp,
                )
                Spacer(Modifier.size(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        node.name.ifBlank { stringResource(R.string.node_unnamed) },
                        style = MaterialTheme.typography.headlineSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        stringResource(R.string.node_heard_not_added, SharedText.kind(context, node.type)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                QuickAction(R.drawable.ic_person_add, stringResource(R.string.node_qa_add), on = true, onClick = onAdd)
                QuickAction(R.drawable.ic_delete, stringResource(R.string.node_qa_forget), onClick = onForget)
            }

            Spacer(Modifier.height(12.dp))
            InfoTile(
                stringResource(R.string.add_contact_key),
                node.keyHex,
                Modifier.fillMaxWidth(),
                mono = true,
                trailing = {
                    IconButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(node.keyHex))
                    }) { Icon(painterResource(R.drawable.ic_content_copy), stringResource(R.string.copy)) }
                },
            )
            // Hops, and the node's own signal only when heard direct: after a relay
            // the latest SNR is the last repeater's (HeardReach). Local arrival times,
            // never the advert's own timestamp, which is the node's clock (LastHeard).
            val reach = SharedText.heardReach(context, node.minHops, node.directSnr)
            Spacer(Modifier.height(8.dp))
            TileRow {
                reach?.let {
                    InfoTile(stringResource(R.string.node_hops_away), it, Modifier.weight(1f).fillMaxHeight())
                }
                InfoTile(
                    stringResource(R.string.node_last_heard),
                    relativeAge(node.lastHeardAt / 1000),
                    Modifier.weight(1f).fillMaxHeight(),
                )
            }
            nodeDistance(node.latitude, node.longitude, self?.latitude, self?.longitude, units)?.let { distance ->
                Spacer(Modifier.height(8.dp))
                TileRow {
                    InfoTile(
                        stringResource(R.string.node_position),
                        "%.5f, %.5f".format(java.util.Locale.ROOT, node.latitude, node.longitude),
                        Modifier.weight(1f).fillMaxHeight(),
                        mono = true,
                    )
                    InfoTile(stringResource(R.string.node_distance), distance, Modifier.weight(1f).fillMaxHeight())
                }
            }
            if (node.firstHeardAt in 1 until node.lastHeardAt) {
                Spacer(Modifier.height(8.dp))
                InfoTile(stringResource(R.string.node_first_heard), stamp.format(Date(node.firstHeardAt)), Modifier.fillMaxWidth())
            }
            signals?.let {
                (if (node.type == Codes.ADV_TYPE_REPEATER) it else it.copy(relayed = 0))
                    .copy(heardDirect = false)
            }?.let { SharedText.signals(context, it) }?.let {
                Spacer(Modifier.height(8.dp))
                InfoTile(stringResource(R.string.node_why_add), it, Modifier.fillMaxWidth())
            }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    if (node.type == Codes.ADV_TYPE_CHAT) R.string.node_add_why_chat else R.string.node_add_why_admin,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactDetailSheet(
    vm: MeshCoreViewModel,
    contact: ContactEntity,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onOpenAdmin: () -> Unit,
    onFlashFirmware: () -> Unit = {},
    /** Ping (nodes that forward) or trace towards a person; upstream issue #5. */
    onTrace: (() -> Unit)? = null,
    /** Extra section under the facts — the map puts a repeater's neighbour links here. */
    extra: (@Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit)? = null,
    /** Tapping Position: centre the map on this node. */
    onShowOnMap: (() -> Unit)? = null,
) {
    // The row as the database has it NOW, not as it was when the sheet opened: a
    // favourite, a rename or a new path shows without closing and reopening.
    @Suppress("NAME_SHADOWING")
    val contact = vm.dbContacts.collectAsState().value.firstOrNull { it.keyHex == contact.keyHex } ?: contact
    var renameOpen by remember { mutableStateOf(false) }
    var routingOpen by remember { mutableStateOf(false) }
    var removeConfirm by remember { mutableStateOf(false) }
    var shareQrOpen by remember { mutableStateOf(false) }
    var updateModeOpen by remember { mutableStateOf(false) }
    var updateModeNote by remember { mutableStateOf<String?>(null) }
    var updateModeBusy by remember { mutableStateOf(false) }
    val units by vm.unitSystem.collectAsState()
    var permissionsOpen by remember { mutableStateOf(false) }
    var telemetryOpen by remember { mutableStateOf(false) }
    // Sensors run the same CLI (PARITY §7): login, settings, telemetry.
    val isAdminable = contact.type == Codes.ADV_TYPE_REPEATER ||
        contact.type == Codes.ADV_TYPE_ROOM ||
        contact.type == Codes.ADV_TYPE_SENSOR
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    val live = vm.liveContacts.collectAsState().value[contact.keyHex]
    val self = vm.selfInfo.collectAsState().value
    val hashWidth = vm.deviceInfo.collectAsState().value?.pathHashByteWidth

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            // ---- Who: the same avatar as the list and the map pin.
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
                        SharedText.kind(LocalContext.current, contact.type) +
                            // Our radio's clock, not the node's claim — see LastHeard.
                            (if (LastHeard.seconds(contact) > 0) " · " + relativeAge(LastHeard.seconds(contact)) else ""),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // ---- What you came to do, one tap each.
            // Flipped on tap: the radio's answer arrives a moment later, and the star
            // used to stay as it was until the sheet was opened again.
            val savedFav = contact.flags and Codes.CONTACT_FLAG_FAVORITE != 0
            var favPending by remember(contact.keyHex) { mutableStateOf<Boolean?>(null) }
            LaunchedEffect(savedFav) { favPending = null }
            val isFav = favPending ?: savedFav
            // One row of equal shares: wrapping left a lone button on a second line.
            // The kind's own action first, then trace, telemetry, share, favourite.
            Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
                // A room is where you read and post, so that comes first — but it is
                // also a node someone configures, so its owner keeps a way in too.
                if (contact.type == Codes.ADV_TYPE_CHAT) {
                    QuickAction(R.drawable.ic_chat, stringResource(R.string.card_message), onClick = onOpenChat)
                }
                if (contact.type == Codes.ADV_TYPE_ROOM) {
                    QuickAction(R.drawable.ic_forum, stringResource(R.string.node_qa_room), onClick = onOpenChat)
                }
                if (isAdminable) {
                    QuickAction(R.drawable.ic_tune, stringResource(R.string.node_qa_manage), onClick = onOpenAdmin)
                }
                onTrace?.let { trace ->
                    QuickAction(
                        R.drawable.ic_route,
                        stringResource(if (isAdminable) R.string.trace_ping else R.string.node_qa_trace),
                        onClick = trace,
                    )
                }
                QuickAction(R.drawable.ic_sensors, stringResource(R.string.node_qa_telemetry)) { telemetryOpen = true }
                QuickAction(R.drawable.ic_qr_code, stringResource(R.string.node_qa_share)) { shareQrOpen = true }
                QuickAction(
                    if (isFav) R.drawable.ic_star_filled else R.drawable.ic_star,
                    stringResource(R.string.node_qa_favourite),
                    on = isFav,
                ) {
                    favPending = !isFav
                    vm.setFavourite(contact.keyHex, !isFav)
                }
            }

            // ---- Facts, as tiles, two to a row: route (hops, hash width), then place
            // (position, distance) — the order a companion's own card uses.
            Spacer(Modifier.height(12.dp))
            InfoTile(
                stringResource(R.string.add_contact_key),
                contact.keyHex,
                Modifier.fillMaxWidth(),
                mono = true,
                trailing = {
                    IconButton(onClick = {
                        clipboard.setText(androidx.compose.ui.text.AnnotatedString(contact.keyHex))
                    }) { Icon(painterResource(R.drawable.ic_content_copy), stringResource(R.string.copy)) }
                },
            )
            val pathInfo = PathCodec.decodePathLen(live?.pathLen ?: contact.pathLen)
            // The contact's own record says how wide ITS hashes are; the device
            // setting only applies to new paths.
            val width = if (pathInfo.isFlood) hashWidth else pathInfo.hashWidth
            val distance = nodeDistance(contact.latitude, contact.longitude, self?.latitude, self?.longitude, units)
            Spacer(Modifier.height(8.dp))
            TileRow {
                InfoTile(
                    stringResource(R.string.node_hops_away),
                    SharedText.pathHops(LocalContext.current, pathInfo.isFlood, pathInfo.hops),
                    Modifier.weight(1f).fillMaxHeight(),
                )
                if (width != null) {
                    InfoTile(
                        stringResource(R.string.node_hash_size),
                        pluralStringResource(R.plurals.node_hash_bytes, width, width),
                        Modifier.weight(1f).fillMaxHeight(),
                    )
                }
            }
            if (distance != null) {
                Spacer(Modifier.height(8.dp))
                TileRow {
                    InfoTile(
                        stringResource(R.string.node_position),
                        "%.5f, %.5f".format(java.util.Locale.ROOT, contact.latitude, contact.longitude),
                        Modifier.weight(1f).fillMaxHeight(),
                        mono = true,
                        onClick = onShowOnMap,
                    )
                    InfoTile(stringResource(R.string.node_distance), distance, Modifier.weight(1f).fillMaxHeight())
                }
            }
            if (LastHeard.seconds(contact) > 0) {
                Spacer(Modifier.height(8.dp))
                InfoTile(
                    stringResource(R.string.node_last_heard),
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(LastHeard.seconds(contact) * 1000)),
                    Modifier.fillMaxWidth(),
                )
            }
            // The node's own claim, shown ONLY when it disagrees with what we
            // observed: a fact about that node's clock, which its owner would
            // otherwise chase as a bug in every client they try.
            if (LastHeard.claimDisagrees(contact)) {
                DetailRow(
                    stringResource(R.string.node_own_clock),
                    stringResource(
                        R.string.node_clock_wrong,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(contact.lastSeen * 1000)),
                    ),
                )
            }
            // Path, mirroring what the radio actually holds for this contact.
            live?.storedPath?.takeIf { it.isNotEmpty() }?.let { bytes ->
                val known = vm.liveContacts.collectAsState().value.mapValues { (_, c) -> c.name }
                val hops = PathCodec.resolveHops(bytes, pathInfo.hashWidth, known)
                SheetHeading(stringResource(R.string.node_out_path))
                for ((index, hop) in hops.withIndex()) {
                    Text(
                        "${index + 1}. ${hop.label}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = if (hop.isResolved) null else FontFamily.Monospace,
                        color = if (hop.isAmbiguous) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                    )
                    // A two-byte hash is 16 bits: more than one node can own it, so
                    // list the candidates rather than asserting one of them is the hop.
                    if (hop.isAmbiguous) {
                        Text(
                            "   " + hop.candidates.joinToString(" / "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            extra?.invoke(this)

            // ---- Everything else, as a list.
            SheetHeading(stringResource(R.string.node_more))
            SheetActionRow(R.drawable.ic_route, stringResource(R.string.conv_routing)) { routingOpen = true }
            SheetActionRow(R.drawable.ic_edit, stringResource(R.string.node_rename)) { renameOpen = true }
            SheetActionRow(R.drawable.ic_policy, stringResource(R.string.node_permissions_row)) {
                permissionsOpen = !permissionsOpen
            }
            if (permissionsOpen) ContactPermissions(vm, contact)
            // Recovery lives on the node, because this is where someone looks
            // for it — and everything else that reaches a repeater goes through
            // the mesh, which is what a node in update mode no longer has.
            // Offered whether or not an address was recorded: "no address" is
            // the case that most needs a way in. The address is the node's
            // Bluetooth address, learned in update mode.
            if (isAdminable) {
                SheetActionRow(
                    R.drawable.ic_update,
                    stringResource(R.string.node_recover_row),
                    detail = contact.otaAddress?.let { stringResource(R.string.node_bt_address) + " " + it },
                ) { updateModeOpen = true }
            }
            // Blocking is local and key-based. Offered only where there is
            // something to block (relayed traffic carries the original sender's
            // key, so on a repeater it could only swallow its console replies) —
            // but never hidden while a block is in force, or an older build's
            // block would have no way back.
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

    if (updateModeOpen) {
        val otaAddress = contact.otaAddress
        val res = androidx.compose.ui.platform.LocalResources.current
        AlertDialog(
            onDismissRequest = { if (!updateModeBusy) updateModeOpen = false },
            title = { Text(stringResource(R.string.recover_title, contact.name.ifBlank { contact.keyHex.take(8) })) },
            text = {
                Column {
                    Text(
                        (
                            if (otaAddress != null) {
                                stringResource(R.string.recover_known, otaAddress)
                            } else {
                                stringResource(R.string.recover_unknown)
                            }
                        ) + "\n\n" + stringResource(R.string.recover_body),
                    )
                    updateModeNote?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, style = MaterialTheme.typography.bodySmall)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !updateModeBusy,
                    onClick = {
                        updateModeBusy = true
                        updateModeNote = res.getString(R.string.recover_looking)
                        vm.exitUpdateMode(contact.keyHex) { result ->
                            updateModeNote = result
                            updateModeBusy = false
                        }
                    },
                ) { Text(stringResource(if (updateModeBusy) R.string.recover_working else R.string.recover_restart)) }
            },
            dismissButton = {
                Row {
                    TextButton(
                        enabled = !updateModeBusy,
                        onClick = {
                            updateModeOpen = false
                            onFlashFirmware()
                        },
                    ) { Text(stringResource(R.string.recover_flash)) }
                    TextButton(enabled = !updateModeBusy, onClick = { updateModeOpen = false }) {
                        Text(stringResource(R.string.close))
                    }
                }
            },
        )
    }

    if (shareQrOpen) {
        ContactQrDialog(vm, contact, onDismiss = { shareQrOpen = false })
    }
    if (telemetryOpen) {
        ContactTelemetryDialog(vm, contact, onDismiss = { telemetryOpen = false })
    }
    if (routingOpen) {
        RoutingSheet(vm, contact, onDismiss = { routingOpen = false })
    }

    if (renameOpen) {
        var name by remember { mutableStateOf(contact.name) }
        AlertDialog(
            onDismissRequest = { renameOpen = false },
            title = { Text(stringResource(R.string.node_rename_title)) },
            text = {
                FormField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.renameContact(contact, name.trim())
                    renameOpen = false
                    onDismiss()
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

/** One label/value line in the contact sheet. */
@Composable
private fun DetailRow(label: String, value: String, mono: Boolean = false) {
    Column(Modifier.padding(top = 8.dp)) {
        Text(label, style = MaterialTheme.typography.labelLarge)
        Text(
            value,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (mono) FontFamily.Monospace else null,
        )
    }
}

/** "9 hours ago" — wording shared with the heard-repeats list. */
/** Localized by the platform ("5 minutes ago", "hace 5 minutos"). */
internal fun relativeAge(epochSeconds: Long): String {
    val now = System.currentTimeMillis()
    return android.text.format.DateUtils.getRelativeTimeSpanString(
        epochSeconds * 1000, now, android.text.format.DateUtils.SECOND_IN_MILLIS,
    ).toString()
}

/**
 * Telemetry published by a node (Cayenne LPP over a binary request).
 *
 * The node decides whether to answer at all: telemetry is permissioned
 * on its side, so silence is a normal outcome, not an error — say that
 * rather than showing a failure.
 */
@Composable
private fun ContactTelemetryDialog(
    vm: MeshCoreViewModel,
    contact: ContactEntity,
    onDismiss: () -> Unit,
) {
    var readings by remember {
        mutableStateOf<List<io.github.thatsfguy.meshcore.protocol.TelemetryReading>?>(null)
    }
    var failure by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    var attempt by remember { mutableIntStateOf(0) }
    val units by vm.unitSystem.collectAsState()
    LaunchedEffect(contact.keyHex, attempt) {
        loading = true
        when (val r = vm.fetchTelemetry(contact.keyHex, contact.type)) {
            is TelemetryFetch.Readings -> { readings = r.readings; failure = null }
            else -> { readings = null; failure = r.message }
        }
        loading = false
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.telemetry_title), maxLines = 1) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    contact.name.ifBlank { contact.keyHex.take(12) },
                    style = MaterialTheme.typography.labelLarge,
                )
                Spacer(Modifier.height(8.dp))
                when {
                    loading -> Column {
                        SectionSpinner(stringResource(R.string.mapnode_asking))
                        RequestProgressHint(vm, contact.keyHex)
                    }
                    readings.isNullOrEmpty() -> Text(
                        failure ?: stringResource(R.string.telemetry_no_reply),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> TelemetryList(readings!!, units)
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
        dismissButton = if (!loading) {
            {
                TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.retry)) }
            }
        } else {
            null
        },
    )
}

/** Self-share QR dialog (used from Settings). */
@Composable
fun SelfQrDialog(vm: MeshCoreViewModel, onDismiss: () -> Unit) =
    ShareQrDialog(
        title = stringResource(R.string.qr_self_title),
        hint = stringResource(R.string.qr_hint),
        load = { vm.selfShareUri() },
        onDismiss = onDismiss,
    )

/** Share an existing contact so someone else can add the same node. */
@Composable
fun ContactQrDialog(vm: MeshCoreViewModel, contact: ContactEntity, onDismiss: () -> Unit) =
    ShareQrDialog(
        title = stringResource(R.string.ch_share_named, contact.name.ifBlank { contact.keyHex.take(8) }),
        hint = stringResource(R.string.qr_hint),
        load = { vm.contactShareUri(contact) },
        onDismiss = onDismiss,
    )

/**
 * Confirmation for a scanned contact card.
 *
 * A card is unsigned — the name is whatever the sender typed, and only
 * the public key identifies anyone — so the app shows the key in full
 * and makes the user accept it rather than adding the contact silently.
 */
@Composable
internal fun ContactCardConfirmDialog(
    card: io.github.thatsfguy.meshcore.protocol.ShareUri.Decoded.Contact,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_person_add), null) },
        title = { Text(stringResource(R.string.add_contact_title)) },
        text = {
            Column {
                Text(
                    card.name.ifBlank { stringResource(R.string.add_contact_unnamed) },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    stringResource(singleKindLabel(card.type)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.add_contact_key), style = MaterialTheme.typography.labelLarge)
                Text(
                    card.pubKeyHex,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    stringResource(R.string.add_contact_warning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        confirmButton = { Button(onClick = onConfirm) { Text(stringResource(R.string.card_add_contact)) } },
        dismissButton = { OutlinedButton(onClick = onDismiss) { Text(stringResource(R.string.back)) } },
    )
}

/**
 * QR for a `meshcore://contact/add?…` card — the form the mainstream
 * MeshCore app scans. Built from local state, so it works whether or not
 * the radio is answering.
 */
@Composable
private fun ShareQrDialog(
    title: String,
    hint: String,
    load: suspend () -> String?,
    onDismiss: () -> Unit,
) {
    var qr by remember { mutableStateOf<Bitmap?>(null) }
    var uri by remember { mutableStateOf<String?>(null) }
    var failed by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        val text = load()
        if (text != null) {
            uri = text
            qr = Qr.encode(text)
        } else {
            failed = true
        }
    }
    // One dialog that never changes shape: the spinner and the code share one
    // fixed box, the hint is there from the start, and Copy is present but off
    // until there is something to copy. When the hint and the button arrived
    // with the code, the window jumped to a new size and read as a second popup.
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                androidx.compose.animation.Crossfade(
                    targetState = qr to failed,
                    modifier = Modifier.size(280.dp),
                    label = "qr",
                ) { (bitmap, didFail) ->
                    Box(Modifier.size(280.dp), contentAlignment = Alignment.Center) {
                        when {
                            bitmap != null -> Image(
                                bitmap.asImageBitmap(),
                                contentDescription = stringResource(R.string.qr_contact),
                                modifier = Modifier.size(280.dp),
                            )
                            didFail -> Text(stringResource(R.string.qr_failed))
                            else -> androidx.compose.material3.CircularProgressIndicator()
                        }
                    }
                }
                Text(hint, style = MaterialTheme.typography.bodySmall)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_done)) } },
        dismissButton = { QrLinkActions(uri) },
    )
}

/**
 * Per-contact permissions (PARITY §2, `ContactPermissionsScreen`).
 *
 * These are the CONTACT_FLAG_TELE_* bits on the radio's contact record:
 * which parts of THIS node's telemetry that contact may read. They only
 * take effect when the matching global policy is set to "Flags" — a
 * per-contact grant cannot widen a global Deny, and a switch that
 * silently does nothing is worse than no switch, so the state is said
 * out loud.
 */
@Composable
private fun ContactPermissions(
    vm: io.alvaroedo.meshconnect.ui.MeshCoreViewModel,
    contact: io.alvaroedo.meshconnect.storage.ContactEntity,
) {
    val self by vm.selfInfo.collectAsState()
    val modes = self?.telemetryModes ?: 0
    val live by vm.liveContacts.collectAsState()
    val flags = live[contact.keyHex]?.flags ?: contact.flags

    HintText(stringResource(R.string.perm_intro))
    for ((label, flag, shift) in listOf(
        Triple(stringResource(R.string.perm_battery), Codes.CONTACT_FLAG_TELE_BASE, 0),
        Triple(stringResource(R.string.perm_location), Codes.CONTACT_FLAG_TELE_LOC, 2),
        Triple(stringResource(R.string.perm_environment), Codes.CONTACT_FLAG_TELE_ENV, 4),
    )) {
        val globalMode = (modes shr shift) and 0x03
        SettingRow(label, flags and flag != 0) {
            vm.setContactTelemetryFlag(contact.keyHex, flag, it)
        }
        // 0 = Deny everyone, 1 = per-contact flags, 2 = allow all.
        HintText(
            when (globalMode) {
                1 -> stringResource(R.string.perm_policy_flags)
                2 -> stringResource(R.string.perm_policy_all)
                else -> stringResource(R.string.perm_policy_deny)
            },
        )
    }

    var muted by remember(contact.keyHex) {
        mutableStateOf(vm.prefs.isContactMuted(contact.keyHex))
    }
    SettingRow(stringResource(R.string.conv_mute), muted) {
        muted = it
        vm.prefs.setContactMuted(contact.keyHex, it)
    }
}

/**
 * Active discovery (PARITY §2, `DiscoverScreen`/`DiscoverNodesScreen`).
 *
 * A broadcast asking nearby repeaters to answer, as opposed to the
 * passive advert inbox on each type's tab. Responders identify themselves
 * with a public-key PREFIX, which is not an identity (PARITY §12) — so
 * this only reports contacts already known, and says plainly that a
 * silent node is not necessarily an absent one.
 */
@Composable
private fun DiscoverNodesDialog(
    vm: MeshCoreViewModel,
    onDismiss: () -> Unit,
    onOpenContact: (ContactEntity) -> Unit,
    onOpenHeard: (io.alvaroedo.meshconnect.storage.DiscoveredEntity) -> Unit,
) {
    var found by remember { mutableStateOf<List<MeshCoreViewModel.DiscoveryHit>?>(null) }
    LaunchedEffect(Unit) { found = vm.discoverNodes() }
    val sf = vm.selfInfo.collectAsState().value?.sf

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(painterResource(R.drawable.ic_radar), null) },
        title = { Text(stringResource(R.string.discover_title)) },
        text = {
            val list = found
            Column(Modifier.verticalScroll(rememberScrollState())) {
                when {
                    list == null -> SectionSpinner(stringResource(R.string.discover_listening))
                    list.isEmpty() -> Text(
                        stringResource(R.string.discover_none),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    else -> {
                        Text(
                            stringResource(R.string.discover_found),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (hit in list) {
                                DiscoveryCard(
                                    hit,
                                    sf,
                                    onClick = when {
                                        hit.contact != null -> { { onOpenContact(hit.contact) } }
                                        hit.heard != null -> { { onOpenHeard(hit.heard) } }
                                        else -> null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}

/** One answer: who it is (or its prefix), the link both ways, and its card on tap. */
@Composable
private fun DiscoveryCard(hit: MeshCoreViewModel.DiscoveryHit, sf: Int?, onClick: (() -> Unit)?) {
    val context = LocalContext.current
    val key = hit.contact?.keyHex ?: hit.heard?.keyHex ?: hit.reply.prefixHex
    val name = hit.contact?.name ?: hit.heard?.name
    val fmt = { v: Double -> String.format(java.util.Locale.ROOT, "%.1f", v) }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            NodeAvatar(seed = key, label = name?.ifBlank { null } ?: key, type = Codes.ADV_TYPE_REPEATER, size = 36.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    name?.ifBlank { null } ?: hit.reply.prefixHex.take(8).uppercase(),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        hit.contact != null -> SharedText.kind(context, hit.contact.type)
                        hit.heard != null -> stringResource(R.string.node_heard_not_added, SharedText.kind(context, hit.heard.type))
                        else -> stringResource(R.string.discover_unknown, hit.reply.prefixHex.take(8).uppercase())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    stringResource(R.string.discover_signal, fmt(hit.reply.snr), fmt(hit.reply.theirSnr)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(8.dp))
            SignalBars(LinkMeter.bars(minOf(hit.reply.snr, hit.reply.theirSnr), sf), live = true)
        }
    }
}

/** Tiles side by side at the height of the tallest, so a value that wraps (a long word in
 *  Valencian) does not leave its neighbour short. */
@Composable
internal fun TileRow(content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(androidx.compose.foundation.layout.IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        content = content,
    )
}

/** Open the Map tab centred on [keyHex], the way the bottom bar opens it. */
internal fun showOnMap(vm: MeshCoreViewModel, nav: NavController, keyHex: String) {
    vm.mapFocus.value = keyHex
    if (nav.currentDestination?.route == "map") return
    nav.navigate("map") {
        popUpTo(nav.graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
