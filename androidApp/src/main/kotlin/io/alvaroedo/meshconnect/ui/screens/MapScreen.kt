package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.storage.MessageRepository
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.res.painterResource
import io.github.thatsfguy.meshcore.protocol.Codes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.platform.NodeMarkers
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.presentation.LinkQuality
import io.github.thatsfguy.meshcore.presentation.NeighbourEndpoint
import io.github.thatsfguy.meshcore.util.isPlausiblePosition
import io.github.thatsfguy.meshcore.presentation.NeighbourLink
import io.github.thatsfguy.meshcore.presentation.neighbourLinks
import androidx.compose.runtime.LaunchedEffect
import io.alvaroedo.meshconnect.ui.map.CameraRequest
import io.alvaroedo.meshconnect.ui.map.MapLine
import io.alvaroedo.meshconnect.ui.map.MapMarker
import io.alvaroedo.meshconnect.ui.map.MapStyles
import io.alvaroedo.meshconnect.ui.map.MeshMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

/**
 * In-app node map (MapLibre + OpenFreeMap vector tiles, MeshMap) — tiles
 * are its only network use, cached by MapLibre, and can be turned off.
 *
 * Markers are Canvas-drawn pins ([NodeMarkers]): color + glyph per node
 * type (companion/repeater/room/sensor, mirroring the reference
 * client's icon scheme) with always-visible name labels — no tap
 * needed. The filter icon chooses what is shown (kinds, labels, links);
 * the ⋮ menu holds actions (re-fit, export, tiles, cache).
 *
 * Tapping a pin opens [MapNodeSheet]; for a repeater that is where its
 * neighbour links are turned on, drawn from the pin to every neighbour
 * this app can place, coloured by the signal the repeater reported.
 */
