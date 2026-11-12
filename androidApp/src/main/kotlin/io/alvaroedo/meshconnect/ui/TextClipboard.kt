package io.alvaroedo.meshconnect.ui

import android.content.ClipData
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.launch

/**
 * Plain-text copy and paste over Compose's suspend-only [Clipboard], which replaced the
 * deprecated `LocalClipboardManager`. Android's clipboard answers at once, so starting
 * undispatched runs each call before the click handler returns, as the old blocking API did:
 * a sheet that pastes and then dismisses itself does not cancel its own paste.
 */
class TextClipboard internal constructor(
    private val clipboard: Clipboard,
    private val scope: CoroutineScope,
) {
    fun copy(text: String) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            clipboard.setClipEntry(ClipEntry(plainTextClip(text)))
        }
    }

    /** [onText] gets the clipboard's text, or null when it holds none. */
    fun paste(onText: (String?) -> Unit) {
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            onText(clipboard.getClipEntry()?.clipData.firstText())
        }
    }
}

@Composable
fun rememberTextClipboard(): TextClipboard {
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    return remember(clipboard, scope) { TextClipboard(clipboard, scope) }
}

/** The label Compose's old `setText` used. */
internal fun plainTextClip(text: String): ClipData = ClipData.newPlainText("plain text", text)

/** The first item's text, as the old `getText` read it; null for an empty or non-text clip. */
internal fun ClipData?.firstText(): String? =
    this?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
