package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.ui.model.MessageSelection
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Multi-select in a thread, plus the two long-press defects reported with it (2026-11-10):
 * the blur that showed once and then never, and cards that swallowed the long-press.
 */
class MessageSelectionTest {

    private fun msg(id: Long, text: String, ts: Long, out: Boolean = false, sender: String? = null) =
        MessageEntity(
            id = id, selfKey = "s", kind = "ch", peerKey = "0", senderName = sender, text = text,
            timestamp = ts, receivedAt = ts * 1000, outgoing = out, status = 0, ackHash = null,
            contentKey = null, snr = null,
        )

    private val thread = listOf(
        // Newest first, the order the thread holds them in.
        msg(3, "third", 300, out = true),
        msg(2, "second", 200, sender = "Bob"),
        msg(1, "first", 100, sender = "Ann"),
    )

    @Test
    fun `toggling the last message off empties the selection`() {
        assertEquals(setOf(1L, 2L), MessageSelection.toggle(setOf(1L), 2L))
        assertEquals(emptySet<Long>(), MessageSelection.toggle(setOf(1L), 1L))
    }

    @Test
    fun `reply is offered for exactly one message`() {
        assertTrue(MessageSelection.canReply(setOf(1L)))
        assertFalse(MessageSelection.canReply(setOf(1L, 2L)))
        assertFalse(MessageSelection.canReply(emptySet()))
    }

    @Test
    fun `one message copies as its bare text, as single Copy always did`() {
        assertEquals("second", MessageSelection.copyText(thread, setOf(2L), showSenders = true))
    }

    @Test
    fun `several copy oldest first, whatever order they were picked in`() {
        assertEquals(
            "Ann: first\nBob: second\nthird",
            MessageSelection.copyText(thread, linkedSetOf(3L, 1L, 2L), showSenders = true),
        )
    }

    @Test
    fun `a DM thread copies without names`() {
        assertEquals("first\nsecond", MessageSelection.copyText(thread, setOf(1L, 2L), showSenders = false))
    }

    @Test
    fun `a blank sender name is not turned into a colon prefix`() {
        val t = listOf(msg(1, "a", 1, sender = "  "), msg(2, "b", 2, sender = null))
        assertEquals("a\nb", MessageSelection.copyText(t, setOf(1L, 2L), showSenders = true))
    }

    // ------------------------------------------------------------ wiring

    private val screens = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens")
    private fun src(name: String) = File(screens, name).readText()

    @Test
    fun `the long-press overlay blurs the app itself, not the window behind it`() {
        val conv = src("ConversationScreen.kt")
        // Window blur is switched off by the system at will; it is why the blur was seen once.
        assertFalse(conv.contains("FLAG_BLUR_BEHIND"))
        assertTrue(conv.contains("BlurBackdropWhileShown()"))
        val shell = File("src/main/kotlin/io/alvaroedo/meshconnect/MainActivity.kt").readText()
        assertTrue("the root must apply the blur", shell.contains("Modifier.backdropBlur(backdrop)"))
        assertTrue(shell.contains("LocalBackdropBlur provides backdrop"))
    }

    @Test
    fun `a card inside a bubble passes its long-press to the message`() {
        val composer = src("Composer.kt")
        val shell = composer.substringAfter("private fun CardShell(").substringBefore("\n}\n")
        assertTrue(shell.contains("onLongClick = longPress"))
        assertTrue(src("ConversationScreen.kt").contains("LocalMessageLongPress provides"))
    }

    @Test
    fun `the action menu offers Select, and selecting replaces the top bar`() {
        val conv = src("ConversationScreen.kt")
        assertTrue(conv.contains("MenuRow(R.drawable.ic_select, stringResource(R.string.conv_select), onSelect)"))
        assertTrue(conv.contains("SelectionTopBar("))
        // Back leaves selection before it leaves the thread.
        assertTrue(conv.contains("BackHandler(enabled = selection != null)"))
    }
}
