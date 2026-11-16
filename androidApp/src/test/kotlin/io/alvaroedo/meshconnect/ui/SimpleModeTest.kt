package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.storage.MessageRepository
import io.alvaroedo.meshconnect.ui.model.ChatThreads
import io.alvaroedo.meshconnect.ui.model.NewChatList
import io.alvaroedo.meshconnect.ui.model.SimpleMode
import io.github.thatsfguy.meshcore.presentation.AppMode
import io.github.thatsfguy.meshcore.protocol.ChannelKind
import io.github.thatsfguy.meshcore.protocol.Codes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MeshConnect's Simple mode rules (ui/model/SimpleMode): contacts, groups and rooms; no channels
 * anyone can join. Every hiding rule here is paired with the case that must still show, since
 * a rule that hid everything would pass the hiding half on its own.
 */
class SimpleModeTest {

    private val full = AppMode.Full
    private val simple = AppMode.Simple

    private val self = "11".repeat(32)
    private val alice = "aa".repeat(32)
    private val repeater = "bb".repeat(32)
    private val room = "cc".repeat(32)
    private val sensor = "dd".repeat(32)

    private fun contact(key: String, type: Int, name: String = key.take(4), unread: Int = 0) = ContactEntity(
        selfKey = self, keyHex = key, name = name, type = type, flags = 0, pathLen = 0,
        latitude = null, longitude = null, lastSeen = 0, lastModified = 0, unread = unread,
    )

    private fun channel(idx: Int, name: String = "ch$idx", unread: Int = 0) =
        ChannelEntity(self, idx, name, ByteArray(0), unread = unread)

    private fun msg(kind: String, peer: String, ts: Long = 100) = MessageEntity(
        selfKey = self, kind = kind, peerKey = peer, senderName = null, text = "t", timestamp = ts,
        receivedAt = ts, outgoing = false, status = 0, ackHash = null, contentKey = null, snr = null,
    )

    // --- Which threads ----------------------------------------------------------------------

    @Test
    fun `the full app shows every thread`() {
        for (kind in ChannelKind.entries + listOf(null)) {
            assertTrue(SimpleMode.showsThread(full, true, kind, null))
        }
        for (type in listOf(Codes.ADV_TYPE_CHAT, Codes.ADV_TYPE_REPEATER, Codes.ADV_TYPE_ROOM, Codes.ADV_TYPE_SENSOR, null)) {
            assertTrue(SimpleMode.showsThread(full, false, null, type))
        }
    }

    @Test
    fun `simple mode hides channels anyone can join and keeps groups`() {
        assertFalse(SimpleMode.showsThread(simple, true, ChannelKind.Public, null))
        assertFalse(SimpleMode.showsThread(simple, true, ChannelKind.Hashtag, null))
        // The group itself, and a slot whose key could not be read: shown, not guessed at.
        assertTrue(SimpleMode.showsThread(simple, true, ChannelKind.Private, null))
        assertTrue(SimpleMode.showsThread(simple, true, null, null))
    }

    @Test
    fun `simple mode shows people and rooms, not consoles or sensors`() {
        assertTrue(SimpleMode.showsThread(simple, false, null, Codes.ADV_TYPE_CHAT))
        // Upstream's AppModes hides rooms; MeshConnect's Simple mode is contacts, groups and rooms.
        assertTrue(SimpleMode.showsThread(simple, false, null, Codes.ADV_TYPE_ROOM))
        assertTrue(SimpleMode.showsThread(simple, false, null, null))
        assertFalse(SimpleMode.showsThread(simple, false, null, Codes.ADV_TYPE_REPEATER))
        assertFalse(SimpleMode.showsThread(simple, false, null, Codes.ADV_TYPE_SENSOR))
    }

    @Test
    fun `adverts and the advanced tile belong to the full app`() {
        assertTrue(SimpleMode.showsAdverts(full))
        assertFalse(SimpleMode.showsAdverts(simple))
        assertTrue(SimpleMode.showsSettingsTile(full, "advanced"))
        assertFalse(SimpleMode.showsSettingsTile(simple, "advanced"))
        // Appearance and alerts, which now holds the switch back, stays.
        assertTrue(SimpleMode.showsSettingsTile(simple, "app"))
        assertTrue(SimpleMode.showsSettingsTile(simple, "channels"))
    }

