package io.alvaroedo.meshconnect.ui.model

import io.alvaroedo.meshconnect.storage.MessageRepository
import io.github.thatsfguy.meshcore.engine.MeshCoreEngine
import io.github.thatsfguy.meshcore.protocol.Advert
import io.github.thatsfguy.meshcore.protocol.Codes

/**
 * The conversation to open once something added from Chats' New chat has landed
 * (2026-11-13): New chat is for starting a conversation on the road, so adding a
 * person or a channel from it is taken to mean "and now I want to say something".
 *
 * Only New chat asks for this. Settings → Channels and the Chats top-bar scanner add
 * exactly as before and stay where they are.
 *
 * A thread is `(kind, key)` as [io.alvaroedo.meshconnect.ui.screens.conversationRoute]
 * takes it. Null means "nothing to open": a repeater, a room server or a sensor has no
 * chat of its own to drop into (they go through their own sign-in flows), and a code
 * that does not parse names nobody.
 */
object NewChatThread {

    /** A contact card: only a person (ADV_TYPE_CHAT) opens a DM. */
    fun forContact(pubKeyHex: String, type: Int): Pair<String, String>? =
        pubKeyHex.lowercase()
            .takeIf { type == Codes.ADV_TYPE_CHAT && it.length == Codes.PUB_KEY_SIZE * 2 }
            ?.let { MessageRepository.KIND_DM to it }

    /** A signed advert blob (a raw packet), read the same way the engine verifies it. */
    fun forAdvert(blob: ByteArray): Pair<String, String>? {
        val payload = MeshCoreEngine.extractAdvertPayload(blob) ?: return null
        val info = Advert.parse(payload) ?: return null
        return forContact(info.publicKeyHex, info.type)
    }

    /** A channel slot — the one just written, or the one already holding that key. */
    fun forChannel(index: Int): Pair<String, String>? =
        index.takeIf { it >= 0 }?.let { MessageRepository.KIND_CHANNEL to it.toString() }
}
