package io.alvaroedo.meshconnect.ui

import io.github.thatsfguy.meshcore.presentation.AppMode
import io.github.thatsfguy.meshcore.presentation.AppModes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Simple mode (upstream 0.11.0). The rules are AppModes in shared, tested there; this pins
 * that every screen asks them, and that the hub's routes are the ones AppModes names.
 */
class SimpleModeWiringTest {

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()

    @Test
    fun `the tab bar and its badge follow the mode`() {
        val shell = src("MainActivity.kt")
        assertTrue(shell.contains(").filter { it.route in shownTabs }"))
        assertTrue(shell.contains("val conversations by vm.shownConversations.collectAsStateWithLifecycle()"))
        assertEquals(listOf("chats", "settings"), AppModes.tabs(AppMode.Simple))
    }

    @Test
    fun `chats lists the threads the mode shows`() {
        assertTrue(src("ui/screens/ChatsScreen.kt").contains("val conversations by vm.shownConversations.collectAsState()"))
        assertTrue(src("ui/MeshCoreViewModel.kt").substringAfter("val shownConversations").contains("AppModes.showsThread("))
    }

    @Test
    fun `every route Simple mode keeps exists on the hub`() {
        val routes = Regex("""composable\("settings/(\w+)"\)""").findAll(src("MainActivity.kt")).map { it.groupValues[1] }.toSet()
        val kept = routes.filter { AppModes.showsSettingsTile(AppMode.Simple, it) }.toSet()
        assertEquals(setOf("connection", "identity", "channels", "blocking", "app", "backup", "about"), kept)
        assertFalse("radio" in kept)
        assertTrue(src("ui/screens/SettingsScreen.kt").contains("AppModes.showsSettingsTile(mode, it.route)"))
    }

    @Test
    fun `a conversation hides signal and routing, and keeps the rest`() {
        val conv = src("ui/screens/ConversationScreen.kt")
        assertTrue(conv.contains("if (technical) MenuAction(stringResource(R.string.conv_channel_settings)"))
        assertTrue(conv.contains("if (technical) MenuAction(stringResource(R.string.conv_routing)"))
        assertTrue(conv.contains("if (technical) MenuAction(stringResource(R.string.trace_route)"))
        assertTrue(conv.contains("routeLabel = if (m.outgoing || !signalDetail) null else routeChipLabel(m, routeNames)"))
        assertTrue(conv.contains("onInfo = if (canInfo)"))
        // Mute, mark unread and clear are not gated.
        assertFalse(conv.contains("if (technical) MenuAction(stringResource(R.string.conv_mute)"))
    }

    @Test
    fun `first run and Settings both switch it, and switching only writes the mode`() {
        assertTrue(src("ui/screens/SetupScreen.kt").contains("onClick = { vm.prefs.appMode = choice }"))
        val hub = src("ui/screens/SettingsScreen.kt")
        assertTrue(hub.contains("vm.prefs.appMode = if (simple)"))
        val setter = src("storage/Preferences.kt").substringAfter("var appMode:").substringBefore("\n\n")
        assertTrue(setter.contains("putString(\"app_mode\", v.name)"))
        assertTrue(setter.contains("appModeFlow.value = v"))
    }
}
