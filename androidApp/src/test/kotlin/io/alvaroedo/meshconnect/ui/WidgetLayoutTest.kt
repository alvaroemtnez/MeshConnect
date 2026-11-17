package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.widget.WidgetLayout
import io.alvaroedo.meshconnect.widget.WidgetLayout.Plan
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's size rules (1.1). Sizes are a ~410 dp-wide phone on GrapheneOS: columns of
 * 78 dp, six rows of 98 dp. The expected plans are the frames reviewed as mockups on
 * 2026-11-11, which were drawn by a port of this same function.
 */
class WidgetLayoutTest {

    private fun cells(cols: Int, rows: Int) = WidgetLayout.plan(cols * 78f, rows * 98f)
    private fun card(wide: Boolean, messages: Int, lines: Int, nearby: Int) =
        Plan(false, false, wide, messages, lines, nearby)

    /**
     * 1.4 (asked for on the beta, 2026-11-17): at most three repeaters, a block at the
     * bottom only as tall as its rows, and the messages above it, more than one when they fit.
     */
    @Test
    fun `every grid size draws the planned frame`() {
        assertEquals(card(wide = false, messages = 1, lines = 5, nearby = 0), cells(2, 2))
        assertEquals(card(wide = false, messages = 1, lines = 4, nearby = 3), cells(2, 3))
        assertEquals(card(wide = false, messages = 3, lines = 2, nearby = 3), cells(2, 4))
        assertEquals(card(wide = true, messages = 1, lines = 3, nearby = 1), cells(3, 2))
        assertEquals(card(wide = true, messages = 2, lines = 2, nearby = 3), cells(3, 3))
        assertEquals(card(wide = true, messages = 3, lines = 3, nearby = 3), cells(4, 4))
    }

    @Test
    fun `never more than three repeaters`() {
        for (h in 140..900 step 7) {
            assertTrue(WidgetLayout.plan(312f, h.toFloat()).nearbyRows <= 3)
        }
    }

    @Test
    fun `wide sizes put a message beside the status from the first line`() {
        for (cols in 3..4) for (rows in 2..4) {
            val p = cells(cols, rows)
            assertTrue(p.wide)
            assertTrue("$cols×$rows has a message", p.messages >= 1 && p.messageLines >= 2)
        }
    }

    @Test
    fun `more height never takes anything away`() {
        for (w in listOf(156f, 234f, 312f)) {
            var last = WidgetLayout.plan(w, WidgetLayout.CARD_MIN_HEIGHT)
            var h = WidgetLayout.CARD_MIN_HEIGHT + 1
            while (h <= 600f) {
                val p = WidgetLayout.plan(w, h)
                assertTrue("nearby at $w×$h", p.nearbyRows >= last.nearbyRows)
                // A message may give way to a new repeater row (the repeaters come first), never otherwise.
                assertTrue("messages at $w×$h", p.messages >= last.messages || p.nearbyRows > last.nearbyRows)
                last = p
                h += 1f
            }
        }
    }

    @Test
    fun `the spare height goes to the messages, not a band`() {
        for (w in listOf(156f, 234f, 312f)) for (hi in 160..600 step 3) {
            val h = hi.toFloat()
            val p = WidgetLayout.plan(w, h)
            if (p.messages == 0 || p.messageLines == WidgetLayout.MAX_MESSAGE_LINES) continue
            with(WidgetLayout) {
                var space = h - 2 * PAD - HEAD - GAP - (if (p.wide) 0f else STATUS_ONE_LINE + GAP)
                if (p.nearbyRows > 0) space -= GAP + NEARBY_HEAD + p.nearbyRows * NEARBY_ROW
                val spare = space - messagesHeight(p.messages, p.messageLines)
                assertTrue("$w×$h overflows by ${-spare}", spare >= 0f)
                // Less than one more line for each message shown.
                assertTrue("$w×$h leaves $spare dp", spare < p.messages * MESSAGE_LINE)
            }
        }
    }

    @Test
    fun `the plan follows what there is to show`() {
        // No message, two repeaters, 4×3: the card lists two, not three.
        val p = WidgetLayout.plan(4 * 78f, 3 * 98f, messagesAvailable = 0, nearbyAvailable = 2)
        assertEquals(0, p.messages)
        assertEquals(2, p.nearbyRows)
        // One chat with a message: it grows into the height, rather than leaving it blank.
        val one = WidgetLayout.plan(4 * 78f, 4 * 98f, messagesAvailable = 1, nearbyAvailable = 1)
        assertEquals(1, one.messages)
        assertEquals(WidgetLayout.MAX_MESSAGE_LINES, one.messageLines)
        // None heard still takes one row, for "none heard".
        assertEquals(1, WidgetLayout.plan(2 * 78f, 3 * 98f, nearbyAvailable = 0).nearbyRows)
    }

    @Test
    fun `the messages take the remaining height and the repeaters sit at the bottom`() {
        val src = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
        assertTrue(src.contains("Nearby(s, ink, muted, stale, plan.nearbyRows)\n"))
        assertTrue(src.contains("messagesAvailable = s.messages.size"))
        assertTrue(src.contains("nearbyAvailable = if (stale) 0 else s.nearby.size"))
    }

    @Test
    fun `signal comes before battery, as in the top bar`() {
        val src = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
        val pairs = Regex("""(Repeater|Battery)\(s, ink[^)]*\)""").findAll(src).map { it.groupValues[1] }.toList()
        // Three places draw both: the wide column, the stacked line, the row widget.
        assertEquals(listOf("Repeater", "Battery", "Repeater", "Battery", "Repeater", "Battery"), pairs)
        assertTrue("Simple mode hides the hash again", src.contains("s.repeaterHash?.takeIf { s.showHash }"))
    }

    @Test
    fun `a 2x1 is a row, and a tall one draws larger`() {
        val short = WidgetLayout.plan(156f, 64f)
        assertTrue(short.row)
        assertFalse(short.largeRow)
        val tall = WidgetLayout.plan(156f, 98f)
        assertTrue(tall.row)
        assertTrue(tall.largeRow)
    }

    @Test
    fun `the widget takes its real size, not breakpoints`() {
        val src = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
        assertTrue(src.contains("override val sizeMode = SizeMode.Exact"))
        assertTrue(src.contains("WidgetLayout.plan(\n        size.width.value,\n        size.height.value,"))
    }
}
