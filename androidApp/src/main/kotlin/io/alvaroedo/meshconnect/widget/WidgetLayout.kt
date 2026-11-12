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
 * - **Status**: battery, then signal bars and the repeater's hash. No SNR: the bars show it.
 *   Narrow, on one line; wide, a narrow two-line column with the message beside it.
 * - **Latest message**, and **nearby repeaters**, which share the spare height in a fixed
 *   order: the message gets two lines, then repeaters get rows, then the message grows.
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
        /** Three columns or more: the mark in the header, the message beside the status. */
        val wide: Boolean,
        /** Lines of the latest message; 0 hides it. */
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
    /** Battery over signal, beside the message. */
    const val STATUS_TWO_LINES = 40f
    /** Battery and signal on one line, stacked layouts. */
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
    const val NEARBY_HEAD = 30f
    const val NEARBY_ROW = 22f
    const val MAX_NEARBY = 6
    const val MAX_MESSAGE_LINES = 6

    /**
     * [hasMessage]: there is a latest message to show. [nearbyAvailable]: repeaters there
     * are to list (an empty list still takes one row, for "none heard").
     */
    fun plan(width: Float, height: Float, hasMessage: Boolean = true, nearbyAvailable: Int = MAX_NEARBY): Plan {
        val wide = width >= WIDE_MIN_WIDTH
        if (height < CARD_MIN_HEIGHT) {
            return Plan(row = true, largeRow = height >= LARGE_ROW_MIN_HEIGHT, wide = wide, messageLines = 0, nearbyRows = 0)
        }
        var left = height - 2 * PAD - HEAD - GAP
        var lines = 0
        if (wide) {
            // Status and message share one band, as tall as the taller of the two. With no
            // message, the band is the status and "No messages yet" sits beside it.
            if (hasMessage) lines = 2
            left -= maxOf(STATUS_TWO_LINES, if (hasMessage) MESSAGE_HEAD + lines * MESSAGE_LINE else 0f)
        } else {
            left -= STATUS_ONE_LINE
            // Stacked, a missing message takes no height at all.
            if (hasMessage && left - GAP >= MESSAGE_HEAD + 2 * MESSAGE_LINE) {
                lines = 2
                left -= GAP + MESSAGE_HEAD + 2 * MESSAGE_LINE
            }
        }
        var nearby = 0
        if (left - GAP >= NEARBY_HEAD + NEARBY_ROW) {
            nearby = ((left - GAP - NEARBY_HEAD) / NEARBY_ROW).toInt()
                .coerceAtMost(MAX_NEARBY)
                .coerceAtMost(nearbyAvailable.coerceAtLeast(1))
            left -= GAP + NEARBY_HEAD + nearby * NEARBY_ROW
        }
        // Whatever is left grows the message.
        if (lines > 0) lines = (lines + (left / MESSAGE_LINE).toInt()).coerceAtMost(MAX_MESSAGE_LINES)
        return Plan(row = false, largeRow = false, wide = wide, messageLines = lines, nearbyRows = nearby)
    }
}
