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
    private fun card(wide: Boolean, lines: Int, nearby: Int) = Plan(false, false, wide, lines, nearby)

    @Test
    fun `every grid size draws the reviewed frame`() {
        assertEquals(card(wide = false, lines = 5, nearby = 0), cells(2, 2))
        assertEquals(card(wide = true, lines = 3, nearby = 1), cells(3, 2))
        assertEquals(card(wide = true, lines = 3, nearby = 1), cells(4, 2))
        assertEquals(card(wide = false, lines = 3, nearby = 4), cells(2, 3))
        assertEquals(card(wide = true, lines = 2, nearby = 6), cells(3, 3))
        assertEquals(card(wide = true, lines = 2, nearby = 6), cells(4, 3))
        assertEquals(card(wide = false, lines = 6, nearby = 6), cells(2, 4))
        assertEquals(card(wide = true, lines = 6, nearby = 6), cells(4, 4))
    }

    @Test
    fun `wide sizes put the message beside the status from the first line`() {
        // 1.0 drew a 3×2 with the message beside the battery and empty space above it.
        for (cols in 3..4) for (rows in 2..4) {
            val p = cells(cols, rows)
            assertTrue(p.wide)
            assertTrue("$cols×$rows has a message", p.messageLines >= 2)
        }
    }

    @Test
    fun `more height never takes anything away`() {
        for (w in listOf(156f, 234f, 312f)) {
            var last = WidgetLayout.plan(w, WidgetLayout.CARD_MIN_HEIGHT)
            var h = WidgetLayout.CARD_MIN_HEIGHT + 1
            while (h <= 450f) {
                val p = WidgetLayout.plan(w, h)
                assertTrue("nearby at $w×$h", p.nearbyRows >= last.nearbyRows)
                // The message may give a line back to the first nearby row, never more.
                assertTrue("message at $w×$h", p.messageLines >= last.messageLines - 1 || last.nearbyRows < p.nearbyRows)
                last = p
                h += 1f
            }
        }
    }

    @Test
    fun `the spare height is used, not left as a band at the bottom`() {
        // What the blocks take must come within one message line of the height.
        for (cols in 2..4) for (rows in 2..4) {
            val w = cols * 78f
            val h = rows * 98f
            val p = WidgetLayout.plan(w, h)
            if (p.messageLines == WidgetLayout.MAX_MESSAGE_LINES || p.nearbyRows == WidgetLayout.MAX_NEARBY) continue
            with(WidgetLayout) {
                var used = 2 * PAD + HEAD + GAP
                used += if (p.wide) maxOf(STATUS_TWO_LINES, MESSAGE_HEAD + p.messageLines * MESSAGE_LINE)
                else STATUS_ONE_LINE + (if (p.messageLines > 0) GAP + MESSAGE_HEAD + p.messageLines * MESSAGE_LINE else 0f)
                if (p.nearbyRows > 0) used += GAP + NEARBY_HEAD + p.nearbyRows * NEARBY_ROW
                assertTrue("$cols×$rows leaves ${h - used} dp", h - used in 0f..MESSAGE_LINE)
            }
        }
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
        assertTrue(src.contains("WidgetLayout.plan(size.width.value, size.height.value)"))
    }
}
