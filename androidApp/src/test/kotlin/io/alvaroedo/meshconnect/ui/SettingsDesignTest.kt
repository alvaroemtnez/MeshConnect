package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.R
import io.alvaroedo.meshconnect.ui.screens.settingsIcon
import io.github.thatsfguy.meshcore.presentation.settingsRoutes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Settings was reworked (2026-11-10) because its screens mixed squared outlined fields,
 * underlined ones, bare text buttons and outlined buttons. These hold the one look.
 */
class SettingsDesignTest {

    private val dir = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens")
    private val settingsFiles = listOf(
        "SettingsScreen", "SettingsSpokeScreens", "SettingsSections", "SettingsComponents",
        "BackupSection", "DataSection", "BlockingSection", "DevicePinSection", "RegionsSection",
    )

    @Test
    fun `every hub route has its own icon`() {
        val icons = settingsRoutes().map { settingsIcon(it) }
        for ((route, icon) in settingsRoutes().zip(icons)) {
            assertNotEquals("$route falls back to the generic icon", R.drawable.ic_settings, icon)
        }
        assertEquals("two routes share an icon", icons.size, icons.toSet().size)
    }

    @Test
    fun `settings screens use the shared field and button styles`() {
        for (name in settingsFiles) {
            val s = File(dir, "$name.kt").readText()
            assertFalse("$name uses OutlinedTextField", Regex("""\bOutlinedTextField\(""").containsMatchIn(s))
            assertFalse("$name uses OutlinedButton", Regex("""\bOutlinedButton\(""").containsMatchIn(s))
        }
    }

    @Test
    fun `reboot is a settings item confirmed in red, not an overflow action`() {
        val s = File(dir, "SettingsScreen.kt").readText()
        assertFalse(s.contains("MenuAction(stringResource(R.string.hub_reboot_menu)"))
        assertTrue(s.contains("icon = R.drawable.ic_restart"))
        assertTrue(s.contains("containerColor = MaterialTheme.colorScheme.error"))
    }
}
