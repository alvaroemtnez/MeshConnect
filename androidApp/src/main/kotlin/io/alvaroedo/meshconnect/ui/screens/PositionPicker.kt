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
