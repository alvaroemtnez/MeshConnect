package io.alvaroedo.meshconnect.ui.model

/**
 * When to tell the user their radio's battery is running low.
 *
 * Fed by the reading the service already takes every few minutes while
 * connected (`refreshBattery`), so the alert costs the phone nothing: no
 * extra wake-ups, no extra radio traffic. It only decides.
 *
 * Two levels, each said once on the way down: [LOW] and then [CRITICAL].
 * A level re-arms only after the battery has climbed [REARM_MARGIN]
 * points above it — a cell hovering at 20% reads 19, 21, 20, 19 as the
 * load changes, and without the margin that is a notification every poll.
 *
 * The estimate comes from [io.github.thatsfguy.meshcore.presentation.BatteryLevel],
 * which returns null for anything that is not believably one Li-ion cell
 * (no sense line, USB rail, 2S pack). Null changes nothing: an alert
 * built on a guessed percentage would be worse than none.
 */
object LowBatteryAlert {

    const val LOW = 20
    const val CRITICAL = 10
    const val REARM_MARGIN = 10

    /** Which levels have already been announced for the current radio. */
    data class State(val saidLow: Boolean = false, val saidCritical: Boolean = false)

    enum class Notice { Low, Critical, Recovered }

    data class Step(val state: State, val notice: Notice?)

    fun step(state: State, percent: Int?): Step {
        if (percent == null) return Step(state, null)
        if (percent <= CRITICAL) {
            // Straight past LOW to CRITICAL (a radio first seen at 8%) says
            // the stronger thing once, not both.
            return if (state.saidCritical) {
                Step(state, null)
            } else {
                Step(State(saidLow = true, saidCritical = true), Notice.Critical)
            }
        }
        if (percent <= LOW) {
            val next = state.copy(
                saidLow = true,
                saidCritical = state.saidCritical && percent < CRITICAL + REARM_MARGIN,
            )
            return Step(next, if (state.saidLow) null else Notice.Low)
        }
        // Above LOW. Recovered only once it is clear of the hysteresis band,
        // and only if there was something to take back.
        if (percent >= LOW + REARM_MARGIN) {
            return if (state.saidLow || state.saidCritical) {
                Step(State(), Notice.Recovered)
            } else {
                Step(state, null)
            }
        }
        return Step(
            state.copy(saidCritical = state.saidCritical && percent < CRITICAL + REARM_MARGIN),
            null,
        )
    }
}
