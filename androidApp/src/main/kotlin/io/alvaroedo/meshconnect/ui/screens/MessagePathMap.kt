package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.protocol.PathSketch
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.drawable.toBitmap
import io.alvaroedo.meshconnect.ui.map.CameraRequest
import io.alvaroedo.meshconnect.ui.map.LineStyle
import io.alvaroedo.meshconnect.ui.map.MapLine
import io.alvaroedo.meshconnect.ui.map.MapMarker
import io.alvaroedo.meshconnect.ui.map.MeshMap

/**
 * The route a received message took, on a map.
 *
 * Sits above the ordered "Arrived via" list in the message info sheet —
 * the list is the authority on what the chain was, the map is the shape
 * of it. Where a node cannot be placed, the LIST still names it, so
 * nothing is lost by the map being unable to draw it.
 *
 * Three visual states, matching [PathSketch]:
 *
 *  - solid line, filled pin — the node advertised this position;
 *  - dotted line, hollow pin with `?` — we know which node it is but
 *    not where, so it is drawn approximately as a drawing aid;
 *  - dashed line, no pin at all — we could not identify the hop, and
 *    dashed is what this app's map already uses for "no route is being
 *    claimed here".
 *
 * The map is skipped entirely when nothing can be placed: an empty
 * grey rectangle communicates less than the sentence that replaces it.
 */
@Composable
fun MessagePathMap(vm: MeshCoreViewModel, m: MessageEntity, senderLabel: String) {
    val contacts by vm.dbContacts.collectAsState()
    val self by vm.selfInfo.collectAsState()
    val deviceInfo by vm.deviceInfo.collectAsState()

    val sketch = remember(m.id, contacts, self, deviceInfo) {
        vm.sketchArrival(m, senderLabel)
    }
    PathSketchMap(vm, sketch)
}

/**
 * A contact's stored outbound route, on the same map.
 *
 * The routing sheet's own view of the route it is editing. This used to
 * be a "Show route on map" button that set a flag the Map tab read: it
 * navigated nowhere, said nothing, and the banner it drew was painted
 * over by the MapView itself (see the clipToBounds note below — the same
 * defect, one screen up). Drawing it where the route is being edited
 * removes the round trip and the flag with it.
 */
@Composable
fun StoredRoutePathMap(
    vm: MeshCoreViewModel,
    keyHex: String,
    heading: String = "Route on map",
) {
    val contacts by vm.dbContacts.collectAsState()
    val self by vm.selfInfo.collectAsState()
    val liveContacts by vm.liveContacts.collectAsState()

    val sketch = remember(keyHex, contacts, self, liveContacts) {
        vm.sketchStoredPath(keyHex)
    }
    PathSketchMap(vm, sketch, heading = heading)
}

/**
 * The shared body: draw a [PathSketch.Sketch] on a [MeshMap].
 *
 * Skipped entirely when nothing can be placed — an empty grey rectangle
 * communicates less than the sentence that replaces it.
 */
@Composable
private fun PathSketchMap(
    vm: MeshCoreViewModel,
    sketch: PathSketch.Sketch?,
    // NOT `title`: inside the Marker { } builder below that name binds
    // to Marker.title, and the assignment silently retargets.
    heading: String = "Route",
) {
    if (sketch == null || sketch.isEmpty) return

    val context = LocalContext.current
    val tilesEnabled = vm.prefs.mapTilesEnabled

    Spacer(Modifier.height(12.dp))
    Text(heading, style = MaterialTheme.typography.titleSmall)
    Text(
        sketch.summary(),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(6.dp))

    val routeColor = MaterialTheme.colorScheme.primary.toArgb()
    val lines = sketch.segments.map { segment ->
        MapLine(
            points = listOf(
                segment.from.latitude to segment.from.longitude,
                segment.to.latitude to segment.to.longitude,
            ),
            // Fainter for anything not fully vouched for.
            argb = if (segment.style == PathSketch.Style.Solid) routeColor else (routeColor and 0x00FFFFFF) or (0x8C shl 24),
            widthDp = 3.5f,
            style = when (segment.style) {
                PathSketch.Style.Solid -> LineStyle.Solid
                // Dotted = "placed approximately".
                PathSketch.Style.Dotted -> LineStyle.Dotted
                // Dashed = "no route claimed", as on the main map.
                PathSketch.Style.Dashed -> LineStyle.Dashed
            },
        )
    }
    val markers = sketch.nodes.mapIndexedNotNull { i, node ->
        val point = node.point ?: return@mapIndexedNotNull null
        val inferred = node.certainty == PathSketch.Certainty.Inferred
        MapMarker(
            id = "n$i", lat = point.latitude, lon = point.longitude,
            imageKey = "path|$inferred|${node.isEndpoint}",
            bitmap = { PathPins.forNode(context, inferred = inferred, isEndpoint = node.isEndpoint).toBitmap() },
            tappable = false,
        )
    }
    val points = sketch.nodes.mapNotNull { it.point }.map { it.latitude to it.longitude }
    MeshMap(
        modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(10.dp)),
        markers = markers,
        lines = lines,
        tilesEnabled = tilesEnabled,
        camera = remember(points) { CameraRequest(1, fit = points, zoom = 13.0, animate = false) },
    )
    if (sketch.inferred > 0 || sketch.unplaced > 0) {
        Spacer(Modifier.height(4.dp))
        Text(
            PathSketch.LEGEND,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 2.dp),
        )
    }
}

