package io.alvaroedo.meshconnect.ui.model

import android.content.res.Resources
import io.alvaroedo.meshconnect.R
import io.github.thatsfguy.meshcore.presentation.RepeaterSignals
import io.github.thatsfguy.meshcore.protocol.Codes
import java.util.Locale

/**
 * Translated versions of text that upstream's shared/ builds in English.
 * Each mirrors the shared function named in its comment, same conditions
 * and order, so shared/ stays as upstream wrote it (FORK.md).
 */
object SharedText {

    /** HeardReach.of */
    fun heardReach(res: Resources, minHops: Int?, directSnr: Double?): String? = when {
        minHops == null || minHops < 0 -> null
        minHops == 0 -> directSnr?.let { res.getString(R.string.reach_direct_snr, snr(it)) }
            ?: res.getString(R.string.reach_direct)
        else -> res.getQuantityString(R.plurals.hops, minHops, minHops)
    }

    /** RepeaterSignals.describe */
    fun signals(res: Resources, s: RepeaterSignals.Signals): String? {
        val parts = buildList {
            if (s.heardDirect) add(res.getString(R.string.signals_heard_direct))
            when {
                s.relayed < RepeaterSignals.MIN_RELAYS -> {}
                s.relayAmbiguous -> add(res.getString(R.string.signals_ambiguous))
                else -> add(res.getQuantityString(R.plurals.signals_relayed, s.relayed, s.relayed))
            }
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** Hops from a stored path (the contact sheet's "Hops away"). */
    fun pathHops(res: Resources, isFlood: Boolean, hops: Int): String = when {
        isFlood -> res.getString(R.string.path_flood)
        hops == 0 -> res.getString(R.string.path_direct)
        else -> res.getQuantityString(R.plurals.hops, hops, hops)
    }

    /** ConversationScreen.hopsLabel (the bubble footer). */
    fun footerHops(res: Resources, hops: Int?): String? = when {
        hops == null -> null
        hops < 0 -> res.getString(R.string.footer_routed)
        hops == 0 -> res.getString(R.string.reach_direct)
        else -> res.getQuantityString(R.plurals.hops, hops, hops)
    }

    /**
     * A received message's SNR under its bubble. The radio reports the signal of the LAST
     * transmission it heard: after a relay that is the last repeater's, not the sender's, so
     * anything not known to be direct says so (upstream 0.10.16, MessageSignal).
     */
    fun snrFooter(res: Resources, snr: Double?, hops: Int?): String? {
        snr ?: return null
        val db = String.format(java.util.Locale.ROOT, "%.1f dB", snr)
        return if (hops == 0) db else res.getString(R.string.snr_last_hop, db)
    }

    /** The info sheet's label for that SNR: the sender's own only when heard direct. */
    fun snrInfoLabel(res: Resources, hops: Int?): String =
        res.getString(if (hops == 0) R.string.info_snr_direct else R.string.info_snr_last_hop)

    /** ConversationScreen.arrivalLabel (the info sheet). */
    fun arrival(res: Resources, hops: Int?): String? = when {
        hops == null -> null
        hops < 0 -> res.getString(R.string.arrival_routed)
        hops == 0 -> res.getString(R.string.arrival_direct)
        else -> res.getString(R.string.arrival_flooded, res.getQuantityString(R.plurals.hops, hops, hops))
    }

    /** channelSubtitle (ChannelSetup.kt): the line under a channel's title. */
    fun channelSubtitle(res: Resources, kind: io.github.thatsfguy.meshcore.protocol.ChannelKind?, region: String?): String {
        val head = res.getString(
            when (kind) {
                io.github.thatsfguy.meshcore.protocol.ChannelKind.Public -> R.string.chsub_public
                io.github.thatsfguy.meshcore.protocol.ChannelKind.Hashtag -> R.string.chsub_hashtag
                io.github.thatsfguy.meshcore.protocol.ChannelKind.Private -> R.string.chsub_private
                null -> R.string.chsub_unknown
            },
        )
        return if (region != null) "$head · #$region" else head
    }

    /** HeardVia.summary: how a message arrived. */
    fun heardVia(res: Resources, hops: Int?, route: String?, hashWidth: Int): String {
        val width = hashWidth.coerceIn(1, 4)
        val routed = route?.takeIf { it.isNotEmpty() && it.length % (width * 2) == 0 }
        return when {
            routed != null -> res.getQuantityString(
                R.plurals.via_repeaters, routed.length / (width * 2), routed.length / (width * 2),
            )
            hops != null && hops < 0 -> res.getString(R.string.via_routed)
            hops == 0 -> res.getString(R.string.via_direct)
            hops != null && hops > 0 -> res.getQuantityString(R.plurals.via_flooded, hops, hops)
            else -> res.getString(R.string.via_unknown)
        }
    }

    /** StaleNodes.keptNote */
    fun staleKept(res: Resources, sweep: io.github.thatsfguy.meshcore.presentation.StaleNodes.Sweep): String? {
        val parts = buildList {
            if (sweep.favouritesKept > 0) add(res.getQuantityString(R.plurals.stale_kept_fav, sweep.favouritesKept, sweep.favouritesKept))
            if (sweep.neverHeardKept > 0) add(res.getString(R.string.stale_kept_never, sweep.neverHeardKept))
            if (sweep.freshKept > 0) add(res.getString(R.string.stale_kept_fresh, sweep.freshKept))
        }
        if (parts.isEmpty()) return null
        val and = res.getString(R.string.nodes_and)
        val joined = if (parts.size == 1) parts[0] else parts.dropLast(1).joinToString(", ") + and + parts.last()
        return res.getString(R.string.stale_keeping, joined)
    }

    /** roomAccessNotice (RoomModel.kt) */
    fun roomNotice(res: Resources, session: io.github.thatsfguy.meshcore.presentation.AdminSession): String? = when (session) {
        io.github.thatsfguy.meshcore.presentation.AdminSession.None -> res.getString(R.string.room_not_signed_in)
        io.github.thatsfguy.meshcore.presentation.AdminSession.Guest -> res.getString(R.string.room_guest)
        else -> null
    }

    /** roomAccessAction (RoomModel.kt) */
    fun roomAction(res: Resources, session: io.github.thatsfguy.meshcore.presentation.AdminSession): String? = when (session) {
        io.github.thatsfguy.meshcore.presentation.AdminSession.None -> res.getString(R.string.login_sign_in)
        io.github.thatsfguy.meshcore.presentation.AdminSession.Guest -> res.getString(R.string.room_sign_in_again)
        else -> null
    }

    fun kind(res: Resources, type: Int): String = res.getString(
        when (type) {
            Codes.ADV_TYPE_REPEATER -> R.string.kind_repeater
            Codes.ADV_TYPE_ROOM -> R.string.kind_room
            Codes.ADV_TYPE_SENSOR -> R.string.kind_sensor
            else -> R.string.kind_contact
        },
    )

    /** LinkQuality.label: the signal band, on the ping and trace screens and the map. */
    fun linkQuality(res: Resources, q: io.github.thatsfguy.meshcore.presentation.LinkQuality): String = res.getString(
        when (q) {
            io.github.thatsfguy.meshcore.presentation.LinkQuality.Strong -> R.string.link_strong
            io.github.thatsfguy.meshcore.presentation.LinkQuality.Good -> R.string.link_good
            io.github.thatsfguy.meshcore.presentation.LinkQuality.Fair -> R.string.link_fair
            io.github.thatsfguy.meshcore.presentation.LinkQuality.Weak -> R.string.link_weak
            io.github.thatsfguy.meshcore.presentation.LinkQuality.Marginal -> R.string.link_marginal
        },
    )

    /** UndrawableLink.reason */
    fun undrawable(res: Resources, u: io.github.thatsfguy.meshcore.presentation.UndrawableLink): String = res.getString(
        when (u) {
            io.github.thatsfguy.meshcore.presentation.UndrawableLink.Unknown -> R.string.link_unknown_node
            io.github.thatsfguy.meshcore.presentation.UndrawableLink.Ambiguous -> R.string.link_ambiguous
            io.github.thatsfguy.meshcore.presentation.UndrawableLink.NoPosition -> R.string.link_no_position
        },
    )

    /** Neighbours.heardAgoLabel: same thresholds, same units. */
    fun heardAgo(res: Resources, secondsAgo: Long): String = when {
        secondsAgo < 0 -> res.getString(R.string.nb_clock_skew)
        secondsAgo < 60 -> res.getString(R.string.nb_ago_s, secondsAgo.toInt())
        secondsAgo < 3_600 -> res.getString(R.string.nb_ago_m, (secondsAgo / 60).toInt())
        secondsAgo < 86_400 -> res.getString(R.string.nb_ago_h, (secondsAgo / 3_600).toInt())
        else -> res.getString(R.string.nb_ago_d, (secondsAgo / 86_400).toInt())
    }

    /** NeighbourLink.mapLabel: "Fair · -4.2 dB", the chip on the line. */
    fun neighbourChip(res: Resources, link: io.github.thatsfguy.meshcore.presentation.NeighbourLink): String =
        "${linkQuality(res, link.quality)} · ${link.snrLabel}"

    /** NeighbourLink.summary, plus why it has no line: "Fair · -4.2 dB · 12m ago · no position". */
    fun neighbourSummary(res: Resources, link: io.github.thatsfguy.meshcore.presentation.NeighbourLink): String =
        listOfNotNull(
            linkQuality(res, link.quality),
            link.snrLabel,
            heardAgo(res, link.secondsAgo),
            link.undrawable?.let { undrawable(res, it) },
        ).joinToString(" · ")

    private fun snr(v: Double) = String.format(Locale.ROOT, "%.1f dB", v)
}
