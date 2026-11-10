package io.alvaroedo.meshconnect.storage

import androidx.test.core.app.ApplicationProvider
import io.github.thatsfguy.meshcore.protocol.Retention
import java.io.File
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Settings that belong to a radio stay with that radio (2026-11-10): a home and a work radio
 * each have a "channel 1", and they are different channels.
 */
@RunWith(RobolectricTestRunner::class)
class PerRadioPreferencesTest {

    private val home = "aa".repeat(32)
    private val work = "bb".repeat(32)
    private lateinit var raw: android.content.SharedPreferences
    private lateinit var prefs: Preferences

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        raw = context.getSharedPreferences("meshcore_prefs", android.content.Context.MODE_PRIVATE)
        raw.edit().clear().commit()
        Preferences.resetActiveRadioForTest()
        prefs = Preferences(context)
    }

    @After
    fun tearDown() = Preferences.resetActiveRadioForTest()

    @Test
    fun `channel 1 on one radio is not channel 1 on the other`() {
        prefs.activateRadio(home)
        prefs.setChannelMuted(1, true)
        prefs.setChannelRegion(1, "valencia")
        prefs.setChannelRetention(1, Retention.Policy(Retention.Mode.Days, 7))
        prefs.setThreadPinned("ch|1", true)
        prefs.setThreadArchived("dm|cc", true)
        prefs.setThreadMarkedUnread("ch|2", true)
        prefs.setRoutePinned("dd".repeat(32), true)
        prefs.floodScope = "valencia"

        prefs.activateRadio(work)
        assertFalse(prefs.isChannelMuted(1))
        assertNull(prefs.channelRegion(1))
        assertEquals(emptyMap(), prefs.channelRegions())
        assertEquals(emptyMap(), prefs.channelRetentions())
        assertTrue(prefs.pinnedThreads.isEmpty())
        assertTrue(prefs.archivedThreads.isEmpty())
        assertTrue(prefs.markedUnreadThreads.isEmpty())
        assertFalse(prefs.isRoutePinned("dd".repeat(32)))
        assertNull(prefs.floodScope)

        // And back: nothing was lost by the trip.
        prefs.activateRadio(home)
        assertTrue(prefs.isChannelMuted(1))
        assertEquals("valencia", prefs.channelRegion(1))
        assertEquals(setOf("ch|1"), prefs.pinnedThreads)
        assertEquals(setOf("dm|cc"), prefs.archivedThreads)
        assertEquals("valencia", prefs.floodScope)
    }

    @Test
    fun `settings from before the split go to the first radio, once`() {
        raw.edit()
            .putStringSet("pinned_threads", setOf("ch|0"))
            .putStringSet("muted_channels", setOf("3"))
            .putString("channel_region_2", "valencia")
            .putString("floodScope", "valencia")
            .putStringSet("blocked_keys", setOf("ee".repeat(32)))
            .commit()

        prefs.activateRadio(home)
        assertEquals(setOf("ch|0"), prefs.pinnedThreads)
        assertTrue(prefs.isChannelMuted(3))
        assertEquals("valencia", prefs.channelRegion(2))
        assertEquals("valencia", prefs.floodScope)
        assertFalse(raw.contains("pinned_threads"), "the unscoped copy is gone")
        // Not a radio's: stays global.
        assertEquals(setOf("ee".repeat(32)), raw.getStringSet("blocked_keys", null))

        prefs.activateRadio(work)
        assertTrue(prefs.pinnedThreads.isEmpty(), "the second radio inherits nothing")
        assertNull(prefs.channelRegion(2))
    }

    @Test
    fun `forgetting a slot forgets it on this radio only`() {
        prefs.activateRadio(work)
        prefs.setChannelRegion(5, "valencia")
        prefs.activateRadio(home)
        prefs.setChannelRegion(5, "castello")
        prefs.setChannelRetention(5, Retention.Policy(Retention.Mode.Days, 30))
        prefs.forgetChannelSlot(5)
        assertEquals(emptySet(), prefs.slotKeyedPreferenceKeys(5))
        prefs.activateRadio(work)
        assertEquals("valencia", prefs.channelRegion(5))
    }

    @Test
    fun `a purge clears every radio's`() {
        prefs.activateRadio(home)
        prefs.setThreadPinned("ch|1", true)
        prefs.activateRadio(work)
        prefs.setChannelMuted(2, true)
        prefs.clearAllRadioScoped()
        assertFalse(prefs.isChannelMuted(2))
        prefs.activateRadio(home)
        assertTrue(prefs.pinnedThreads.isEmpty())
    }

    @Test
    fun `a radio switch redraws the chat list`() {
        val before = prefs.threadListRevision.value
        prefs.activateRadio(home)
        assertTrue(prefs.threadListRevision.value > before)
    }

    @Test
    fun `the service restores the scope per radio and the app activates it too`() {
        val svc = File("src/main/kotlin/io/alvaroedo/meshconnect/service/MeshCoreService.kt").readText()
        assertTrue(svc.contains("if (scopeRestoredFor != info.publicKeyHex)"))
        val vm = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()
        assertTrue(vm.contains("selfKey.collect { prefs.activateRadio(it) }"))
        assertTrue(vm.contains("prefs.clearAllRadioScoped()"))
    }
}
