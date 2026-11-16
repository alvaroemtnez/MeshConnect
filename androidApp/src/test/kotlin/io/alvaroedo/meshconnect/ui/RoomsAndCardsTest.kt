package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.ui.model.BatteryAlarm
import io.alvaroedo.meshconnect.ui.model.NewChatList
import io.alvaroedo.meshconnect.ui.model.RoomAutoSignIn
import io.alvaroedo.meshconnect.ui.model.ScanFollowUp
import io.github.thatsfguy.meshcore.presentation.AdminSession
import io.github.thatsfguy.meshcore.presentation.AppMode
import io.github.thatsfguy.meshcore.protocol.Codes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Room sign-in, favourite groups, nicknames, confirmations and the battery glyph (2026-11-16). */
class RoomsAndCardsTest {

    private val self = "11".repeat(32)
    private fun contact(key: String, type: Int, name: String = key.take(4)) = ContactEntity(
        selfKey = self, keyHex = key, name = name, type = type, flags = 0, pathLen = 0,
        latitude = null, longitude = null, lastSeen = 0, lastModified = 0,
    )

    private val room1 = "aa".repeat(32)
    private val room2 = "bb".repeat(32)
    private val person = "cc".repeat(32)
    private val contacts = listOf(
        contact(room1, Codes.ADV_TYPE_ROOM), contact(room2, Codes.ADV_TYPE_ROOM), contact(person, Codes.ADV_TYPE_CHAT),
    )

    @Test
    fun `only rooms not signed in, with a saved password, are signed in to`() {
        val saved = setOf(room1, room2, person)
        assertEquals(
            listOf(room2),
            RoomAutoSignIn.candidates(true, contacts, mapOf(room1 to AdminSession.Member)) { it in saved },
        )
        // No saved password: left to the banner.
        assertEquals(listOf(room1), RoomAutoSignIn.candidates(true, contacts, emptyMap()) { it == room1 })
        // Switched off: nothing at all.
        assertEquals(emptyList<String>(), RoomAutoSignIn.candidates(false, contacts, emptyMap()) { true })
        // Positive control: both, when both qualify.
        assertEquals(listOf(room1, room2), RoomAutoSignIn.candidates(true, contacts, emptyMap()) { true })
    }

    @Test
    fun `a favourite group is listed under Favourites, and a renamed contact by its nickname`() {
        val list = NewChatList.entries(
            AppMode.Full,
            listOf(contact(person, Codes.ADV_TYPE_CHAT, "Marta")),
            listOf(ChannelEntity(self, 3, "Family", ByteArray(0)), ChannelEntity(self, 4, "Work", ByteArray(0))),
            emptyMap(),
            nicknames = mapOf(person to "Mum"),
            favouriteChannels = setOf(3),
        )
        val sections = NewChatList.sections(list)
        assertEquals(listOf("Family"), sections.first().entries.map { it.title })
        assertEquals(null, sections.first().letter)
        assertTrue(list.any { it.title == "Mum" })
        assertFalse(list.any { it.title == "Marta" })
    }

    @Test
    fun `simple mode never retunes the radio`() {
        assertTrue(ScanFollowUp.refusesRadioSettings(AppMode.Simple))
        assertFalse(ScanFollowUp.refusesRadioSettings(AppMode.Full))
        val vm = src("ui/MeshCoreViewModel.kt").substringAfter("fun importContactUri(").substringBefore("\n    }\n")
        assertTrue(vm.indexOf("ScanFollowUp.refusesRadioSettings(") in 0 until vm.indexOf("pendingRadioConfig.value = decoded"))
    }

    @Test
    fun `the battery turns red at 15 and blinks at 10`() {
        assertFalse(BatteryAlarm.isLow(16))
        assertTrue(BatteryAlarm.isLow(15))
        assertFalse(BatteryAlarm.blinks(11))
        assertTrue(BatteryAlarm.blinks(10))
        assertFalse(BatteryAlarm.isLow(null))
    }

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()

    @Test
    fun `every delete, remove, block and clear asks first`() {
        val sheets = src("ui/screens/ChannelSheets.kt")
        assertFalse("removing a channel straight from the row", sheets.substringAfter("R.string.ch_remove").substringBefore("}").contains("vm.deleteChannel("))
        assertTrue(sheets.contains("ConfirmDestructive("))
        for (card in listOf("ui/screens/ContactCards.kt", "ui/screens/NodesScreen.kt")) {
            val s = src(card)
            assertTrue(card, s.contains("if (blocked) vm.setBlocked(contact.keyHex, false) else blockConfirm = true"))
        }
        val conv = src("ui/screens/ConversationScreen.kt")
        assertTrue(conv.contains("onDelete = { confirmDeleteMany = setOf(m.id) },"))
        assertFalse(conv.contains("vm.deleteMessage(picked.single())"))
        assertTrue(conv.contains("ConfirmDestructive("))
    }

    @Test
    fun `rename is a nickname everywhere a contact is named`() {
        val cards = src("ui/screens/ContactCards.kt")
        val rename = cards.substringAfter("internal fun ContactRenameDialog(").substringBefore("\n}\n")
        assertTrue(rename.contains("vm.setNickname("))
        assertFalse(rename.contains("renameContact("))
        assertTrue(src("ui/screens/NodesScreen.kt").contains("if (renameOpen) ContactRenameDialog("))
        assertTrue(src("ui/screens/ConversationScreen.kt").contains("vm.nicknames.collectAsState().value[peerKey]"))
        assertTrue(src("ui/screens/NodesScreen.kt").contains("nickname = nicknames[c.keyHex],"))
        assertFalse(src("ui/screens/ChatsScreen.kt").contains("NicknameDialog"))
    }

    @Test
    fun `the ongoing notification carries the battery`() {
        val svc = src("service/MeshCoreService.kt")
        assertTrue(svc.contains("getString(R.string.svc_connected_battery, label ?: \"\", batteryPercent)"))
        assertTrue(svc.contains("combine(engine.state, engine.battery)"))
    }

    @Test
    fun `channel kinds are drawn from memory first, then from the keys`() {
        val kinds = src("ui/MeshCoreViewModel.kt").substringAfter("val channelKinds:").substringBefore(".stateIn(")
        assertTrue(kinds.indexOf("emit(remembered)") in 0 until kinds.indexOf("secrets.unsealPsk("))
        assertTrue(kinds.contains("prefs.rememberChannelKinds(key, kinds)"))
    }
}
