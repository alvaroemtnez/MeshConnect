package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.crypto.CryptoProvider
import io.github.thatsfguy.meshcore.protocol.ChannelCrypto

/**
 * `#mcn-internal`: a hashtag channel every MeshConnect app joins, for traffic between the apps
 * that is not conversation (2026-11-16). First use: asking someone to add you, so direct
 * messages work, when you share no group with them (DmRequest).
 *
 * - A hashtag channel because its key is its name: every MeshConnect app can join it with no
 *   code to pass around. Anyone can read it — so only what is public anyway goes on it, and
 *   who asked whom.
 * - One channel for everything internal, with each kind of message recognisable by its text
 *   (DmRequest's tags), so it costs one channel slot however many uses it gets.
 * - It lives in the radio's HIGHEST free slot, out of the way of the slots people use, and is
 *   hidden everywhere: Chats, Settings, New chat, notifications, the widget. Its requests reach
 *   the user as a prompt of their own (DmRequestInbox), like requests on any other channel.
 * - MeshConnect only. Other apps never see it, unless someone joins `#mcn-internal` by hand.
 */
object InternalChannel {

    const val NAME = "#mcn-internal"

    fun psk(crypto: CryptoProvider): ByteArray = ChannelCrypto.hashtagPsk(crypto, NAME)

    /** A slot with this name is ours, before its key can be read (the start-up list). */
    fun isNamed(name: String): Boolean = name.trim().equals(NAME, ignoreCase = true)

    /** The highest free slot below [max]; null when the radio has none. */
    fun slotFor(used: Set<Int>, max: Int): Int? = (max - 1 downTo 0).firstOrNull { it !in used }

    /**
     * Whether a request's name is confirmed: our radio holds, or has heard a signed advert from,
     * that very key under that name. A request carries the asker's key, but the name in front
     * of it is whatever they typed; an advert is signed by the key, so its name is theirs.
     */
    fun nameConfirmed(keyHex: String, name: String, known: List<Pair<String, String>>): Boolean {
        val n = name.trim()
        return n.isNotEmpty() && known.any { (k, kn) -> k.equals(keyHex, ignoreCase = true) && kn.trim().equals(n, ignoreCase = true) }
    }
}
