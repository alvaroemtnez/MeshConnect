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
