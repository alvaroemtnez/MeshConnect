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
         * Public keys of the repeaters and rooms this radio knows of (contacts and heard
         * adverts). They decide whether a short hash is one node or several: see [fold].
         */
        knownKeys: Collection<String> = emptyList(),
    ): List<DirectRepeater> {
        val packet = RawPacket.parse(rawPacket) ?: return current
        val hop = lastHop(packet) ?: return current
        return fold(current, hop, snr, rssi, nowMs, knownKeys)
    }

    /**
     * One reading of [hop] folded into [current], one row per repeater.
     *
     * A path's hash width is chosen by whoever SENT the packet (their path hash mode), not
     * by the repeater that relayed it: each relay appends its hash at the packet's width. So
     * one repeater arrives as `AB` from a 1-byte sender, `ABCD` from a 2-byte one and
     * `ABCDEF` from a 3-byte one, and took three rows (reported 2026-11-17).
     *
     * A row is the widest hash seen for a repeater. A reading or row with a narrower hash
     * joins the wider row it is the start of, but only when that is exactly one row AND no
     * other known repeater starts with that hash too: a 1-byte hash routinely belongs to
     * several nodes, and merging it into one of them would credit someone else's signal.
     * When it is ambiguous it keeps a row of its own, showing the short hash, which is the
     * honest answer. The newest reading always sets the row's SNR and RSSI.
     */
    fun fold(
        current: List<DirectRepeater>,
        hop: String,
        snr: Double,
        rssi: Int,
        nowMs: Long,
        knownKeys: Collection<String> = emptyList(),
    ): List<DirectRepeater> {
        val prefix = hop.lowercase()
        val known = knownKeys.map { it.lowercase() }
        // Where the reading goes: its own row, or the one wider row it uniquely extends.
        val target = current.firstOrNull { it.prefixHex == prefix }?.prefixHex
            ?: uniqueWider(prefix, current.map { it.prefixHex }, known)
            ?: prefix
        val old = current.firstOrNull { it.prefixHex == target }
        val updated = DirectRepeater(target, snr, rssi, nowMs, (old?.packets ?: 0) + 1)
        val list = listOf(updated) + current.filter { it.prefixHex != target }
        return absorbNarrower(list, known)
            .sortedByDescending { it.lastHeardMs }
            .take(MAX_ENTRIES)
    }

    /** The one row strictly wider than [prefix] that starts with it, if [prefix] names one node. */
    private fun uniqueWider(prefix: String, rows: List<String>, known: List<String>): String? {
        val wider = rows.filter { it.length > prefix.length && it.startsWith(prefix) }
        // Rows that are themselves a prefix of one another are the same candidate.
        val widest = wider.filter { w -> wider.none { o -> o.length > w.length && o.startsWith(w) } }
        val only = widest.singleOrNull() ?: return null
        val others = known.count { it.startsWith(prefix) && !it.startsWith(only) }
        return only.takeIf { others == 0 }
    }

    /** Merge every row that uniquely extends into a wider one; newest reading wins. */
    private fun absorbNarrower(rows: List<DirectRepeater>, known: List<String>): List<DirectRepeater> {
        var out = rows
        // Narrowest first, so AB → ABCD → ABCDEF settles in one pass.
        for (row in rows.sortedBy { it.prefixHex.length }) {
            val into = uniqueWider(row.prefixHex, out.map { it.prefixHex }, known) ?: continue
            val wide = out.first { it.prefixHex == into }
            val newest = if (row.lastHeardMs > wide.lastHeardMs) row else wide
            val merged = DirectRepeater(into, newest.snr, newest.rssi, newest.lastHeardMs, row.packets + wide.packets)
            out = out.filter { it.prefixHex != row.prefixHex && it.prefixHex != into } + merged
        }
        return out
    }

    /**
     * The name of the repeater behind a hop hash, only on a unique match
     * among repeaters and rooms: a short hash can belong to several nodes,
     * and guessing would name the wrong one.
     *
     * [heard] are nodes heard advertising but not added (key to name): a
     * repeater's advert names it whether or not it is a contact, and Simple
     * mode shows no hashes, so a heard repeater with no name was a blank.
     */
    fun nameFor(
        prefixHex: String,
        contacts: Collection<io.github.thatsfguy.meshcore.model.Contact>,
        heard: Map<String, String> = emptyMap(),
    ): String? {
        val infra = contacts.filter {
            it.type == io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_REPEATER ||
                it.type == io.github.thatsfguy.meshcore.protocol.Codes.ADV_TYPE_ROOM
        }.associate { it.publicKeyHex.lowercase() to it.name }
        val all = heard.mapKeys { it.key.lowercase() } + infra
        return all.filterKeys { it.startsWith(prefixHex.lowercase()) }
            .values.singleOrNull()?.ifBlank { null }
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
