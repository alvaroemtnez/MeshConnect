package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.storage.PathHistoryEntity
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.protocol.HopSelection
import io.github.thatsfguy.meshcore.protocol.PathCodec
import io.github.thatsfguy.meshcore.protocol.PathHistoryHygiene
import io.github.thatsfguy.meshcore.protocol.RoutingMode
import io.github.thatsfguy.meshcore.protocol.TraceResult
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Per-contact routing control: Auto / Flood / Manual, the paths we have
 * seen with their success record, a hop-by-hop manual editor, and a
 * path trace. MeshCore keeps the route inside the contact record, so
 * every mode here rewrites that record on the radio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutingSheet(
    vm: MeshCoreViewModel,
    contact: ContactEntity,
    onDismiss: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val keyHex = contact.keyHex
    val paths by remember(keyHex) { vm.pathHistory(keyHex) }.collectAsState()
    val liveContacts by vm.liveContacts.collectAsState()

    var mode by remember(keyHex) { mutableStateOf(vm.routingMode(keyHex)) }
    val liveContact = liveContacts[keyHex]
    // Bytes per hop on THIS mesh; every hop-token parse depends on it.
    val hopWidth = vm.deviceInfo.collectAsState().value?.pathHashByteWidth ?: 1
    val hasStoredRoute = (liveContact?.storedPath?.size ?: 0) > 0
    // Full-key-hex -> name, so a picked hop can be named and, more
    // importantly, so a stored path can be resolved back to the nodes
    // behind it rather than to bare hashes.
    val contactNames = remember(liveContacts) {
        liveContacts.values.associate { it.publicKeyHex to it.name }
    }
    // The route under construction. Hops carry the node's FULL key where
    // we know it and derive their hash at the current width on demand —
    // see HopSelection: the width is a property of the mesh and has
    // arrived late often enough to be treated as untrustworthy here.
    var hops by remember(keyHex) {
        mutableStateOf(
            liveContacts[keyHex]?.storedPath?.takeIf { it.isNotEmpty() }?.let { bytes ->
                HopSelection.fromPath(bytes, liveContacts[keyHex]!!.pathInfo.hashWidth, contactNames)
            } ?: emptyList(),
        )
    }
    var showAdvanced by remember(keyHex) { mutableStateOf(false) }
    var manualHops by remember(keyHex) { mutableStateOf("") }
    var trace by remember { mutableStateOf<TraceResult?>(null) }
    var tracing by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(stringResource(R.string.route_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                contact.name.ifBlank { keyHex.take(12) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))

            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                RoutingMode.entries.forEachIndexed { i, m ->
                    SegmentedButton(
                        selected = mode == m,
                        onClick = {
                            mode = m
                            if (m != RoutingMode.Manual) vm.setRouting(keyHex, m)
                        },
                        shape = SegmentedButtonDefaults.itemShape(i, RoutingMode.entries.size),
                    ) { Text(m.name) }
                }
            }
            HintText(
                when (mode) {
                    RoutingMode.Auto ->
                        stringResource(R.string.route_auto)
                    RoutingMode.Flood ->
                        stringResource(R.string.route_flood)
                    RoutingMode.Manual ->
                        stringResource(R.string.route_manual)
                },
            )

            if (mode == RoutingMode.Manual) {
                Spacer(Modifier.height(8.dp))

                // --- the route, in order --------------------------------
                Text(stringResource(R.string.route_route), style = MaterialTheme.typography.titleSmall)
                if (hops.isEmpty()) {
                    HintText(stringResource(R.string.route_no_hops))
                }
                val unresolved = HopSelection.unresolvedIndices(hops, hopWidth)
                hops.forEachIndexed { i, hop ->
                    HopRow(
                        position = i + 1,
                        label = hop.label(hopWidth),
                        isUnresolved = i in unresolved,
                        canMoveUp = i > 0,
                        canMoveDown = i < hops.size - 1,
                        onUp = { hops = HopSelection.move(hops, i, -1) },
                        onDown = { hops = HopSelection.move(hops, i, +1) },
                        onRemove = { hops = HopSelection.removeAt(hops, i) },
                    )
                }
                if (unresolved.isNotEmpty()) {
                    // Refusing to send is the point: a hop that can't be
                    // expressed at this mesh's width is a DIFFERENT node
                    // if we pad or truncate it, not a vaguer version of
                    // the same one.
                    HintText(
                        stringResource(R.string.route_unresolved, unresolved.joinToString(", ") { (it + 1).toString() }, hopWidth * 2),
                    )
                }

                // --- pick a node ----------------------------------------
                val full = hops.size >= PathCodec.maxHopsFor(hopWidth)
                // Repeaters and room servers are what actually carries
                // traffic; a plain chat node is an endpoint, not a hop.
                val relays = liveContacts.values
                    .filter { it.type == Codes.ADV_TYPE_REPEATER || it.type == Codes.ADV_TYPE_ROOM }
                    .sortedBy { it.name.lowercase() }
                Spacer(Modifier.height(8.dp))
                if (relays.isEmpty()) {
                    HintText(
                        stringResource(R.string.route_no_repeaters),
                    )
                } else {
                    HintText(
                        if (full) {
                            stringResource(R.string.route_full, PathCodec.maxHopsFor(hopWidth))
                        } else {
                            stringResource(R.string.route_tap_add)
                        },
                    )
                    LazyColumn(Modifier.heightIn(max = 180.dp)) {
                        items(relays, key = { it.publicKeyHex }) { r ->
                            val name = r.name.ifBlank { r.publicKeyHex.take(12) }
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable(enabled = !full) {
                                        hops = HopSelection.add(
                                            hops,
                                            HopSelection.fromContact(r.publicKeyHex, name),
                                            hopWidth,
                                        )
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    name,
                                    Modifier.weight(1f),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (full) {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                // The hop this node contributes AT THIS
                                // MESH'S WIDTH. The old picker showed (and
                                // inserted) one byte regardless, so on a
                                // 2-byte mesh every tap added half a hop.
                                Text(
                                    r.publicKeyHex.take(hopWidth * 2),
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                ButtonFlowRow {
                    TextButton(
                        enabled = hops.isNotEmpty(),
                        onClick = { hops = emptyList() },
                    ) { Text(stringResource(R.string.route_clear)) }
                    TextButton(
                        enabled = hops.isNotEmpty(),
                        onClick = {
                            val hex = HopSelection.toHex(hops, hopWidth)
                            if (hex == null) {
                                vm.transientMessage.value = ctx.getString(R.string.route_bad_hops)
                            } else {
                                vm.setRouting(keyHex, RoutingMode.Manual, hex)
                            }
                        },
                    ) { Text(stringResource(R.string.route_apply)) }
                }

                // --- advanced: the old free-text field ------------------
                TextButton(onClick = {
                    showAdvanced = !showAdvanced
                    if (showAdvanced) manualHops = HopSelection.toTokens(hops, hopWidth)
                }) { Text(stringResource(if (showAdvanced) R.string.route_hide_hex else R.string.route_hex)) }
                if (showAdvanced) {
                    HintText(
                        stringResource(R.string.route_hex_hint, hopWidth * 2),
                    )
                    SettingsTextField(stringResource(R.string.route_hops), manualHops) { manualHops = it }
                    ButtonFlowRow {
                        TextButton(
                            enabled = manualHops.isNotBlank(),
                            onClick = {
                                // The hop-hash WIDTH is a property of the
                                // mesh (DEVICE_INFO), not a constant. Parsing
                                // with the default of 1 rejected every token
                                // on a 2-byte mesh, so Apply silently did
                                // nothing but flash an error naming the wrong
                                // digit count.
                                val parsed = HopSelection.fromTokens(manualHops, hopWidth)
                                if (parsed.isNullOrEmpty()) {
                                    vm.transientMessage.value = ctx.getString(R.string.route_hex_bad, hopWidth * 2, PathCodec.maxHopsFor(hopWidth))
                                } else {
                                    // Into the same ordered list, so the hex
                                    // path and the tap path can't disagree
                                    // about what will be sent.
                                    hops = HopSelection.fromPath(
                                        HopSelection.toBytes(parsed, hopWidth) ?: ByteArray(0),
                                        hopWidth,
                                        contactNames,
                                    )
                                    showAdvanced = false
                                }
                            },
                        ) { Text(stringResource(R.string.route_use_hops)) }
                    }
                }
            }

            // The route their last message came in on, reversed into one
            // we could send on. Offered separately from "Known paths"
            // because it is evidence of a route that WORKED in the other
            // direction, which is not the same as one that has delivered
            // for us.
            val replyRoute by remember(keyHex) { vm.replyRouteFromArrival(keyHex) }.collectAsState()
            replyRoute?.let { (hex, width) ->
                Spacer(Modifier.height(12.dp))
                Text(stringResource(R.string.route_reply_way), style = MaterialTheme.typography.titleSmall)
                val reversedHops = remember(hex, width, contactNames) {
                    HopSelection.fromPath(hexToBytes(hex), width, contactNames)
                }
                HintText(
                    pluralStringResource(R.plurals.route_reversed, reversedHops.size, reversedHops.size),
                )
                Text(
                    reversedHops.joinToString(" → ") { it.label(width) },
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
                TextButton(onClick = {
                    hops = reversedHops
                    mode = RoutingMode.Manual
                }) { Text(stringResource(R.string.route_use_this)) }
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.route_known), style = MaterialTheme.typography.titleSmall)
            if (paths.isEmpty()) {
                HintText(stringResource(R.string.route_known_none))
            }
            for (p in paths) {
                PathRow(
                    path = p,
                    onUse = {
                        // Split at the MESH's width, not at 1 byte. A
                        // remembered path was recorded on this mesh, so
                        // this is the width it was recorded at.
                        hops = HopSelection.fromPath(hexToBytes(p.pathHex), hopWidth, contactNames)
                        mode = RoutingMode.Manual
                        vm.setRouting(keyHex, RoutingMode.Manual, p.pathHex)
                    },
                    hashWidth = hopWidth,
                    onDelete = { vm.deletePath(keyHex, p.pathHex) },
                )
            }
            if (paths.isNotEmpty()) {
                TextButton(onClick = { vm.clearPathHistory(keyHex) }) {
                    Text(stringResource(R.string.route_clear_history), color = MaterialTheme.colorScheme.error)
                }
            }

            // The route drawn here, not shipped to the Map tab. The old
            // button set a flag the Map tab read, which meant tapping it
            // appeared to do nothing: it did not navigate, its toast was
            // swallowed by the modal sheet, and the summary it drew over
            // there was painted over by the MapView. Same map component
            // as the message info sheet, so there is one implementation
            // of "draw a route" and it is the one already proven.
            if (hasStoredRoute) {
                StoredRoutePathMap(vm, keyHex)
            }

            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.trace_route), style = MaterialTheme.typography.titleSmall)
            // Confirmed on hardware: the radio answers a trace with no
            // path with RESP_CODE_ERR. There is genuinely nothing to
            // report for a direct contact, so say that instead of
            // offering a button that spends airtime to be refused.
            val hasRoute = io.github.thatsfguy.meshcore.protocol.PathCodec
                .decodePathLen(contact.pathLen).hops > 0
            HintText(
                if (hasRoute) {
                    stringResource(R.string.route_trace_hint)
                } else {
                    stringResource(R.string.route_trace_direct)
                },
            )
            ButtonFlowRow {
                TextButton(
                    enabled = !tracing && hasRoute,
                    onClick = {
                        scope.launch {
                            tracing = true
                            trace = vm.tracePath(keyHex)
                            tracing = false
                            if (trace == null) {
                                // Distinguish the two silences: a
                                // flood-routed contact has no route to
                                // probe in the first place.
                                vm.transientMessage.value =
                                    if (contact.pathLen == 0xFF || contact.pathLen < 0) {
                                        ctx.getString(R.string.route_trace_flood)
                                    } else {
                                        // A trace has to traverse the whole route and come
                                        // back, so one node being down anywhere along it
                                        // looks exactly like a broken feature. Say which
                                        // to check rather than implying a fault.
                                        ctx.getString(R.string.route_trace_no_reply)
                                    }
                            }
                        }
                    },
                ) { Text(stringResource(if (tracing) R.string.route_tracing else R.string.route_run_trace)) }
            }
            if (tracing) SectionSpinner(stringResource(R.string.trace_waiting))
            trace?.let { t ->
                Text(
                    if (t.hopCount == 0) stringResource(R.string.path_direct)
                    else pluralStringResource(R.plurals.hops, t.hopCount, t.hopCount) + ": " + t.hops.joinToString(" → "),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "SNR: " + t.snrs.joinToString(", ") { "%.1f dB".format(it) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (t.hops.isNotEmpty()) {
                    // Where the route goes, as far as it can honestly be
                    // said (PARITY §9). An ambiguous hop is reported as a
                    // gap rather than pinned to a guess — a map that
                    // guesses looks exactly like one that knows.
                    val plot = vm.plotPath(t.hops.joinToString(" "))
                    HintText(plot.summary())
                    for (hop in plot.hops) {
                        val where = when {
                            hop.isPlotted ->
                                "${hop.name} · %.4f, %.4f".format(hop.latitude, hop.longitude)
                            hop.gap != null ->
                                (hop.name?.plus(" · ") ?: "") +
                                    io.github.thatsfguy.meshcore.protocol.PathGeometry
                                        .gapReason(hop.gap!!)
                            else -> "unknown"
                        }
                        Text(
                            "${hop.hashHex}  $where",
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = if (hop.isPlotted) {
                                MaterialTheme.colorScheme.onSurface
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                    TextButton(onClick = {
                        // The trace reports its OWN hash width in the
                        // frame; split at that, not at whatever the
                        // editor happens to assume.
                        hops = HopSelection.fromPath(
                            hexToBytes(t.hops.joinToString("")),
                            t.hashWidth,
                            contactNames,
                        )
                        mode = RoutingMode.Manual
                    }) { Text(stringResource(R.string.route_use_traced)) }
                }
            }
        }
    }
}

/**
 * One hop of the route being built: its position, what it is, and the
 * controls to move or drop it.
 *
 * Reorder buttons rather than drag-and-drop, deliberately: these lists
 * are two or three hops long, and a drag target that small is fiddlier
 * than a pair of arrows. Revisit if the lists get longer.
 */
@Composable
private fun HopRow(
    position: Int,
    label: String,
    isUnresolved: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onUp: () -> Unit,
    onDown: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "$position.",
            Modifier.width(24.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = if (isUnresolved) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        )
        TextButton(onClick = onUp, enabled = canMoveUp) { Text("▲") }
        TextButton(onClick = onDown, enabled = canMoveDown) { Text("▼") }
        TextButton(onClick = onRemove) { Text("×", color = MaterialTheme.colorScheme.error) }
    }
}

@Composable
private fun PathRow(
    path: PathHistoryEntity,
    onUse: () -> Unit,
    onDelete: () -> Unit,
    hashWidth: Int,
) {
    val isFlood = path.pathHex.isEmpty()
    // The row records the width it was written at; fall back to the
    // mesh's only for rows that predate the column.
    val width = path.hashWidth.takeIf { it in 1..4 } ?: hashWidth
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                if (isFlood) stringResource(R.string.flood) else PathCodec.formatHops(hexToBytes(path.pathHex), width),
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                buildString {
                    append(PathCodec.qualityLabel(path.successes, path.failures, isFlood))
                    if (!isFlood) {
                        // Recomputed, not read: rows written before the
                        // width column held a BYTE count here, and this
                        // list is exactly where "2 hops" read as "4".
                        val hops = PathHistoryHygiene.hopCount(path.pathHex.length / 2, width)
                        append(" · " + androidx.compose.ui.platform.LocalContext.current.resources.getQuantityString(R.plurals.hops, hops, hops))
                    }
                    append(" · ${path.successes}✓/${path.failures}✗")
                    if (path.lastWorkedAt > 0) {
                        append(
                            " · " + androidx.compose.ui.platform.LocalContext.current.getString(R.string.route_worked) + " " +
                                DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                                    .format(Date(path.lastWorkedAt)),
                        )
                    }
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isFlood) TextButton(onClick = onUse) { Text(stringResource(R.string.route_use)) }
        TextButton(onClick = onDelete) { Text("×", color = MaterialTheme.colorScheme.error) }
    }
}

private fun hexToBytes(hex: String): ByteArray =
    io.github.thatsfguy.meshcore.util.hexToBytesOrNull(hex) ?: ByteArray(0)
