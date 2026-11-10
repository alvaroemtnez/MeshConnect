package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.presentation.BatteryLevel
import io.github.thatsfguy.meshcore.protocol.TelemetryReading

/**
 * Telemetry readings by Cayenne LPP channel, so two readings with the same label stay apart.
 *
 * What the firmware puts where (`examples/simple_repeater/MyMesh.cpp`, the
 * REQ_TYPE_GET_TELEMETRY_DATA handler, and `src/helpers/sensors/EnvironmentSensorManager.cpp`
 * `querySensors`): channel 1, `TELEM_CHANNEL_SELF`, is the board itself — its battery voltage,
 * its MCU's built-in temperature and its GPS. Each external sensor then takes the next channel
 * from 2 up, in the order it was initialised. A RAK4631 with a temperature/humidity module
 * therefore reports two "Temperature" readings, the processor's on 1 and the module's on 2,
 * which a flat list showed as two identical rows (reported 2026-11-10).
 */
object TelemetryGroups {

    data class Group(val channel: Int, val isBoard: Boolean, val readings: List<TelemetryReading>)

    /** Channels in ascending order, each keeping the order its readings arrived in. */
    fun group(readings: List<TelemetryReading>): List<Group> =
        readings.groupBy { it.channel }.toSortedMap().map { (ch, rs) ->
            Group(ch, ch == BatteryLevel.SELF_CHANNEL, rs)
        }

    /** Headings earn their place only when there is more than one channel to tell apart. */
    fun needsHeadings(groups: List<Group>): Boolean = groups.size > 1
}
