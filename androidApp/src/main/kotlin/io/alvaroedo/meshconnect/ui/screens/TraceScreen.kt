package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.platform.NodeMarkers
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.alvaroedo.meshconnect.ui.map.CameraRequest
import io.alvaroedo.meshconnect.ui.map.MapLine
import io.alvaroedo.meshconnect.ui.map.MapMarker
import io.alvaroedo.meshconnect.ui.map.MeshMap
import io.alvaroedo.meshconnect.ui.model.LinkMeter
import io.alvaroedo.meshconnect.ui.model.TraceRoutes
import io.alvaroedo.meshconnect.ui.theme.MonoFontFamily
import io.github.thatsfguy.meshcore.presentation.LinkQuality
import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.util.isPlausiblePosition
import java.util.Locale

const val TRACE_ROUTE = "trace/{key}"
fun traceRoute(keyHex: String) = "trace/$keyHex"

/**
 * Ping a repeater, room or sensor, or trace the route towards a person,
 * and draw the answer on a map: each leg coloured by the SNR it was heard
 * at, with the reading on the line, and the legs listed underneath.
 */
@Composable
fun TraceScreen(vm: MeshCoreViewModel, nav: NavController, keyHex: String) {
    val context = LocalContext.current
    val contacts by vm.dbContacts.collectAsState()
    val self by vm.selfInfo.collectAsState()
    val target = contacts.firstOrNull { it.keyHex == keyHex }
    val isPing = target != null && target.type != Codes.ADV_TYPE_CHAT

    var runs by remember { mutableIntStateOf(0) }
    var running by remember { mutableStateOf(true) }
    var run by remember { mutableStateOf<MeshCoreViewModel.TraceRun?>(null) }
    LaunchedEffect(runs) {
        running = true
        run = vm.traceNode(keyHex)
        running = false
    }

    // A hop is named, and placed, only on a unique match among nodes that
    // forward: a short hash can belong to several, and guessing would draw
    // the route through the wrong one.
    fun resolve(hash: String): ContactEntity? = contacts.filter {
        it.type != Codes.ADV_TYPE_CHAT && it.keyHex.startsWith(hash, ignoreCase = true)
    }.singleOrNull()

    val selfPoint = self?.let { (it.latitude to it.longitude).takeIf { p -> isPlausiblePosition(p.first, p.second) } }
    fun pointOf(hash: String?): Pair<Double, Double>? =
        if (hash == null) selfPoint else resolve(hash)?.let { c ->
            (c.latitude to c.longitude).takeIf { isPlausiblePosition(it.first, it.second) }
                ?.let { it.first!! to it.second!! }
        }

    val result = run?.result
    val legs = result?.let { TraceRoutes.legs(it.hops, it.snrs) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = stringResource(if (isPing) R.string.trace_ping_title else R.string.trace_title),
                subtitle = target?.name?.ifBlank { null } ?: keyHex.take(12),
                vm = vm,
                nav = nav,
                menuActions = emptyList(),
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // Each segment once on the map; out and back share it, so it
            // takes the weaker of the two readings.
            val segments = LinkedHashMap<Set<String?>, MutableList<Double>>()
            legs?.forEach { leg -> segments.getOrPut(setOf(leg.fromHash, leg.hopHash)) { mutableListOf() }.add(leg.snr) }
            val lines = mutableListOf<MapLine>()
            val markers = mutableListOf<MapMarker>()
            for ((ends, readings) in segments) {
                val list = ends.toList()
                val a = pointOf(list[0]) ?: continue
                val b = pointOf(list.getOrElse(1) { list[0] }) ?: continue
                val worst = readings.min()
                val quality = LinkQuality.of(worst)
                lines += MapLine(listOf(a, b), quality.argb, widthDp = 4f)
                val label = readings.joinToString(" · ") { String.format(Locale.ROOT, "%.1f", it) } + " dB"
                markers += MapMarker(
                    id = "chip|$ends", lat = (a.first + b.first) / 2, lon = (a.second + b.second) / 2,
                    imageKey = "chip|$label|${quality.argb}",
                    bitmap = { NodeMarkers.buildChip(context, label, quality.argb).bitmap },
                    centred = true, tappable = false,
                )
            }
            selfPoint?.let { (lat, lon) ->
                markers += MapMarker(
                    "self", lat, lon, "pin|self|${self?.name}",
                    bitmap = { NodeMarkers.build(context, 0, self?.name, isSelf = true).drawable.bitmap },
                    anchorV = NodeMarkers.anchorV(context, self?.name),
                    tappable = false,
                )
            }
            val placed = (run?.sentHops ?: emptyList()).distinct().mapNotNull { h -> resolve(h)?.let { h to it } }
            for ((_, c) in placed) {
                val lat = c.latitude ?: continue
                val lon = c.longitude ?: continue
                if (!isPlausiblePosition(lat, lon)) continue
                val label = c.name.ifBlank { c.keyHex.take(8) }
                markers += MapMarker(
                    c.keyHex, lat, lon, "pin|${c.type}|$label",
                    bitmap = { NodeMarkers.build(context, c.type, label).drawable.bitmap },
                    anchorV = NodeMarkers.anchorV(context, label),
                    tappable = false,
                )
            }
            val fit = listOfNotNull(selfPoint) + placed.mapNotNull { (_, c) ->
                (c.latitude to c.longitude).takeIf { isPlausiblePosition(it.first, it.second) }?.let { it.first!! to it.second!! }
            }

            MeshMap(
                modifier = Modifier.fillMaxWidth().weight(1f),
                markers = markers,
                lines = lines,
                tilesEnabled = vm.prefs.mapTilesEnabled,
                camera = remember(fit) { if (fit.isEmpty()) null else CameraRequest(fit.size, fit = fit) },
                controls = true,
                selfPosition = selfPoint,
                fitAll = fit,
            )

            Column(Modifier.fillMaxWidth().weight(1f).navigationBarsPadding()) {
                if (running) LinearProgressIndicator(Modifier.fillMaxWidth())
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        traceSummary(run, running),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = { runs++ }, enabled = !running) {
                        Icon(painterResource(R.drawable.ic_refresh), null)
                        Spacer(Modifier.width(6.dp))
                        Text(stringResource(R.string.trace_again))
                    }
                }
                LazyColumn(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(0.dp)) {
                    itemsIndexed(legs ?: emptyList()) { _, leg ->
                        val from = leg.fromHash?.let { nameOf(resolve(it), it) } ?: stringResource(R.string.trace_you)
                        val to = leg.hopHash?.let { nameOf(resolve(it), it) } ?: stringResource(R.string.trace_you)
                        ListItem(
                            headlineContent = {
                                Text("$from → $to", maxLines = 1, overflow = TextOverflow.Ellipsis)
                            },
                            supportingContent = {
                                Text(
                                    LinkQuality.of(leg.snr).label + " · " +
                                        String.format(Locale.ROOT, "%.2f dB", leg.snr),
                                    fontFamily = MonoFontFamily,
                                )
                            },
                            leadingContent = { SignalBars(LinkMeter.bars(leg.snr, self?.sf), live = true) },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }
        }
    }
}

private fun nameOf(c: ContactEntity?, hash: String): String =
    c?.name?.ifBlank { null } ?: hash.uppercase()

@Composable
private fun traceSummary(run: MeshCoreViewModel.TraceRun?, running: Boolean): String = when {
    running -> stringResource(R.string.trace_waiting)
    run == null -> ""
    run.refused == MeshCoreViewModel.TraceRefusal.NotConnected -> stringResource(R.string.link_not_connected)
    run.refused == MeshCoreViewModel.TraceRefusal.UnknownNode -> stringResource(R.string.trace_unknown)
    run.refused == MeshCoreViewModel.TraceRefusal.NoRepeater -> stringResource(R.string.trace_direct)
    run.result == null -> stringResource(R.string.trace_no_reply)
    else -> stringResource(
        R.string.trace_done,
        String.format(Locale.getDefault(), "%.1f", run.roundTripMs / 1000.0),
        run.sentHops.size,
    )
}
