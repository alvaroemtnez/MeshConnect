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

    private fun snr(v: Double) = String.format(Locale.ROOT, "%.1f dB", v)
}
