package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * "Mark unread" is a flag, not a count (reported 2026-11-10: it sometimes did nothing, and
 * showed a number for a message that does not exist).
 *
 * It used to `bumpUnread` the contact or channel row. A DM whose node is not in the radio's
 * contact table — heard but never added, or since removed — has no row, so the UPDATE
 * matched nothing and the chat stayed read. The flag lives in preferences, keyed by thread,
 * so it works for every thread, and the list draws it as a dot.
 */
class MarkUnreadWiringTest {

    private val vm = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()
    private val chats = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ChatsScreen.kt").readText()

    private fun body(src: String, signature: String) =
        src.substringAfter(signature).substringBefore("\n    }\n")

    @Test
    fun `marking unread does not depend on a contact row existing`() {
        val mark = body(vm, "fun markUnread(kind: String, peerKey: String) {")
        assertFalse("must not need a DB row", mark.contains("bumpUnread"))
        assertTrue(mark.contains("prefs.setThreadMarkedUnread(Inbox.threadKey(kind, peerKey), true)"))
    }

    @Test
    fun `opening a thread or marking it read clears the mark`() {
        assertTrue(body(vm, "fun markThreadOpen(kind: String, peerKey: String) {")
            .contains("setThreadMarkedUnread(Inbox.threadKey(kind, peerKey), false)"))
        assertTrue(body(vm, "fun markRead(kind: String, peerKey: String) {")
            .contains("setThreadMarkedUnread(Inbox.threadKey(kind, peerKey), false)"))
    }

    @Test
    fun `the conversation list recomputes when a mark changes`() {
        val conversations = vm.substringAfter("val conversations: StateFlow<List<ConversationRow>> = combine(")
            .substringBefore(".stateIn(")
        assertTrue(conversations.contains("prefs.threadListRevision"))
        assertTrue(conversations.contains("copy(markedUnread = true)"))
    }

    @Test
    fun `a marked chat shows a dot, and a swipe on it marks it read`() {
        assertTrue(chats.contains("} else if (row.markedUnread) {"))
        assertTrue(chats.contains("if (row.unread > 0 || row.markedUnread) vm.markRead(row.kind, row.key)"))
    }
}
