package io.alvaroedo.meshconnect.ui.model

import io.alvaroedo.meshconnect.ui.ConversationRow

/**
 * How the Chats list is split: pinned on top, then recent, with archived chats out of the
 * way behind one "Archived" row. Pure, so the rules are tested rather than driven.
 *
 * Pinned and archived are exclusive: archiving a chat unpins it ([archive]), and a key in
 * both sets (an old backup, say) counts as archived, so a chat never shows in two places.
 */
object ChatListModel {

    data class Split(
        val pinned: List<ConversationRow>,
        val recent: List<ConversationRow>,
        val archived: List<ConversationRow>,
    ) {
        /** New messages wait in the archive: the Archived row shows a dot. */
        val archivedUnread: Boolean get() = archived.any { it.unread > 0 }
    }

    fun key(row: ConversationRow): String = "${row.kind}|${row.key}"

    fun split(rows: List<ConversationRow>, pinned: Set<String>, archived: Set<String>): Split {
        val (arch, live) = rows.partition { key(it) in archived }
        val (pin, rest) = live.partition { key(it) in pinned }
        return Split(pin, rest, arch)
    }

    /** New (pinned, archived) sets after archiving or unarchiving [key]. */
    fun archive(key: String, archive: Boolean, pinned: Set<String>, archived: Set<String>): Pair<Set<String>, Set<String>> =
        if (archive) (pinned - key) to (archived + key) else pinned to (archived - key)

    /**
     * A backup's thread-key set, validated: `kind|key` with a short lowercase kind and a
     * hex or decimal key. A backup file is handed to us, so anything else is dropped.
     */
    fun parseKeys(encoded: String, max: Int = 1000): Set<String> =
        encoded.split('\n')
            .map { it.trim() }
            .filter { THREAD_KEY.matches(it) }
            .take(max)
            .toSet()

    fun encodeKeys(keys: Set<String>): String = keys.sorted().joinToString("\n")

    private val THREAD_KEY = Regex("^[a-z]{1,12}\\|[0-9a-fA-F]{1,64}$")
}
