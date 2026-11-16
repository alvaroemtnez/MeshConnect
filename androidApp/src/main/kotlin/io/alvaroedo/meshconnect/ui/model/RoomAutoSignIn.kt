package io.alvaroedo.meshconnect.ui.model

import io.alvaroedo.meshconnect.storage.ContactEntity
import io.github.thatsfguy.meshcore.presentation.AdminSession
import io.github.thatsfguy.meshcore.protocol.Codes

/**
 * Signing in to rooms without a tap (2026-11-16), and keeping the airtime down while doing it.
 *
 * What the firmware does (examples/simple_room_server/MyMesh.cpp, checked 2026-11-16):
 * - A sign-in (ANON_REQ) makes us a client of the room and sets "sync since". There is no idle
 *   timeout: the room keeps the client, pushing each new post and waiting for our radio's ACK.
 * - Our RADIO acks those pushes, app or no app; three missed acks stop the pushes until we send
 *   the room anything (a post, a sign-in), which resets the count.
 * - Only admin clients are saved (saveFilter): a room that reboots forgets everyone else.
 * - The login reply's keep-alive interval is 0 ("Legacy"), so nothing has to be sent on a timer.
 *
 * So one sign-in per room per connection is enough, and this app's session record (in memory)
 * is the only thing that forgets. Signed in: when the radio connects, one room at a time, and
 * when a room is opened unsigned. Only with a saved password, one attempt each, no retries.
 */
object RoomAutoSignIn {

    /** Spacing between rooms on connect, so one connection is not a burst of logins. */
    const val SPACING_MS = 3_000L

    /** Time after connecting before starting: contacts and queued messages sync first. */
    const val SETTLE_MS = 6_000L

    /** Rooms to sign in to now: rooms, not signed in, with a saved password. */
    fun candidates(
        enabled: Boolean,
        contacts: List<ContactEntity>,
        sessions: Map<String, AdminSession>,
        hasSavedPassword: (String) -> Boolean,
    ): List<String> {
        if (!enabled) return emptyList()
        return contacts
            .filter { it.type == Codes.ADV_TYPE_ROOM }
            .filter { (sessions[it.keyHex] ?: AdminSession.None) == AdminSession.None }
            .map { it.keyHex }
            .filter(hasSavedPassword)
    }
}
