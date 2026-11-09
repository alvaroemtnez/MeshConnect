package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.ChatListModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChatListModelTest {

    private fun row(kind: String, key: String, unread: Int = 0) =
        ConversationRow(kind, key, key, "", 0, unread, kind == "ch", null)

    private val a = row("dm", "aa11")
    private val b = row("dm", "bb22", unread = 3)
    private val pub = row("ch", "0")

    @Test
    fun `pinned on top, archived out of the list`() {
        val s = ChatListModel.split(listOf(a, b, pub), pinned = setOf("ch|0"), archived = setOf("dm|bb22"))
        assertEquals(listOf(pub), s.pinned)
        assertEquals(listOf(a), s.recent)
        assertEquals(listOf(b), s.archived)
        assertTrue("an unread archived chat shows a dot", s.archivedUnread)
    }

    @Test
    fun `a chat in both sets is archived, never shown twice`() {
        val s = ChatListModel.split(listOf(a), pinned = setOf("dm|aa11"), archived = setOf("dm|aa11"))
        assertEquals(emptyList<ConversationRow>(), s.pinned)
        assertEquals(listOf(a), s.archived)
    }

    @Test
    fun `archiving unpins, unarchiving does not re-pin`() {
        val (p1, a1) = ChatListModel.archive("dm|aa11", true, pinned = setOf("dm|aa11"), archived = emptySet())
        assertEquals(emptySet<String>(), p1)
        assertEquals(setOf("dm|aa11"), a1)
        val (p2, a2) = ChatListModel.archive("dm|aa11", false, p1, a1)
        assertEquals(emptySet<String>(), p2)
        assertEquals(emptySet<String>(), a2)
    }

    @Test
    fun `nothing unread in the archive, no dot`() {
        assertFalse(ChatListModel.split(listOf(a), emptySet(), setOf("dm|aa11")).archivedUnread)
    }

    @Test
    fun `backup keys round-trip and hostile entries are dropped`() {
        val keys = setOf("dm|aa11", "ch|0", "dm|BB22")
        assertEquals(keys, ChatListModel.parseKeys(ChatListModel.encodeKeys(keys)))
        val hostile = "dm|aa11\n\nDM|zz\n|00\ndm|${"a".repeat(65)}\nch|1;drop\nverylongkindname|00\n  ch|2  "
        assertEquals(setOf("dm|aa11", "ch|2"), ChatListModel.parseKeys(hostile))
        assertEquals(3, ChatListModel.parseKeys((1..10).joinToString("\n") { "ch|$it" }, max = 3).size)
    }
}
