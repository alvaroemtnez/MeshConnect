package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.LinkMeter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The top bar's signal meter. Written against the firmware, not our parser:
 *
 * - `src/Packet.h:14-17`: route types 0 transport-flood, 1 flood,
 *   2 direct, 3 transport-direct.
 * - `src/Mesh.cpp:349`: a flood relay APPENDS its hash to the path, so the
 *   last entry is whoever transmitted the copy we heard.
 * - `src/Mesh.cpp:335-341`: a direct relay REMOVES itself from the front,
 *   so a direct path is the route still to go and names no last hop.
 */
class LinkMeterTest {

    private fun hex(s: String) = ByteArray(s.length / 2) { s.substring(it * 2, it * 2 + 2).toInt(16).toByte() }

    // header = (ver << 6) | (payload_type << 2) | route_type; GRP_TXT = 5.
    private fun header(route: Int) = ((5 shl 2) or route).toByte()

    /**
     * The real two-hop route pinned by upstream's HeardViaTest: hash width
     * 2 (path_len 0x42), relays b389 then c985.
     */
    private val capturedFlood = byteArrayOf(header(1), 0x42) + hex("b389c985") + hex("deadbeef")

    @Test
    fun `the last relay of a flooded packet is the one we heard`() {
        val out = LinkMeter.record(emptyList(), capturedFlood, snr = 6.25, rssi = -88, nowMs = 1000)
        assertEquals(1, out.size)
        assertEquals("c985", out[0].prefixHex)
        assertEquals(6.25, out[0].snr, 0.0)
        assertEquals(-88, out[0].rssi)
    }

    @Test
    fun `the width comes from the packet, not a constant`() {
        // Same bytes read at width 1 would name 85; at width 2 they name c985.
        val oneByte = byteArrayOf(header(1), 0x04) + hex("b389c985") + hex("00")
        assertEquals("85", LinkMeter.record(emptyList(), oneByte, 0.0, -90, 0)[0].prefixHex)
        assertEquals("c985", LinkMeter.record(emptyList(), capturedFlood, 0.0, -90, 0)[0].prefixHex)
    }

    @Test
    fun `transport flood counts too`() {
        val raw = byteArrayOf(header(0), 1, 2, 3, 4, 0x01, 0x7a, 0x00)
        assertEquals("7a", LinkMeter.record(emptyList(), raw, 1.0, -100, 0)[0].prefixHex)
    }

    @Test
    fun `direct packets and zero-hop packets name nobody`() {
        val direct = byteArrayOf(header(2), 0x42) + hex("b389c985") + hex("00")
        val transportDirect = byteArrayOf(header(3), 1, 2, 3, 4, 0x01, 0x7a, 0x00)
        val zeroHop = byteArrayOf(header(1), 0x00, 0x55)
        for (raw in listOf(direct, transportDirect, zeroHop)) {
            assertTrue(LinkMeter.record(emptyList(), raw, 5.0, -80, 0).isEmpty())
        }
    }

    @Test
    fun `hostile and truncated packets change nothing`() {
        val before = LinkMeter.record(emptyList(), capturedFlood, 1.0, -90, 0)
        val hostile = listOf(
            ByteArray(0),
            byteArrayOf(header(1)),
            byteArrayOf(header(1), 0x05, 0x11), // claims 5 hops, has 1
            byteArrayOf(header(1), 0x3F) + ByteArray(10), // 63 hops claimed
        )
        for (raw in hostile) assertEquals(before, LinkMeter.record(before, raw, 9.0, -50, 5))
    }

    @Test
    fun `a repeat updates the reading and counts packets`() {
        var s = LinkMeter.record(emptyList(), capturedFlood, 1.0, -90, 0)
        s = LinkMeter.record(s, capturedFlood, 7.5, -70, 60_000)
        assertEquals(1, s.size)
        assertEquals(2, s[0].packets)
        assertEquals(7.5, s[0].snr, 0.0)
        assertEquals(60_000L, s[0].lastHeardMs)
    }

    @Test
    fun `fresh drops stale readings and puts the strongest first`() {
        val a = byteArrayOf(header(1), 0x01, 0x11, 0x00)
        val b = byteArrayOf(header(1), 0x01, 0x22, 0x00)
        val c = byteArrayOf(header(1), 0x01, 0x33, 0x00)
        var s = LinkMeter.record(emptyList(), a, 2.0, -90, 0)
        s = LinkMeter.record(s, b, -3.0, -100, LinkMeter.STALE_MS)
        s = LinkMeter.record(s, c, 8.0, -80, LinkMeter.STALE_MS + 1)
        val fresh = LinkMeter.fresh(s, LinkMeter.STALE_MS + 1)
        assertEquals(listOf("33", "22"), fresh.map { it.prefixHex })
    }

    @Test
    fun `the list is bounded`() {
        var s = emptyList<LinkMeter.DirectRepeater>()
        for (i in 0 until 40) s = LinkMeter.record(s, byteArrayOf(header(1), 0x01, i.toByte(), 0x00), 0.0, -90, i.toLong())
        assertEquals(LinkMeter.MAX_ENTRIES, s.size)
        assertEquals("27", s[0].prefixHex) // newest kept
    }

    @Test
    fun `bars scale with spreading factor`() {
        // Floors (Semtech): SF7 -7.5 dB, SF12 -20 dB.
        assertEquals(-7.5, LinkMeter.snrFloor(7), 0.0)
        assertEquals(-20.0, LinkMeter.snrFloor(12), 0.0)
        // -10 dB is below the SF7 floor but a good link at SF12.
        assertEquals(0, LinkMeter.bars(-10.0, 7))
        assertEquals(4, LinkMeter.bars(-10.0, 12)) // 10 dB above the SF12 floor
        assertEquals(4, LinkMeter.bars(10.0, 7))
        // Edges of each band at SF10 (floor -15).
        assertEquals(4, LinkMeter.bars(-5.0, 10))
        assertEquals(3, LinkMeter.bars(-10.0, 10))
        assertEquals(2, LinkMeter.bars(-12.5, 10))
        assertEquals(1, LinkMeter.bars(-15.0, 10))
        assertEquals(0, LinkMeter.bars(-15.25, 10))
    }

    @Test
    fun `unknown spreading factor assumes the common SF10`() {
        assertEquals(LinkMeter.bars(-12.0, 10), LinkMeter.bars(-12.0, null))
    }
}
