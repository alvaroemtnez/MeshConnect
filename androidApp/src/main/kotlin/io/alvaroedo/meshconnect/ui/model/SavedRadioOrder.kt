package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.transport.SavedNode

/**
 * The order saved radios are listed in: the connected (or last connected) one first, then the
 * rest from most to least recently used (2026-11-18).
 *
 * Preferences keeps them oldest first, because saveNode moves a radio to the END each time it
 * connects, and every list drew that order as it was: the radio just picked dropped to the
 * bottom, and the no-radio screen's "first three" were the three used longest ago.
 */
object SavedRadioOrder {
    fun forDisplay(saved: List<SavedNode>, currentKey: String?): List<SavedNode> {
        val recent = saved.asReversed()
        val current = recent.firstOrNull { currentKey != null && it.key == currentKey } ?: return recent.toList()
        return listOf(current) + recent.filter { it.key != current.key }
    }
}
