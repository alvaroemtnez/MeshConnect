package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's lines (1.1, reviewed 2026-11-11). 1.0 (reported 2026-11-10): at 2×1 the
 * heard repeater's hash was gated on `wide` and vanished, while 3×1 showed it.
 */
class WidgetRowTest {

    private val src = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
    private val row = src.substringAfter("private fun RowContent(").substringBefore("private fun statusDot")
    private val header = src.substringAfter("private fun androidx.glance.layout.RowScope.HeaderLine(")
        .substringBefore("@Composable")

    @Test
    fun `the header line is status, name and unread, in that order`() {
        val dot = header.indexOf("statusDot(s, stale)")
        val name = header.indexOf("s.nodeName")
        val unread = header.indexOf("R.drawable.ic_mark_unread")
        assertTrue(dot in 0 until name && name < unread)
        assertTrue("the name gives way, the count doesn't", header.contains("modifier = GlanceModifier.defaultWeight()"))
        assertFalse("shown at 0 too", header.contains("s.unread > 0) {"))
    }

    @Test
    fun `the row is the header, then battery and the repeater`() {
        assertTrue(row.contains("HeaderLine(s, stale, ink, nameSp, textSp, icon - 2.dp)"))
        val second = row.substringAfter("HeaderLine(")
        assertTrue(second.indexOf("Battery(") in 0 until second.indexOf("Repeater("))
        // The hash is not gated on width.
        assertFalse(row.contains("if (wide) Repeater"))
    }

    @Test
    fun `no SNR anywhere on the widget - the bars show it`() {
        assertFalse(src.contains("repeaterSnr"))
        assertFalse(src.contains("dB\""))
    }
}
