package io.alvaroedo.meshconnect.storage

import io.alvaroedo.meshconnect.ui.screens.DiagnosticsFile
import java.io.File
import java.time.OffsetDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The log's lines and its export (2026-11-17): lines stamped with Unix seconds, readable only
 * on screen, could not be used to diagnose a failed firmware update.
 */
class DiagnosticsFormatTest {

    // 2026-11-17 19:13:05.123 UTC
    private val at = 1_794_942_785_123L

    @Test
    fun `a line is stamped in ISO 8601 with milliseconds and the offset`() {
        val line = DiagnosticsLog.formatLine(at, "Firmware", "scanning", ZoneId.of("Europe/Madrid"))
        assertEquals("2026-11-17T20:13:05.123+01:00 [Firmware] scanning", line)
    }

    @Test
    fun `the stamp parses back to the same instant`() {
        val line = DiagnosticsLog.formatLine(at, "Ble", "x", ZoneId.of("America/Detroit"))
        val stamp = line.substringBefore(" [")
        assertEquals(at, OffsetDateTime.parse(stamp).toInstant().toEpochMilli())
        // UTC writes Z, which parses the same way.
        val utc = DiagnosticsLog.formatLine(at, "Ble", "x", ZoneId.of("UTC")).substringBefore(" [")
        assertEquals("2026-11-17T19:13:05.123Z", utc)
        assertEquals(at, OffsetDateTime.parse(utc).toInstant().toEpochMilli())
    }

    @Test
    fun `lines sort as text in time order`() {
        val zone = ZoneId.of("Europe/Madrid")
        val a = DiagnosticsLog.formatLine(at, "A", "", zone)
        val b = DiagnosticsLog.formatLine(at + 9, "A", "", zone)
        val c = DiagnosticsLog.formatLine(at + 61_000, "A", "", zone)
        assertEquals(listOf(a, b, c), listOf(c, a, b).sorted())
    }

    @Test
    fun `a saved file is named by when, sortably, without colons`() {
        val name = DiagnosticsFile.name(at, ZoneId.of("Europe/Madrid"))
        assertEquals("meshconnect-log-2026-11-17T201305.txt", name)
        assertFalse(name.contains(':'))
    }

    @Test
    fun `enough lines are kept for one firmware update`() {
        assertTrue(DiagnosticsLog.MAX_LINES >= 2000)
    }

    @Test
    fun `the log opens from the firmware screen, and a failure points to it`() {
        val fw = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/FirmwareScreen.kt").readText()
        assertTrue(fw.contains("MenuAction(logLabel, icon = R.drawable.ic_bug_report) { logOpen = true }"))
        assertTrue(fw.substringAfter("is FirmwareUi.Failed ->").contains("R.string.diag_view_for_failure"))
    }
}
