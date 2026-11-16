package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.MessageRepository
import io.alvaroedo.meshconnect.ui.model.NewChatList
import io.alvaroedo.meshconnect.ui.model.ScanFollowUp
import io.alvaroedo.meshconnect.ui.model.ScanFollowUp.Step
import io.alvaroedo.meshconnect.ui.model.ScanOrigin
import io.github.thatsfguy.meshcore.presentation.AppMode
import io.github.thatsfguy.meshcore.protocol.ChannelKind
import io.github.thatsfguy.meshcore.protocol.Codes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every code ends somewhere you can act on it (2026-11-16): what opens after a scan, a paste or
 * a link, by where it came in and what it is; and what Simple mode turns away.
 */
class ScanFollowUpTest {

    private val key = "ab".repeat(32)

    @Test
    fun `from Chats, people and rooms open their conversation`() {
        assertEquals(Step.OpenChat(MessageRepository.KIND_DM, key), ScanFollowUp.forContact(ScanOrigin.Chats, Codes.ADV_TYPE_CHAT, key))
        assertEquals(Step.OpenChat(MessageRepository.KIND_DM, key), ScanFollowUp.forContact(ScanOrigin.Chats, Codes.ADV_TYPE_ROOM, key))
        // Keyed lowercase, like the contact table.
        assertEquals(Step.OpenChat(MessageRepository.KIND_DM, key), ScanFollowUp.forContact(ScanOrigin.Chats, Codes.ADV_TYPE_CHAT, key.uppercase()))
    }

    @Test
    fun `from Nodes, every node opens its card`() {
        for (type in listOf(Codes.ADV_TYPE_CHAT, Codes.ADV_TYPE_ROOM, Codes.ADV_TYPE_REPEATER, Codes.ADV_TYPE_SENSOR)) {
            assertEquals(Step.OpenCard(key), ScanFollowUp.forContact(ScanOrigin.Nodes, type, key))
        }
    }

    @Test
    fun `a repeater or sensor opens its card from Chats too, having no chat`() {
        assertEquals(Step.OpenCard(key), ScanFollowUp.forContact(ScanOrigin.Chats, Codes.ADV_TYPE_REPEATER, key))
        assertEquals(Step.OpenCard(key), ScanFollowUp.forContact(ScanOrigin.Chats, Codes.ADV_TYPE_SENSOR, key))
    }

    @Test
    fun `a group opens its conversation from Chats and from Nodes, slot 0 included`() {
        assertEquals(Step.OpenChat(MessageRepository.KIND_CHANNEL, "0"), ScanFollowUp.forChannel(ScanOrigin.Chats, 0))
        assertEquals(Step.OpenChat(MessageRepository.KIND_CHANNEL, "5"), ScanFollowUp.forChannel(ScanOrigin.Nodes, 5))
        assertEquals(Step.Nothing, ScanFollowUp.forChannel(ScanOrigin.Chats, -1))
    }

    @Test
    fun `Settings' scanners open nothing, and a bad key names nobody`() {
        assertEquals(Step.Nothing, ScanFollowUp.forContact(ScanOrigin.Elsewhere, Codes.ADV_TYPE_CHAT, key))
        assertEquals(Step.Nothing, ScanFollowUp.forChannel(ScanOrigin.Elsewhere, 3))
        assertEquals(Step.Nothing, ScanFollowUp.forContact(ScanOrigin.Chats, Codes.ADV_TYPE_CHAT, "ab".repeat(31)))
    }

    @Test
    fun `simple mode turns away what it cannot talk to, and only in simple mode`() {
        assertTrue(ScanFollowUp.refusesContact(AppMode.Simple, Codes.ADV_TYPE_REPEATER))
        assertTrue(ScanFollowUp.refusesContact(AppMode.Simple, Codes.ADV_TYPE_SENSOR))
        assertFalse(ScanFollowUp.refusesContact(AppMode.Simple, Codes.ADV_TYPE_CHAT))
        assertFalse(ScanFollowUp.refusesContact(AppMode.Simple, Codes.ADV_TYPE_ROOM))
        assertFalse(ScanFollowUp.refusesContact(AppMode.Full, Codes.ADV_TYPE_REPEATER))
        assertTrue(ScanFollowUp.refusesChannel(AppMode.Simple, ChannelKind.Public))
        assertTrue(ScanFollowUp.refusesChannel(AppMode.Simple, ChannelKind.Hashtag))
        assertFalse(ScanFollowUp.refusesChannel(AppMode.Simple, ChannelKind.Private))
        assertFalse(ScanFollowUp.refusesChannel(AppMode.Full, ChannelKind.Public))
    }

    @Test
    fun `only a person just added is told to ask to be added back`() {
        assertTrue(ScanFollowUp.warnsToAddBack(wasNew = true, type = Codes.ADV_TYPE_CHAT))
        assertFalse(ScanFollowUp.warnsToAddBack(wasNew = false, type = Codes.ADV_TYPE_CHAT))
        assertFalse(ScanFollowUp.warnsToAddBack(wasNew = true, type = Codes.ADV_TYPE_ROOM))
    }

    // ---- New chat's sections ----

    private fun entry(title: String, fav: Boolean = false) =
        NewChatList.Entry(title = title, kind = MessageRepository.KIND_DM, key = title, favourite = fav)

