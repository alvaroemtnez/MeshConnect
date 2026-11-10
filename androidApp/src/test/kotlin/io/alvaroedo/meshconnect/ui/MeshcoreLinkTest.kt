package io.alvaroedo.meshconnect.ui

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * meshcore:// links open the app (2026-11-10): a camera app's QR scan or a tapped link went to
 * whichever other app claimed the scheme. They take the in-app scanner's path, so the same
 * confirmations apply, and wait for the radio service, which a cold start does not have yet.
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

    @Test
    fun `a link is imported through the scanner once the service is bound`() {
        assertTrue(shell.contains("if (service == null) return@LaunchedEffect"))
        assertTrue(shell.contains("vm.importScannedCode(text)"))
        // Both a cold start and a running app's new intent deliver it.
        assertTrue(shell.contains("if (savedInstanceState == null) openLink.value = linkFrom(intent)"))
        assertTrue(shell.contains("linkFrom(intent)?.let { openLink.value = it }"))
    }

    @Test
    fun `new chat can take a code from the clipboard`() {
        val sheet = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ChannelSheets.kt").readText()
        assertTrue(sheet.contains("vm.importPastedText(clipboard.getText()?.text)"))
    }
}
