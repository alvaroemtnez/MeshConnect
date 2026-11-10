package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.ChannelEditRules
import io.github.thatsfguy.meshcore.protocol.ChannelKind
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ChannelEditRulesTest {

    @Test
    fun `public and hashtag keys are locked, a private one is not`() {
        assertTrue(ChannelEditRules.keyLocked(ChannelKind.Public))
        assertTrue(ChannelEditRules.keyLocked(ChannelKind.Hashtag))
        assertFalse(ChannelEditRules.keyLocked(ChannelKind.Private))
        // Key unreadable: re-entering it is the repair, so it stays open.
        assertFalse(ChannelEditRules.keyLocked(null))
    }

    @Test
    fun `only a hashtag channel's name is locked, because its key is made from it`() {
        assertTrue(ChannelEditRules.nameLocked(ChannelKind.Hashtag))
        assertFalse(ChannelEditRules.nameLocked(ChannelKind.Public))
        assertFalse(ChannelEditRules.nameLocked(ChannelKind.Private))
        assertFalse(ChannelEditRules.nameLocked(null))
    }

    @Test
    fun `the editor makes locked fields read-only and saves the stored name when it is locked`() {
        val src = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ChannelSheets.kt").readText()
            .substringAfter("fun ChannelEditSheet(").substringBefore("// Sharing a channel hands over its key")
        assertTrue(src.contains("readOnly = nameLocked"))
        assertTrue(src.contains("readOnly = keyLocked"))
        assertTrue(src.contains("(if (nameLocked) channel.name else name).trim()"))
    }
}
