package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.RadioSessions
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Repeater sessions follow the companion radio (reported 2026-11-11: signed in as admin
 * from one companion, switched to another not on the repeater's allowlist, and still had
 * the admin tools; the other way round, a guest session followed the user back).
 */
class RadioSessionsTest {

    private val a = "aa".repeat(32)
    private val b = "bb".repeat(32)

    @Test
    fun `switching radios drops the sessions`() {
        assertEquals(b to true, RadioSessions.onIdentity(a, b))
        // Through a disconnect, as a switch actually happens.
        val (mid, dropMid) = RadioSessions.onIdentity(a, "")
        assertEquals(a to false, mid to dropMid)
        assertEquals(b to true, RadioSessions.onIdentity(mid, b))
    }

    @Test
    fun `a reconnect of the same radio keeps them`() {
        val (mid, _) = RadioSessions.onIdentity(a, "")
        assertEquals(a to false, RadioSessions.onIdentity(mid, a))
        assertEquals("same key in another case", false, RadioSessions.onIdentity(a, a.uppercase()).second)
    }

    @Test
    fun `the first radio of the app's life drops nothing`() {
        assertEquals(a to false, RadioSessions.onIdentity("", a))
        assertEquals("" to false, RadioSessions.onIdentity("", ""))
    }

    @Test
    fun `the view model clears sessions and errors on a change, after they exist`() {
        val vm = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()
        val hook = vm.indexOf("RadioSessions.onIdentity(lastRadio, key)")
        assertTrue("wired", hook > 0)
        // Declared before the collector, or an init block reaches a null field.
        assertTrue(vm.indexOf("private val _adminSessions") in 0 until hook)
        assertTrue(vm.indexOf("private val _loginError") in 0 until hook)
        val body = vm.substring(hook, vm.indexOf("\n    }\n", hook))
        assertTrue(body.contains("_adminSessions.value = emptyMap()"))
        assertTrue(body.contains("_loginError.value = emptyMap()"))
    }
}