    @Test
    fun `favourites come first, then a section per letter, accents folded, others last`() {
        val list = listOf(entry("Àngels"), entry("alba"), entry("Bea", fav = true), entry("#cats"), entry("3rd floor"), entry("Carla"))
        val sections = NewChatList.sections(list)
        assertEquals(listOf(null, "A", "C", "#"), sections.map { it.letter })
        assertEquals(listOf("Bea"), sections[0].entries.map { it.title })
        assertEquals(listOf("Àngels", "alba"), sections[1].entries.map { it.title })
        assertEquals(listOf("#cats", "Carla"), sections[2].entries.map { it.title })
        // Positive control: with no favourites there is no empty Favourites header.
        assertEquals("A", NewChatList.sections(listOf(entry("alba"))).first().letter)
    }

    // ---- Wiring ----

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()
    private val vm = src("ui/MeshCoreViewModel.kt")

    @Test
    fun `a node or channel already held skips the dialog and goes straight on`() {
        val contact = vm.substringAfter("private fun offerContact(").substringBefore("\n    }\n")
        assertTrue(contact.indexOf("svc.engine.contacts.value[key]") in 0 until contact.indexOf("pendingContactCard.value = card"))
        assertTrue(contact.contains("follow(ScanFollowUp.forContact(origin, known.type, key))"))
        val channel = vm.substringAfter("private fun offerChannel(").substringBefore("\n    }\n")
        assertTrue(channel.indexOf("ChannelList.findByPsk(") in 0 until channel.indexOf("pendingChannelShare.value = share"))
        // A new flood scope still asks first: it changes routing.
        assertTrue(channel.contains("if (held != null && !scopeChanges)"))
    }

    @Test
    fun `simple mode refuses before any dialog, and every decoded code goes through the gates`() {
        val contact = vm.substringAfter("private fun offerContact(").substringBefore("\n    }\n")
        assertTrue(contact.indexOf("ScanFollowUp.refusesContact(") in 0 until contact.indexOf("pendingContactCard.value"))
        val channel = vm.substringAfter("private fun offerChannel(").substringBefore("\n    }\n")
        assertTrue(channel.indexOf("ScanFollowUp.refusesChannel(") in 0 until channel.indexOf("pendingChannelShare.value"))
        val uri = vm.substringAfter("fun importContactUri(").substringBefore("\n    }\n")
        assertTrue(uri.contains("is ShareUri.Decoded.Contact -> offerContact(decoded, origin)"))
        assertTrue(uri.contains("is ShareUri.Decoded.ChannelShare -> offerChannel(decoded, origin)"))
        assertTrue(vm.substringAfter("private fun importAdvertBlob(").substringBefore("\n    }\n").contains("offerContact(card, origin)"))
    }

    @Test
    fun `confirming follows up, warns once for a new person, and the tell-back offer is gone`() {
        val confirm = vm.substringAfter("fun confirmContactCard(").substringBefore("fun dismissContactCard(")
        assertTrue(confirm.contains("follow(ScanFollowUp.forContact(origin, card.type, card.pubKeyHex))"))
        assertTrue(confirm.contains("pendingAddBack.value ="))
        assertFalse(vm.contains("pendingTellBack"))
        assertFalse(src("ui/screens/ScanConfirmations.kt").contains("tellBack"))
    }

    @Test
    fun `every entry point says where it is`() {
        assertTrue(src("ui/screens/ChatsScreen.kt").contains("vm.importScannedCode(it, io.alvaroedo.meshconnect.ui.model.ScanOrigin.Chats)"))
        val nodes = src("ui/screens/NodesScreen.kt")
        assertTrue(nodes.contains("vm.importScannedCode(it, io.alvaroedo.meshconnect.ui.model.ScanOrigin.Nodes)"))
        assertTrue(nodes.contains("vm.importPastedText(it, io.alvaroedo.meshconnect.ui.model.ScanOrigin.Nodes)"))
        // Nodes ⋮: paste, then scan, then my QR.
        val menu = nodes.substringAfter("menuActions = listOf(")
        val paste = menu.indexOf("R.string.nodes_paste_code")
        val scan = menu.indexOf("R.string.nodes_scan_qr")
        val mine = menu.indexOf("R.string.my_qr")
        assertTrue(paste in 0 until scan && scan < mine)
        assertTrue(vm.contains("importScannedCode(offer.link, ScanOrigin.Chats)"))
        assertTrue(src("MainActivity.kt").contains("NodeCardHost(vm, nav)"))
    }

    @Test
    fun `simple mode's long-press has no edit entry, and the conversation menu has details`() {
        assertTrue(src("ui/screens/ChatsScreen.kt").contains("onEdit = if (simple) {"))
        val conv = src("ui/screens/ConversationScreen.kt")
        assertFalse(conv.contains("if (technical) MenuAction(stringResource(R.string.conv_contact_details)"))
        assertTrue(conv.contains("SimpleContactSheet(vm, contact, onDismiss = { showContact = false })"))
    }
}

class RelativeAgeTest {
    @Test
    fun `a time from a radio clock running ahead is never shown as in the future`() {
        val body = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/NodesScreen.kt").readText()
            .substringAfter("internal fun relativeAge(").substringBefore("\n}\n")
        assertTrue(body.contains("minOf(epochSeconds * 1000, now)"))
    }
}
