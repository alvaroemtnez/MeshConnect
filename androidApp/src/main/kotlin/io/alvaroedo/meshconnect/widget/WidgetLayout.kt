package io.alvaroedo.meshconnect.widget

/**
 * What the widget draws at a given size, in dp. Pure, so the rules are tested without a
 * launcher.
 *
 * 1.1 rethink (reviewed as mockups, 2026-11-11). 1.0 fitted blocks greedily around a fixed
 * card, and it showed: a 3×2 put the message beside the battery with empty space above it
 * and cut the repeater off, a 3×3 had empty space at the bottom, a 4×3 wasted most of
 * itself. Now every size is the same blocks:
 *
 * - **Header**: status dot, name, unread count (the mark too, from three columns).
 * - **Status**: signal bars and the repeater's hash (not in Simple mode), then battery: the
 *   top bar's order. No SNR: the bars show it. Narrow, on one line; wide, a narrow two-line
 *   column with the messages beside it.
 * - **Latest messages**, one per chat, and **nearby repeaters** (1.4, 2026-11-17): the
 *   repeaters are a block at the bottom, at most three rows and only as tall as those; the
 *   messages take the height above it, as many chats as fit with two lines each.
 *
 * The same function drew the mockups, so the reviewed frames are what ships.
 *
 * **Content, not just size** (on the phone, 2026-11-11): planning for the most each block
 * could hold left a blank band under a radio with no messages and two repeaters, because
 * the message's six lines and the card's six rows were reserved and never drawn. The plan
 * now takes what there is to show, and MeshWidget gives the last block the remaining
 * height, so any surplus (or a launcher reporting a size a little off) lands inside the
 * last block rather than under it.
 */
object WidgetLayout {

    data class Plan(
        /** One row (2×1, 4×1) rather than the card. */
        val row: Boolean,
        /** A row tall enough to look empty at the regular size: everything a size up. */
        val largeRow: Boolean,
        /** Three columns or more: the mark in the header, the messages beside the status. */
        val wide: Boolean,
        /** How many chats' latest messages to show; 0 hides them. */
        val messages: Int,
        /** Lines of text under each of those messages. */
        val messageLines: Int,
        /** Nearby repeaters to list; 0 hides the card. */
        val nearbyRows: Int,
    )

    /** Below this it is a row, not the card. */
    const val CARD_MIN_HEIGHT = 140f

    /** Three cells on a five-column grid are about 230 dp; four on four, 300+. */
    const val WIDE_MIN_WIDTH = 220f

    /** A row this tall draws a size up. */
    const val LARGE_ROW_MIN_HEIGHT = 96f

    const val PAD = 14f
    const val HEAD = 28f
    const val GAP = 10f
    /** Signal over battery, beside the messages. */
    const val STATUS_TWO_LINES = 40f
    /** Signal and battery on one line, stacked layouts. */
    const val STATUS_ONE_LINE = 20f
    /** Width of the two-line status column, wide layouts. */
    const val STATUS_WIDTH = 64f
    /**
     * The same column holding the link state in words instead (1.2, WidgetStatus): wide enough
     * that every state fits in two lines in English, Spanish and Valencian ("L'aplicació no
     * està oberta").
     */
    const val STATUS_WIDTH_WORDS = 88f
    const val MESSAGE_HEAD = 20f
    const val MESSAGE_LINE = 16f
    /** Between two messages. */
    const val MESSAGE_GAP = 6f
    const val NEARBY_HEAD = 30f
    const val NEARBY_ROW = 22f
    /**
     * At most three repeaters (asked for on the beta, 2026-11-17): past that the list was
     * taking height that more messages put to better use.
     */
    const val MAX_NEARBY = 3
    const val MAX_MESSAGES = 4
    const val MAX_MESSAGE_LINES = 6

    /** Height of [count] messages of [lines] lines each. */
    fun messagesHeight(count: Int, lines: Int): Float =
        if (count == 0) 0f else count * (MESSAGE_HEAD + lines * MESSAGE_LINE) + (count - 1) * MESSAGE_GAP

    /**
     * [messagesAvailable]: chats with a latest message to show. [nearbyAvailable]: repeaters
     * there are to list (an empty list still takes one row, for "none heard").
     *
     * The repeaters are a block at the bottom, as tall as their rows and no taller; the
     * messages take everything above it: as many chats as fit with two lines each, and any
     * height left over grows their lines.
     */
    fun plan(
        width: Float,
        height: Float,
        messagesAvailable: Int = MAX_MESSAGES,
        nearbyAvailable: Int = MAX_NEARBY,
    ): Plan {
        val wide = width >= WIDE_MIN_WIDTH
        if (height < CARD_MIN_HEIGHT) {
            return Plan(row = true, largeRow = height >= LARGE_ROW_MIN_HEIGHT, wide = wide, messages = 0, messageLines = 0, nearbyRows = 0)
        }
        val available = messagesAvailable.coerceIn(0, MAX_MESSAGES)
        var left = height - 2 * PAD - HEAD - GAP
        // What stands above the messages, and the least one message needs.
        val firstMessage = messagesHeight(1, 2)
        val band = if (wide) {
            // Status and messages side by side, as tall as the taller.
            maxOf(STATUS_TWO_LINES, if (available > 0) firstMessage else 0f)
        } else {
            STATUS_ONE_LINE + if (available > 0 && left - STATUS_ONE_LINE - GAP >= firstMessage) GAP + firstMessage else 0f
        }
        val oneMessageFits = available > 0 && (wide || band > STATUS_ONE_LINE)
        left -= band
        // The repeaters next, from the bottom.
        var nearby = 0
        if (left - GAP >= NEARBY_HEAD + NEARBY_ROW) {
            nearby = ((left - GAP - NEARBY_HEAD) / NEARBY_ROW).toInt()
                .coerceAtMost(MAX_NEARBY)
                .coerceAtMost(nearbyAvailable.coerceAtLeast(1))
        }
        if (!oneMessageFits) {
            return Plan(row = false, largeRow = false, wide = wide, messages = 0, messageLines = 0, nearbyRows = nearby)
        }
        // Everything else is the messages': the height the band reserved for one, plus the rest.
        var space = height - 2 * PAD - HEAD - GAP - (if (wide) 0f else STATUS_ONE_LINE + GAP)
        if (nearby > 0) space -= GAP + NEARBY_HEAD + nearby * NEARBY_ROW
        // The most chats that still get two lines each; then the lines grow.
        var count = 1
        for (k in available downTo 1) {
            if (messagesHeight(k, 2) <= space) {
                count = k
                break
            }
        }
        val lines = ((space - count * MESSAGE_HEAD - (count - 1) * MESSAGE_GAP) / (count * MESSAGE_LINE)).toInt()
            .coerceIn(2, MAX_MESSAGE_LINES)
        return Plan(row = false, largeRow = false, wide = wide, messages = count, messageLines = lines, nearbyRows = nearby)
    }
}
