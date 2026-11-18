package io.alvaroedo.meshconnect.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.alvaroedo.meshconnect.ui.model.SavedRadioOrder
import io.alvaroedo.meshconnect.ui.model.SharedText
import io.github.thatsfguy.meshcore.presentation.LinkQuality
import io.github.thatsfguy.meshcore.presentation.UndrawableLink
import io.github.thatsfguy.meshcore.transport.ConnectionMemory
import io.github.thatsfguy.meshcore.transport.SavedNode
import java.io.File
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Saved radios are listed current first, then most recent (found on 1.3.0, 2026-11-18). */
class SavedRadioOrderTest {

    private fun ble(addr: String) = SavedNode(ConnectionMemory.KIND_BLE, addr, null, addr)

    // Stored oldest first: Preferences.saveNode moves a radio to the end when it connects.
    private val a = ble("AA:00")
    private val b = ble("BB:00")
    private val c = ble("CC:00")

    @Test
    fun `the connected radio is first, the rest most recent first`() {
        assertEquals(listOf(b, c, a), SavedRadioOrder.forDisplay(listOf(a, b, c), b.key))
        // Just connected to c: saveNode put it last, and it is shown first.
        assertEquals(listOf(c, b, a), SavedRadioOrder.forDisplay(listOf(a, b, c), c.key))
    }

    @Test
    fun `with nothing connected, most recent first`() {
        assertEquals(listOf(c, b, a), SavedRadioOrder.forDisplay(listOf(a, b, c), null))
        assertEquals(listOf(c, b, a), SavedRadioOrder.forDisplay(listOf(a, b, c), "tcp:gone:1"))
        assertEquals(emptyList<SavedNode>(), SavedRadioOrder.forDisplay(emptyList(), a.key))
    }

    @Test
    fun `every list of saved radios goes through it, and saving still moves a radio to the end`() {
        val picker = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/RadioPicker.kt").readText()
        // The picker sheet and the no-radio screen's three.
        assertEquals(2, Regex("""SavedRadioOrder\.forDisplay\(vm\.prefs\.savedNodes\(\)""").findAll(picker).count())
        assertFalse(picker.contains("remember { vm.prefs.savedNodes().filter"))
        val settings = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/SettingsSections.kt").readText()
        assertTrue(settings.contains("SavedRadioOrder.forDisplay(vm.prefs.savedNodes(), currentKey)"))
        val prefs = File("src/main/kotlin/io/alvaroedo/meshconnect/storage/Preferences.kt").readText()
        assertTrue("the order this reads is no longer oldest first", prefs.contains("savedNodes().filter { it.key != node.key } + node"))
    }
}

/** A spoke's card is as tall as what is in it (Advanced was a tall empty card, 2026-11-18). */
class SettingsSpokeHeightTest {
    @Test
    fun `no spoke scrolls inside a card stretched to the screen`() {
        val src = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/SettingsSpokeScreens.kt").readText()
        assertFalse(src.contains("scroll = false"))
        for (screen in listOf("fun SettingsAppScreen(", "fun SettingsAdvancedScreen(")) {
            val body = src.substringAfter(screen).substringBefore("\n}\n")
            assertFalse(screen, body.contains("fillMaxSize()"))
            assertFalse(screen, body.contains("verticalScroll("))
        }
        val spoke = src.substringAfter("private fun SettingsSpoke(").substringBefore("\n}\n")
        assertTrue(spoke.contains("base.verticalScroll(rememberScrollState())"))
        // Yesterday's keyboard fix stays.
        assertTrue(spoke.contains(".imePadding()"))
    }
}

/** Signal bands and neighbour lines in the user's language (the ping screen, 2026-11-18). */
@org.junit.runner.RunWith(org.robolectric.RobolectricTestRunner::class)
class LinkTextTest {

    private fun resources(tag: String): Resources {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return app.createConfigurationContext(config).resources
    }

    @Test
    fun `every band and reason has its own words in every language`() {
        for (tag in listOf("en", "es", "ca")) {
            val res = resources(tag)
            val bands = LinkQuality.entries.map { SharedText.linkQuality(res, it) }
            assertEquals(tag, bands.size, bands.toSet().size)
            val reasons = UndrawableLink.entries.map { SharedText.undrawable(res, it) }
            assertEquals(tag, reasons.size, reasons.toSet().size)
        }
        // English reads as upstream's labels did.
        assertEquals(LinkQuality.entries.map { it.label }, LinkQuality.entries.map { SharedText.linkQuality(resources("en"), it) })
        assertEquals(UndrawableLink.entries.map { it.reason }, UndrawableLink.entries.map { SharedText.undrawable(resources("en"), it) })
        assertEquals("Fuerte", SharedText.linkQuality(resources("es"), LinkQuality.Strong))
        assertEquals("Feble", SharedText.linkQuality(resources("ca"), LinkQuality.Weak))
    }

    @Test
    fun `heard-ago keeps upstream's thresholds`() {
        val en = resources("en")
        for (s in listOf(-5L, 0L, 59L, 60L, 3_599L, 3_600L, 86_399L, 86_400L, 900_000L)) {
            assertEquals("at $s s", io.github.thatsfguy.meshcore.protocol.Neighbours.heardAgoLabel(s), SharedText.heardAgo(en, s))
        }
        assertEquals("hace 12 min", SharedText.heardAgo(resources("es"), 12 * 60))
        assertEquals("fa 3 h", SharedText.heardAgo(resources("ca"), 3 * 3_600))
    }

    @Test
    fun `no screen shows upstream's English band, reason or age`() {
        val screens = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens")
        val text = screens.walk().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        for (english in listOf(").label + ", "quality.label", "link.mapLabel", "link.summary", "it.reason}", "heardAgoLabel(")) {
            assertFalse(english, text.contains(english))
        }
        // Positive control: the translated ones are what replaced them.
        for (used in listOf("SharedText.linkQuality(", "SharedText.neighbourChip(", "SharedText.neighbourSummary(", "SharedText.heardAgo(")) {
            assertTrue(used, text.contains(used))
        }
    }
}
