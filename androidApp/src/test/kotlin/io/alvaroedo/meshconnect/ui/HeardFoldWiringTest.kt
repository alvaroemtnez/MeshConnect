package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Heard-but-not-added nodes are a fold-out first row of the Nodes list, tucked just above
 * the top. They were revealed by pull-to-refresh, whose spinner promised a refresh that
 * never happened.
 */
class HeardFoldWiringTest {

    private val s = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/NodesScreen.kt").readText()

    @Test
    fun `no pull-to-refresh on the Nodes list`() {
        assertFalse(s.contains("PullToRefreshBox"))
    }

    @Test
    fun `the list opens one row down so the heard row starts out of sight`() {
        assertTrue(s.contains("rememberLazyListState(initialFirstVisibleItemIndex = if (heard.isNotEmpty()) 1 else 0)"))
        assertTrue(s.contains("onToggle = { heardRevealed = !heardRevealed }"))
    }
}
