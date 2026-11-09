package io.github.thatsfguy.meshcore.protocol

/**
 * The companion radio's own RF readings: `CMD_GET_STATS` (56) with
 * `STATS_TYPE_RADIO` (1), answered with `RESP_CODE_STATS` (24).
 *
 * Layout after `[24][1]`, as companion_radio/MyMesh.cpp writes it
 * (CMD_GET_STATS, STATS_TYPE_RADIO branch):
 *
 *   i16 noise_floor (dBm) · i8 last_rssi (dBm) · i8 last_snr (dB × 4)
 *   · u32 tx_air_secs · u32 rx_air_secs
 *
 * Added by MeshConnect for MeshCore Hardened issue #6 (live noise floor,
 * RSSI and SNR). Kept in upstream's style so it can be offered back.
 */
data class RadioStats(
    val noiseFloorDbm: Int,
    val lastRssiDbm: Int,
    val lastSnrDb: Double,
    val txAirSecs: Long,
    val rxAirSecs: Long,
) {
    companion object {
        /** [payload] is what follows the type byte. Null when truncated. */
        fun parse(payload: ByteArray): RadioStats? {
            if (payload.size < 12) return null
            return try {
                val r = BufferReader(payload)
                RadioStats(
                    noiseFloorDbm = r.readInt16LE(),
                    lastRssiDbm = r.readInt8(),
                    lastSnrDb = r.readInt8() / 4.0,
                    txAirSecs = r.readUInt32LE(),
                    rxAirSecs = r.readUInt32LE(),
                )
            } catch (_: TruncatedFrameException) {
                null
            }
        }

        /** `[56][1]`: the request. */
        fun request(): ByteArray = byteArrayOf(Codes.CMD_GET_STATS.toByte(), Codes.STATS_TYPE_RADIO.toByte())
    }
}
