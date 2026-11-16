package io.alvaroedo.meshconnect.storage

import androidx.test.core.app.ApplicationProvider
import io.github.thatsfguy.meshcore.protocol.ChannelKind
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Nicknames, favourite groups, remembered channel kinds and room sign-in (2026-11-16). */
@RunWith(RobolectricTestRunner::class)
class SimpleModePreferencesTest {

    private lateinit var prefs: Preferences
    private val radio = "11".repeat(32)
    private val other = "22".repeat(32)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        context.getSharedPreferences("meshcore_prefs", android.content.Context.MODE_PRIVATE).edit().clear().commit()
        Preferences.resetActiveRadioForTest()
        prefs = Preferences(context)
    }

    @Test
    fun `channel kinds are remembered per radio, and an unknown kind is dropped`() {
        prefs.rememberChannelKinds(radio, mapOf(0 to ChannelKind.Public, 3 to ChannelKind.Private, 5 to ChannelKind.Hashtag))
        assertEquals(mapOf(0 to ChannelKind.Public, 3 to ChannelKind.Private, 5 to ChannelKind.Hashtag), prefs.rememberedChannelKinds(radio))
        // Another radio's slots are different channels.
        assertEquals(emptyMap(), prefs.rememberedChannelKinds(other))
        // Before any radio has answered there is nothing to remember against.
        assertEquals(emptyMap(), prefs.rememberedChannelKinds(""))
    }

    @Test
    fun `favourite groups are per radio and leave with their slot`() {
        prefs.activateRadio(radio)
        prefs.setChannelFavourite(2, true)
        prefs.setChannelMuted(2, true)
        assertEquals(setOf(2), prefs.favouriteChannels)
        prefs.activateRadio(other)
        assertEquals(emptySet(), prefs.favouriteChannels)
        prefs.activateRadio(radio)
        // The slot goes back to the radio: the next channel in it is neither favourite nor muted.
        prefs.forgetChannelSlot(2)
        assertEquals(emptySet(), prefs.favouriteChannels)
        assertFalse(prefs.isChannelMuted(2))
    }

    @Test
    fun `every nickname is listed, and clearing one removes it`() {
        prefs.setNickname("aa", "Mum")
        prefs.setNickname("bb", "Dad")
        assertEquals(mapOf("aa" to "Mum", "bb" to "Dad"), prefs.allNicknames())
        prefs.setNickname("aa", null)
        assertNull(prefs.nicknameFor("aa"))
        assertEquals(mapOf("bb" to "Dad"), prefs.allNicknames())
    }

    @Test
    fun `room auto sign-in is on until switched off`() {
        assertTrue(prefs.roomAutoSignIn)
        prefs.roomAutoSignIn = false
        assertFalse(prefs.roomAutoSignIn)
    }
}
