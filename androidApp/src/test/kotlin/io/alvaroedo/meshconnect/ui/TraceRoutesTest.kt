package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.TraceRoutes
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pinned to the firmware's trace forwarding (src/Mesh.cpp:42-67): each
 * listed hop appends an SNR and retransmits, and the result goes to
 * whoever hears the transmission after the last listed hop. So every
 * list built here must end on a node we can hear: our first hop.
 */
class TraceRoutesTest {

    @Test
    fun `pinging a neighbour lists just the neighbour`() {
        assertEquals(listOf("c985"), TraceRoutes.ping(emptyList(), "c985"))
    }

    @Test
    fun `pinging over a route goes out and comes back`() {
        val out = listOf("b389", "c985")
        assertEquals(listOf("b389", "c985", "7a1f", "c985", "b389"), TraceRoutes.ping(out, "7a1f"))
    }

    @Test
    fun `a trace towards a person turns at the last repeater`() {
        assertEquals(listOf("b389", "c985", "b389"), TraceRoutes.towards(listOf("b389", "c985")))
        assertEquals(listOf("b389"), TraceRoutes.towards(listOf("b389")))
        assertNull("no repeater, nothing to trace", TraceRoutes.towards(emptyList()))
    }

    @Test
    fun `every list ends on our first hop, so the reply reaches us`() {
        val out = listOf("aa11", "bb22", "cc33")
        val ping = TraceRoutes.ping(out, "dd44")
        assertEquals(out.first(), ping.last())
        assertEquals(out.first(), TraceRoutes.towards(out)!!.last())
        // A round trip reads the same backwards (and so survives the
        // engine's reverse-and-retry unchanged).
        assertEquals(ping, ping.reversed())
    }

    @Test
    fun `hops are split at the mesh's width, never assumed to be one byte`() {
        // The real two-hop route pinned by upstream's HeardViaTest.
        val path = byteArrayOf(0xb3.toByte(), 0x89.toByte(), 0xc9.toByte(), 0x85.toByte())
        assertEquals(listOf("b389", "c985"), TraceRoutes.hops(path, 2))
        assertEquals(listOf("b3", "89", "c9", "85"), TraceRoutes.hops(path, 1))
        assertTrue("a width that doesn't divide the path is refused", TraceRoutes.hops(path, 3).isEmpty())
        assertTrue(TraceRoutes.hops(path, 0).isEmpty())
    }

    @Test
    fun `a node's hash is its key prefix at the current width`() {
        val key = "B389C985" + "00".repeat(28)
        assertEquals("b3", TraceRoutes.hashOf(key, 1))
        assertEquals("b389", TraceRoutes.hashOf(key, 2))
        assertEquals("b389c985", TraceRoutes.hashOf(key, 4))
    }

    @Test
    fun `hops round-trip to wire bytes`() {
        assertArrayEquals(
            byteArrayOf(0xb3.toByte(), 0x89.toByte(), 0x7a, 0x1f),
            TraceRoutes.toBytes(listOf("b389", "7a1f")),
        )
    }

    @Test
    fun `legs pair each hop with the SNR it heard, and the last with us`() {
        val legs = TraceRoutes.legs(listOf("b389", "c985", "b389"), listOf(6.0, -2.5, 4.25, 7.0))!!
        assertEquals(4, legs.size)
        assertEquals(TraceRoutes.Leg(null, "b389", 6.0), legs[0])
        assertEquals(TraceRoutes.Leg("b389", "c985", -2.5), legs[1])
        assertEquals(TraceRoutes.Leg("b389", null, 7.0), legs.last())
    }

    @Test
    fun `a reply whose SNRs don't match its hops is refused`() {
        assertNull(TraceRoutes.legs(listOf("b389"), listOf(1.0)))
        assertNull(TraceRoutes.legs(listOf("b389"), listOf(1.0, 2.0, 3.0)))
    }
}
