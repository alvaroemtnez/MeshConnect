package io.alvaroedo.meshconnect.ui.model

/**
 * When repeater sessions stop being true: on a change of companion radio.
 *
 * A repeater grants a role to a CLIENT KEY (its ACL is keyed by the public key of the
 * radio that logged in), so a session is a fact about the pair (this radio, that
 * repeater). The app kept sessions keyed by the repeater alone, so switching companions
 * carried the old radio's grant across (reported 2026-11-11): admin tools shown to a radio
 * the repeater doesn't know, and a guest session following the user back to the radio
 * that is on the allowlist.
 *
 * Only a change between two KNOWN identities counts. The key reads "" while disconnected
 * or reconnecting, and a reconnect of the same radio keeps its sessions, as the repeater
 * does.
 */
object RadioSessions {

    /** The radio identity to remember after [now] is seen, and whether sessions must go. */
    fun onIdentity(lastKnown: String, now: String): Pair<String, Boolean> =
        if (now.isEmpty()) lastKnown to false
        else now to (lastKnown.isNotEmpty() && !lastKnown.equals(now, ignoreCase = true))
}
