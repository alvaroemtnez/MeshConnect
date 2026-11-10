package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.storage.MessageEntity
import io.alvaroedo.meshconnect.ui.model.RouteChip
import io.alvaroedo.meshconnect.ui.screens.routeChipLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The "heard via" chip on a received bubble. */
class RouteChipTest {

    // Full keys as the contact list holds them; the mesh uses 2-byte hops.
    private val names = mapOf(
        "a17c" + "0".repeat(60) to "Port Rpt",
        "85a7" + "0".repeat(60) to "Hilltop Rpt",
        "b389" + "1".repeat(60) to "Twin A",
        "b389" + "2".repeat(60) to "Twin B",
    )

    private fun msg(path: String?, width: Int?) = MessageEntity(
        selfKey = "self", kind = "dm", peerKey = "marta", senderName = null, text = "hi",
        timestamp = 0, receivedAt = 0, outgoing = false, status = 0, ackHash = null,
        contentKey = null, snr = null,
        arrivalPathHex = path, arrivalHashWidth = width,
    )

    // Positive control: the case the feature exists for. Every null test
    // below would pass if the chip were never drawn.
    @Test
    fun `a known two-hop route names both repeaters in travel order`() {
        assertEquals("Port Rpt › Hilltop Rpt", routeChipLabel(msg("a17c85a7", 2), names))
    }

    @Test
    fun `an ambiguous hop shows its hash, never a guess`() {
        assertEquals("B389 › Hilltop Rpt", routeChipLabel(msg("b38985a7", 2), names))
    }

    @Test
    fun `an unknown hop shows its hash`() {
        assertEquals("FFFF", routeChipLabel(msg("ffff", 2), names))
    }

    @Test
    fun `a long route keeps the first and the last, and counts the rest`() {
        assertEquals(
            "Port Rpt › +2 › Hilltop Rpt",
            routeChipLabel(msg("a17cffffeeee85a7", 2), names),
        )
    }

    @Test
    fun `the width is the message's own, not a constant`() {
        // The same bytes read at width 1 are four different hops: the
        // recurring defect in this codebase is assuming one width.
        assertEquals("Port Rpt › +2 › A7", routeChipLabel(msg("a17c85a7", 1), names))
    }

    @Test
    fun `no route, no chip`() {
        assertNull(routeChipLabel(msg(null, null), names))
        assertNull(routeChipLabel(msg("", 2), names))
        assertNull(routeChipLabel(msg("a17c", null), names))
    }

    @Test
    fun `a malformed stored path draws nothing rather than a wrong route`() {
        assertNull(routeChipLabel(msg("a17c85", 2), names)) // not a whole number of hops
        assertNull(routeChipLabel(msg("zz7c", 2), names)) // not hex
        assertNull(routeChipLabel(msg("a17c", 9), names)) // impossible width
    }

    @Test
    fun `an empty hop list is no label`() {
        assertNull(RouteChip.label(emptyList()))
    }
}
