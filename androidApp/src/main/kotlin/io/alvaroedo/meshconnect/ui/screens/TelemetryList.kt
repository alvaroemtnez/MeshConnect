package io.alvaroedo.meshconnect.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.model.TelemetryGroups
import io.github.thatsfguy.meshcore.presentation.BatteryLevel
import io.github.thatsfguy.meshcore.presentation.UnitSystem
import io.github.thatsfguy.meshcore.presentation.Units
import io.github.thatsfguy.meshcore.protocol.TelemetryReading

/**
 * A node's telemetry, one card per LPP channel: the board's own readings, then each
 * sensor's (see [TelemetryGroups]). Shared by the node card's dialog and repeater Status.
 */
@Composable
internal fun TelemetryList(readings: List<TelemetryReading>, units: UnitSystem, modifier: Modifier = Modifier) {
    val groups = TelemetryGroups.group(readings)
    val headed = TelemetryGroups.needsHeadings(groups)
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        for (g in groups) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    if (headed) {
                        Text(
                            if (g.isBoard) {
                                stringResource(R.string.telemetry_board, g.channel)
                            } else {
                                stringResource(R.string.telemetry_sensor, g.channel)
                            },
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    for (r in g.readings) {
                        Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                            Text(
                                r.label,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            Text(telemetryValue(r, units), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Converted here and only here: the reading keeps the unit the sensor encoded. */
internal fun telemetryValue(r: TelemetryReading, units: UnitSystem): String =
    if (BatteryLevel.isSelfBattery(r)) {
        BatteryLevel.label(BatteryLevel.millivolts(r))
    } else {
        Units.reading(r.value, r.unit, units).let { (v, u) ->
            val n = if (v == v.toLong().toDouble()) v.toLong().toString() else "%.2f".format(v)
            n + (if (u.isBlank()) "" else " $u")
        }
    }
