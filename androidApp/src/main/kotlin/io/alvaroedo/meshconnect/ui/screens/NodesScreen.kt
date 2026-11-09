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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.model.NodeKinds
import io.alvaroedo.meshconnect.ui.model.SharedText
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.navigation.NavController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import io.alvaroedo.meshconnect.platform.PortraitCaptureActivity
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

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let { vm.importScannedCode(it) }
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
            FloatingActionButton(onClick = { scanLauncher.launch(meshScanOptions(scanPrompt)) }) {
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

            // Revealed by pulling the list down; kept across navigation so
            // opening a node and coming back doesn't fold it away again.
            var heardRevealed by rememberSaveable { mutableStateOf(false) }
            var confirmDismiss by remember { mutableStateOf<List<String>?>(null) }
            val radioFull by vm.contactsFull.collectAsState()
            val pullState = rememberPullToRefreshState()

            PullToRefreshBox(
                isRefreshing = false,
                onRefresh = { if (heard.isNotEmpty()) heardRevealed = true },
                state = pullState,
                modifier = Modifier.fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (heard.isNotEmpty() && heardRevealed) {
                        item(key = "heard_header") {
                            HeardHeader(
                                count = heard.size,
                                radioFull = radioFull,
                                onHide = { heardRevealed = false },
                                onDismissAll = { confirmDismiss = heard.map { it.keyHex } },
                            )
                        }
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
                    } else if (heard.isNotEmpty()) {
                        item(key = "heard_hint") {
                            Text(
                                stringResource(R.string.heard_pull_hint),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                            )
                        }
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
                        // Infrastructure rows go straight to administration;
                        // long-press opens the sheet (routing, rename, QR).
                        // A room opens its conversation, signing in first.
                        val adminable = c.type == Codes.ADV_TYPE_REPEATER ||
                            c.type == Codes.ADV_TYPE_SENSOR
                        ContactRow(
                            c = c,
                            onClick = {
                                when {
                                    c.type == Codes.ADV_TYPE_ROOM -> openRoom(c)
                                    adminable -> openAdmin(c.keyHex)
                                    else -> detail = c
                                }
                            },
                            onLongClick = { detail = c },
                        )
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
                        Button(onClick = {
                            keys.forEach { vm.dismissDiscovered(it) }
                            confirmDismiss = null
                            heardRevealed = false
                        }) { Text(stringResource(R.string.dismiss_all)) }
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
        DiscoverNodesDialog(vm, nav, onDismiss = { discovering = false })
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
    DropdownMenu(expanded = open, onDismissRequest = onDismiss) {
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
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeAvatar(
            seed = node.keyHex,
            label = node.name.ifBlank { node.keyHex },
            type = node.type,
            typeIcon = true,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                node.name.ifBlank { node.keyHex.take(12) },
                style = MaterialTheme.typography.bodyLarge,
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
                style = MaterialTheme.typography.bodySmall,
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
    radioFull: Boolean,
    onHide: () -> Unit,
    onDismissAll: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                stringResource(R.string.heard_header, count),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = onDismissAll) { Text(stringResource(R.string.dismiss_all)) }
            IconButton(onClick = onHide) {
                Icon(painterResource(R.drawable.ic_expand_less), stringResource(R.string.heard_hide))
            }
        }
        if (radioFull) {
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
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NodeAvatar(
            seed = c.keyHex,
            label = c.name.ifBlank { c.keyHex },
            type = c.type,
            typeIcon = true,
        )
        Spacer(Modifier.size(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                (if (c.flags and Codes.CONTACT_FLAG_FAVORITE != 0) "★ " else "") +
                    c.name.ifBlank { c.keyHex.take(12) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                c.keyHex.take(12) + " · " + PathCodec.decodePathLen(c.pathLen).let { info ->
                    if (info.isFlood) stringResource(R.string.flood) else stringResource(R.string.hops_path, info.hops)
                },
                style = MaterialTheme.typography.bodySmall,
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
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

/**
 * Position and distance, for either node sheet. One copy on purpose: the
 * two rows once used different rules and contradicted each other in the
 * same sheet (a node a few microdegrees from Null Island printed
 * "0.00000, 0.00000" over a Distance of "Unknown"; reported 2026-09-07).
 */
@Composable
private fun PositionRows(
    lat: Double?,
    lon: Double?,
    selfLat: Double?,
    selfLon: Double?,
    units: io.github.thatsfguy.meshcore.presentation.UnitSystem,
) {
    if (!isPlausiblePosition(lat, lon)) return
    Spacer(Modifier.height(8.dp))
    DetailRow(stringResource(R.string.node_position), "%.5f, %.5f".format(java.util.Locale.ROOT, lat, lon))
    val distance = if (isPlausiblePosition(selfLat, selfLon)) {
        Units.distance(haversineMetres(selfLat!!, selfLon!!, lat!!, lon!!), units)
    } else {
        stringResource(R.string.node_unknown)
    }
    DetailRow(stringResource(R.string.node_distance), distance)
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
    val self = vm.selfInfo.collectAsState().value
    val units by vm.unitSystem.collectAsState()
    val stamp = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(node.name.ifBlank { stringResource(R.string.node_unnamed) }, style = MaterialTheme.typography.headlineSmall)
            Text(
                stringResource(R.string.node_heard_not_added, SharedText.kind(LocalContext.current, node.type)),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.add_contact_key), style = MaterialTheme.typography.labelLarge)
                    Text(node.keyHex, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(node.keyHex))
                }) { Text(stringResource(R.string.copy)) }
            }

            PositionRows(node.latitude, node.longitude, self?.latitude, self?.longitude, units)

            // Local arrival times — never the advert's own timestamp,
            // which is the node's clock (see LastHeard).
            DetailRow(
                stringResource(R.string.node_last_heard),
                relativeAge(node.lastHeardAt / 1000) + " · " + stamp.format(Date(node.lastHeardAt)),
            )
            if (node.firstHeardAt in 1 until node.lastHeardAt) {
                DetailRow(stringResource(R.string.node_first_heard), stamp.format(Date(node.firstHeardAt)))
            }
            // Hops, and the node's own signal only when heard direct: after
            // a relay the latest SNR is the last repeater's (HeardReach).
            SharedText.heardReach(LocalContext.current, node.minHops, node.directSnr)
                ?.let { DetailRow(stringResource(R.string.node_hops_away), it) }
            signals?.let {
                (if (node.type == Codes.ADV_TYPE_REPEATER) it else it.copy(relayed = 0))
                    .copy(heardDirect = false)
            }?.let { SharedText.signals(LocalContext.current, it) }
                ?.let { DetailRow(stringResource(R.string.node_why_add), it) }

            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(
                    if (node.type == Codes.ADV_TYPE_CHAT) R.string.node_add_why_chat else R.string.node_add_why_admin,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onAdd) { Text(stringResource(R.string.node_add_to_contacts)) }
            TextButton(onClick = onForget) {
                Text(stringResource(R.string.node_forget), color = MaterialTheme.colorScheme.error)
            }
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
) {
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
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                contact.name.ifBlank { stringResource(R.string.node_unnamed) },
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                SharedText.kind(LocalContext.current, contact.type),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.add_contact_key), style = MaterialTheme.typography.labelLarge)
                    Text(
                        contact.keyHex,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(contact.keyHex))
                }) { Text(stringResource(R.string.copy)) }
            }

            // `isPlausiblePosition`, not a null check. These two rows
            // used different rules and contradicted each other in the
            // same sheet: a node a few microdegrees from Null Island is
            // non-null, so Position printed "0.00000, 0.00000" while
            // Distance directly underneath it said "Unknown". Reported
            // from the Public channel, 2026-09-07.
            PositionRows(contact.latitude, contact.longitude, self?.latitude, self?.longitude, units)

            if (LastHeard.seconds(contact) > 0) {
                DetailRow(
                    stringResource(R.string.node_last_heard),
                    relativeAge(LastHeard.seconds(contact)) + " · " +
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                            .format(Date(LastHeard.seconds(contact) * 1000)),
                )
            }
            // The node's own claim, shown ONLY when it disagrees with
            // what we observed. It is not a second opinion about when we
            // heard it — it is a fact about that node's clock, and the
            // thing its owner would otherwise be chasing as a bug in
            // every client they try.
            if (LastHeard.claimDisagrees(contact)) {
                DetailRow(
                    stringResource(R.string.node_own_clock),
                    stringResource(
                        R.string.node_clock_wrong,
                        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(contact.lastSeen * 1000)),
                    ),
                )
            }

            // Path, mirroring what the radio actually holds for this
            // contact rather than what we last asked for.
            val pathInfo = PathCodec.decodePathLen(live?.pathLen ?: contact.pathLen)
            DetailRow(
                stringResource(R.string.node_hops_away),
                SharedText.pathHops(LocalContext.current, pathInfo.isFlood, pathInfo.hops),
            )
            live?.storedPath?.takeIf { it.isNotEmpty() }?.let { bytes ->
                val known = vm.liveContacts.collectAsState().value
                    .mapValues { (_, c) -> c.name }
                val hops = PathCodec.resolveHops(bytes, pathInfo.hashWidth, known)
                Column(Modifier.padding(top = 8.dp)) {
                    Text(stringResource(R.string.node_out_path), style = MaterialTheme.typography.labelLarge)
                    for ((index, hop) in hops.withIndex()) {
                        Text(
                            "${index + 1}. ${hop.label}",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = if (hop.isResolved) null else FontFamily.Monospace,
                            color = if (hop.isAmbiguous) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                        )
                        // A two-byte hash is 16 bits: more than one node
                        // can own it, so list the candidates rather than
                        // asserting one of them is the hop.
                        if (hop.isAmbiguous) {
                            Text(
                                "   " + hop.candidates.joinToString(" / "),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            // The contact's own record says how wide ITS hashes are; the
            // device setting only applies to new paths.
            val width = if (pathInfo.isFlood) hashWidth else pathInfo.hashWidth
            width?.let { DetailRow(stringResource(R.string.node_hash_size), pluralStringResource(R.plurals.node_hash_bytes, it, it)) }

            Spacer(Modifier.height(16.dp))

            if (contact.type == Codes.ADV_TYPE_CHAT) {
                TextButton(onClick = onOpenChat) { Text(stringResource(R.string.node_open_conversation)) }
            }
            // A room is where you read and post, so that comes first —
            // but it is also a node someone configures (and may run as
            // a repeater too), so its owner keeps a direct way in.
            if (contact.type == Codes.ADV_TYPE_ROOM) {
                TextButton(onClick = onOpenChat) { Text(stringResource(R.string.node_open_room)) }
            }
            if (isAdminable) {
                TextButton(onClick = onOpenAdmin) { Text(stringResource(R.string.node_administer)) }
            }
            // Recovery lives here, on the node, because this is where
            // someone looks for it — and because everything else that
            // reaches a repeater goes through the mesh, which is the one
            // thing a node in update mode no longer has.
            //
            // Offered whether or not an address was recorded: a node can
            // enter update mode from a session this app never saw, and
            // "no address stored" is the case that most needs a way in,
            // not the case to withhold one from.
            if (isAdminable) {
                // Labelled for what it is. "Update-mode address" read as
                // a statement about the node's current state; it is the
                // node's Bluetooth address, learned in update mode and
                // true whatever the node is doing now.
                contact.otaAddress?.let { DetailRow(stringResource(R.string.node_bt_address), it) }
                TextButton(onClick = { updateModeOpen = true }) {
                    Text(stringResource(R.string.node_recover))
                }
            }
            val isFav = contact.flags and Codes.CONTACT_FLAG_FAVORITE != 0
            TextButton(onClick = {
                vm.setFavourite(contact.keyHex, !isFav)
                onDismiss()
            }) { Text(stringResource(if (isFav) R.string.node_unfavourite else R.string.node_favourite)) }
            TextButton(onClick = { permissionsOpen = !permissionsOpen }) {
                Text(stringResource(if (permissionsOpen) R.string.node_hide_permissions else R.string.node_permissions))
            }
            if (permissionsOpen) {
                ContactPermissions(vm, contact)
            }
            TextButton(onClick = { shareQrOpen = true }) { Text(stringResource(R.string.node_share_qr)) }
            onTrace?.let { trace ->
                TextButton(onClick = trace) {
                    Text(stringResource(if (isAdminable) R.string.trace_ping else R.string.trace_route))
                }
            }
            TextButton(onClick = { telemetryOpen = true }) { Text(stringResource(R.string.node_telemetry)) }
            TextButton(onClick = { routingOpen = true }) { Text(stringResource(R.string.conv_routing)) }
            TextButton(onClick = { renameOpen = true }) { Text(stringResource(R.string.node_rename)) }
            // Blocking is local and key-based: it drops their direct
            // messages before they are written down. Removing the
            // contact does NOT block them — the radio would happily
            // re-add them on the next advert.
            //
            // Offered only where there is something to block. It used to
            // appear on every node, including repeaters, where it could
            // not do the thing its name promised (relayed traffic
            // carries the original sender's key) and did do one thing
            // nobody wanted: swallow the node's console replies.
            // ...but never hidden while a block is in force, or a
            // repeater blocked by an older build would have no way back.
            // Hiding the only "Unblock" is how a setting becomes
            // permanent by accident.
            val blocked = vm.isBlocked(contact.keyHex)
            if (BlockList.isBlockableNodeType(contact.type) || blocked) {
                TextButton(onClick = { vm.setBlocked(contact.keyHex, !blocked) }) {
                    Text(
                        stringResource(if (blocked) R.string.node_unblock else R.string.node_block),
                        color = if (blocked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
            TextButton(onClick = { removeConfirm = true }) {
                Text(stringResource(R.string.node_remove), color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (updateModeOpen) {
        val otaAddress = contact.otaAddress
        val ctx = LocalContext.current
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
                        updateModeNote = ctx.getString(R.string.recover_looking)
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
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
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
            Column {
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
                    else -> for (r in readings!!) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                r.label,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                // Converted here and only here: the reading
                                // keeps the unit the sensor encoded.
                                if (BatteryLevel.isSelfBattery(r)) {
                                    BatteryLevel.label(BatteryLevel.millivolts(r))
                                } else {
                                    Units.reading(r.value, r.unit, units).let { (v, u) ->
                                        formatReading(v) + (if (u.isBlank()) "" else " $u")
                                    }
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                    }
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

/** Trim trailing zeros: "23.0" reads better than "23.000000". */
private fun formatReading(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else "%.2f".format(value)

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
    val clipboard = androidx.compose.ui.platform.LocalClipboardManager.current
    LaunchedEffect(Unit) {
        val text = load()
        if (text != null) {
            uri = text
            qr = Qr.encode(text)
        } else {
            failed = true
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                qr?.let {
                    Image(
                        it.asImageBitmap(),
                        contentDescription = stringResource(R.string.qr_contact),
                        modifier = Modifier.size(280.dp),
                    )
                    Text(hint, style = MaterialTheme.typography.bodySmall)
                }
                if (failed) Text(stringResource(R.string.qr_failed))
                if (qr == null && !failed) LoadingBox(280.dp)
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.setup_done)) } },
        dismissButton = {
            uri?.let { text ->
                TextButton(onClick = {
                    clipboard.setText(androidx.compose.ui.text.AnnotatedString(text))
                }) { Text(stringResource(R.string.ch_copy_link)) }
            }
        },
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
    nav: NavController,
    onDismiss: () -> Unit,
) {
    var found by remember { mutableStateOf<List<ContactEntity>?>(null) }
    LaunchedEffect(Unit) { found = vm.discoverNodes() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.discover_title)) },
        text = {
            val list = found
            Column {
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
                        Spacer(Modifier.height(8.dp))
                        for (contact in list) {
                            TextButton(onClick = {
                                onDismiss()
                                nav.navigate("repeater/${contact.keyHex}")
                            }) {
                                Text(contact.name.ifBlank { contact.keyHex.take(12) })
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.close)) } },
    )
}
