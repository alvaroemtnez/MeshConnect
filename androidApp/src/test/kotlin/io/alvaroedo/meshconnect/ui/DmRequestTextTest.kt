package io.alvaroedo.meshconnect.ui

import android.content.Context
import android.content.res.Configuration
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import io.alvaroedo.meshconnect.ui.model.DmRequestText
import io.github.thatsfguy.meshcore.presentation.DmRequest
import java.io.File
import java.util.Locale
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Direct-message requests over a channel (upstream 0.11.0): the line each one reads as, and
 * the wiring that keeps a faked accept from adding anyone.
 */
@RunWith(RobolectricTestRunner::class)
class DmRequestTextTest {

    private val alice = "a1".repeat(32)
    private val bob = "b0".repeat(32)

    private fun res(tag: String): Resources {
        val app = ApplicationProvider.getApplicationContext<Context>()
        val config = Configuration(app.resources.configuration).apply { setLocale(Locale.forLanguageTag(tag)) }
        return app.createConfigurationContext(config).resources
    }

    private val request = DmRequest.request("Bob", alice)!!
    private val accept = DmRequest.accept("Alice", alice, bob)!!

    @Test
    fun `a request reads from each side`() {
        val en = res("en")
        assertEquals("You asked Bob to message privately.", DmRequestText.lineFor(en, request, "Alice", true, "Alice", alice))
        assertEquals("Alice wants to message you privately.", DmRequestText.lineFor(en, request, "Alice", false, "bob", bob))
        assertEquals("Alice asked Bob to message privately.", DmRequestText.lineFor(en, request, "Alice", false, "Carol", "c2".repeat(32)))
    }

    @Test
    fun `an accept reads from each side`() {
        val en = res("en")
        assertEquals("You accepted Alice's request to message privately.", DmRequestText.lineFor(en, accept, "Bob", true, "Bob", bob))
        assertEquals(
            "Bob accepted your request. You can message each other directly.",
            DmRequestText.lineFor(en, accept, "Bob", false, "Alice", alice),
        )
        assertEquals(
            "Bob accepted Alice's request to message privately.",
            DmRequestText.lineFor(en, accept, "Bob", false, "Carol", "c2".repeat(32)),
        )
    }

    @Test
    fun `it follows the language, and an unnamed sender is someone`() {
        assertEquals("Alice quiere hablar contigo en privado.", DmRequestText.lineFor(res("es"), request, "Alice", false, "Bob", bob))
        assertEquals("Algú ha demanat a Bob parlar en privat.", DmRequestText.lineFor(res("ca"), request, " ", false, "Carol", "c2".repeat(32)))
    }

    @Test
    fun `ordinary text is not a request`() {
        assertNull(DmRequestText.lineFor(res("en"), "DM request for Bob", "Alice", false, "Bob", bob))
        assertNull(DmRequestText.lineFor(res("en"), "hello", "Alice", false, "Bob", bob))
    }

    // ---- wiring ----

    private fun src(path: String) = File("src/main/kotlin/io/alvaroedo/meshconnect/$path").readText()

    @Test
    fun `an accept only adds anyone through decide, against our outstanding requests`() {
        val repo = src("storage/MessageRepository.kt")
        val hook = repo.substringAfter("as? io.github.thatsfguy.meshcore.presentation.DmRequest.Parsed.Accept)")
            .substringBefore("val channelKey = event.channelIndex.toString()")
        assertTrue(hook.contains("dmOutstanding(),"))
        assertTrue(hook.contains("if (action is io.github.thatsfguy.meshcore.presentation.DmRequest.Action.TheyAccepted)"))
        val svc = src("service/MeshCoreService.kt")
        assertTrue(svc.contains("repository.dmOutstanding = { prefs.dmRequestsOutstanding }"))
        assertTrue(svc.contains("engine.addContactFromCard(key, name, io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_CHAT)"))
    }

    @Test
    fun `nothing is posted that would arrive cut short`() {
        val vm = src("ui/MeshCoreViewModel.kt")
        for (fn in listOf("fun askToMessagePrivately(", "fun acceptDmRequest(")) {
            val body = vm.substringAfter(fn).substringBefore("\n    }\n")
            val fits = body.indexOf("DmRequest.fits(")
            val send = body.indexOf("sendChannelMessage(")
            assertTrue(fits in 0 until send, "$fn checks fits before sending")
        }
    }

    @Test
    fun `sharing our own code opens the door for one answer`() {
        val vm = src("ui/MeshCoreViewModel.kt")
        assertTrue(vm.substringAfter("suspend fun selfShareUri(").substringBefore("return ShareUri").contains("noteSharedOwnCode()"))
        assertTrue(src("ui/screens/ConversationScreen.kt").contains("vm.noteSharedOwnCode()"))
    }

    @Test
    fun `the line is quiet - italic and greyed - and the ask is only on others' channel messages`() {
        val conv = src("ui/screens/ConversationScreen.kt")
        val line = conv.substringAfter("private fun DmRequestLine(").substringBefore("\n}\n")
        assertTrue(line.contains("fontStyle = androidx.compose.ui.text.font.FontStyle.Italic"))
        assertTrue(line.contains("onSurfaceVariant.copy(alpha = 0.7f)"))
        assertTrue(conv.contains("onAskDm = if (isChannel && !m.outgoing && Mentions.canMention(m.senderName.orEmpty()))"))
    }
}
