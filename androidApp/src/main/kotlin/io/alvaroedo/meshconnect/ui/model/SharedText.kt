package io.alvaroedo.meshconnect.ui.model

import android.content.Context
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
    fun heardReach(context: Context, minHops: Int?, directSnr: Double?): String? = when {
        minHops == null || minHops < 0 -> null
        minHops == 0 -> directSnr?.let { context.getString(R.string.reach_direct_snr, snr(it)) }
            ?: context.getString(R.string.reach_direct)
        else -> context.resources.getQuantityString(R.plurals.hops, minHops, minHops)
    }

    /** RepeaterSignals.describe */
    fun signals(context: Context, s: RepeaterSignals.Signals): String? {
        val parts = buildList {
            if (s.heardDirect) add(context.getString(R.string.signals_heard_direct))
            when {
                s.relayed < RepeaterSignals.MIN_RELAYS -> {}
                s.relayAmbiguous -> add(context.getString(R.string.signals_ambiguous))
                else -> add(context.resources.getQuantityString(R.plurals.signals_relayed, s.relayed, s.relayed))
            }
        }
        return parts.joinToString(" · ").ifEmpty { null }
    }

    /** Hops from a stored path (the contact sheet's "Hops away"). */
    fun pathHops(context: Context, isFlood: Boolean, hops: Int): String = when {
        isFlood -> context.getString(R.string.path_flood)
        hops == 0 -> context.getString(R.string.path_direct)
        else -> context.resources.getQuantityString(R.plurals.hops, hops, hops)
    }

    /** ConversationScreen.hopsLabel (the bubble footer). */
    fun footerHops(context: Context, hops: Int?): String? = when {
        hops == null -> null
        hops < 0 -> context.getString(R.string.footer_routed)
        hops == 0 -> context.getString(R.string.reach_direct)
        else -> context.resources.getQuantityString(R.plurals.hops, hops, hops)
    }

    /** ConversationScreen.arrivalLabel (the info sheet). */
    fun arrival(context: Context, hops: Int?): String? = when {
        hops == null -> null
        hops < 0 -> context.getString(R.string.arrival_routed)
        hops == 0 -> context.getString(R.string.arrival_direct)
        else -> context.getString(R.string.arrival_flooded, context.resources.getQuantityString(R.plurals.hops, hops, hops))
    }

    /** channelSubtitle (ChannelSetup.kt): the line under a channel's title. */
    fun channelSubtitle(context: Context, kind: io.github.thatsfguy.meshcore.protocol.ChannelKind?, region: String?): String {
        val head = context.getString(
            when (kind) {
                io.github.thatsfguy.meshcore.protocol.ChannelKind.Public -> R.string.chsub_public
                io.github.thatsfguy.meshcore.protocol.ChannelKind.Hashtag -> R.string.chsub_hashtag
                io.github.thatsfguy.meshcore.protocol.ChannelKind.Private -> R.string.chsub_private
                null -> R.string.chsub_unknown
            },
        )
        return if (region != null) "$head · #$region" else head
    }

    fun kind(context: Context, type: Int): String = context.getString(
        when (type) {
            Codes.ADV_TYPE_REPEATER -> R.string.kind_repeater
            Codes.ADV_TYPE_ROOM -> R.string.kind_room
            Codes.ADV_TYPE_SENSOR -> R.string.kind_sensor
            else -> R.string.kind_contact
        },
    )

    private fun snr(v: Double) = String.format(Locale.ROOT, "%.1f dB", v)
}
