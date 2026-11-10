package io.alvaroedo.meshconnect.ui.screens

import io.alvaroedo.meshconnect.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.RepeaterStatus
import io.github.thatsfguy.meshcore.protocol.StatusCodec
import io.github.thatsfguy.meshcore.presentation.Units
import io.github.thatsfguy.meshcore.presentation.BatteryLevel
import io.github.thatsfguy.meshcore.presentation.TelemetryFetch
import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.protocol.TelemetryReading
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Decoded repeater status + telemetry — the binary status response and
 * Cayenne-LPP sensor payload rendered as fields instead of raw CLI
 * text. Both are fetched on demand (the node has to answer over the
 * air, which can take seconds).
 */
@Composable
fun RepeaterStatusPanel(vm: MeshCoreViewModel, keyHex: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var status by remember(keyHex) { mutableStateOf<RepeaterStatus?>(null) }
    var telemetry by remember(keyHex) { mutableStateOf<List<TelemetryReading>>(emptyList()) }
    val units by vm.unitSystem.collectAsState()
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    suspend fun fetchStatus() {
        loading = true
        note = null
        status = vm.repeaterStatus(keyHex)
        if (status == null) note = ctx.getString(R.string.rs_no_status)
        loading = false
    }

    // Opening the Status screen IS the request for status. Asking the
    // user to then press "Fetch status" is §6.1 — a control for
    // something the system already knows you want — and it is the one
    // place the rule was applied everywhere else and missed here. The
    // reference client does the same on entry
    // (`repeater_status_screen.initState` → `_loadStatus()`).
    // Refresh stays available for a second look.
    LaunchedEffect(keyHex) { fetchStatus() }

    suspend fun fetchTelemetry() {
        loading = true; note = null
        // Only infrastructure reaches this panel, so a
        // contact missing from the table is a repeater.
        val type = vm.dbContacts.value.firstOrNull { it.keyHex == keyHex }?.type
            ?: Codes.ADV_TYPE_REPEATER
        when (val r = vm.fetchTelemetry(keyHex, type)) {
            is TelemetryFetch.Readings -> telemetry = r.readings
            else -> note = r.message
        }
        loading = false
    }

    // Cards, as the node card and Settings are (2026-11-10): what the node reports first,
    // as tiles; then the reads that each cost a request, behind their own button.
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp, bottom = 24.dp),
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
    ) {
        PanelCard(
            stringResource(R.string.rs_status),
            R.drawable.ic_monitoring,
            action = {
                androidx.compose.material3.IconButton(enabled = !loading, onClick = { scope.launch { fetchStatus() } }) {
                    androidx.compose.material3.Icon(
                        androidx.compose.ui.res.painterResource(R.drawable.ic_refresh),
                        stringResource(R.string.rs_refresh),
                    )
                }
            },
        ) {
            if (loading) {
                SectionSpinner(stringResource(R.string.rs_waiting))
                RequestProgressHint(vm, keyHex)
            }
            note?.let { HintText(it) }
            status?.let { s ->
                TileRow {
                    InfoTile(stringResource(R.string.rs_battery), BatteryLevel.label(s.batteryMillivolts), Modifier.weight(1f).fillMaxHeight())
                    InfoTile(stringResource(R.string.rs_uptime), StatusCodec.formatUptime(s.uptimeSeconds), Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(Modifier.height(8.dp))
                TileRow {
                    InfoTile(stringResource(R.string.rs_last_rssi), "${s.lastRssi} dBm · %.1f dB".format(s.lastSnr), Modifier.weight(1f).fillMaxHeight())
                    // dBm, not dB: an absolute power, like RSSI beside it. SNR
                    // is the ratio and keeps dB. (The firmware's own comment
                    // says "dBi", which is antenna gain — wrong a third way.)
                    InfoTile(stringResource(R.string.rs_noise), "${s.noiseFloor} dBm", Modifier.weight(1f).fillMaxHeight())
                }
                Spacer(Modifier.height(8.dp))
                TileRow {
                    InfoTile(stringResource(R.string.rs_util), "%.1f %%".format(s.channelUtilizationPercent), Modifier.weight(1f).fillMaxHeight())
                    InfoTile(stringResource(R.string.rs_queue), s.queueLength.toString(), Modifier.weight(1f).fillMaxHeight())
                }
                SheetHeading(stringResource(R.string.rs_packets))
                StatField(stringResource(R.string.rs_rx_tx), "${s.packetsReceived} / ${s.packetsSent}")
                StatField(stringResource(R.string.rs_flood), "${s.floodTx} / ${s.floodRx}")
                StatField(stringResource(R.string.rs_direct), "${s.directTx} / ${s.directRx}")
                StatField(stringResource(R.string.rs_dups), "${s.directDuplicates} / ${s.floodDuplicates}")
                StatField(stringResource(R.string.rs_airtime), "${s.txAirSeconds}s / ${s.rxAirSeconds}s")
                StatField(stringResource(R.string.rs_errors), s.errorEvents.toString())
            }
            if (status == null && !loading) {
                HintText(stringResource(R.string.rs_no_answer_yet))
            }
        }

        PanelCard(
            stringResource(R.string.rs_telemetry),
            R.drawable.ic_sensors,
            action = {
                TextButton(enabled = !loading, onClick = { scope.launch { fetchTelemetry() } }) {
                    Text(stringResource(R.string.rs_fetch_tele))
                }
            },
        ) {
            if (telemetry.isNotEmpty()) TelemetryList(telemetry, units) else HintText(stringResource(R.string.rs_tele_hint))
        }

        // --- Access list -------------------------------------------------
        AccessListSection(vm, keyHex)

        // --- Live noise floor ---------------------------------------------
        NoiseFloorSection(vm, keyHex)

        // --- One-hop neighbours -------------------------------------------
        NeighboursSection(vm, keyHex)
    }
}

@Composable
private fun StatField(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}


/**
 * Who the node grants admin or guest access to.
 *
 * Read-only on purpose: writing an ACL entry grants someone control of
 * a repeater, and the set-command syntax varies by firmware. Reading is
 * unambiguous; guessing at a write is not.
 */
@Composable
private fun AccessListSection(vm: MeshCoreViewModel, keyHex: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    var entries by remember(keyHex) {
        mutableStateOf<List<io.github.thatsfguy.meshcore.protocol.AccessList.BinEntry>?>(null)
    }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    PanelCard(stringResource(R.string.rs_acl), R.drawable.ic_admin_panel) {
        ButtonFlowRow {
            TextButton(
                enabled = !loading,
                onClick = {
                    scope.launch {
                        loading = true; note = null
                        // Binary request, NOT `get acl`. That command is
                        // guarded by `sender_timestamp == 0` in the
                        // firmware, so it only ever answers the serial
                        // console — over the air the node replied "??: acl",
                        // which read like old firmware rather than a
                        // question it could never have answered.
                        entries = vm.repeaterAccessList(keyHex)
                        if (entries == null) {
                            note = ctx.getString(R.string.rs_acl_no_reply)
                        }
                        loading = false
                    }
                },
            ) { Text(stringResource(R.string.rs_acl_fetch)) }
        }
        if (loading) {
            SectionSpinner(stringResource(R.string.rs_asking))
            RequestProgressHint(vm, keyHex)
        }
        note?.let { HintText(it) }
        entries?.let { list ->
            if (list.isEmpty()) {
                HintText(stringResource(R.string.rs_acl_empty))
            }
            for (entry in list) {
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        entry.keyPrefixHex,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        entry.roleLabel + if (entry.hasUnknownFlags) {
                            // Never hide a bit we do not understand on an
                            // access-list row: an unexplained flag is the
                            // last thing to render as though it were known.
                            " (0x%02x)".format(entry.permissions)
                        } else {
                            ""
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (list.isNotEmpty()) {
                HintText(
                    stringResource(R.string.rs_acl_prefixes),
                )
            }
        }
    }
}

/**
 * Noise floor, polled while the screen is open.
 *
 * Each sample is a round-trip over the air, so the interval is seconds,
 * not milliseconds, and it stops when you leave — a background poller
 * against someone else's repeater is airtime you're spending on their
 * behalf.
 */
@Composable
private fun NoiseFloorSection(vm: MeshCoreViewModel, keyHex: String) {
    var watching by remember(keyHex) { mutableStateOf(false) }
    var samples by remember(keyHex) { mutableStateOf<List<Int>>(emptyList()) }

    LaunchedEffect(watching, keyHex) {
        while (watching) {
            vm.repeaterStatus(keyHex)?.let { status ->
                samples = (samples + status.noiseFloor).takeLast(WATCH_SAMPLES)
            }
            kotlinx.coroutines.delay(WATCH_INTERVAL_MS)
        }
    }

    PanelCard(stringResource(R.string.rs_noise), R.drawable.ic_graphic_eq) {
        ButtonFlowRow {
            TextButton(onClick = { watching = !watching }) {
                Text(stringResource(if (watching) R.string.rs_stop_watch else R.string.rs_watch))
            }
            if (samples.isNotEmpty()) {
                TextButton(onClick = { samples = emptyList() }) { Text(stringResource(R.string.clear)) }
            }
        }
        if (samples.isEmpty()) {
            HintText(stringResource(R.string.rs_watch_hint, (WATCH_INTERVAL_MS / 1000).toInt()))
        } else {
            // The unit once, on the leading value — the run below is a
            // shape, and repeating "dBm" on every figure buries it.
            Text(
                stringResource(R.string.rs_noise_now, samples.last(), samples.min(), samples.max(), samples.size),
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                samples.joinToString(" ") { it.toString() },
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * How long to let discover replies land before re-reading the table.
 *
 * Not a guess: driven against a live repeater, a neighbour that had been
 * missing for hours appeared roughly 30s after the broadcast. Re-reading
 * immediately returns the same stale list, which reads as "the probe did
 * nothing".
 */
private const val PROBE_SETTLE_MS = 40_000L

/** Seconds between noise-floor samples — each one costs airtime. */
private const val WATCH_INTERVAL_MS = 5_000L
private const val WATCH_SAMPLES = 24

/**
 * A repeater's one-hop neighbour table (PARITY §6).
 *
 * Two honesty requirements, both from §12. Entries identify nodes by a
 * 4-byte key prefix, so a name is offered only when exactly one contact
 * matches and "(N matches)" otherwise. And the whole table is hearsay:
 * it is what this repeater says it hears, relayed by that repeater —
 * useful for understanding coverage, useless as evidence about anyone.
 */
@Composable
private fun NeighboursSection(vm: MeshCoreViewModel, keyHex: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    // Only repeater firmware keeps a neighbour table — room servers and
    // sensors have no `0x06` handler at all, so the button could only
    // ever time out into "no reply, are you in range?", which blames the
    // link for a question the node cannot be asked.
    val contacts by vm.dbContacts.collectAsState()
    val isRepeater = contacts.firstOrNull { it.keyHex == keyHex }?.type ==
        io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_REPEATER
    if (!isRepeater) return

    val scope = rememberCoroutineScope()
    var table by remember(keyHex) {
        mutableStateOf<io.github.thatsfguy.meshcore.protocol.Neighbours.Table?>(null)
    }
    var loading by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf<String?>(null) }

    // Rows accumulated across pages; the node returns at most a
    // bufferful per reply and we ask for the rest by offset.
    var rows by remember(keyHex) {
        mutableStateOf<List<io.github.thatsfguy.meshcore.protocol.Neighbours.Neighbour>>(emptyList())
    }

    // The probe goes through the admin CLI, and Status is reachable on a
    // read-only session, so the button has to be gated separately from
    // the section.
    val sessions by vm.adminSessions.collectAsState()
    val isAdmin = sessions[keyHex]?.isAdmin == true
    var probing by remember(keyHex) { mutableStateOf(false) }

    suspend fun fetch(offset: Int) {
        loading = true; note = null
        val t = vm.repeaterNeighbours(keyHex, offset = offset)
        table = t
        when {
            t == null -> note = ctx.getString(R.string.rs_no_reply)
            // A correct request cannot produce this: the node is
            // claiming rows and returning an empty first page. Say it is
            // wrong rather than inviting a retry that cannot help.
            t.isEmptyButNotEmpty ->
                note = ctx.getString(R.string.rs_nb_rejected, t.total)
            else -> rows = if (offset == 0) t.entries else rows + t.entries
        }
        loading = false
    }

    PanelCard(stringResource(R.string.rs_neighbours), R.drawable.ic_hub) {
        ButtonFlowRow {
            TextButton(
                enabled = !loading && !probing,
                onClick = { scope.launch { rows = emptyList(); fetch(0) } },
            ) { Text(stringResource(R.string.rs_nb_fetch)) }
            if (isAdmin) {
                TextButton(
                    enabled = !loading && !probing,
                    onClick = {
                        scope.launch {
                            probing = true
                            note = null
                            if (!vm.probeNeighbours(keyHex)) {
                                note = ctx.getString(R.string.rs_probe_refused)
                            } else {
                                // Replies arrive over the air, one per
                                // repeater that hears it. Measured against a
                                // live node: a new neighbour landed in the
                                // table about 30s after the broadcast, so
                                // re-reading immediately would show the same
                                // stale list and look like the probe failed.
                                delay(PROBE_SETTLE_MS)
                                rows = emptyList()
                                fetch(0)
                            }
                            probing = false
                        }
                    },
                ) { Text(stringResource(R.string.rs_probe)) }
            }
            table?.let { t ->
                if (t.isPartial && rows.isNotEmpty()) {
                    TextButton(
                        enabled = !loading && !probing,
                        onClick = { scope.launch { fetch(rows.size) } },
                    ) { Text(stringResource(R.string.rs_fetch_more)) }
                }
            }
        }
        if (probing) {
            SectionSpinner(stringResource(R.string.rs_probing))
            // Without this the spinner sits for the better part of a minute
            // with nothing to say why.
            HintText(stringResource(R.string.rs_probing_hint))
        }
        if (loading && !probing) {
            SectionSpinner(stringResource(R.string.rs_asking))
            RequestProgressHint(vm, keyHex)
        }
        note?.let { HintText(it) }

        table?.let { t ->
            if (rows.isEmpty() && t.total == 0) {
                // "Nobody is out there" and "nobody has advertised lately"
                // look identical here, and only one of them is true — so
                // point at the probe rather than letting an empty list read
                // as a finding.
                HintText(
                    if (isAdmin) {
                        stringResource(R.string.rs_nb_none_admin)
                    } else {
                        stringResource(R.string.rs_nb_none)
                    },
                )
            }
            for (n in rows) {
                val names = vm.neighbourNames(n)
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        io.github.thatsfguy.meshcore.protocol.Neighbours.label(n, names),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        io.github.thatsfguy.meshcore.protocol.Neighbours
                            .heardAgoLabel(n.heardSecondsAgo),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    Text("%.1f dB".format(n.snr), style = MaterialTheme.typography.bodySmall)
                }
            }
            if (rows.isNotEmpty()) {
                if (t.isPartial) {
                    HintText(stringResource(R.string.rs_nb_partial, rows.size, t.total))
                }
                // Lead with what the table actually contains. "Neighbours"
                // reads as "everyone this node hears", and the firmware
                // records something much narrower — so a short list looks
                // like a bug until you know that.
                ExpandableHint(stringResource(R.string.rs_nb_hint)) {
                    HintText(
                        stringResource(R.string.rs_nb_d1),
                    )
                    HintText(
                        stringResource(R.string.rs_nb_d2),
                    )
                    HintText(
                        stringResource(R.string.rs_nb_d3),
                    )
                    HintText(
                        stringResource(R.string.rs_nb_d4),
                    )
                }
            }
        }
    }
}
