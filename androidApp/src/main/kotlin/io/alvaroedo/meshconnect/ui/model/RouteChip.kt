package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.protocol.PathCodec

/**
 * The words on a received message's "heard via" chip: the repeaters it
 * came through, in travel order (sender's side first, ours last).
 *
 * Only for a route that is actually known (`arrivalPathHex`, exact for a
 * channel post, correlated for a DM — see HeardVia). The chip never
 * claims more than the info sheet does:
 *
 *  - a hop is named only when exactly one contact matches its hash; an
 *    ambiguous or unknown hop shows the hash itself, never a guess
 *    (PARITY §12 — a 2-byte hop is 16 bits, collisions are real);
 *  - one or two hops are listed in full; longer routes keep the first and
 *    the last (the one that reached us) with the count of the rest between.
 */
object RouteChip {

    const val SEPARATOR = " › "

    fun hopLabel(hop: PathCodec.Hop): String =
        hop.candidates.singleOrNull()?.takeIf { it.isNotBlank() } ?: hop.hashHex.uppercase()

    /** Null for an empty route: zero hops is "direct", which the footer already says. */
    fun label(hops: List<PathCodec.Hop>): String? {
        val names = hops.map(::hopLabel)
        return when {
            names.isEmpty() -> null
            names.size <= 2 -> names.joinToString(SEPARATOR)
            else -> names.first() + SEPARATOR + "+${names.size - 2}" + SEPARATOR + names.last()
        }
    }
}
