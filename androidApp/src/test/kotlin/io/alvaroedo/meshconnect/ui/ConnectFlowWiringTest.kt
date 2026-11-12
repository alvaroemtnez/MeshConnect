package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Connecting is one picker (RadioPicker) reached from wherever the user is: first run,
 * an empty Chats, the top bar's connection card, Settings → Connection. Upstream's
 * screens said "go to Settings → Connection" instead.
 */
class ConnectFlowWiringTest {

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()

    @Test
    fun `every entry point uses the picker`() {
        assertTrue(src("ui/screens/SetupScreen.kt").contains("RadioPicker(vm)"))
        assertTrue(src("ui/screens/ChatsScreen.kt").contains("NoRadioState(vm,"))
        assertTrue(src("ui/screens/AppTopBar.kt").contains("RadioPicker(vm, onPicked = onDismiss)"))
        assertTrue(src("ui/screens/SettingsSections.kt").contains("RadioPickerSheet(vm, onDismiss)"))
    }

    @Test
    fun `first run moves on when the radio answers`() {
        assertTrue(src("ui/screens/SetupScreen.kt").contains("LaunchedEffect(connected) { if (connected && step == 1) step = 2 }"))
    }

    @Test
    fun `a reconnecting radio is not an invitation to pick another`() {
        val s = src("ui/screens/RadioPicker.kt")
        val state = s.substring(s.indexOf("fun NoRadioState"))
        // The picker button sits only in the Detached branch.
        assertTrue(state.contains("if (engineState == EngineState.Detached) {"))
        assertTrue(state.indexOf("picker = true") < state.indexOf("} else if (engineState != EngineState.Ready) {"))
    }

    @Test
    fun `bluetooth permission is not asked before first run is done`() {
        val s = src("MainActivity.kt")
        // 1.2.0: the start-up request also carries the local network permission (TCP on,
        // Android 17+), so it is built as a list, but all of it stays behind setupComplete.
        val gate = s.indexOf("if (prefs.setupComplete) {")
        val ble = s.indexOf("BlePermissions.required()")
        val lan = s.indexOf("add(LocalNetworkPermission.PERMISSION)")
        val launch = s.indexOf("permissionLauncher.launch(")
        assertTrue("nothing is asked outside the first-run gate", gate in 0 until ble && ble < lan && lan < launch)
        assertTrue(s.substring(gate, launch).count { it == '{' } > s.substring(gate, launch).count { it == '}' })
        assertFalse(s.contains("        if (!BlePermissions.allGranted(this)) {"))
    }
}
