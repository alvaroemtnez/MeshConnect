package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * meshcore:// links open the app (2026-11-10): a camera app's QR scan or a tapped link went to
 * whichever other app claimed the scheme. They take the in-app scanner's path, so the same
 * confirmations apply, and wait until the radio is connected, which a cold start is not yet
 * (LinkInbox, upstream 0.11.0).
 */
class MeshcoreLinkTest {

    private val manifest = File("src/main/AndroidManifest.xml").readText()
    private val shell = File("src/main/kotlin/io/alvaroedo/meshconnect/MainActivity.kt").readText()

    @Test
    fun `the main activity claims meshcore links as a browsable view`() {
        val main = manifest.substringAfter("android:name=\".MainActivity\"").substringBefore("</activity>")
        assertTrue(main.contains("android.intent.action.VIEW"))
        assertTrue(main.contains("android.intent.category.BROWSABLE"))
        assertTrue(main.contains("android:scheme=\"meshcore\""))
    }

    private val vm = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/MeshCoreViewModel.kt").readText()

    @Test
    fun `a link waits for the radio, then goes through the scanner`() {
        assertTrue(shell.contains("vm.openIncomingLink(text)"))
        val open = vm.substringAfter("fun openIncomingLink(").substringBefore("private val linkInbox")
        assertTrue(open.contains("linkInbox.offer(raw, engineState.value == EngineState.Ready)"))
        assertTrue(open.contains("is io.github.thatsfguy.meshcore.presentation.LinkInbox.Offer.Open -> importScannedCode(offer.link)"))
        // The held link opens when the radio is ready, at most once (LinkInbox.onReady).
        assertTrue(vm.contains("if (state == EngineState.Ready) linkInbox.onReady()?.let { importScannedCode(it) }"))
        // Both a cold start and a running app's new intent deliver it.
        assertTrue(shell.contains("if (savedInstanceState == null) openLink.value = linkFrom(intent)"))
        assertTrue(shell.contains("linkFrom(intent)?.let { openLink.value = it }"))
    }

    @Test
    fun `new chat can take a code from the clipboard`() {
        val sheet = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ChannelSheets.kt").readText()
        assertTrue(sheet.contains("clipboard.paste {\n                                vm.importPastedText(it, openChat = newChat)"))
    }
}
