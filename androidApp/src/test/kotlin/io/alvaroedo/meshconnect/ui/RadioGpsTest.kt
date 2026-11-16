package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.RadioGps
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** The radio's GPS in Identity (2026-11-16), and the ask dialog's channel choice. */
class RadioGpsTest {

    @Test
    fun `the gps variable says whether there is a GPS and whether it is on`() {
        assertEquals(RadioGps.State.None, RadioGps.of(emptyMap()))
        assertEquals(RadioGps.State.None, RadioGps.of(mapOf("other" to "1")))
        assertEquals(RadioGps.State.On, RadioGps.of(mapOf("gps" to "1")))
        assertEquals(RadioGps.State.Off, RadioGps.of(mapOf("gps" to "0")))
    }

    @Test
    fun `a position can be set by hand only when no fix will overwrite it`() {
        assertFalse(RadioGps.editable(RadioGps.State.On))
        assertTrue(RadioGps.editable(RadioGps.State.Off))
        assertTrue(RadioGps.editable(RadioGps.State.None))
    }

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()

    @Test
    fun `identity asks the radio for its variables and offers the choice only with a GPS`() {
        assertTrue(src("ui/screens/SettingsSpokeScreens.kt").contains("vm.querySelfInfo(); vm.queryCustomVars()"))
        val identity = src("ui/screens/SettingsSections.kt").substringAfter("internal fun IdentitySection(").substringBefore("internal fun RadioSection(")
        assertTrue(identity.contains("if (gps != io.alvaroedo.meshconnect.ui.model.RadioGps.State.None) {"))
        assertTrue(identity.contains("readOnly = !editable,"))
        assertTrue(identity.contains("vm.setRadioGps(on)"))
        // Not behind Simple mode's early return: the position block comes before it.
        assertTrue(identity.indexOf("RadioGps.of(") < identity.indexOf("SimpleMode.showsAdverts("))
    }

    @Test
    fun `the ask dialog sends on our channel, with private groups one button away`() {
        val dialog = src("ui/screens/DmRequestUi.kt").substringAfter("fun AskToBeAddedDialog(").substringBefore("\n}\n")
        assertFalse(dialog.contains("RadioButton"))
        assertTrue(dialog.contains("R.string.dmreq_choose_private"))
        assertTrue(dialog.contains("send(null)"))
        assertTrue(dialog.contains("send(g.idx)"))
    }
}

class SnackbarAndAddBackTest {
    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()

    @Test
    fun `the snackbar keeps clear of the system bar everywhere, and of the tab bar and composer`() {
        val shell = src("MainActivity.kt").substringAfter("snackbarHost = {").substringBefore("contentWindowInsets")
        assertTrue(shell.contains("WindowInsets.navigationBars.union(WindowInsets.ime)"))
        assertEquals(80, io.alvaroedo.meshconnect.snackbarLift("chats", onTab = true).value.toInt())
        assertEquals(72, io.alvaroedo.meshconnect.snackbarLift("conversation/dm/ab", onTab = false).value.toInt())
        assertEquals(0, io.alvaroedo.meshconnect.snackbarLift("settings/app", onTab = false).value.toInt())
    }

    @Test
    fun `the add-back dialog closes with Close, not OK, and has its two ways as buttons`() {
        val dialog = src("ui/screens/ScanConfirmations.kt").substringAfter("addBack?.let { name ->").substringBefore("if (showOwnQr)")
        assertTrue(dialog.contains("R.string.close"))
        assertFalse(dialog.contains("R.string.ok"))
        assertTrue(dialog.indexOf("R.string.addback_ask") < dialog.indexOf("R.string.addback_show_qr"))
        assertTrue(dialog.contains("FilledTonalButton("))
    }
}