@Composable
fun MapScreen(vm: MeshCoreViewModel, nav: NavController) {
    val openAdmin = rememberAdminOpener(vm, nav)
    val context = LocalContext.current
    val res = androidx.compose.ui.platform.LocalResources.current
    val contacts by vm.dbContacts.collectAsState()
    val self by vm.selfInfo.collectAsState()
    val neighbours by vm.neighbourRecords.collectAsState()

    var showLabels by remember { mutableStateOf(true) }
    var fitRequest by remember { mutableIntStateOf(0) }
    var typeFilter by remember { mutableStateOf(vm.prefs.mapTypeFilter) }
    var tilesEnabled by remember { mutableStateOf(vm.prefs.mapTilesEnabled) }
    var filterMenu by remember { mutableStateOf(false) }

    // The tapped pin, and separately the repeater whose links are drawn.
    // They are not the same state: the whole point of the links is to
    // look at them, which means the sheet that switched them on has to
    // be able to close without taking them with it.
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var linksFor by remember { mutableStateOf<String?>(null) }

    val located = contacts
        .filter { isPlausiblePosition(it.latitude, it.longitude) }
        .filter { typeFilter.isEmpty() || it.type in typeFilter }

    // Links are resolved against EVERY contact, not the filtered pins:
    // a neighbour hidden by the type filter is still a node we know, and
    // resolving against the visible subset would report it as unknown.
    val endpoints = contacts.map {
        NeighbourEndpoint(it.keyHex, it.name, it.latitude, it.longitude)
    }
    fun linksOf(key: String): List<NeighbourLink> = neighbourLinks(
        records = neighbours.filter { it.repeaterKeyHex == key },
        nodes = endpoints,
        nowMillis = System.currentTimeMillis(),
    )

    // Two different questions. The popup lists what the repeater heard
    // whether or not it is drawn — a neighbour with no position is still
    // a neighbour, and it can only ever be read there. `linksFor` is the
    // narrower one: which repeater's lines are on the map right now.
    val drawn: List<NeighbourLink> = linksFor?.let { linksOf(it) } ?: emptyList()

    fun toggleType(type: Int) {
        typeFilter = if (type in typeFilter) typeFilter - type else typeFilter + type
        vm.prefs.mapTypeFilter = typeFilter
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(R.string.nav_map),
                vm = vm,
                subtitle = pluralStringResource(R.plurals.map_subtitle, located.size, located.size),
                // What the map shows lives behind its own icon, as on Nodes; the ⋮ holds
                // things to DO (2026-11-13). Mixed together, "Show the whole network" sat
                // under "Only contacts" and read as the way to undo it — it only zooms out.
                actions = {
                    Box {
                        IconButton(onClick = { filterMenu = true }) {
                            Icon(
                                painterResource(R.drawable.ic_filter_list),
                                stringResource(R.string.map_filter),
                                // Tinted while something is hidden, as Nodes' sort icon is.
                                tint = if (typeFilter.isEmpty()) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                            )
                        }
                        MapFilterMenu(
                            open = filterMenu,
                            onDismiss = { filterMenu = false },
                            typeFilter = typeFilter,
                            onToggleType = ::toggleType,
                            onClearTypes = { typeFilter = emptySet(); vm.prefs.mapTypeFilter = typeFilter },
                            showLabels = showLabels,
                            onToggleLabels = { showLabels = !showLabels },
                            linksShown = linksFor != null,
                            onHideLinks = { linksFor = null },
                        )
                    }
                },
                menuActions = listOf(
                    MenuAction(stringResource(R.string.map_fit_all), icon = R.drawable.ic_zoom_out_map) { fitRequest++ },
                    MenuAction(stringResource(R.string.map_export_gpx), icon = R.drawable.ic_download) { vm.exportGpx(located.size) },
                    MenuAction(stringResource(R.string.map_load_tiles), icon = R.drawable.ic_layers, checked = tilesEnabled) {
                        tilesEnabled = !tilesEnabled
                        vm.prefs.mapTilesEnabled = tilesEnabled
                    },
                    MenuAction(stringResource(R.string.map_clear_cache), icon = R.drawable.ic_delete) {
                        MapStyles.clearCache(context) { vm.transientMessage.value = res.getString(R.string.map_cache_cleared) }
                    },
                ),
            )
        },
    ) { padding ->
        if (located.isEmpty()) {
            val anyPlaced = contacts.any { isPlausiblePosition(it.latitude, it.longitude) }
            EmptyHint(
                modifier = Modifier.padding(padding),
                // A filter that hides every pin must not read as "nobody has a position".
                text = stringResource(if (anyPlaced && typeFilter.isNotEmpty()) R.string.map_empty_filtered else R.string.map_empty),
            )
            return@Scaffold
        }

        // Restore the last camera so returning to the tab (or relaunching)
        // lands where the user left off instead of re-fitting the world.
        val savedCamera = remember { vm.prefs.mapCamera }
        val selfPos = self?.let { (it.latitude to it.longitude).takeIf { p -> isPlausiblePosition(p.first, p.second) } }
        val allPoints = listOfNotNull(selfPos) + located.map { it.latitude!! to it.longitude!! }
        // First visit with no saved camera: fit everything once. After that
        // only the fit button (or the menu) moves the view.
        var cameraRequest by remember {
            mutableStateOf(if (savedCamera == null) CameraRequest(1, fit = allPoints, animate = false) else null)
        }
        LaunchedEffect(fitRequest) {
            if (fitRequest > 0) cameraRequest = CameraRequest(fitRequest + 1, fit = allPoints)
        }
        // Position on a node's card: centre on it, close in.
        val focus by vm.mapFocus.collectAsState()
        LaunchedEffect(focus, contacts) {
            val key = focus ?: return@LaunchedEffect
            val c = contacts.firstOrNull { it.keyHex == key } ?: return@LaunchedEffect
            if (isPlausiblePosition(c.latitude, c.longitude)) {
                cameraRequest = CameraRequest(
                    (cameraRequest?.token ?: 0) + 1000,
                    centre = c.latitude!! to c.longitude!!,
                    zoom = 15.0,
                )
            }
            vm.mapFocus.value = null
        }

        val markers = buildList {
            // Links' reading chips are centred markers; MeshMap draws them
            // above the pins so a neighbour's label never covers a reading.
            val origin = linksFor?.let { key -> contacts.firstOrNull { it.keyHex == key } }
            if (origin?.latitude != null && origin.longitude != null) {
                for (link in drawn) {
                    val end = link.endpoint ?: continue
                    val midLat = (origin.latitude + end.latitude!!) / 2
                    val midLon = (origin.longitude + end.longitude!!) / 2
                    add(
                        MapMarker(
                            id = "chip|${end.keyHex}", lat = midLat, lon = midLon,
                            imageKey = "chip|${link.mapLabel}|${link.quality.argb}",
                            bitmap = { NodeMarkers.buildChip(context, link.mapLabel, link.quality.argb).bitmap },
                            centred = true, tappable = false,
                        ),
                    )
                }
            }
            selfPos?.let { (lat, lon) ->
                val label = if (showLabels) self?.name ?: "" else null
                add(
                    MapMarker(
                        id = "self", lat = lat, lon = lon, imageKey = "pin|self|$label",
                        bitmap = { NodeMarkers.build(context, 0, label, isSelf = true).drawable.bitmap },
                        anchorV = NodeMarkers.anchorV(context, label),
                        tappable = false,
                    ),
                )
            }
            for (c in located) {
                val label = if (showLabels) c.displayName() else null
                add(
                    MapMarker(
                        id = c.keyHex, lat = c.latitude!!, lon = c.longitude!!,
                        imageKey = "pin|${c.type}|$label",
                        bitmap = { NodeMarkers.build(context, c.type, label).drawable.bitmap },
                        anchorV = NodeMarkers.anchorV(context, label),
                    ),
                )
            }
        }
        val origin = linksFor?.let { key -> contacts.firstOrNull { it.keyHex == key } }
        val lines = if (origin?.latitude != null && origin.longitude != null) {
            drawn.mapNotNull { link ->
                val end = link.endpoint ?: return@mapNotNull null
                MapLine(
                    points = listOf(origin.latitude to origin.longitude, end.latitude!! to end.longitude!!),
                    argb = link.quality.argb,
                    // Width carries the band a second time: colour alone is
                    // not a reading on a colour-blind screen.
                    widthDp = when (link.quality) {
                        LinkQuality.Strong -> 4.5f
                        LinkQuality.Good -> 3.75f
                        LinkQuality.Fair -> 3f
                        LinkQuality.Weak -> 2.25f
                        LinkQuality.Marginal -> 1.5f
                    },
                )
            }
        } else {
            emptyList()
        }

        MeshMap(
            modifier = Modifier.fillMaxSize().padding(padding),
            markers = markers,
            lines = lines,
            tilesEnabled = tilesEnabled,
            initial = savedCamera?.let {
                CameraPosition.Builder().target(LatLng(it.first, it.second)).zoom(it.third).build()
            },
            camera = cameraRequest,
            controls = true,
            selfPosition = selfPos,
            fitAll = allPoints,
            onMarkerTap = { selectedKey = it },
            onCameraIdle = { cam ->
                cam.target?.let { vm.prefs.mapCamera = Triple(it.latitude, it.longitude, cam.zoom) }
            },
        )
    }

    selectedKey?.let { key ->
        val contact = contacts.firstOrNull { it.keyHex == key }
        if (contact == null) {
            selectedKey = null
        } else {
            // The same sheet the Nodes list opens, with the repeater's
            // neighbour links — the map's own business — added under the facts.
            ContactDetailSheet(
                vm = vm,
                contact = contact,
                onDismiss = { selectedKey = null },
                onOpenChat = {
                    selectedKey = null
                    nav.navigate(conversationRoute(MessageRepository.KIND_DM, key))
                },
                onOpenAdmin = {
                    selectedKey = null
                    openAdmin(key)
                },
                onTrace = {
                    selectedKey = null
                    nav.navigate(traceRoute(key))
                },
                onShowOnMap = {
                    selectedKey = null
                    showOnMap(vm, nav, key)
                },
                extra = {
                    NeighbourSection(
                        vm = vm,
                        contact = contact,
                        links = linksOf(key),
                        linksShown = linksFor == key,
                        onToggleLinks = { linksFor = if (linksFor == key) null else key },
                    )
                },
            )
        }
    }
}

