package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The logo drawables are generated (scripts/gen_logo.py). A hand-pasted
 * path once shipped as a single "M x,y" token, which draws nothing: the
 * launcher icon, themed icon and notification icons were all blank.
 */
class LogoAssetsTest {
    @Test
    fun `every logo drawable has real geometry`() {
        for (name in listOf("ic_launcher_foreground", "ic_launcher_monochrome", "ic_stat_message", "ic_stat_link")) {
            val xml = File("src/main/res/drawable/$name.xml").readText()
            val d = Regex("""android:pathData="([^"]+)"""").find(xml)?.groupValues?.get(1).orEmpty()
            assertTrue("$name has no path", d.isNotEmpty())
            assertTrue("$name path is too short to be the mark (${d.length} chars)", d.length > 500)
            assertTrue("$name lacks the bubble outline and its cut-out", d.count { it == 'Z' } >= 2)
        }
    }

    @Test
    fun `launcher glyph is modest and sits inside the round mask`() {
        // 52 dp of glyph read as oversized and off-centre on a Pixel 9 Pro (2026-11-09).
        // A Pixel masks to a 72 dp circle; stay well inside it and around the centre.
        val xml = File("src/main/res/drawable/ic_launcher_foreground.xml").readText()
        val d = Regex("""android:pathData="([^"]+)"""").find(xml)!!.groupValues[1]
        val pts = Regex("""([\d.]+),([\d.]+)""").findAll(d).map { it.groupValues[1].toDouble() to it.groupValues[2].toDouble() }.toList()
        val w = pts.maxOf { it.first } - pts.minOf { it.first }
        assertTrue("glyph is $w dp wide", w <= 42.0)
        val far = pts.maxOf { Math.hypot(it.first - 54, it.second - 54) }
        assertTrue("glyph reaches $far dp from the centre", far <= 30.0)
    }

    @Test
    fun `node type icons match the Nodes filter chips`() {
        val pairs = listOf(
            io.github.thatsfguy.meshcore.presentation.NodeTab.Contacts to io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_CHAT,
            io.github.thatsfguy.meshcore.presentation.NodeTab.Repeaters to io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_REPEATER,
            io.github.thatsfguy.meshcore.presentation.NodeTab.Rooms to io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_ROOM,
            io.github.thatsfguy.meshcore.presentation.NodeTab.Sensors to io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_SENSOR,
        )
        for ((tab, type) in pairs) {
            org.junit.Assert.assertEquals(
                "$tab",
                io.alvaroedo.meshconnect.ui.screens.kindIcon(tab),
                io.alvaroedo.meshconnect.platform.NodeMarkers.iconFor(type, isSelf = false),
            )
        }
    }
}