    @Test
    fun `groups and rooms share one avatar in simple mode, people keep initials`() {
        assertTrue(SimpleMode.groupAvatar(simple, isChannel = true, contactType = null))
        assertTrue(SimpleMode.groupAvatar(simple, isChannel = false, contactType = Codes.ADV_TYPE_ROOM))
        assertFalse(SimpleMode.groupAvatar(simple, isChannel = false, contactType = Codes.ADV_TYPE_CHAT))
        // The full app keeps its lock, tag, globe and room badge.
        assertFalse(SimpleMode.groupAvatar(full, isChannel = true, contactType = null))
        assertFalse(SimpleMode.groupAvatar(full, isChannel = false, contactType = Codes.ADV_TYPE_ROOM))
    }

    // --- The widget's count and last message (ChatThreads) -------------------------------------

    private val kinds = mapOf(0 to ChannelKind.Public, 1 to ChannelKind.Private, 2 to ChannelKind.Hashtag)
    private val channels = listOf(channel(0, "Public", 5), channel(1, "Family", 2), channel(2, "#test", 3))
    private val contacts = listOf(contact(alice, Codes.ADV_TYPE_CHAT, unread = 1), contact(room, Codes.ADV_TYPE_ROOM, unread = 4))
    private val latest = listOf(
        msg("ch", "0", ts = 900),
        msg("ch", "1", ts = 300),
        msg("ch", "2", ts = 800),
        msg("dm", alice, ts = 200),
        msg("dm", room, ts = 100),
    )

    @Test
    fun `the widget counts what simple mode shows`() {
        // Family 2, Alice 1, the room 4 — not Public's 5 nor the hashtag's 3.
        assertEquals(7, ChatThreads.unreadTotal(latest, contacts, channels, simple, kinds))
        // Positive control: the full app counts all of it, and so does the old call shape.
        assertEquals(15, ChatThreads.unreadTotal(latest, contacts, channels, full, kinds))
        assertEquals(15, ChatThreads.unreadTotal(latest, contacts, channels))
    }

    @Test
    fun `the widget's last message comes from a thread simple mode shows`() {
        val byKey = contacts.associateBy { it.keyHex }
        val newest = ChatThreads.chatLatest(latest, byKey, simple, kinds).maxByOrNull { it.timestamp }
        assertEquals("1", newest?.peerKey)
        assertEquals("0", ChatThreads.chatLatest(latest, byKey, full, kinds).maxByOrNull { it.timestamp }?.peerKey)
    }

    // --- New chat's list -----------------------------------------------------------------------

    private val known = listOf(
        contact(alice, Codes.ADV_TYPE_CHAT, "alice"),
        contact(room, Codes.ADV_TYPE_ROOM, "Bakery room"),
        contact(repeater, Codes.ADV_TYPE_REPEATER, "Hilltop"),
        contact(sensor, Codes.ADV_TYPE_SENSOR, "Weather"),
    )
    private val slots = listOf(channel(0, "Public"), channel(1, "Family"), channel(2, "#cats"), channel(3, ""))

    @Test
    fun `new chat in simple mode lists people, rooms and groups alphabetically`() {
        val list = NewChatList.entries(simple, known, slots, kinds + (3 to ChannelKind.Private))
        assertEquals(listOf("alice", "Bakery room", "Family"), list.map { it.title })
        assertEquals(listOf(MessageRepository.KIND_DM, MessageRepository.KIND_DM, MessageRepository.KIND_CHANNEL), list.map { it.kind })
        // The conversation route's key: the contact key, or the channel's slot.
        assertEquals(listOf(alice, room, "1"), list.map { it.key })
    }

    @Test
    fun `new chat in the full app lists every channel, a hashtag by its name`() {
        val list = NewChatList.entries(full, known, slots, kinds)
        // "#cats" sorts under c, not before everything under '#'. No repeater, sensor or empty slot.
        assertEquals(listOf("alice", "Bakery room", "#cats", "Family", "Public"), list.map { it.title })
    }

    @Test
    fun `new chat search matches any part of a name, ignoring case`() {
        assertEquals(listOf("Bakery room"), NewChatList.entries(full, known, slots, kinds, query = "ROOM").map { it.title })
        assertEquals(emptyList<String>(), NewChatList.entries(simple, known, slots, kinds, query = "cats").map { it.title })
    }
}

class AgoTest {
    @Test
    fun `heard since the clock last ticked is just now, not in 0 minutes`() {
        val now = 1_000_000L
        assertTrue(io.alvaroedo.meshconnect.ui.model.Ago.isJustNow(now + 20_000, now))
        assertTrue(io.alvaroedo.meshconnect.ui.model.Ago.isJustNow(now - 59_999, now))
        // Positive control: a minute old is no longer "just now".
        assertFalse(io.alvaroedo.meshconnect.ui.model.Ago.isJustNow(now - 60_000, now))
    }
}
