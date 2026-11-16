package io.alvaroedo.meshconnect.ui.model

import io.alvaroedo.meshconnect.storage.MessageRepository
import io.github.thatsfguy.meshcore.presentation.AppMode
import io.github.thatsfguy.meshcore.protocol.ChannelKind
import io.github.thatsfguy.meshcore.protocol.Codes

/** Where a code or link came in: decides what opens once it is in (ScanFollowUp). */
enum class ScanOrigin {
    /** Chats: its ⋮ scanner, New chat's scan and paste, a card in a message, a tapped link. */
    Chats,

    /** The Nodes tab: its scan button, ⋮ scan and paste. */
    Nodes,

    /** Settings' own scanners (radio codes): nothing to open afterwards. */
    Elsewhere,
}

/**
 * What a scanned or pasted code leads to (2026-11-16). Every code ends somewhere you can act on
 * it, whether it was just added or was already there:
 *
 * - From Chats, a person, a room or a group opens its conversation.
 * - From Nodes, a person or a room opens its card; a group still opens its conversation,
 *   since a channel has no node card.
 * - A repeater or a sensor opens its card from either: there is no conversation to open.
 * - Simple mode refuses repeaters, sensors and channels anyone can join, before any dialog and
 *   without adding anything: they are the full app's, and adding one there would put it where
 *   Simple mode does not show it.
 *
 * A thread is `(kind, key)` as conversationRoute takes it.
 */
object ScanFollowUp {

    sealed interface Step {
        data class OpenChat(val kind: String, val key: String) : Step

        /** A contact's card, by public key. */
        data class OpenCard(val keyHex: String) : Step

        data object Nothing : Step
    }

    fun forContact(origin: ScanOrigin, type: Int, pubKeyHex: String): Step {
        val key = pubKeyHex.lowercase()
        if (key.length != Codes.PUB_KEY_SIZE * 2) return Step.Nothing
        val talks = type == Codes.ADV_TYPE_CHAT || type == Codes.ADV_TYPE_ROOM
        return when (origin) {
            ScanOrigin.Elsewhere -> Step.Nothing
            ScanOrigin.Chats -> if (talks) Step.OpenChat(MessageRepository.KIND_DM, key) else Step.OpenCard(key)
            ScanOrigin.Nodes -> Step.OpenCard(key)
        }
    }

    /** A channel slot: the one just written, or the one already holding that key. */
    fun forChannel(origin: ScanOrigin, index: Int): Step =
        if (origin == ScanOrigin.Elsewhere || index < 0) {
            Step.Nothing
        } else {
            Step.OpenChat(MessageRepository.KIND_CHANNEL, index.toString())
        }

    /** A node code Simple mode turns away: anything you cannot talk to. */
    fun refusesContact(mode: AppMode, type: Int): Boolean =
        mode == AppMode.Simple && type != Codes.ADV_TYPE_CHAT && type != Codes.ADV_TYPE_ROOM

    /** A channel code Simple mode turns away: Public and hashtags (SimpleMode.showsChannel). */
    fun refusesChannel(mode: AppMode, kind: ChannelKind): Boolean = !SimpleMode.showsChannel(mode, kind)

    /**
     * After adding a PERSON who was not a contact yet: their radio drops our direct messages
     * until they add us too, so say so. A room learns who we are when we sign in.
     */
    fun warnsToAddBack(wasNew: Boolean, type: Int): Boolean = wasNew && type == Codes.ADV_TYPE_CHAT
}
