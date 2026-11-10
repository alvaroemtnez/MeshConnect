package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Long-pressing a message lifts it over a blurred thread (Signal/Molly style): reactions
 * above, an icon list of actions below, the message where it was.
 */
class MessageFocusWiringTest {

    private val s = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ConversationScreen.kt").readText()

    @Test
    fun `the overlay blurs what is behind and keeps the bubble in place`() {
        // The app blurs itself (Backdrop.kt); window blur is switched off by the system at will.
        assertTrue(s.contains("BlurBackdropWhileShown()"))
        assertFalse(s.contains("blurBehindRadius"))
        assertTrue(s.contains("val y = bounds.top.toInt().coerceIn(minY, maxY)"))
        assertFalse("the old popup bar is gone", s.contains("private fun MessageActionBar("))
    }

    @Test
    fun `no reacting to your own message, delete is destructive`() {
        assertTrue(s.contains("canReact = !m.outgoing,"))
        assertTrue(s.contains("MenuRow(R.drawable.ic_delete, stringResource(R.string.delete), onDelete, destructive = true)"))
    }

    @Test
    fun `the overlay's limits ignore the keyboard it makes slide away`() {
        val overlay = s.substringAfter("private fun MessageFocusOverlay(").substringBefore("private fun ReactionPill(")
        assertFalse("safeDrawing includes the IME; the bubble jumped as it hid", overlay.contains("WindowInsets.safeDrawing"))
        assertTrue(overlay.contains("WindowInsets.systemBars"))
    }
}
