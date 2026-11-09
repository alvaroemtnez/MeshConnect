package io.github.thatsfguy.meshcore.protocol

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Against the firmware's WRITER, not our parser: companion_radio/MyMesh.cpp,
 * CMD_GET_STATS / STATS_TYPE_RADIO: memcpy i16 noise_floor, then i8
 * last_rssi, i8 last_snr*4, u32 tx_air_secs, u32 rx_air_secs, all LE.
 */
class RadioStatsTest {

    @Test
    fun requestIsCommandThenType() {
        // `cmd_frame[0] == CMD_GET_STATS && len >= 2`, `stats_type = cmd_frame[1]`.
        assertContentEquals(byteArrayOf(56, 1), RadioStats.request())
    }

    @Test
    fun parsesTheFirmwareLayout() {
        val payload = byteArrayOf(
            0x8E.toByte(), 0xFF.toByte(), // -114 dBm, i16 LE
            0xA6.toByte(),                // -90 dBm
            0xE6.toByte(),                // -26 → -6.5 dB
            0x10, 0x0E, 0x00, 0x00,       // 3600 s TX
            0x20, 0x1C, 0x00, 0x00,       // 7200 s RX
        )
        assertEquals(RadioStats(-114, -90, -6.5, 3600, 7200), RadioStats.parse(payload))
    }

    @Test
    fun positiveSnrAndQuietNoise() {
        val payload = byteArrayOf(0x88.toByte(), 0xFF.toByte(), 0xC4.toByte(), 0x1E, 0, 0, 0, 0, 0, 0, 0, 0)
        val s = RadioStats.parse(payload)!!
        assertEquals(-120, s.noiseFloorDbm)
        assertEquals(-60, s.lastRssiDbm)
        assertEquals(7.5, s.lastSnrDb)
    }

    @Test
    fun truncatedIsNull() {
        for (n in 0 until 12) assertNull(RadioStats.parse(ByteArray(n)), "length $n")
    }
}
