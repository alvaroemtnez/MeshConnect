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
        assertTrue(s.contains("rememberLazyListState(initialFirstVisibleItemIndex = if (heard.isNotEmpty() || hideHeard) 1 else 0)"))
        assertTrue(s.contains("onToggle = { heardRevealed = !heardRevealed }"))
    }

    @Test
    fun `hiding newcomers puts a quiet reminder in the -1 row, with no Show button`() {
        val list = s.substringAfter("LazyColumn(Modifier.fillMaxSize(), state = listState) {")
        assertTrue(list.trimStart().startsWith("if (hideHeard) {\n                        item(key = \"heard_hidden\") { HiddenHeardRow() }"))
        val row = s.substringAfter("private fun HiddenHeardRow()").substringBefore("\n}\n")
        assertTrue(row.contains("R.string.hiding_not_added"))
        assertTrue(row.contains("FontStyle.Italic"))
        assertTrue(row.contains("TextAlign.Center"))
        assertFalse(row.contains("Button"))
        // And it is no longer a line under the chips.
        assertFalse(s.substringAfter("private fun ActiveFilterNotes(").substringBefore("\n}\n").contains("hiding_not_added"))
        assertFalse(s.contains("onShowHeard"))
    }
}
