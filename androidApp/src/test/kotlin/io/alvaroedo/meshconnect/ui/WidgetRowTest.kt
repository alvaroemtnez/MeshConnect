package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one-row widget (reported 2026-11-10): at 2×1 the heard repeater's hash was gated on
 * `wide` and vanished, while 3×1 showed it. The 2×1 row now carries the hash, its SNR and
 * the unread count, and gives up the app mark to make room.
 */
class WidgetRowTest {

    private val row = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
        .substringAfter("private fun RowContent(").substringBefore("private fun statusDot")

    @Test
    fun `the hash and SNR are not gated on width`() {
        // On the gauge line when there's no room below, on its own line when there is —
        // never behind `wide`.
        assertTrue(row.contains("if (!ownLine) RepeaterReading(s, ink, textSp = textSp)"))
        assertTrue(row.contains("if (ownLine && s.repeaterHash != null)"))
        assertTrue(row.contains("val ownLine = !wide && roomy"))
    }

    @Test
    fun `the row shows the unread count beside the name, at zero as well`() {
        val narrow = row.substringAfter("// Beside the name at every width").substringBefore("Row(verticalAlignment")
        assertTrue(narrow.contains("R.drawable.ic_mark_unread"))
        assertFalse("shown at 0 too, like the card", narrow.contains("s.unread > 0) {"))
    }
}
