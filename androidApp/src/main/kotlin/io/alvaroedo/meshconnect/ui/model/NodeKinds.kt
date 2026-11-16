package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.presentation.HeardNode
import io.github.thatsfguy.meshcore.presentation.NodeListModel
import io.github.thatsfguy.meshcore.presentation.SelfPosition
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
     * Heard-but-not-added nodes for [kind]: one tab's, or every tab's merged, in [sort]
     * order. Upstream's heard section follows the list's sort (0.10.16: with auto-add off
     * most rows are heard nodes, so a section stuck on newest-first made "Fewest hops" look
     * broken). Hops are the heard hops, and [self] is this radio's position for Nearest.
     */
    fun <T : HeardNode> heard(
        kind: NodeTab?,
        heard: List<T>,
        query: String,
        filtersActive: Boolean,
        hidden: Boolean,
        sort: NodeListModel.Sort = NodeListModel.Sort.Activity,
        self: SelfPosition? = null,
    ): List<T> =
        if (kind != null) {
            NodeTabsModel.heardFor(kind, heard, query, filtersActive, hidden, sort, self)
        } else {
            NodeTab.entries
                .flatMap { NodeTabsModel.heardFor(it, heard, query, filtersActive, hidden, sort, self) }
                .sortedWith(comparator(sort, self))
        }

    /** The order upstream's NodeTabsModel uses within one tab, for the merged All. */
    private fun <T : HeardNode> comparator(sort: NodeListModel.Sort, self: SelfPosition?): Comparator<T> {
        val newest = compareByDescending<T> { it.lastHeardAt }.thenBy { it.keyHex }
        val byName = compareBy<T> { it.name.ifBlank { it.keyHex }.lowercase() }.thenBy { it.keyHex }
        return when (sort) {
            NodeListModel.Sort.Activity, NodeListModel.Sort.LastHeard -> newest
            NodeListModel.Sort.Name -> byName
            NodeListModel.Sort.Hops -> compareBy<T> { it.minHops == null }.thenBy { it.minHops ?: 0 }.then(newest)
            NodeListModel.Sort.Distance -> {
                val metres = { n: T -> self?.metresTo(n.latitude, n.longitude) }
                compareBy<T> { metres(it) == null }.thenBy { metres(it) ?: 0.0 }.then(byName)
            }
        }
    }
}
