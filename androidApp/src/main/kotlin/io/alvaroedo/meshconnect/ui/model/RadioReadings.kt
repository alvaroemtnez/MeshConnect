package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.protocol.RadioStats

/**
 * How the top-bar sheet reads the radio's own stats (upstream issue #6), with the rules of
 * upstream's `SignalReadout` but returning values instead of English sentences, so the
 * wording goes through the string table.
 *
 * - A noise floor of 0 is the firmware's "not measured yet", and -120 is its clamp, not a
 *   reading (RadioLibWrappers.cpp).
 * - "Last RSSI/SNR" is whichever node was decoded last; with packet counters, zero received
 *   means nothing has been heard, and without them an all-zero reading is the untouched
 *   initial state.
 *
 * Whether to keep polling is upstream's `SignalPoll`, used as is.
 */
object RadioReadings {

    sealed class Noise {
        data object Measuring : Noise()

        /** At the firmware's clamp: this or quieter. */
        data class AtClamp(val dbm: Int) : Noise()

        data class Value(val dbm: Int) : Noise()
    }

    fun noise(dbm: Int): Noise = when {
        dbm == 0 -> Noise.Measuring
        dbm <= RadioStats.NOISE_FLOOR_CLAMP -> Noise.AtClamp(RadioStats.NOISE_FLOOR_CLAMP)
        else -> Noise.Value(dbm)
    }

    fun nothingHeard(radio: RadioStats.Radio, packets: RadioStats.Packets?): Boolean =
        if (packets != null) packets.received == 0L else radio.lastRssiDbm == 0 && radio.lastSnrDb == 0.0

    /**
     * Noise-floor samples while the sheet is open, for a low/high line. Unmeasured (0)
     * samples are left out, or "high 0 dBm" would report the placeholder as the loudest
     * moment.
     */
    class NoiseRange {
        private val samples = ArrayDeque<Int>()

        fun add(dbm: Int) {
            if (dbm == 0) return
            samples.addLast(dbm)
            if (samples.size > MAX_SAMPLES) samples.removeFirst()
        }

        /** (low, high), or null with fewer than two samples. */
        fun range(): Pair<Int, Int>? = if (samples.size < 2) null else samples.min() to samples.max()

        companion object {
            /** About three minutes at the sheet's 3 s poll. */
            const val MAX_SAMPLES = 60
        }
    }
}
