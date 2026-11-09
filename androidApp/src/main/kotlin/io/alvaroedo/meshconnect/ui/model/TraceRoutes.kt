package io.alvaroedo.meshconnect.ui.model

/**
 * The hop list for a ping or a trace, built so the reply comes back to us.
 *
 * How the firmware handles a trace (src/Mesh.cpp:42-67): the payload lists
 * hop hashes; each listed node in turn appends the SNR it heard and
 * retransmits; whoever hears the transmission after the LAST listed hop
 * gets the result (companion_radio/MyMesh.cpp:855, PUSH_CODE_TRACE_DATA).
 * So the list has to end with a node in our own range: going out and
 * coming back along the same hops does that.
 *
 * - Ping a repeater, room or sensor: the route to it, the node, and the
 *   route back. A neighbour is just `[node]`.
 * - Trace towards a person: companions don't forward traces, so the list
 *   goes as far as the last repeater and back.
 *
 * Hashes are hex at the mesh's own hop-hash width: the caller derives them
 * from full keys at the current width (CLAUDE.md, the recurring defect).
 */
object TraceRoutes {

    /** Ping [targetHash] (a node that forwards) via [outPath], our route to it. */
    fun ping(outPath: List<String>, targetHash: String): List<String> =
        outPath + targetHash + outPath.reversed()

    /** Trace towards a companion over [outPath]; null when there is no repeater to trace. */
    fun towards(outPath: List<String>): List<String>? =
        if (outPath.isEmpty()) null else outPath + outPath.dropLast(1).reversed()

    /** Split a stored path into hop hashes of [width] bytes each, hex. */
    fun hops(path: ByteArray, width: Int): List<String> {
        if (width !in 1..4 || path.size % width != 0) return emptyList()
        return path.toList().chunked(width).map { hop ->
            hop.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }
        }
    }

    /** A node's hop hash: the first [width] bytes of its public key. */
    fun hashOf(publicKeyHex: String, width: Int): String = publicKeyHex.take(width * 2).lowercase()

    fun toBytes(hops: List<String>): ByteArray =
        hops.joinToString("").chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    /**
     * One leg of a finished trace: from the node before to [hopHash],
     * heard at [snr] dB. The last leg ends at us (hopHash null).
     */
    data class Leg(val fromHash: String?, val hopHash: String?, val snr: Double)

    /**
     * Legs of a reply: hop i heard the leg into it at snrs[i]; the final
     * SNR is how we heard the last hop. Null when the reply doesn't add up.
     */
    fun legs(hops: List<String>, snrs: List<Double>): List<Leg>? {
        if (snrs.size != hops.size + 1) return null
        return hops.indices.map { i -> Leg(hops.getOrNull(i - 1), hops[i], snrs[i]) } +
            Leg(hops.lastOrNull(), null, snrs.last())
    }
}
