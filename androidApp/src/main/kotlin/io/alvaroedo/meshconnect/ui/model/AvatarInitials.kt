package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.presentation.AvatarGlyph

/**
 * Up to two letters for a contact's avatar circle, the way Signal does it, for
 * circles big enough to hold them (the chat and node lists since 1.1).
 *
 * Words are runs of letters and digits, so mesh names split where people split
 * them: "Donk-N8FWG" → "DN", "Álvaro Edo" → "ÁE", "blue_base" → "BB". A single
 * word with a hump takes it too ("BlueBase" → "BB"), since that is a two-word
 * name written without a space. Otherwise one letter, and anything with no
 * letter or digit at all falls back to [AvatarGlyph] (its first emoji, or "?").
 *
 * Kept in androidApp rather than beside AvatarGlyph so `shared/` stays as
 * upstream has it (FORK.md).
 */
object AvatarInitials {

    fun of(label: String): String {
        val words = label.split(Regex("[^\\p{L}\\p{N}]+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return AvatarGlyph.of(label)
        if (words.size >= 2) return (words[0].take(1) + words[1].take(1)).uppercase()
        val word = words[0]
        // A hump: an upper-case letter straight after a lower-case one.
        val hump = (1 until word.length).firstOrNull { word[it].isUpperCase() && word[it - 1].isLowerCase() }
        return if (hump != null) (word.take(1) + word[hump]).uppercase() else word.take(1).uppercase()
    }
}