private fun ContactEntity.displayName(): String = name.ifBlank { keyHex.take(8) }

/** The node kinds the map filters on, in the Nodes tabs' order. */
private val MAP_KINDS = listOf(
    Codes.ADV_TYPE_CHAT to R.string.kind_contacts,
    Codes.ADV_TYPE_REPEATER to R.string.kind_repeaters,
    Codes.ADV_TYPE_ROOM to R.string.kind_rooms,
    Codes.ADV_TYPE_SENSOR to R.string.kind_sensors,
)

/**
 * What the map shows: which kinds of node, and what is drawn with them. Laid out like
 * Nodes' sort/filter menu, with toggles that stack and so keep the menu open.
 */
@Composable
private fun MapFilterMenu(
    open: Boolean,
    onDismiss: () -> Unit,
    typeFilter: Set<Int>,
    onToggleType: (Int) -> Unit,
    onClearTypes: () -> Unit,
    showLabels: Boolean,
    onToggleLabels: () -> Unit,
    linksShown: Boolean,
    onHideLinks: () -> Unit,
) {
    MeshDropdownMenu(expanded = open, onDismissRequest = onDismiss) {
        MenuHeading(stringResource(R.string.nodes_show_only))
        for ((type, label) in MAP_KINDS) {
            DropdownMenuItem(
                text = { Text(stringResource(label)) },
                onClick = { onToggleType(type) },
                trailingIcon = { if (type in typeFilter) SelectedTick() },
            )
        }
        HorizontalDivider()
        MenuHeading(stringResource(R.string.map_on_map))
        DropdownMenuItem(
            text = { Text(stringResource(R.string.map_labels)) },
            onClick = onToggleLabels,
            trailingIcon = { if (showLabels) SelectedTick() },
        )
        if (linksShown) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.map_hide_links)) },
                onClick = { onHideLinks(); onDismiss() },
            )
        }
        if (typeFilter.isNotEmpty()) {
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text(stringResource(R.string.nodes_clear_filters)) },
                onClick = { onClearTypes(); onDismiss() },
            )
        }
    }
}

