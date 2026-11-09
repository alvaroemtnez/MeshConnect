package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sign-in to a node happens over the screen you were on, and the hub opens only after the
 * node grants a session. Pushing `repeater/{key}` first put the login dialog over an empty
 * window (black in dark theme). These screens must go through rememberAdminOpener.
 */
class AdminOpenerWiringTest {

    private fun src(name: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/$name.kt").readText()

    @Test
    fun `screens that open a hub sign in first`() {
        for (name in listOf("NodesScreen", "MapScreen", "ConversationScreen")) {
            assertTrue("$name lacks the opener", src(name).contains("rememberAdminOpener(vm, nav)"))
        }
        assertFalse(src("NodesScreen").contains("adminable -> nav.navigate(\"repeater/"))
        assertFalse(src("MapScreen").contains("nav.navigate(\"repeater/\$key\")"))
    }

    @Test
    fun `the opener pushes the hub only for a granted session`() {
        val s = src("RepeaterLoginDialog")
        assertTrue(s.contains("if ((vm.adminSessions.value[key] ?: AdminSession.None).signedIn) {\n                    nav.navigate(\"repeater/\$key\")"))
    }

    @Test
    fun `the hub's own gate paints a surface and does not pop a granted session`() {
        val s = src("RepeaterHubScreen")
        assertTrue(s.contains("Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface)"))
        assertTrue(s.contains("onDismiss = { if (!(vm.adminSessions.value[keyHex] ?: AdminSession.None).signedIn) nav.popBackStack() }"))
    }
}
