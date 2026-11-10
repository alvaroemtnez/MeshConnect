package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.TelemetryGroups
import io.github.thatsfguy.meshcore.protocol.TelemetryReading
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TelemetryGroupsTest {

    private fun r(ch: Int, type: Int, label: String, v: Double, unit: String = "") =
        TelemetryReading(ch, type, label, v, unit)

    @Test
    fun `the board's temperature and a sensor's are told apart by channel`() {
        // A RAK4631 repeater with a temperature/humidity module, as the firmware sends it:
        // voltage and MCU temperature on TELEM_CHANNEL_SELF, the module on the next channel.
        val readings = listOf(
            r(1, 0x74, "Voltage", 4.1, "V"),
            r(2, 0x67, "Temperature", 21.5, "°C"),
            r(2, 0x68, "Humidity", 48.0, "%"),
            r(1, 0x67, "Temperature", 27.0, "°C"),
        )
        val groups = TelemetryGroups.group(readings)
        assertEquals(listOf(1, 2), groups.map { it.channel })
        assertTrue(groups[0].isBoard)
        assertFalse(groups[1].isBoard)
        assertEquals(listOf("Voltage", "Temperature"), groups[0].readings.map { it.label })
        assertEquals(27.0, groups[0].readings[1].value, 0.0)
        assertEquals(listOf("Temperature", "Humidity"), groups[1].readings.map { it.label })
        assertTrue(TelemetryGroups.needsHeadings(groups))
    }

    @Test
    fun `a board-only node gets no headings`() {
        val groups = TelemetryGroups.group(listOf(r(1, 0x74, "Voltage", 4.0, "V")))
        assertFalse(TelemetryGroups.needsHeadings(groups))
        assertTrue(TelemetryGroups.group(emptyList()).isEmpty())
    }

    @Test
    fun `channels come out in order whatever order they arrive in`() {
        val groups = TelemetryGroups.group(listOf(r(4, 0x67, "T", 1.0), r(2, 0x67, "T", 2.0), r(3, 0x67, "T", 3.0)))
        assertEquals(listOf(2, 3, 4), groups.map { it.channel })
    }
}
