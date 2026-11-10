package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.ui.MeshCoreViewModel
import io.github.thatsfguy.meshcore.util.isPlausiblePosition
import io.github.thatsfguy.meshcore.util.meshCentre
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.map.MeshMap
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng

/**
 * Pick a position by moving the map under a fixed crosshair.
 *
 * Crosshair-and-centre rather than tap-to-drop: a tap target on a map is
 * a few metres wide at useful zoom levels, and the position being chosen
 * here is the one the radio broadcasts to the whole mesh.
 *
 * Tiles are the app's only outbound HTTP, and this respects the same
 * off-switch as the map tab — with the switch off the picker still
 * works, just against a blank canvas with coordinates.
 */
@Composable
fun PositionPickerDialog(
    vm: MeshCoreViewModel,
    initialLat: Double,
    initialLon: Double,
    onPick: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
    /** True when the point becomes the radio's advertised position. */
    forAdvert: Boolean = true,
) {
    val tilesEnabled = vm.prefs.mapTilesEnabled

    // A radio with no fix reports 0,0, and starting the map there drops the
    // user in the Gulf of Guinea. Fall back to the last camera, then to the
    // middle of the nodes we know, and only then to the world view. Read
    // once, so the camera doesn't jump when a contact sync lands.
    val start = remember {
        when {
            isPlausiblePosition(initialLat, initialLon) -> Triple(initialLat, initialLon, 13.0)
            else -> vm.prefs.mapCamera
                ?: meshCentre(
                    vm.dbContacts.value.map { (it.latitude ?: 0.0) to (it.longitude ?: 0.0) },
                )
                    // Zoom 10 is roughly a 30 km view on a phone.
                    ?.let { Triple(it.first, it.second, 10.0) }
                ?: Triple(0.0, 0.0, 2.0)
        }
    }
    var centre by remember { mutableStateOf(start.first to start.second) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.picker_title)) },
        text = {
            Column {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    MeshMap(
                        modifier = Modifier.fillMaxWidth().height(300.dp),
                        tilesEnabled = tilesEnabled,
                        initial = CameraPosition.Builder()
                            .target(LatLng(start.first, start.second)).zoom(start.third).build(),
                        onCameraIdle = { cam -> cam.target?.let { centre = it.latitude to it.longitude } },
                    )
                    // The pin's tip marks the centre of the map.
                    Icon(
                        painterResource(R.drawable.ic_location_on),
                        null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(40.dp).padding(bottom = 20.dp),
                    )
                }
                Text(
                    "%.5f, %.5f".format(centre.first, centre.second),
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(top = 8.dp),
                )
                if (forAdvert) {
                    Text(
                        stringResource(R.string.picker_advert_warning),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (!tilesEnabled) {
                    Text(
                        stringResource(R.string.picker_tiles_off),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onPick(centre.first, centre.second) }) {
                Text(stringResource(if (forAdvert) R.string.picker_use else R.string.send))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}

/** Where a "My location" fix came from — the radio's is a fallback and says so. */
enum class FixSource { Phone, Radio }

/**
 * "My location": find the fix, show it, and send only once confirmed.
 *
 * It used to send as soon as a fix arrived, with a snackbar saying it was looking — so the
 * first the user saw of the position was in the thread, already on the air. This is the
 * place picker's dialog with the search in place of the crosshair: a spinner while the
 * phone looks, then the map at the fix, fixed (it is the fix being confirmed, not a place
 * being chosen — "Place on the map" is for that), with Cancel and Send.
 */
@Composable
fun MyLocationDialog(
    vm: MeshCoreViewModel,
    locate: suspend () -> Pair<Pair<Double, Double>, FixSource>?,
    onSend: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
) {
    val tilesEnabled = vm.prefs.mapTilesEnabled
    // null = still looking; attempt bumps to retry.
    var attempt by remember { mutableStateOf(0) }
    var result by remember { mutableStateOf<Pair<Pair<Double, Double>, FixSource>?>(null) }
    var failed by remember { mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(attempt) {
        result = null
        failed = false
        val r = locate()
        if (r == null) failed = true else result = r
    }
    val fix = result
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.attach_my_location)) },
        text = {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                        .clip(androidx.compose.foundation.shape.RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        fix != null -> {
                            val (lat, lon) = fix.first
                            MeshMap(
                                modifier = Modifier.fillMaxWidth().height(260.dp),
                                tilesEnabled = tilesEnabled,
                                initial = CameraPosition.Builder()
                                    .target(LatLng(lat, lon)).zoom(15.0).build(),
                                gesturesEnabled = false,
                            )
                            Icon(
                                painterResource(R.drawable.ic_location_on),
                                null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(40.dp).padding(bottom = 20.dp),
                            )
                        }
                        failed -> Text(
                            stringResource(R.string.attach_location_failed),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.padding(24.dp),
                        )
                        else -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            androidx.compose.material3.CircularProgressIndicator()
                            Text(
                                stringResource(R.string.attach_locating),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 16.dp),
                            )
                        }
                    }
                }
                if (fix != null) {
                    Text(
                        "%.5f, %.5f".format(fix.first.first, fix.first.second),
                        style = MaterialTheme.typography.bodyMedium,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    if (fix.second == FixSource.Radio) {
                        Text(
                            stringResource(R.string.loc_from_radio),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
        confirmButton = {
            when {
                fix != null -> TextButton(onClick = { onSend(fix.first.first, fix.first.second) }) {
                    Text(stringResource(R.string.send))
                }
                failed -> TextButton(onClick = { attempt++ }) { Text(stringResource(R.string.loc_retry)) }
                else -> {}
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
    )
}
