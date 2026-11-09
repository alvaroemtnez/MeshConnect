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
}
