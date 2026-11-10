package io.github.thatsfguy.meshcore.presentation

import io.github.thatsfguy.meshcore.protocol.CayenneLpp
import io.github.thatsfguy.meshcore.protocol.TelemetryReading
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Battery percentage from volts, along Meshtastic's 1S Li-ion table (`OCV_ARRAY`,
 * meshtastic/firmware `src/power.h`): 4190 4050 3990 3890 3800 3720 3630 3530 3420 3300 3100
 * mV for 100% down to 0% in tens. Expected values are worked by hand from that table.
 */
class BatteryLevelTest {

    @Test
    fun theTablesOwnPointsAreExact() {
        val expected = listOf(100, 90, 80, 70, 60, 50, 40, 30, 20, 10, 0)
        BatteryLevel.CURVE_MV.forEachIndexed { i, mv -> assertEquals(expected[i], BatteryLevel.percent(mv), "$mv mV") }
    }

    @Test
    fun interpolatesBetweenPoints() {
        // 3920 sits between 3890 (70%) and 3990 (80%): 70 + 10 * 30 / 100 = 73.
        assertEquals(73, BatteryLevel.percent(3920))
        // 3700: 40 + 10 * 70 / 90 = 47.8 → 48.
        assertEquals(48, BatteryLevel.percent(3700))
    }

    @Test
    fun theLineItReplacedWasWrongWhereItMatters() {
        // The old 3000–4200 line read 3.70 V as 58% and put "20%" at 3.24 V. On the
        // curve 3.70 V is under half, and 3.24 V is 7%: the alert must fire well before.
        assertTrue(BatteryLevel.percent(3700)!! < 50)
        assertEquals(7, BatteryLevel.percent(3240))
        assertEquals(20, BatteryLevel.percent(3420))
    }

    @Test
    fun clampsInsideThePlausibleWindow() {
        assertEquals(0, BatteryLevel.percent(2800))
        assertEquals(0, BatteryLevel.percent(3100))
        assertEquals(100, BatteryLevel.percent(4200))
        assertEquals(100, BatteryLevel.percent(4350))
    }

    @Test
    fun neverRisesAsTheVoltageFalls() {
        // Exhaustive over the whole plausible window: a dip must never read as a gain.
        var previous = 100
        for (mv in BatteryLevel.PLAUSIBLE_MAX_MV downTo BatteryLevel.PLAUSIBLE_MIN_MV) {
            val p = BatteryLevel.percent(mv)!!
            assertTrue(p in 0..previous, "$mv mV gave $p after $previous")
            previous = p
        }
    }

    @Test
    fun theCurveIsStrictlyDescending() {
        BatteryLevel.CURVE_MV.toList().zipWithNext().forEach { (a, b) -> assertTrue(a > b) }
        assertEquals(11, BatteryLevel.CURVE_MV.size)
    }

    @Test
    fun noSenseLineGivesNoPercentage() {
        assertNull(BatteryLevel.percent(0))
        assertNull(BatteryLevel.percent(2499))
        assertNull(BatteryLevel.percent(-1))
    }

    @Test
    fun twoCellAndUsbRailGiveNoPercentage() {
        // tbeam_1w is 2S (6000–8400 in its platformio.ini); 7.4 V nominal
        // would read 100% on a 1S line until the pack died.
        assertNull(BatteryLevel.percent(7400))
        assertNull(BatteryLevel.percent(5000))
        assertNull(BatteryLevel.percent(4501))
        // u16 maximum off the wire.
        assertNull(BatteryLevel.percent(65_535))
    }

    @Test
    fun labelKeepsTheVoltageAndMarksTheEstimate() {
        assertEquals("3.92 V · ~73%", BatteryLevel.label(3920))
        assertEquals("4.20 V · ~100%", BatteryLevel.label(4200))
        assertEquals("7.40 V", BatteryLevel.label(7400))
        assertEquals("0.00 V", BatteryLevel.label(0))
    }

    @Test
    fun aRepeatersSelfVoltageOffTheWireIsItsBattery() {
        // [ch 1][type 0x74][0x01 0x88] = 392 × 0.01 V, as
        // telemetry.addVoltage(TELEM_CHANNEL_SELF, …) encodes it.
        val readings = CayenneLpp.parse(byteArrayOf(0x01, 0x74, 0x01, 0x88.toByte()))
        val r = readings.single()
        assertTrue(BatteryLevel.isSelfBattery(r))
        assertEquals(3920, BatteryLevel.millivolts(r))
        assertEquals(73, BatteryLevel.percent(BatteryLevel.millivolts(r)))
    }

    @Test
    fun otherVoltagesAreNotBatteries() {
        // An INA219 bus voltage on a sensor channel.
        assertFalse(BatteryLevel.isSelfBattery(TelemetryReading(2, 0x74, "Voltage", 3.9, "V")))
        // Channel 1, but not a voltage.
        assertFalse(BatteryLevel.isSelfBattery(TelemetryReading(1, 0x67, "Temperature", 3.9, "°C")))
    }
}
