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
        // MeshConnect's rule (rooms shown, hashtags hidden), not upstream's AppModes one.
        assertTrue(src("ui/MeshCoreViewModel.kt").substringAfter("val shownConversations").contains("SimpleMode.showsThread("))
    }

    @Test
    fun `every route Simple mode keeps exists on the hub`() {
        val routes = Regex("""composable\("settings/(\w+)"\)""").findAll(src("MainActivity.kt")).map { it.groupValues[1] }.toSet()
        val kept = routes.filter { AppModes.showsSettingsTile(AppMode.Simple, it) }.toSet()
        assertEquals(setOf("connection", "identity", "channels", "blocking", "app", "backup", "about"), kept)
        assertFalse("radio" in kept)
        assertFalse("advanced" in kept)
        assertTrue(src("ui/screens/SettingsScreen.kt").contains("SimpleMode.showsSettingsTile(mode, it.route)"))
    }

    @Test
    fun `every hub tile, Advanced included, has a screen`() {
        val routes = Regex("""composable\("settings/(\w+)"\)""").findAll(src("MainActivity.kt")).map { it.groupValues[1] }.toSet()
        val hub = io.alvaroedo.meshconnect.ui.screens.SettingsHubText.routes()
        assertTrue(hub.toString(), routes.containsAll(hub))
        // After Appearance and alerts, in the App group.
        assertEquals(hub.indexOf("app") + 1, hub.indexOf("advanced"))
    }

    @Test
    fun `map tiles, the log and datagrams moved from Appearance and alerts to Advanced`() {
        val spokes = src("ui/screens/SettingsSpokeScreens.kt")
        val app = spokes.substringAfter("fun SettingsAppScreen(").substringBefore("\n}\n")
        val advanced = spokes.substringAfter("fun SettingsAdvancedScreen(").substringBefore("\n}\n")
        for (moved in listOf("MapTilesSection(vm)", "DiagnosticsSection(vm)", "ChannelDatagramTool(vm)")) {
            assertFalse(moved, app.contains(moved))
            assertTrue(moved, advanced.contains(moved))
        }
        assertTrue(app.contains("SimpleModeSwitch(vm)"))
    }

    @Test
    fun `the tab bar pops to Chats, which is on the stack even on first run`() {
        // On first run the graph starts at "setup", which finishing setup pops: popUpTo(start)
        // was ignored and every tab tap pushed a fresh screen (reproduced 2026-11-16).
        val shell = src("MainActivity.kt")
        val tabClick = shell.substringAfter("NavigationBarItem(").substringBefore("icon = {")
        assertTrue(tabClick.contains("popUpTo(TAB_ROOT) { saveState = true }"))
        assertFalse(tabClick.contains("startDestinationId"))
        assertEquals("chats", io.alvaroedo.meshconnect.TAB_ROOT)
        // Setup lands on exactly that root.
        assertTrue(shell.contains("nav.navigate(\"chats\") { popUpTo(\"setup\") { inclusive = true } }"))
    }

    @Test
    fun `notifications ask the same rule as Chats`() {
        val notify = src("service/MeshCoreService.kt").substringAfter("private fun postMessageNotification(")
        assertTrue(notify.substringBefore("val title =").contains("SimpleMode.showsThread(mode, isChannel, channelKind, contactType)"))
    }

    @Test
    fun `a conversation hides signal and routing, and keeps the rest`() {
        val conv = src("ui/screens/ConversationScreen.kt")
        // Channel settings is back in Simple mode, as the simple sheet (rename, QR, remove).
        assertFalse(conv.contains("if (technical) MenuAction(stringResource(R.string.conv_channel_settings)"))
        assertTrue(conv.contains("if (technical) R.string.conv_channel_settings else R.string.simple_group_settings"))
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
        // A switch on Appearance and alerts, not a hub tile that toggles on tap.
        assertFalse(src("ui/screens/SettingsScreen.kt").contains("vm.prefs.appMode ="))
        val switch = src("ui/screens/SettingsSpokeScreens.kt").substringAfter("private fun SimpleModeSwitch(")
        assertTrue(switch.contains("vm.prefs.appMode = if (on)"))
        val setter = src("storage/Preferences.kt").substringAfter("var appMode:").substringBefore("\n\n")
        assertTrue(setter.contains("putString(\"app_mode\", v.name)"))
        assertTrue(setter.contains("appModeFlow.value = v"))
    }
}
