package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Feedback when a direct-message request is accepted (asked for on the beta, 2026-11-17).
 *
 * Before: the accepter's app added the contact and showed a toast; the requester's app
 * added the accepter silently. Neither side's chat said anything. Now both get a line in
 * the direct thread, the requester a notification too, and the accepter is taken there.
 *
 * Not a direct message from the accepter: the requester's radio drops a direct message
 * from a key it does not hold, and it does not hold the accepter's until the channel reply
 * has been read, so a DM sent alongside it could arrive first and be lost.
 */
class DmAcceptFeedbackTest {

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()

    @Test
    fun `accepting writes the line, opens the chat, and still answers on the channel`() {
        val accept = src("ui/MeshCoreViewModel.kt").substringAfter("fun acceptDmRequest(").substringBefore("fun declineDmRequest(")
        assertTrue(accept.contains("requesterKeyHex, requesterName.trim(), reply, outgoing = true,"))
        assertTrue(accept.contains("openThreadRequest.value = MessageRepository.KIND_DM to asker"))
        assertTrue("the reply that adds us on their radio went missing", accept.contains("sendChannelMessage(channelIndex, reply)"))
        // Only after the add succeeded: no chat with someone the radio refused.
        assertTrue(accept.indexOf("if (!added)") < accept.indexOf("noteDmAccept("))
    }

    @Test
    fun `the requester is told, once`() {
        val service = src("service/MeshCoreService.kt").substringAfter("repository.onDmAccepted = {").substringBefore("registerReceiver(")
        assertTrue(service.contains("repository.noteDmAccept(keyHex, name, wireText, outgoing = false, occasion = messageKey)"))
        assertTrue("told before the add worked", service.indexOf("if (added)") < service.indexOf("noteDmAccept("))

        val note = src("storage/MessageRepository.kt").substringAfter("suspend fun noteDmAccept(").substringBefore("\n    }\n")
        // The reply arrives twice (sync and RX log) under one channel content key, which
        // bounces the second; a later accept of the same person has its own (2026-11-18).
        assertTrue(note.contains("contentKey = (if (outgoing) \"dm-accepted:\" else \"dm-accept:\") + peer + \":\" + occasion"))
        assertTrue(src("storage/MessageRepository.kt").contains("action.accepterKeyHex, event.text, event.contentKey)"))
        assertTrue(note.contains("if (inserted == -1L || outgoing) return"))
        assertTrue("not in Chats as unread", note.contains("bumpUnread(self, peer, now)"))
        assertTrue("no notification", note.contains("onNewMessage?.invoke(KIND_DM, peer, null, wireText)"))
    }

    @Test
    fun `a direct thread reads the accepted line as a line, without buttons`() {
        val conv = src("ui/screens/ConversationScreen.kt")
        assertTrue(conv.contains("?.takeIf { isChannel || it is DmRequest.Parsed.Accept }"))
        assertTrue(conv.contains("onAccept = askedMe?.takeIf { isChannel }"))
        // The notification reads it as words in a direct thread too, not as wire text.
        val notify = src("service/MeshCoreService.kt")
        assertFalse(notify.contains("            if (isChannel) {\n                engine.selfInfo.value?.let { me ->"))
    }

    @Test
    fun `an unconfirmed name is a warning in the error colours`() {
        val prompt = src("ui/screens/DmRequestUi.kt").substringAfter("fun DmRequestPrompt(")
        val warning = prompt.substringAfter("} else {").substringBefore("dmreq_unconfirmed")
        assertTrue(warning.contains("colorScheme.errorContainer"))
        assertTrue(warning.contains("R.drawable.ic_warning"))
    }
}
