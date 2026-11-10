package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.leavesOnRadioSwitch
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Switching radios from inside a chat left the chat open, emptied, under the old peer's bare
 * key (reported 2026-11-10). Screens tied to the old radio go back to Chats.
 */
class RadioSwitchNavTest {

    @Test
    fun `screens about one radio's peers leave, the rest stay`() {
        assertTrue(leavesOnRadioSwitch("conversation/{kind}/{peer}"))
        assertTrue(leavesOnRadioSwitch("repeater/{key}"))
        assertTrue(leavesOnRadioSwitch("repeater/{key}/status"))
        assertTrue(leavesOnRadioSwitch("trace/{key}"))
        for (stay in listOf("chats", "nodes", "map", "settings", "settings/radio", "repeats", null)) {
            assertFalse(stay.toString(), leavesOnRadioSwitch(stay))
        }
    }

    @Test
    fun `only a different radio counts, not the same one reconnecting`() {
        val shell = File("src/main/kotlin/io/alvaroedo/meshconnect/MainActivity.kt").readText()
        assertTrue(shell.contains("if (last != null && key != last && leavesOnRadioSwitch(nav.currentDestination?.route))"))
        // A disconnect (null) in between does not reset what "last" was.
        assertTrue(shell.contains("val key = info?.publicKeyHex ?: return@collect"))
    }

    @Test
    fun `the start destination is decided once, not on every recomposition`() {
        // Re-read each time, it flipped from setup to chats when setup finished, and the next
        // recomposition (the first message sent) gave NavHost a new graph, which reset the
        // back stack and threw the user out of the chat. Reproduced on the emulator.
        val shell = File("src/main/kotlin/io/alvaroedo/meshconnect/MainActivity.kt").readText()
        assertTrue(shell.contains("val startDestination = remember { if (vm.prefs.setupComplete) \"chats\" else \"setup\" }"))
        assertTrue(shell.contains("startDestination = startDestination,"))
        assertFalse(shell.contains("startDestination = if (vm.prefs.setupComplete)"))
    }
}
