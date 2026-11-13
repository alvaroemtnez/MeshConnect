package io.alvaroedo.meshconnect.ui

import android.content.ClipData
import android.content.ClipDescription
import android.net.Uri
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.NativeClipboard
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.asCoroutineDispatcher
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Copy and paste moved off the deprecated LocalClipboardManager (2026-11-13). */
@RunWith(RobolectricTestRunner::class)
class TextClipboardTest {

    private class MemoryClipboard : Clipboard {
        var entry: ClipEntry? = null
        override suspend fun getClipEntry(): ClipEntry? = entry
        override suspend fun setClipEntry(clipEntry: ClipEntry?) { entry = clipEntry }
        override val nativeClipboard: NativeClipboard get() = error("not used")
    }

    // A dispatcher that drops everything handed to it: only work that runs before the first
    // dispatch happens at all, which is what the click handlers rely on.
    private val neverRuns = CoroutineScope(java.util.concurrent.Executor { }.asCoroutineDispatcher())

    @Test
    fun `copy lands before the click handler returns`() {
        val memory = MemoryClipboard()
        TextClipboard(memory, neverRuns).copy("meshcore://contact/add?name=A")
        val clip = memory.entry?.clipData
        assertEquals("meshcore://contact/add?name=A", clip.firstText())
        assertTrue(clip!!.description.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN))
    }

    @Test
    fun `paste answers before the click handler returns, so a sheet can dismiss after it`() {
        val memory = MemoryClipboard().apply { entry = ClipEntry(plainTextClip("abc")) }
        var got: String? = "unset"
        TextClipboard(memory, neverRuns).paste { got = it }
        assertEquals("abc", got)
    }

    @Test
    fun `an empty clipboard pastes as null rather than as nothing happening`() {
        var called = false
        var got: String? = "unset"
        TextClipboard(MemoryClipboard(), neverRuns).paste { called = true; got = it }
        assertTrue(called)
        assertNull(got)
    }

    @Test
    fun `a clip without text reads as null`() {
        assertNull(null.firstText())
        assertNull(ClipData.newRawUri("", Uri.parse("content://x/1")).firstText())
        assertEquals("first", ClipData.newPlainText("", "first").apply { addItem(ClipData.Item("second")) }.firstText())
    }

    @Test
    fun `nothing uses the deprecated clipboard manager, and the sheet dismisses after pasting`() {
        val main = File("src/main/kotlin")
        val offenders = main.walk().filter { it.extension == "kt" && it.name != "TextClipboard.kt" }
            .filter { it.readText().contains("LocalClipboardManager") }.map { it.name }.toList()
        assertEquals(emptyList(), offenders)
        val sheets = File("src/main/kotlin/io/alvaroedo/meshconnect/ui/screens/ChannelSheets.kt").readText()
        assertTrue(sheets.contains("clipboard.paste {\n                                vm.importPastedText(it, openChat = newChat)\n                                onDismiss()"))
        assertFalse(sheets.contains("vm.importPastedText(it, openChat = newChat)\n                            }\n                            onDismiss()"))
    }
}
