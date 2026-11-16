package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.ui.model.InternalChannel
import io.github.thatsfguy.meshcore.platform.androidCryptoProvider
import io.github.thatsfguy.meshcore.presentation.ChannelSetup
import io.github.thatsfguy.meshcore.presentation.DmRequest
import io.github.thatsfguy.meshcore.protocol.ChannelCrypto
import java.io.File
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** #mcn-internal (2026-11-16): where it goes, what it surfaces, and that it shows nowhere. */
class InternalChannelTest {

    private val me = "11".repeat(32)
    private val marta = "aa".repeat(32)
    private val pau = "bb".repeat(32)
    private val now = 2_000_000_000L

    @Test
    fun `the name is a valid hashtag and the key is the hashtag's`() {
        assertEquals(ChannelSetup.Result.Ok(InternalChannel.NAME), ChannelSetup.hashtag(InternalChannel.NAME))
        val crypto = androidCryptoProvider()
        assertArrayEquals(ChannelCrypto.hashtagPsk(crypto, "#mcn-internal"), InternalChannel.psk(crypto))
        assertTrue(InternalChannel.isNamed(" #MCN-Internal "))
        assertFalse(InternalChannel.isNamed("#mcn-internals"))
    }

    @Test
    fun `it takes the highest free slot`() {
        assertEquals(39, InternalChannel.slotFor(setOf(0, 1, 2), 40))
        assertEquals(37, InternalChannel.slotFor(setOf(0, 38, 39), 40))
        assertEquals(7, InternalChannel.slotFor(setOf(0, 1), 8))
        assertEquals(null, InternalChannel.slotFor((0 until 8).toSet(), 8))
    }

    private fun msg(sender: String, text: String, ts: Long, outgoing: Boolean = false) = MessageEntity(
        selfKey = me, kind = "ch", peerKey = "39", senderName = sender, text = text, timestamp = ts,
        receivedAt = ts, outgoing = outgoing, status = 0, ackHash = null, contentKey = null, snr = null,
    )

    @Test
    fun `requests to us surface once per asker, newest kept, oldest asker first`() {
        val messages = listOf(
            msg("Pau", DmRequest.request("Ana", pau)!!, now - 50),
            msg("Marta", DmRequest.request("Ana", marta)!!, now - 100),
            msg("Marta", DmRequest.request("ana", marta)!!, now - 10),
            // Not to us, our own, too old, and not a request at all: none of them surface.
            msg("Joan", DmRequest.request("Bea", pau)!!, now - 5),
            msg("Ana", DmRequest.request("Ana", me)!!, now - 5, outgoing = true),
            msg("Old", DmRequest.request("Ana", "cc".repeat(32))!!, now - DmRequest.Outstanding.LIFETIME_SEC - 1),
            msg("Marta", "hello", now - 1),
        )
        val pending = InternalChannel.pending(messages, "Ana", me, handled = emptySet(), nowSec = now)
        assertEquals(listOf(pau, marta), pending.map { it.keyHex })
        assertEquals(now - 10, pending.last().atSeconds)
        // Answered ones go.
        assertEquals(listOf(marta), InternalChannel.pending(messages, "Ana", me, setOf(pau), now).map { it.keyHex })
    }

    @Test
    fun `a name is confirmed only by the same key under the same name`() {
        val known = listOf(marta to "Marta", pau to "Pau")
        assertTrue(InternalChannel.nameConfirmed(marta, "marta", known))
        // Someone else's key wearing Marta's name, and Marta's key under another name.
        assertFalse(InternalChannel.nameConfirmed(pau, "Marta", known))
        assertFalse(InternalChannel.nameConfirmed(marta, "Mum", known))
        assertFalse(InternalChannel.nameConfirmed(marta, " ", known))
    }

    // ---- wiring: it shows nowhere, and is never added by hand ----

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()
    private val vm = src("ui/MeshCoreViewModel.kt")

    @Test
    fun `every channel list and the chat list leave it out`() {
        assertTrue(vm.substringAfter("val dbChannels: StateFlow").substringBefore(".stateIn(").contains("all.filter { it.idx !in internal }"))
        assertTrue(vm.substringAfter("val conversations: StateFlow").substringBefore(".stateIn(").contains("it.key.toIntOrNull() in internal"))
        val svc = src("service/MeshCoreService.kt")
        val notify = svc.substringAfter("private fun postMessageNotification(").substringBefore("val title =")
        assertTrue(notify.contains("in internalSlots()"))
        assertTrue(svc.contains("val chs = allChs.filter { it.idx !in internal }"))
    }

    @Test
    fun `it is joined by the app only, after the sweep, in the highest free slot`() {
        val join = vm.substringAfter("// #mcn-internal (ui/model/InternalChannel)").substringBefore("private fun isInternalChannel(")
        assertTrue(join.indexOf("svc.engine.channelsKnown") in 0 until join.indexOf("svc.engine.setChannel("))
        assertTrue(join.contains("InternalChannel.slotFor("))
        assertTrue(join.contains("R.string.vm_internal_no_slot"))
        assertTrue(join.contains("R.string.vm_internal_failed"))
        val add = vm.substringAfter("private fun addChannelWithKey(name: String, psk: ByteArray, regionScope: String, origin: ScanOrigin) {")
        assertTrue(add.substringBefore("viewModelScope.launch").contains("isInternalChannel(name, psk)"))
        assertTrue(vm.substringAfter("private fun offerChannel(").substringBefore("\n    }\n").contains("isInternalChannel(share.name, psk)"))
        assertTrue(src("ui/screens/SetupScreen.kt").contains("R.string.onb_internal_note"))
    }

    @Test
    fun `the tab bar belongs to each tab screen, told its own route`() {
        for ((file, route) in listOf("ChatsScreen.kt" to "chats", "NodesScreen.kt" to "nodes", "MapScreen.kt" to "map", "SettingsScreen.kt" to "settings")) {
            assertTrue(file, src("ui/screens/$file").contains("LocalTabBar.current(\"$route\")"))
        }
        val shell = src("MainActivity.kt")
        assertFalse(shell.contains("bottomBar = {"))
        assertTrue(shell.contains("selected = route == tab.route"))
    }
}

class QuietSignInTest {
    @Test
    fun `an automatic room sign-in is not announced, a tapped one still is`() {
        val vm = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()
        val handler = vm.substringAfter("is MeshEvent.LoginResult -> {").substringBefore("else -> Unit")
        assertTrue(handler.contains("quietLogins.any { it.startsWith(prefix) }"))
        assertTrue(handler.contains("if (!quiet) {"))
        val quiet = vm.substringAfter("private suspend fun signInQuietly(").substringBefore("\n    }\n")
        assertTrue(quiet.indexOf("quietLogins += ") in 0 until quiet.indexOf("performLogin("))
        // The dialog's sign-in is not quiet: it goes through repeaterLogin, which never adds.
        assertFalse(vm.substringAfter("fun repeaterLogin(").substringBefore("\n    }\n").contains("quietLogins"))
    }
}
