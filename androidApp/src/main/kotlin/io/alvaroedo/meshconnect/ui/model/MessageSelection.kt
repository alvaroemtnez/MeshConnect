package io.alvaroedo.meshconnect.ui.model

import io.alvaroedo.meshconnect.storage.MessageEntity

/**
 * Selecting several messages in a thread: which actions the selection bar offers, and what
 * Copy puts on the clipboard. Pure, so the rules are tested without a device.
 */
object MessageSelection {

    /** Toggle [id]; an empty result means selection mode ends. */
    fun toggle(selected: Set<Long>, id: Long): Set<Long> =
        if (id in selected) selected - id else selected + id

    /** Reply quotes one message, so it is offered for exactly one. */
    fun canReply(selected: Set<Long>): Boolean = selected.size == 1

    /**
     * The clipboard text for a selection, oldest first whatever order it was picked in.
     *
     * One message copies as its text alone, as the single-message Copy always has. Several
     * copy as one line each; on a channel or room each received line is prefixed with the
     * name it claimed, or a pasted run of a group conversation loses who said what. The name
     * is unauthenticated display text — it is copied as text, the same as it is shown.
     */
    fun copyText(messages: List<MessageEntity>, selected: Set<Long>, showSenders: Boolean): String {
        val picked = messages.filter { it.id in selected }
            .sortedWith(compareBy({ it.timestamp }, { it.id }))
        if (picked.size == 1) return picked.single().text
        return picked.joinToString("\n") { m ->
            val name = m.senderName?.trim()?.ifEmpty { null }
            if (showSenders && !m.outgoing && name != null) "$name: ${m.text}" else m.text
        }
    }
}
