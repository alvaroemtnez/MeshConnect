package io.alvaroedo.meshconnect.ui.model

/**
 * The values the radio's LoRa settings can take, for pickers rather than free text
 * (2026-11-10).
 *
 * The companion firmware range-checks CMD_SET_RADIO_PARAMS (sf 5..12, cr 5..8, bw 7..500 kHz),
 * but the chip only has discrete bandwidths: the Semtech SX126x/SX127x set, which RadioLib's
 * setBandwidth accepts and refuses anything else from. Typing "60" for 62.5 kHz passed the app's
 * check and was refused by the chip. A value already on the radio that is not in a list is
 * still offered, so a picker never misstates what the radio holds.
 */
object RadioChoices {

    /** LoRa bandwidths, Hz. */
    val BANDWIDTHS_HZ = listOf(7_800L, 10_400L, 15_600L, 20_800L, 31_250L, 41_700L, 62_500L, 125_000L, 250_000L, 500_000L)
    val SPREADING_FACTORS = (5..12).toList()

    /** The coding rate's denominator: 5 is 4/5, 8 is 4/8. */
    val CODING_RATES = (5..8).toList()

    fun bandwidths(current: Long?): List<Long> =
        if (current == null || current in BANDWIDTHS_HZ) BANDWIDTHS_HZ else (BANDWIDTHS_HZ + current).sorted()

    fun spreadingFactors(current: Int?): List<Int> =
        if (current == null || current in SPREADING_FACTORS) SPREADING_FACTORS else (SPREADING_FACTORS + current).sorted()

    fun codingRates(current: Int?): List<Int> =
        if (current == null || current in CODING_RATES) CODING_RATES else (CODING_RATES + current).sorted()

    fun codingRateLabel(cr: Int): String = "4/$cr"
}
