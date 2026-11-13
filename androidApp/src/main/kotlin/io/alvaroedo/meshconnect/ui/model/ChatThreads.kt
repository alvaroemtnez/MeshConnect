package io.alvaroedo.meshconnect.ui.model

import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.storage.MessageRepository
import io.github.thatsfguy.meshcore.presentation.Inbox
import io.github.thatsfguy.meshcore.protocol.Codes

/**
 * Which threads are chats: the one rule behind the Chats list (MeshCoreViewModel.conversations),
 * the Chats tab badge and the home-screen widget. Pure, so they cannot disagree untested.
 *
 * 1.2.1-beta.2 (reported 2026-11-13: "the unread chats indicator in the widget is not displaying
 * the correct number"). The widget summed the `unread` column of every contact and channel row,
 * while the list left repeater threads out. A repeater's thread is its admin console, and every
 * console reply was counted as unread with nothing in the app able to clear it, so the widget
 * climbed with each `get` and never came back down. Two smaller leaks of the same shape: a
 * contact whose messages retention deleted, and a contact row with no thread at all.
 */
object ChatThreads {

    /** A direct thread with this peer is a chat; a repeater's is its admin console, kept in the repeater screen. */
    fun isChatPeer(contact: ContactEntity?): Boolean = contact?.type != Codes.ADV_TYPE_REPEATER

    /** [latest] (one message per thread) without the threads the Chats list leaves out. */
    fun chatLatest(latest: List<MessageEntity>, contacts: Map<String, ContactEntity>): List<MessageEntity> =
        latest.filter { it.kind == MessageRepository.KIND_CHANNEL || isChatPeer(contacts[it.peerKey]) }

    /**
     * The unread count of each row the Chats list shows, the same numbers the rows carry: every
     * channel (a joined channel is a row even before its first message), and each direct thread
     * that has messages and is not a repeater's.
     */
    fun unreadCounts(
        latest: List<MessageEntity>,
        contacts: List<ContactEntity>,
        channels: List<ChannelEntity>,
    ): List<Int> {
        val byKey = contacts.associateBy { it.keyHex }
        val direct = chatLatest(latest, byKey)
            .filter { it.kind != MessageRepository.KIND_CHANNEL }
            .map { it.peerKey }
            .distinct()
            .map { byKey[it]?.unread ?: 0 }
        return channels.map { it.unread } + direct
    }

    /** The total the widget shows: the Chats tab badge's number, before the badge's "99+". */
    fun unreadTotal(
        latest: List<MessageEntity>,
        contacts: List<ContactEntity>,
        channels: List<ChannelEntity>,
    ): Int = Inbox.unreadTotal(unreadCounts(latest, contacts, channels))
}
