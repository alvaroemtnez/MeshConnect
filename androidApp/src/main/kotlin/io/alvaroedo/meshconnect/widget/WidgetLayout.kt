package io.alvaroedo.meshconnect.widget

/**
 * What the widget draws at a given size, in dp (2026-11-10).
 *
 * It used to pick from fixed breakpoints, which Glance resolves to the largest one that fits:
 * when GrapheneOS moved to six shorter rows, a 3×2 fell just under the 270 dp the nearby
 * repeaters needed and drew the 180 dp card with the rest empty, and a 2×1 drew the same
 * small row whatever its height. The widget now gets its real size and this decides, so
 * every extra few dp go to something: larger text in a row, another line of the message,
 * another nearby repeater. Pure, so the rules are tested without a launcher.
 */
object WidgetLayout {

    data class Plan(
        /** One row of status (2×1, 4×1) rather than the card. */
        val row: Boolean,
        /** A row with room for a third line, where the repeater gets its own. */
        val roomyRow: Boolean,
        /** Row text and icons a size up, for a row tall enough to look empty otherwise. */
        val largeRow: Boolean,
        /** Wide enough for the latest message beside the gauges, or the mark in a row. */
        val wide: Boolean,
        /** The heard repeater's SNR under the gauges. */
        val snrLine: Boolean,
        /** Lines of the latest message: beside the gauges when [wide], under them otherwise. */
        val messageLines: Int,
        /** Nearby repeaters to list; 0 hides the card. */
        val nearbyRows: Int,
    )

    /** Below this the card cannot fit (header, status, gauges, unread, padding). */
    const val CARD_MIN_HEIGHT = 140f

    /** Three cells on a five-column grid are about 230 dp; four on four, 300+. */
    const val WIDE_MIN_WIDTH = 220f

    private const val CARD_BASE = 126f      // padding 28, header 26+6, status 16+8, gauges 18+6, unread 18
    private const val SNR_LINE = 16f
    private const val MESSAGE_HEAD = 34f    // gap, sender, age
    private const val MESSAGE_LINE = 15f
    private const val NEARBY_HEAD = 46f     // gap, padding, title
    private const val NEARBY_ROW = 22f
    const val MAX_NEARBY = 4

    fun plan(width: Float, height: Float): Plan {
        val wide = width >= WIDE_MIN_WIDTH
        if (height < CARD_MIN_HEIGHT) {
            return Plan(
                row = true,
                roomyRow = height >= 80f,
                largeRow = height >= 96f,
                wide = wide,
                snrLine = false,
                messageLines = 0,
                nearbyRows = 0,
            )
        }
        var left = height - CARD_BASE
        val messageLines: Int
        if (wide) {
            // Beside the gauges, in height they already use.
            messageLines = 3
        } else {
            // Under the gauges: only with room for at least two lines.
            val fit = ((left - MESSAGE_HEAD) / MESSAGE_LINE).toInt()
            messageLines = if (fit >= 2) fit.coerceAtMost(6) else 0
            if (messageLines > 0) left -= MESSAGE_HEAD + messageLines * MESSAGE_LINE
        }
        // Nearby before the SNR line: each nearby row carries its own SNR, and the list
        // says more than one reading does.
        val nearby = ((left - NEARBY_HEAD) / NEARBY_ROW).toInt().coerceIn(0, MAX_NEARBY)
        if (nearby > 0) left -= NEARBY_HEAD + nearby * NEARBY_ROW
        val snrLine = left >= SNR_LINE
        return Plan(
            row = false,
            roomyRow = false,
            largeRow = false,
            wide = wide,
            snrLine = snrLine,
            messageLines = messageLines,
            nearbyRows = nearby,
        )
    }
}
