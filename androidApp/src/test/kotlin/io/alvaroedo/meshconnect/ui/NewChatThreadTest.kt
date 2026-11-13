package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.MessageRepository
import io.alvaroedo.meshconnect.ui.model.NewChatThread
import io.github.thatsfguy.meshcore.platform.androidCryptoProvider
import io.github.thatsfguy.meshcore.protocol.Advert
import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.util.toHex
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Adding someone or a channel from Chats' New chat opens the conversation
 * (2026-11-13); adding from anywhere else does not.
 */
class NewChatThreadTest {

    private val key = "ab".repeat(32)

    @Test
    fun `a person's card opens their DM, keyed lowercase like the contact table`() {
        assertEquals(MessageRepository.KIND_DM to key, NewChatThread.forContact(key.uppercase(), Codes.ADV_TYPE_CHAT))
    }

    @Test
    fun `infrastructure has no chat to drop into`() {
        assertNull(NewChatThread.forContact(key, Codes.ADV_TYPE_REPEATER))
        assertNull(NewChatThread.forContact(key, Codes.ADV_TYPE_ROOM))
    }

    @Test
    fun `a key of the wrong length names nobody`() {
        assertNull(NewChatThread.forContact("ab".repeat(31), Codes.ADV_TYPE_CHAT))
        assertNull(NewChatThread.forContact("", Codes.ADV_TYPE_CHAT))
    }

    @Test
    fun `a channel opens by slot, including slot 0`() {
        assertEquals(MessageRepository.KIND_CHANNEL to "0", NewChatThread.forChannel(0))
        assertEquals(MessageRepository.KIND_CHANNEL to "5", NewChatThread.forChannel(5))
        assertNull(NewChatThread.forChannel(-1))
    }

    /** A signed advert, wrapped as the packet a `meshcore://<hex>` code carries. */
    private fun advertPacket(type: Int): Pair<ByteArray, String> {
        val crypto = androidCryptoProvider()
        val seed = ByteArray(32) { (it + 1).toByte() }
        val payload = Advert.build(
            crypto,
            seed,
            timestamp = 1_700_000_000L,
            appData = Advert.buildAppData(type, "Alice", null, null),
        )
        val header = (Codes.PAYLOAD_TYPE_ADVERT shl 2) or 0x01
        return (byteArrayOf(header.toByte(), 0) + payload) to crypto.ed25519PublicKey(seed).toHex()
    }

    @Test
    fun `an advert code opens the DM of the key it carries`() {
        val (blob, pub) = advertPacket(Codes.ADV_TYPE_CHAT)
        assertEquals(MessageRepository.KIND_DM to pub, NewChatThread.forAdvert(blob))
    }

    @Test
    fun `a repeater's advert opens nothing, and nor does junk`() {
        assertNull(NewChatThread.forAdvert(advertPacket(Codes.ADV_TYPE_REPEATER).first))
        assertNull(NewChatThread.forAdvert(ByteArray(3)))
        // The bare payload, not a packet: the engine would refuse it, so must this.
        val crypto = androidCryptoProvider()
        val bare = Advert.build(crypto, ByteArray(32) { 7 }, 1L, Advert.buildAppData(Codes.ADV_TYPE_CHAT, "x", null, null))
        assertNull(NewChatThread.forAdvert(bare))
    }

    // ---- wiring: only New chat asks ----

    private val sheets = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ChannelSheets.kt").readText()
    private val chats = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ChatsScreen.kt").readText()
    private val settings = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/SettingsSections.kt").readText()
    private val vm = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()
    private val shell = File("src/main/kotlin/io/alvaroedo/meshconnect/MainActivity.kt").readText()

    @Test
    fun `the sheet's scan, paste and four forms pass newChat through`() {
        assertTrue(sheets.contains("vm.importScannedCode(it, openChat = newChat)"))
        assertTrue(sheets.contains("vm.importPastedText(it, openChat = newChat)"))
        assertTrue(sheets.contains("AddChannelForm(vm, c, openChat = newChat,"))
        for (call in listOf(
            "vm.joinPublicChannel(openChat)",
            "vm.joinHashtagChannel(okName!!, openChat)",
            "vm.createPrivateChannel(okName!!, openChat)",
            "vm.joinPrivateChannel(okName!!, okKey!!, openChat)",
        )) assertTrue(call, sheets.contains(call))
        // newChat is exactly "Chats gave a contact callback", which Settings does not.
        assertTrue(sheets.contains("val newChat = onMessageContact != null"))
    }

    @Test
    fun `Settings and the Chats top-bar scanner never ask`() {
        assertTrue("Settings must not pass a contact callback", settings.contains("ChannelAddSheet(") && !settings.contains("onMessageContact"))
        assertTrue(chats.contains("result?.let { vm.importScannedCode(it) }"))
        assertFalse(chats.contains("openChat"))
    }

    @Test
    fun `the flag crosses the confirm dialog once and dies with a dismiss`() {
        val card = vm.substringAfter("fun confirmContactCard(").substringBefore("fun dismissContactCard(")
        assertTrue(card.contains("pendingOpensChat.also { pendingOpensChat = false }"))
        val share = vm.substringAfter("fun confirmChannelShare(").substringBefore("fun dismissChannelShare(")
        assertTrue(share.contains("pendingOpensChat.also { pendingOpensChat = false }"))
        assertTrue(share.contains("openChat = openChat"))
        for (dismiss in listOf("fun dismissContactCard()", "fun dismissChannelShare()")) {
            assertTrue(dismiss, vm.substringAfter(dismiss).substringBefore("\n    }").contains("pendingOpensChat = false"))
        }
        // Every import resets it before deciding, so a later scan from elsewhere can't inherit it.
        assertTrue(vm.substringAfter("fun importContactUri(").substringBefore("when (").contains("pendingOpensChat = false"))
    }

    @Test
    fun `both channel outcomes open the slot - the new one and the one already held`() {
        val add = vm.substringAfter("private fun addChannelWithKey(").substringBefore("private fun applyChannelScope")
            .ifEmpty { vm.substringAfter("private fun addChannelWithKey(") }
        assertTrue(add.contains("openAfterAdd(openChat, NewChatThread.forChannel(existing.index))"))
        assertTrue(add.contains("openAfterAdd(openChat, NewChatThread.forChannel(idx))"))
    }

    @Test
    fun `the shell navigates the request and clears it`() {
        val effect = shell.substringAfter("LaunchedEffect(requestedThread)").substringBefore("\n    }\n")
        assertTrue(effect.contains("openThread.value = thread"))
        assertTrue(effect.contains("vm.openThreadRequest.value = null"))
    }
}
