package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.protocol.ChannelKind

/**
 * Which parts of a channel the editor lets you change (2026-11-10).
 *
 * The Public key is the one every MeshCore radio ships with; changing it makes a private
 * channel still called Public. A hashtag channel's key is SHA-256 of its name, so a new key
 * is a different channel, and a new name leaves a name that no longer matches its key (the
 * app then shows it as private). Both were editable, and either edit was a way to break a
 * working channel by accident. An unknown kind — the key could not be read — stays editable:
 * that is the case where re-entering the key is the fix.
 */
object ChannelEditRules {
    fun keyLocked(kind: ChannelKind?): Boolean = kind == ChannelKind.Public || kind == ChannelKind.Hashtag

    fun nameLocked(kind: ChannelKind?): Boolean = kind == ChannelKind.Hashtag
}
