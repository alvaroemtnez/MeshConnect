package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.ui.model.ChatThreads
import io.github.thatsfguy.meshcore.protocol.Codes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The widget's unread number is the Chats tab's number (reported on 1.2.1-beta.1: the widget
 * showed more unread than the app). See ChatThreads for the history.
 */
class ChatThreadsTest {

    private val self = "11".repeat(32)
    private val alice = "aa".repeat(32)
    private val repeater = "bb".repeat(32)
    private val room = "cc".repeat(32)

    private fun contact(key: String, type: Int, unread: Int) = ContactEntity(
        selfKey = self, keyHex = key, name = key.take(4), type = type, flags = 0, pathLen = 0,
        latitude = null, longitude = null, lastSeen = 0, lastModified = 0, unread = unread,
    )

    private fun channel(idx: Int, unread: Int) =
        ChannelEntity(self, idx, "ch$idx", ByteArray(0), unread = unread)

    private fun msg(kind: String, peer: String, ts: Long = 100, outgoing: Boolean = false, txtType: Int = 0) =
        MessageEntity(
            selfKey = self, kind = kind, peerKey = peer, senderName = null, text = "t", timestamp = ts,
            receivedAt = ts, outgoing = outgoing, status = 0, ackHash = null, contentKey = null,
            snr = null, txtType = txtType,
        )

    @Test
    fun `a repeater's console replies are not counted`() {
        // The defect itself: 7 unread on a repeater (its console) made the widget say 9.
        val contacts = listOf(contact(alice, Codes.ADV_TYPE_CHAT, 2), contact(repeater, Codes.ADV_TYPE_REPEATER, 7))
        val latest = listOf(msg("dm", alice), msg("dm", repeater, txtType = 1))
        assertEquals(2, ChatThreads.unreadTotal(latest, contacts, emptyList()))
    }

    @Test
    fun `every row the list shows is counted`() {
        // Positive control: a rule that counted nothing would pass the test above with 0 unread.
        // A chat, a room server, a channel with messages and a channel with none yet.
        val contacts = listOf(contact(alice, Codes.ADV_TYPE_CHAT, 2), contact(room, Codes.ADV_TYPE_ROOM, 4))
        val channels = listOf(channel(0, 3), channel(1, 1))
        val latest = listOf(msg("dm", alice), msg("dm", room), msg("ch", "0"))
        assertEquals(10, ChatThreads.unreadTotal(latest, contacts, channels))
    }

    @Test
    fun `a contact with no thread is not counted`() {
        // Retention deleted the messages but left the count: the list has no row for it.
        val contacts = listOf(contact(alice, Codes.ADV_TYPE_CHAT, 5))
        assertEquals(0, ChatThreads.unreadTotal(emptyList(), contacts, emptyList()))
    }

    @Test
    fun `a thread whose contact is gone is a row with nothing unread`() {
        val latest = listOf(msg("dm", alice))
        assertEquals(listOf(0), ChatThreads.unreadCounts(latest, emptyList(), emptyList()))
        assertTrue(ChatThreads.isChatPeer(null))
    }

    @Test
    fun `a damaged negative count is not a credit`() {
        val contacts = listOf(contact(alice, Codes.ADV_TYPE_CHAT, -3))
        val channels = listOf(channel(0, 4))
        assertEquals(4, ChatThreads.unreadTotal(listOf(msg("dm", alice)), contacts, channels))
    }

    @Test
    fun `the latest message leaves out repeater threads`() {
        // The widget's "last message" line must not be a console reply either.
        val contacts = listOf(contact(alice, Codes.ADV_TYPE_CHAT, 0), contact(repeater, Codes.ADV_TYPE_REPEATER, 0))
        val latest = listOf(msg("dm", alice, ts = 100), msg("dm", repeater, ts = 200, txtType = 1), msg("ch", "0", ts = 50))
        val kept = ChatThreads.chatLatest(latest, contacts.associateBy { it.keyHex })
        assertEquals(listOf(alice, "0"), kept.map { it.peerKey })
    }

    // --- wiring: both counts go through the rule ---

    private val service = File("src/main/kotlin/io/alvaroedo/meshconnect/service/MeshCoreService.kt").readText()
    private val viewModel = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()

    @Test
    fun `the widget counts with the shared rule, not a sum of every row`() {
        assertTrue(service.contains("ChatThreads.unreadTotal(latest, cs, chs)"))
        assertTrue(service.contains(".chatLatest(latest,"))
        assertFalse("the service must not sum unread counts itself", service.contains("sumOf { it.unread }"))
    }

    @Test
    fun `the chats list leaves repeaters out by the same rule`() {
        assertTrue(viewModel.contains("ChatThreads.isChatPeer(c)"))
    }
}
