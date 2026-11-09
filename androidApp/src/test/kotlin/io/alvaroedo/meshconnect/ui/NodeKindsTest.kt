package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.NodeKinds
import io.github.thatsfguy.meshcore.presentation.HeardNode
import io.github.thatsfguy.meshcore.presentation.NodeTab
import io.github.thatsfguy.meshcore.protocol.Codes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeKindsTest {

    private data class H(
        override val keyHex: String,
        override val name: String,
        override val type: Int,
        override val lastHeardAt: Long,
    ) : HeardNode

    private val heard = listOf(
        H("aa", "Alice", Codes.ADV_TYPE_CHAT, 10),
        H("bb", "Hilltop", Codes.ADV_TYPE_REPEATER, 30),
        H("cc", "Club room", Codes.ADV_TYPE_ROOM, 20),
        H("dd", "Weather", Codes.ADV_TYPE_SENSOR, 40),
    )

    @Test
    fun `all merges every kind newest first`() {
        val all = NodeKinds.heard(null, heard, "", filtersActive = false, hidden = false)
        assertEquals(listOf("dd", "bb", "cc", "aa"), all.map { it.keyHex })
    }

    @Test
    fun `one kind shows only that kind`() {
        val rep = NodeKinds.heard(NodeTab.Repeaters, heard, "", false, false)
        assertEquals(listOf("bb"), rep.map { it.keyHex })
    }

    @Test
    fun `all still honours search, filters and hiding`() {
        assertEquals(listOf("bb"), NodeKinds.heard(null, heard, "hill", false, false).map { it.keyHex })
        assertTrue(NodeKinds.heard(null, heard, "", filtersActive = true, hidden = false).isEmpty())
        assertTrue(NodeKinds.heard(null, heard, "", filtersActive = false, hidden = true).isEmpty())
    }

    @Test
    fun `saved form round-trips, and junk falls back to all`() {
        assertNull(NodeKinds.fromSaved(-1))
        for (t in NodeTab.entries) assertEquals(t, NodeKinds.fromSaved(NodeKinds.toSaved(t)))
        assertEquals(-1, NodeKinds.toSaved(null))
        assertNull(NodeKinds.fromSaved(99))
    }

    @Test
    fun `matches follows upstream's tab mapping`() {
        assertTrue(NodeKinds.matches(null, Codes.ADV_TYPE_SENSOR))
        assertTrue(NodeKinds.matches(NodeTab.Contacts, 0)) // unknown types sit with contacts
        assertEquals(false, NodeKinds.matches(NodeTab.Rooms, Codes.ADV_TYPE_REPEATER))
    }
}
