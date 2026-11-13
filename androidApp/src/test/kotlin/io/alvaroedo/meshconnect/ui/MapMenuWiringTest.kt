package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map's top bar keeps "what is shown" and "what to do" apart (2026-11-13), as Nodes
 * does. They shared the ⋮, and "Show the whole network" — a zoom — sat under "Only
 * contacts" and read as the way to undo it.
 */
class MapMenuWiringTest {

    private val s = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/MapScreen.kt").readText()
    private val overflow = s.substringAfter("menuActions = listOf(").substringBefore("\n                ),")
    private val filter = s.substringAfter("private fun MapFilterMenu(")

    @Test
    fun `the overflow holds actions only, each with an icon`() {
        val actions = Regex("""MenuAction\(""").findAll(overflow).count()
        assertTrue("expected the four actions, got $actions", actions == 4)
        assertTrue(Regex("""MenuAction\([^\n]*icon = R\.drawable\.""").findAll(overflow).count() == 4)
        for (action in listOf("map_fit_all", "map_export_gpx", "map_load_tiles", "map_clear_cache")) {
            assertTrue(action, overflow.contains("R.string.$action"))
        }
        for (visibility in listOf("map_labels", "map_hide_links", "toggleType", "kind_contacts")) {
            assertFalse(visibility, overflow.contains(visibility))
        }
    }

    @Test
    fun `the filter menu holds kinds, labels and links, in sections`() {
        assertTrue(s.contains("MapFilterMenu("))
        assertTrue(filter.contains("MenuHeading(stringResource(R.string.nodes_show_only))"))
        assertTrue(filter.contains("MenuHeading(stringResource(R.string.map_on_map))"))
        assertTrue(filter.contains("R.string.map_labels"))
        assertTrue(filter.contains("R.string.map_hide_links"))
        assertTrue(filter.contains("R.string.nodes_clear_filters"))
        assertFalse("a zoom is not a filter", filter.contains("map_fit_all"))
        for (kind in listOf("ADV_TYPE_CHAT", "ADV_TYPE_REPEATER", "ADV_TYPE_ROOM", "ADV_TYPE_SENSOR")) {
            assertTrue(kind, s.substringAfter("private val MAP_KINDS").contains(kind))
        }
    }

    @Test
    fun `kind toggles stack, so they leave the menu open`() {
        val kindRow = filter.substringAfter("for ((type, label) in MAP_KINDS)").substringBefore("HorizontalDivider()")
        assertTrue(kindRow.contains("onClick = { onToggleType(type) }"))
        assertFalse(kindRow.contains("onDismiss"))
    }

    @Test
    fun `a filter hiding every pin says so instead of claiming nobody has a position`() {
        assertTrue(s.contains("if (anyPlaced && typeFilter.isNotEmpty()) R.string.map_empty_filtered else R.string.map_empty"))
    }
}
