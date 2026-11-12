package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's lines (1.1, reviewed 2026-11-11). 1.0 (reported 2026-11-10): at 2×1 the
 * heard repeater's hash was gated on `wide` and vanished, while 3×1 showed it. 1.2
 * (reported 2026-11-12): the link is an icon, not a colour-only dot, and with no link the
 * battery and signal line gives way to the state in words (WidgetStatusTest).
 */
class WidgetRowTest {

    private val src = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
    private val row = src.substringAfter("private fun RowContent(").substringBefore("private fun linkTint")
    private val header = src.substringAfter("private fun androidx.glance.layout.RowScope.HeaderLine(")
        .substringBefore("@Composable")

    @Test
    fun `the header line is the link, name and unread, in that order`() {
        val link = header.indexOf("LinkIcon(link,")
        val name = header.indexOf("s.nodeName")
        val unread = header.indexOf("R.drawable.ic_mark_unread")
        assertTrue(link in 0 until name && name < unread)
        assertTrue("the name gives way, the count doesn't", header.contains("modifier = GlanceModifier.defaultWeight()"))
        assertFalse("shown at 0 too", header.contains("s.unread > 0) {"))
    }

    @Test
    fun `no colour-only dot is left`() {
        assertFalse(src.contains("statusDot"))
        assertFalse(src.contains("GlanceModifier.size(8.dp).cornerRadius(4.dp)"))
    }

    @Test
    fun `the row is the header, then battery and the repeater while connected`() {
        assertTrue(row.contains("HeaderLine(s, link, ink, nameSp, textSp, icon)"))
        val second = row.substringAfter("HeaderLine(")
        val gate = second.indexOf("if (WidgetStatus.showsGauges(link))")
        assertTrue(gate in 0 until second.indexOf("Battery(") && second.indexOf("Battery(") < second.indexOf("Repeater("))
        assertTrue("otherwise the state in words", second.substringAfter("} else {").trimStart().startsWith("LinkWords(link"))
        // The hash is not gated on width.
        assertFalse(row.contains("if (wide) Repeater"))
    }

    @Test
    fun `the card's status line is gated the same way, in both layouts`() {
        val card = src.substringAfter("private fun Content(").substringBefore("private fun androidx.glance.layout.RowScope.HeaderLine(")
        assertTrue(card.contains("val gauges = WidgetStatus.showsGauges(link)"))
        // Wide: the two-line column; stacked: the one line.
        assertTrue(card.contains("LinkWords(link, textSp = 13, maxLines = 2)"))
        assertTrue(card.contains("LinkWords(link, textSp = 13, maxLines = 1)"))
        assertTrue(card.contains("if (gauges) WidgetLayout.STATUS_WIDTH else WidgetLayout.STATUS_WIDTH_WORDS"))
    }

    @Test
    fun `every link state has an icon and words`() {
        val icon = src.substringAfter("private fun LinkIcon(").substringBefore("private fun LinkWords(")
        val words = src.substringAfter("private fun linkWords(")
        for (state in listOf("Connected", "ConnectedUnencrypted", "Connecting", "NotConnected", "NotRunning")) {
            assertTrue("$state icon", icon.contains("WidgetStatus.Link.$state"))
            assertTrue("$state words", words.contains("WidgetStatus.Link.$state"))
        }
        // The shapes differ, not just the colours.
        assertTrue(icon.contains("R.drawable.ic_link") && icon.contains("R.drawable.ic_sync") && icon.contains("R.drawable.ic_link_off"))
        assertTrue("described for screen readers", icon.contains("linkWords(LocalContext.current, link),"))
    }

    @Test
    fun `no SNR anywhere on the widget - the bars show it`() {
        assertFalse(src.contains("repeaterSnr"))
        assertFalse(src.contains("dB\""))
    }
}
