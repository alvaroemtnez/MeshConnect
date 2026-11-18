package io.alvaroedo.meshconnect.ui.model

import io.alvaroedo.meshconnect.storage.MessageEntity
import io.github.thatsfguy.meshcore.presentation.DmRequest

/**
 * Requests to us waiting for an answer (DmRequest), whichever channel they came on, and which
 * ones have been answered (2026-11-18).
 *
 * In 1.3.0 only #mcn-internal surfaced a prompt, and an answered request was remembered by the
 * asker's key alone, for good and for every radio: once Marta had been accepted or declined,
 * no later request from her reached any radio on this phone as more than a notification that
 * opened nothing. An answer now covers the requests that had arrived when it was given, on the
 * radio that gave it ([Answers]); a request after that is a new one and asks again.
 */
object DmRequestInbox {

    /** Someone who asked us on channel [channelIndex], and has not been answered. */
    data class Pending(
        val name: String,
        val keyHex: String,
        val atSeconds: Long,
        val receivedAtMs: Long,
        val channelIndex: Int,
    ) {
        /**
         * What "Not now" puts off: this request, not the person. A request they send after it
         * is a new one and asks again, as it does after an answer.
         */
        val laterKey: String get() = "${keyHex.lowercase()}@$receivedAtMs"
    }

    /**
     * Requests addressed to us in [messages] (channel rows of one radio), oldest first: one
     * per asker (their latest), not answered since it arrived, not from a blocked key, and at
     * most a week old, like the answers we wait for (DmRequest.Outstanding.LIFETIME_SEC).
     */
    fun pending(
        messages: List<MessageEntity>,
        myName: String?,
        myKeyHex: String,
        answers: Answers,
        isBlocked: (String) -> Boolean,
        nowSec: Long,
    ): List<Pending> = messages
        .asSequence()
        .filter { !it.outgoing && nowSec - it.timestamp <= DmRequest.Outstanding.LIFETIME_SEC }
        .mapNotNull { m ->
            val channel = m.peerKey.toIntOrNull() ?: return@mapNotNull null
            val parsed = DmRequest.parse(m.text) as? DmRequest.Parsed.Request ?: return@mapNotNull null
            val asked = DmRequest.decide(parsed, m.senderName, myName, myKeyHex, DmRequest.Outstanding.NONE, nowSec)
                as? DmRequest.Action.AskedMe ?: return@mapNotNull null
            Pending(asked.requesterName, asked.requesterKeyHex, m.timestamp, m.receivedAt, channel)
        }
        .filterNot { answers.isAnswered(myKeyHex, it.keyHex, it.receivedAtMs) || isBlocked(it.keyHex) }
        .groupBy { it.keyHex }
        .map { (_, same) -> same.maxWith(compareBy({ it.atSeconds }, { it.receivedAtMs })) }
        .sortedBy { it.atSeconds }

    /**
     * When each asker was last answered, per radio: `"<our key>:<their key>"` to the moment
     * (our clock, ms). A request that arrived at or before it is answered; one after it is new.
     *
     * Our receive time, not the request's own timestamp: that is the asker's clock, and a slow
     * one would date a new request before the last answer and hide it.
     */
    class Answers(private val entries: Map<String, Long>) {

        fun isAnswered(selfKeyHex: String, requesterKeyHex: String, receivedAtMs: Long): Boolean {
            val r = requesterKeyHex.lowercase()
            val at = maxOf(entries[slot(selfKeyHex, r)] ?: -1L, entries[slot(ANY_RADIO, r)] ?: -1L)
            return at >= 0 && receivedAtMs <= at
        }

        /** After answering [requesterKeyHex] on [selfKeyHex] at [nowMs]; drops lapsed entries. */
        fun answered(selfKeyHex: String, requesterKeyHex: String, nowMs: Long): Answers =
            Answers(live(nowMs) + (slot(selfKeyHex, requesterKeyHex) to nowMs))

        /**
         * Entries still able to hide something. A request is shown for a week from its
         * timestamp, which is never more than a few minutes after we received it (MessageClock),
         * so an answer older than [KEEP_MS] has nothing left to cover.
         */
        fun live(nowMs: Long): Map<String, Long> = entries.filterValues { nowMs - it <= KEEP_MS }

        fun toMap(): Map<String, Long> = entries

        companion object {
            /** A 1.3.0 entry: it said who, but not on which radio. */
            const val ANY_RADIO = "*"
            const val KEEP_MS = (DmRequest.Outstanding.LIFETIME_SEC + 24 * 3600) * 1000
            val NONE = Answers(emptyMap())

            fun slot(selfKeyHex: String, requesterKeyHex: String) =
                "${selfKeyHex.lowercase()}:${requesterKeyHex.lowercase()}"

            /**
             * 1.3.0's set of answered askers, read as answered on every radio at [nowMs]: the
             * requests it hid stay hidden, and anything those people send later asks again.
             */
            fun fromLegacy(keys: Set<String>, nowMs: Long): Answers =
                Answers(keys.associate { slot(ANY_RADIO, it) to nowMs })
        }
    }

    /** What tapping a request's notification does. */
    sealed class Tap {
        /** Still waiting: bring its prompt up. */
        data class Show(val request: Pending) : Tap()

        /** Answered, and they are a contact: their chat, where the accept was written. */
        data class OpenChat(val keyHex: String) : Tap()

        /** Answered (declined, or accepted and since removed). */
        data object Answered : Tap()

        /** It was sent to another of our radios; this one cannot answer it. */
        data class OtherRadio(val radioName: String?) : Tap()

        /** Gone: past its week, or from someone since blocked. */
        data object Gone : Tap()
    }

    /**
     * Decide a tap on the notification for [requesterKeyHex]'s request to the radio
     * [notifiedSelfKeyHex] (null on a notification without one), posted at [postedAtMs], now
     * that [currentSelfKeyHex] is connected and [pending] are its waiting requests.
     */
    fun tap(
        requesterKeyHex: String,
        notifiedSelfKeyHex: String?,
        notifiedRadioName: String?,
        currentSelfKeyHex: String,
        pending: List<Pending>,
        answers: Answers,
        isContact: Boolean,
        postedAtMs: Long,
    ): Tap {
        val r = requesterKeyHex.lowercase()
        if (notifiedSelfKeyHex != null && !notifiedSelfKeyHex.equals(currentSelfKeyHex, ignoreCase = true)) {
            return Tap.OtherRadio(notifiedRadioName)
        }
        pending.firstOrNull { it.keyHex.equals(r, ignoreCase = true) }?.let { return Tap.Show(it) }
        // Not waiting. An answer covers every request that had arrived when it was given, so
        // the notification's request was answered if an answer came after it was posted.
        if (answers.isAnswered(currentSelfKeyHex, r, postedAtMs)) {
            return if (isContact) Tap.OpenChat(r) else Tap.Answered
        }
        return Tap.Gone
    }
}
