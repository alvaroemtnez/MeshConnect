package io.alvaroedo.meshconnect.ui.model

import android.content.res.Resources
import io.alvaroedo.meshconnect.R
import io.github.thatsfguy.meshcore.presentation.DmRequest

/**
 * The line a direct-message request or reply reads as, in place of its wire text
 * (`DM request for @[Bob] mch:r:<key>`), in the thread, the chat list and notifications.
 *
 * Upstream's `DmRequest.describe` with the same branches, through the string table. The
 * wire text itself is never translated: it is what MeshCore Hardened parses.
 */
object DmRequestText {

    /** The line for [text] if it is a request or reply, else null. */
    fun lineFor(
        res: Resources,
        text: String,
        senderName: String?,
        outgoing: Boolean,
        myName: String?,
        myKeyHex: String,
    ): String? = DmRequest.parse(text)?.let { describe(res, it, senderName, outgoing, myName, myKeyHex) }

    fun describe(
        res: Resources,
        parsed: DmRequest.Parsed,
        senderName: String?,
        outgoing: Boolean,
        myName: String?,
        myKeyHex: String,
    ): String {
        val sender = senderName?.trim()?.ifEmpty { null } ?: res.getString(R.string.dm_someone)
        val me = myKeyHex.trim().lowercase().takeIf { it.length == 64 }
        return when (parsed) {
            is DmRequest.Parsed.Request -> when {
                outgoing -> res.getString(R.string.dm_req_you_asked, parsed.targetName)
                myName != null && same(parsed.targetName, myName) -> res.getString(R.string.dm_req_asked_you, sender)
                else -> res.getString(R.string.dm_req_other, sender, parsed.targetName)
            }
            is DmRequest.Parsed.Accept -> when {
                outgoing -> res.getString(R.string.dm_acc_you, parsed.requesterName)
                me != null && me.startsWith(parsed.requesterPrefixHex) -> res.getString(R.string.dm_acc_yours, sender)
                else -> res.getString(R.string.dm_acc_other, sender, parsed.requesterName)
            }
        }
    }

    private fun same(a: String, b: String) = a.trim().lowercase() == b.trim().lowercase()
}
