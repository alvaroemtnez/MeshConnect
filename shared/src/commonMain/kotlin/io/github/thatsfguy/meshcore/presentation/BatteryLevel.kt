package io.github.thatsfguy.meshcore.presentation

import io.github.thatsfguy.meshcore.protocol.TelemetryReading
import io.github.thatsfguy.meshcore.util.fixed
import kotlin.math.roundToInt

/**
 * A battery percentage ESTIMATED from a voltage.
 *
 * **No MeshCore node sends a percentage.** Every source this app reads
 * is volts: `battery_mv` in the status response, the Cayenne LPP
 * `Voltage` (type 116) a node puts on channel 1 (`TELEM_CHANNEL_SELF`,
 * `src/helpers/SensorManager.h:10`; written as
 * `telemetry.addVoltage(TELEM_CHANNEL_SELF, getBattMilliVolts() / 1000.0f)`
 * in `examples/simple_repeater/MyMesh.cpp:244` and its room-server,
 * sensor and companion siblings), and `RESP_CODE_BATT_AND_STORAGE`.
 * Checked against firmware `main` at a366955 (2026-09-30).
 *
 * **The estimate is a discharge curve, not a line** (MeshConnect, 2026-11-10). The node's own
 * screen draws a straight line from 3000 to 4200 mV (`renderBatteryIndicator` in
 * `examples/companion_radio/ui-new/UITask.cpp`), and this used to copy it so the two agreed.
 * But a Li-ion cell is not linear: it spends most of its charge between about 3.6 and 4.0 V
 * and falls off a cliff below 3.5 V. The line read 3.70 V as 58% when the cell holds under
 * half, and its "20%" was 3.24 V, nearly flat — so the low-battery alert came too late to act
 * on. Agreeing with the device's screen is worth less than being right about the cell.
 *
 * The curve is Meshtastic's default 1S Li-ion/LiPo open-circuit table (`OCV_ARRAY` in
 * meshtastic/firmware `src/power.h`), field-tested on the same ESP32/nRF52 LoRa boards and
 * cells MeshCore runs on, linearly interpolated between its 10% points. The radio reports
 * its voltage under its own light load, a little below open-circuit, so the estimate leans
 * low — the safe side for a battery warning. Firmwares that gauge the pack themselves
 * (inw-mesh on the T-Pager counts charge with its fuel gauge) still send only millivolts
 * over the companion protocol, so their screen and this can differ; this is the better
 * reading of the voltage the app is given. It is still an estimate, marked with `~`.
 *
 * Outside a plausible single-cell window there is no percentage at all.
 * The firmware itself overrides the range to 6000–8400 for a 2S board
 * (`variants/lilygo_tbeam_1w/platformio.ini`), and a 1S line would read
 * that pack as 100% until it died. A board with no battery sense
 * reports 0. Either way the honest answer is the voltage alone.
 */
object BatteryLevel {

    /**
     * Millivolts at 100%, 90%, … 0% (Meshtastic `OCV_ARRAY`, `src/power.h`). Strictly
     * descending; [percent] interpolates between neighbours.
     */
    val CURVE_MV = intArrayOf(4190, 4050, 3990, 3890, 3800, 3720, 3630, 3530, 3420, 3300, 3100)

    /**
     * The window in which a reading is believed to be one Li-ion cell.
     * Below it is "no sense line"; above it is USB rail, 2S, or solar.
     */
    const val PLAUSIBLE_MIN_MV = 2500
    const val PLAUSIBLE_MAX_MV = 4500

    /** LPP channel a node uses for its own supply (`TELEM_CHANNEL_SELF`). */
    const val SELF_CHANNEL = 1

    /** Cayenne LPP voltage (`LPP_VOLTAGE`, 116). */
    const val LPP_VOLTAGE = 0x74

    /** 0–100, or null when the voltage does not look like one cell. */
    fun percent(millivolts: Int): Int? {
        if (millivolts < PLAUSIBLE_MIN_MV || millivolts > PLAUSIBLE_MAX_MV) return null
        if (millivolts >= CURVE_MV.first()) return 100
        if (millivolts <= CURVE_MV.last()) return 0
        // CURVE_MV[i] is (10 - i) * 10 percent; find the step the reading falls in.
        val i = CURVE_MV.indexOfFirst { millivolts >= it } // 1..10
        val hi = CURVE_MV[i - 1]
        val lo = CURVE_MV[i]
        val base = (10 - i) * 10
        return (base + 10.0 * (millivolts - lo) / (hi - lo)).roundToInt().coerceIn(0, 100)
    }

    /** "3.92 V · ~76%", or "3.92 V" when no estimate is believable. */
    fun label(millivolts: Int): String {
        val volts = fixed(millivolts / 1000.0, 2) + " V"
        val p = percent(millivolts) ?: return volts
        return "$volts · ~$p%"
    }

    /**
     * True for the node's own supply voltage in a telemetry reply. Only
     * that reading is a battery: a sensor node can publish other
     * voltages (an INA219 bus, a solar panel) on other channels, and a
     * percentage beside those would be invented.
     */
    fun isSelfBattery(reading: TelemetryReading): Boolean =
        reading.channel == SELF_CHANNEL && reading.type == LPP_VOLTAGE

    /** Millivolts from an LPP voltage (0.01 V resolution). */
    fun millivolts(reading: TelemetryReading): Int = (reading.value * 1000).roundToInt()
}
