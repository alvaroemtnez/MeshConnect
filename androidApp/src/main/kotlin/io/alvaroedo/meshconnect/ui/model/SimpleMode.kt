package io.alvaroedo.meshconnect.ui.model

import io.alvaroedo.meshconnect.storage.ChannelEntity
import io.alvaroedo.meshconnect.storage.ContactEntity
import io.alvaroedo.meshconnect.storage.MessageRepository
import io.github.thatsfguy.meshcore.presentation.AppMode
import io.github.thatsfguy.meshcore.presentation.AppModes
import io.github.thatsfguy.meshcore.protocol.ChannelKind
import io.github.thatsfguy.meshcore.protocol.Codes

/**
 * MeshConnect's Simple mode: contacts, groups and rooms, joined by link or QR.
 *
 * Upstream's [AppModes] (shared/) decides the tabs and the Settings tiles, and stays as
 * upstream wrote it (FORK.md). What MeshConnect decides differently lives here, pure, so it is
 * tested and so the Chats list, its badge, the widget and notifications cannot disagree:
 *
 * - Channels anyone can join are hidden: Public (every radio has it) and hashtag channels
 *   (the key is the name, so they are public too). A group is a private channel.
 * - Rooms are shown. A room is a group with a server behind it; who runs it does not matter
 *   to the person reading it.
 *
 * Like upstream's, it only HIDES. Nothing is left, muted or deleted on the radio, so switching
 * back shows everything as it was.
 */
object SimpleMode {

    fun isSimple(mode: AppMode): Boolean = mode == AppMode.Simple

    /**
     * Whether a channel is listed: in Chats, in Settings → Channels, in New chat. A channel
     * whose kind is not known (its key could not be read) is shown: hiding someone's group
     * because we could not classify it is the wrong way round.
     */
    fun showsChannel(mode: AppMode, kind: ChannelKind?): Boolean =
        mode == AppMode.Full || (kind != ChannelKind.Public && kind != ChannelKind.Hashtag)

    /** A direct thread with this kind of node: people and rooms. Unknown is shown. */
    fun showsContactType(mode: AppMode, contactType: Int?): Boolean =
        mode == AppMode.Full || contactType == null ||
            contactType == Codes.ADV_TYPE_CHAT || contactType == Codes.ADV_TYPE_ROOM

    /**
     * Whether a thread appears in Chats, counts on the badge and the widget, and notifies.
     * One rule for all four: a notification for a thread you cannot open is a dead end.
     */
    fun showsThread(mode: AppMode, isChannel: Boolean, channelKind: ChannelKind?, contactType: Int?): Boolean =
        if (isChannel) showsChannel(mode, channelKind) else showsContactType(mode, contactType)

    /** A Settings hub tile. Upstream's list; "advanced" is not in it, so it is hidden too. */
    fun showsSettingsTile(mode: AppMode, route: String): Boolean = AppModes.showsSettingsTile(mode, route)

    /** Advert ("announce") buttons: discovery is the full app's; Simple mode joins by link or QR. */
    fun showsAdverts(mode: AppMode): Boolean = mode == AppMode.Full

    /** Rooms and groups wear one group icon; people their initials. */
    fun groupAvatar(mode: AppMode, isChannel: Boolean, contactType: Int?): Boolean =
        isSimple(mode) && (isChannel || contactType == Codes.ADV_TYPE_ROOM)
}

/**
 * New chat's list of somewhere to write: people, rooms and channels, in one alphabetical list
 * (Simple mode, and the full app's "Existing contact, group or room").
 */
object NewChatList {

    data class Entry(
        val title: String,
        /** MessageRepository.KIND_DM or KIND_CHANNEL; with [key], the conversation route. */
        val kind: String,
        /** Contact public key, or channel slot. */
        val key: String,
        val contactType: Int? = null,
        val channelKind: ChannelKind? = null,
        /** Epoch seconds the contact was last heard; 0 for channels and never. */
        val heard: Long = 0,
        /** The radio's favourite flag for people and rooms; the app's own for channels. */
        val favourite: Boolean = false,
    ) {
        val isChannel: Boolean get() = kind == MessageRepository.KIND_CHANNEL
    }

    fun entries(
        mode: AppMode,
        contacts: List<ContactEntity>,
        channels: List<ChannelEntity>,
        kinds: Map<Int, ChannelKind>,
        query: String = "",
        /** Nicknames by key: a renamed contact is listed, sorted and found by it. */
        nicknames: Map<String, String> = emptyMap(),
        /** Channel slots marked favourite on this radio (app-only). */
        favouriteChannels: Set<Int> = emptySet(),
    ): List<Entry> {
        val people = contacts
            // Only people and rooms take messages; a repeater's thread is its console.
            .filter { it.type == Codes.ADV_TYPE_CHAT || it.type == Codes.ADV_TYPE_ROOM }
            .map {
                Entry(
                    title = (nicknames[it.keyHex] ?: it.name).ifBlank { it.keyHex.take(12) },
                    kind = MessageRepository.KIND_DM,
                    key = it.keyHex,
                    contactType = it.type,
                    heard = io.github.thatsfguy.meshcore.presentation.LastHeard.seconds(it),
                    favourite = it.flags and Codes.CONTACT_FLAG_FAVORITE != 0,
                )
            }
        val groups = channels
            .filter { SimpleMode.showsChannel(mode, kinds[it.idx]) }
            // A slot with no name is an empty one, not a group.
            .filter { it.name.isNotBlank() }
            .map {
                Entry(
                    title = it.name,
                    kind = MessageRepository.KIND_CHANNEL,
                    key = it.idx.toString(),
                    channelKind = kinds[it.idx],
                    favourite = it.idx in favouriteChannels,
                )
            }
        val q = query.trim()
        return (people + groups)
            .filter { q.isEmpty() || it.title.contains(q, ignoreCase = true) }
            // Case- and accent-insensitive, and a hashtag sorts by its name, not under '#'.
            .sortedWith(compareBy<Entry> { sortKey(it.title) }.thenBy { it.key })
    }

    /** A titled run of the list: [letter] null is Favourites. */
    data class Section(val letter: String?, val entries: List<Entry>)

    /**
     * Favourites first, then one section per initial, as Signal's new-message list does. A
     * favourite is listed only under Favourites. An initial that is not a letter (a digit, an
     * emoji) files under "#", last.
     */
    fun sections(entries: List<Entry>): List<Section> {
        val favourites = entries.filter { it.favourite }
        val byLetter = entries.filterNot { it.favourite }
            .groupBy { initial(it.title) }
            .toList()
            .sortedWith(compareBy<Pair<String, List<Entry>>> { it.first == "#" }.thenBy { it.first })
            .map { (letter, list) -> Section(letter, list) }
        return (if (favourites.isEmpty()) emptyList() else listOf(Section(null, favourites))) + byLetter
    }

    /** "Àngels" files under A, "#cats" under C, "3rd floor" under #. */
    fun initial(title: String): String {
        val first = sortKey(title).firstOrNull() ?: return "#"
        return if (first.isLetter()) first.uppercaseChar().toString() else "#"
    }

    private fun sortKey(title: String): String =
        java.text.Normalizer.normalize(title.trim().removePrefix("#"), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
}
