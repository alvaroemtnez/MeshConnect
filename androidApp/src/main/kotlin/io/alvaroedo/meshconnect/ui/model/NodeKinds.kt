package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.presentation.HeardNode
import io.github.thatsfguy.meshcore.presentation.NodeTab
import io.github.thatsfguy.meshcore.presentation.NodeTabsModel

/**
 * The Nodes screen's kind filter: All, or one [NodeTab]. Upstream had tabs
 * and no "All"; this adds it on top of upstream's per-tab rules rather than
 * changing them, so NodeTabsModel stays as upstream wrote it.
 */
object NodeKinds {

    /** Saved form: -1 for All, else the tab's ordinal. */
    fun fromSaved(index: Int): NodeTab? = if (index < 0) null else NodeTab.entries.getOrNull(index)

    fun toSaved(kind: NodeTab?): Int = kind?.ordinal ?: -1

    fun matches(kind: NodeTab?, type: Int): Boolean = kind == null || NodeTab.of(type) == kind

    /**
     * Heard-but-not-added nodes for [kind]: one tab's, or every tab's merged
     * in the same order upstream uses (newest first, then key).
     */
    fun <T : HeardNode> heard(
        kind: NodeTab?,
        heard: List<T>,
        query: String,
        filtersActive: Boolean,
        hidden: Boolean,
    ): List<T> =
        if (kind != null) {
            NodeTabsModel.heardFor(kind, heard, query, filtersActive, hidden)
        } else {
            NodeTab.entries
                .flatMap { NodeTabsModel.heardFor(it, heard, query, filtersActive, hidden) }
                .sortedWith(compareByDescending<T> { it.lastHeardAt }.thenBy { it.keyHex })
        }
}
