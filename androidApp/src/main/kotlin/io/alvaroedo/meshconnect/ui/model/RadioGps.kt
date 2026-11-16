package io.alvaroedo.meshconnect.ui.model

/**
 * Where the radio's own position comes from (2026-11-16), as the firmware decides it.
 *
 * - A radio with GPS hardware lists a `gps` custom variable, "1" or "0"; one without lists
 *   nothing (EnvironmentSensorManager::getSettingName, only `if (gps_detected)`).
 * - With GPS on, the sensor loop overwrites the position on every fix
 *   (EnvironmentSensorManager::loop), so a position set by hand (CMD_SET_ADVERT_LATLON) lasts
 *   only until the next one. With GPS off, a position set by hand stays.
 *
 * Firmware that drives its GPS itself (Squatch Mesh on the T-Lora Pager: its own "follow gps live"
 * setting, writing node_lat directly) lists no variable and looks like a radio without one.
 *
 * Firmware that drives its GPS itself lists no variable, and looks like a radio without one:
 * Squatch Mesh (T-Lora Pager) has its own "gps on" and "follow gps live" settings and writes
 * node_lat directly, never through the sensor manager. Nothing in the companion protocol
 * reports those settings, so the no-GPS hint only says the radio may still override.
 *
 * So Identity offers "From the radio's GPS" or "Set by hand" only when there is a GPS, and a
 * position set by hand means switching GPS off first, or it would quietly be undone.
 */
object RadioGps {

    enum class State { None, On, Off }

    fun of(customVars: Map<String, String>): State = when (customVars["gps"]?.trim()) {
        null -> State.None
        "1" -> State.On
        else -> State.Off
    }

    /** Positions may be typed or picked only when nothing will overwrite them. */
    fun editable(state: State): Boolean = state != State.On

    /** How often Identity re-reads the position while GPS is on: over Bluetooth, not the air. */
    const val REFRESH_MS = 30_000L
}
