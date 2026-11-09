package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.protocol.RawPacket

/**
 * The repeaters this radio hears directly, and how well: the source of the
 * signal meter in the top bar.
 *
 * Every flooded packet the radio logs (`PUSH_CODE_LOG_RX_DATA`) carries the
 * path it took, and each repeater appends its own hash as it relays, so the
 * LAST entry is the node whose transmission our radio demodulated, and the
 * logged SNR/RSSI are that hop's. Earlier hops say nothing about our link
 * and are never credited (the same rule upstream's HeardRepeats follows).
 *
 * Direct-routed packets are skipped: their path is the planned route, not a
 * record of who transmitted last. A zero-hop packet came straight from its
 * sender, which may not be a repeater at all, so it is skipped too.
 *
 * The hop-hash width comes from each packet, never a constant (the
 * recurring defect in this codebase, see CLAUDE.md).
 */
object LinkMeter {

    /** Readings older than this no longer count as "hearing" a repeater. */
    const val STALE_MS: Long = 15L * 60 * 1000

    /** How many direct repeaters are remembered. */
    const val MAX_ENTRIES = 16

    data class DirectRepeater(
        /** Lower-case hex of the hop hash, as wide as the packet's path. */
        val prefixHex: String,
        val snr: Double,
        val rssi: Int,
        val lastHeardMs: Long,
        val packets: Int,
    )

    private const val ROUTE_TRANSPORT_FLOOD = 0x00
    private const val ROUTE_FLOOD = 0x01

    /** The hash of the repeater our radio heard this packet from, or null. */
    fun lastHop(packet: RawPacket): String? {
        if (packet.routeType != ROUTE_FLOOD && packet.routeType != ROUTE_TRANSPORT_FLOOD) return null
        val width = packet.pathHashWidth
        val hops = packet.hopCount
        if (hops == 0 || packet.pathBytes.size != hops * width) return null
        val last = packet.pathBytes.copyOfRange((hops - 1) * width, hops * width)
        return last.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
    }

    /** Fold one logged packet into the list. Unparseable or non-flood packets change nothing. */
    fun record(
        current: List<DirectRepeater>,
        rawPacket: ByteArray,
        snr: Double,
        rssi: Int,
        nowMs: Long,
        /**
         * Our radio's hop-hash width (DEVICE_INFO.pathHashByteWidth), or null if unknown.
         * A packet's path width is chosen by whoever sent it, so the same repeater arrives
         * as 2 bytes from one sender and 3 from another: it showed as `85A7` and `85A709`
         * and took two rows. Longer hashes are cut to our width; narrower ones are kept as
         * they are, because a 1-byte hash may belong to several repeaters and must not be
         * merged into one.
         */
        width: Int? = null,
    ): List<DirectRepeater> {
        val packet = RawPacket.parse(rawPacket) ?: return current
        val full = lastHop(packet) ?: return current
        val prefix = width?.takeIf { it in 1..4 }?.let { full.take(it * 2) } ?: full
        val old = current.firstOrNull { it.prefixHex == prefix }
        val updated = DirectRepeater(prefix, snr, rssi, nowMs, (old?.packets ?: 0) + 1)
        return (listOf(updated) + current.filter { it.prefixHex != prefix })
            .sortedByDescending { it.lastHeardMs }
            .take(MAX_ENTRIES)
    }

    /** Repeaters heard within [STALE_MS], strongest first. */
    fun fresh(all: List<DirectRepeater>, nowMs: Long): List<DirectRepeater> =
        all.filter { nowMs - it.lastHeardMs <= STALE_MS }.sortedByDescending { it.snr }

    /**
     * The demodulation floor for a spreading factor, in dB: Semtech's
     * figures, -7.5 at SF7 falling 2.5 dB per step to -20 at SF12.
     */
    fun snrFloor(sf: Int): Double = -7.5 - 2.5 * (sf.coerceIn(5, 12) - 7)

    /**
     * 0..4 bars from the margin above the floor for this SF. The same SNR
     * means a strong link at SF12 and a marginal one at SF7, which is why
     * MeshCore Open scales its meter by SF too.
     */
    fun bars(snr: Double, sf: Int?): Int {
        val margin = snr - snrFloor(sf ?: 10)
        return when {
            margin >= 10.0 -> 4
            margin >= 5.0 -> 3
            margin >= 2.5 -> 2
            margin >= 0.0 -> 1
            else -> 0
        }
    }
}
