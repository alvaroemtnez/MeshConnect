package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.widget.WidgetLayout
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's size rules (2026-11-10). Cell sizes are illustrative of a ~410 dp-wide phone:
 * five columns of ~78 dp; rows of ~118 dp at five rows (before) and ~98 dp at six (GrapheneOS
 * now), less the launcher's padding.
 */
class WidgetLayoutTest {

    @Test
    fun `a 3x2 on six short rows shows nearby repeaters, not empty space`() {
        // 3 × 78 wide, 2 × 98 tall: under the old 270 dp threshold, so the card stopped short.
        val p = WidgetLayout.plan(234f, 196f)
        assertFalse(p.row)
        assertTrue(p.wide)
        assertTrue("some of the height goes to nearby repeaters", p.nearbyRows >= 1)
    }

    @Test
    fun `a 3x3 lists all four`() {
        assertEquals(WidgetLayout.MAX_NEARBY, WidgetLayout.plan(234f, 294f).nearbyRows)
    }

    @Test
    fun `more height never takes anything away`() {
        var last = WidgetLayout.plan(234f, 140f)
        var h = 141f
        while (h <= 450f) {
            val p = WidgetLayout.plan(234f, h)
            assertTrue("nearby at $h", p.nearbyRows >= last.nearbyRows)
            last = p
            h += 1f
        }
        // And narrow ones grow the message before anything else.
        val narrow = WidgetLayout.plan(156f, 196f)
        assertTrue(narrow.messageLines >= 2)
    }

    @Test
    fun `a 2x1 is a row, and a tall one draws larger`() {
        val short = WidgetLayout.plan(156f, 60f)
        assertTrue(short.row)
        assertFalse(short.roomyRow)
        assertFalse(short.largeRow)
        val tall = WidgetLayout.plan(156f, 98f)
        assertTrue(tall.row)
        assertTrue(tall.roomyRow)
        assertTrue(tall.largeRow)
    }

    @Test
    fun `the widget takes its real size, not breakpoints`() {
        val src = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
        assertTrue(src.contains("override val sizeMode = SizeMode.Exact"))
        assertTrue(src.contains("WidgetLayout.plan(size.width.value, size.height.value)"))
    }
}
