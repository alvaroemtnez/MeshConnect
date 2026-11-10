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
        val hash = row.substringBefore("s.repeaterHash?.let {").takeLast(200)
        assertFalse("hash must show at 2x1 too", hash.contains("if (wide) {"))
        assertTrue(row.contains("s.repeaterSnr?.takeIf { s.repeaterHash != null }"))
    }

    @Test
    fun `2x1 shows the unread count, at zero as well`() {
        val narrow = row.substringAfter("if (!wide) {").substringBefore("Row(verticalAlignment")
        assertTrue(narrow.contains("R.drawable.ic_mark_unread"))
        assertFalse("shown at 0 too, like the card", narrow.contains("s.unread > 0) {"))
    }
}
