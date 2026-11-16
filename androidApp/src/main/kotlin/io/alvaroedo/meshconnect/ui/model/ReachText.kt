package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.presentation.Reachability

/**
 * Which sentence says what [Reachability] concluded, without upstream's English.
 *
 * [Reachability.of] decides the state and writes its sentence in one go; the state is all
 * the UI can translate, and the sentence also depends on WHY (a fresh confirmation, or a
 * repeater relaying us; an attempt that never arrived, or none made). So the view model
 * keeps the evidence beside the verdict, and this picks the line by the same branches.
 */
object ReachText {

    /** A verdict and the evidence it was drawn from. */
    data class Info(val verdict: Reachability.Verdict, val evidence: Reachability.Evidence) {
        val state: Reachability.State get() = verdict.state
    }

    sealed class Line {
        /** Two-way, confirmed at [at] (epoch seconds), within the fresh window. */
        data class Confirmed(val at: Long) : Line()

        /** Two-way: a repeater heard relaying our own packets. */
        data object RepeatsYou : Line()

        /** Two-way before, last confirmed at [at]. */
        data class ConfirmedBefore(val at: Long) : Line()

        /** They sent a route back; nothing we sent confirmed yet. */
        data object TheyHeardYou : Line()

        /** Heard, and something we sent never arrived. */
        data object NotReached : Line()

        /** Heard; nothing shows they hear us. */
        data object NoSignOfHearing : Line()

        /** Tried, never heard from, nothing confirmed. */
        data object NoAnswer : Line()

        /** Nothing known either way: say nothing. */
        data object None : Line()
    }

    fun line(info: Info, nowSeconds: Long): Line {
        val e = info.evidence
        val at = e.confirmedAt
        return when (info.state) {
            Reachability.State.TwoWay ->
                if (at != null && nowSeconds - at <= Reachability.FRESH_SECONDS) Line.Confirmed(at) else Line.RepeatsYou
            Reachability.State.TwoWayBefore -> Line.ConfirmedBefore(at ?: 0)
            Reachability.State.TheyHeardYou -> Line.TheyHeardYou
            Reachability.State.HeardOnly -> if (e.tried) Line.NotReached else Line.NoSignOfHearing
            Reachability.State.NoAnswer -> Line.NoAnswer
            Reachability.State.Unknown -> Line.None
        }
    }
}
