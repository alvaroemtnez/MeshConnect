package io.alvaroedo.meshconnect.service

import io.github.thatsfguy.meshcore.transport.ConnectionMemory
import io.github.thatsfguy.meshcore.transport.SavedNode

/**
 * Moving between saved radios — one at home, one at work — without touching the app.
 *
 * Reconnecting only ever went back to the last radio used, so arriving at work meant the app
 * kept calling the home radio, out of range, until someone opened Settings. Now, once the
 * current Bluetooth radio has been unreachable for [UNREACHABLE_MS], the service listens
 * briefly for the OTHER saved Bluetooth radios and moves to the strongest one it hears.
 *
 * The wait is what keeps a radio that is only briefly out of range (a pocket, the next room)
 * from being dropped for another one on the table. Pure, so the rules are tested.
 */
object RadioRoaming {

    /** How long the current radio must be unreachable before looking for another. */
    const val UNREACHABLE_MS = 45_000L

    /** How often to check, and how long each listen lasts. */
    const val CHECK_EVERY_MS = 15_000L
    const val LISTEN_MS = 8_000L

    /** Should we listen for others now? Only when roaming from a Bluetooth radio. */
    fun shouldLook(
        enabled: Boolean,
        current: ConnectionMemory?,
        ready: Boolean,
        busy: Boolean,
        unreachableSinceMs: Long?,
        nowMs: Long,
    ): Boolean =
        enabled && !ready && !busy && current is ConnectionMemory.Ble &&
            unreachableSinceMs != null && nowMs - unreachableSinceMs >= UNREACHABLE_MS

    /** Saved Bluetooth radios other than [current]: what to listen for. */
    fun candidates(saved: List<SavedNode>, current: ConnectionMemory?): List<SavedNode> {
        val currentAddress = (current as? ConnectionMemory.Ble)?.address
        return saved
            .filter { it.kind == ConnectionMemory.KIND_BLE }
            .filterNot { currentAddress != null && it.address.equals(currentAddress, ignoreCase = true) }
            .distinctBy { it.address.uppercase() }
    }

    /**
     * The radio to move to: the strongest candidate heard, by address (case-insensitive,
     * since Android reports MACs upper-case and older saves may not be). Null when none was.
     */
    fun pick(candidates: List<SavedNode>, heardRssi: Map<String, Int>): ConnectionMemory.Ble? {
        val heard = heardRssi.mapKeys { it.key.uppercase() }
        return candidates
            .mapNotNull { c -> heard[c.address.uppercase()]?.let { c to it } }
            .maxByOrNull { it.second }
            ?.first
            ?.let { ConnectionMemory.Ble(it.address, it.name) }
    }
}
