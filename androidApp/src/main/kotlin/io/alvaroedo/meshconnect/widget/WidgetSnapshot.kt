package io.alvaroedo.meshconnect.widget

import android.content.SharedPreferences

/**
 * What the home-screen widget shows, built by the service and saved, so a
 * widget drawn after the app was killed still shows the last state (dimmed
 * and marked as such) instead of nothing.
 *
 * Message text is NOT saved here: the widget shows who wrote last and how
 * many are unread, and the body only while the app is running. A home
 * screen is the least private surface on the phone, and the database is
 * encrypted for a reason.
 */
data class WidgetSnapshot(
    val status: Status = Status.Offline,
    val nodeName: String? = null,
    val batteryPercent: Int? = null,
    val plaintext: Boolean = false,
    /** 0..4, null with no repeater heard recently. */
    val signalBars: Int? = null,
    val repeaterHash: String? = null,
    val repeaterSnr: Double? = null,
    val unread: Int = 0,
    val lastFrom: String? = null,
    val lastText: String? = null,
    val lastAtMs: Long = 0,
    val updatedAtMs: Long = 0,
    /** Repeaters heard directly, strongest first (largest widget size). */
    val nearby: List<Nearby> = emptyList(),
) {
    enum class Status { Offline, Connecting, Connected }

    /** One directly-heard repeater. [name] only on a unique match (LinkMeter.nameFor). */
    data class Nearby(val hash: String, val name: String?, val snr: Double, val bars: Int)

    /** Older than this and nobody is updating it: the app isn't running. */
    fun isStale(nowMs: Long): Boolean = nowMs - updatedAtMs > STALE_MS

    fun save(p: SharedPreferences) {
        p.edit()
            .putString("status", status.name)
            .putString("node", nodeName)
            .putInt("battery", batteryPercent ?: -1)
            .putBoolean("plaintext", plaintext)
            .putInt("bars", signalBars ?: -1)
            .putString("rep", repeaterHash)
            .putFloat("snr", repeaterSnr?.toFloat() ?: Float.NaN)
            .putInt("unread", unread)
            .putString("from", lastFrom)
            // The body is deliberately not written to disk (see above).
            .putLong("at", lastAtMs)
            .putLong("updated", updatedAtMs)
            // Public adverts, not messages: fine to keep for a redraw.
            .putString("nearby", encodeNearby(nearby))
            .apply()
    }

    companion object {
        /** Twice the service's battery poll: past this the data is not live. */
        const val STALE_MS = 10L * 60 * 1000

        /** The most the largest widget lists. */
        const val MAX_NEARBY = 4

        /**
         * One repeater per line, tab-separated. A name is advert text, so
         * tabs and line breaks in it are flattened rather than trusted.
         */
        fun encodeNearby(list: List<Nearby>): String =
            list.take(MAX_NEARBY).joinToString("\n") { n ->
                val name = n.name?.replace(Regex("[\t\r\n]"), " ").orEmpty()
                listOf(n.hash, n.snr.toString(), n.bars.toString(), name).joinToString("\t")
            }

        /** Lenient: a malformed line is dropped, never a crash in the launcher. */
        fun decodeNearby(text: String?): List<Nearby> =
            text.orEmpty().lineSequence().mapNotNull { line ->
                val f = line.split('\t')
                if (f.size != 4) return@mapNotNull null
                val hash = f[0].takeIf { h -> h.isNotEmpty() && h.length <= 16 && h.all { it.isLetterOrDigit() } }
                    ?: return@mapNotNull null
                val snr = f[1].toDoubleOrNull()?.takeIf { it.isFinite() } ?: return@mapNotNull null
                val bars = f[2].toIntOrNull()?.takeIf { it in 0..4 } ?: return@mapNotNull null
                Nearby(hash, f[3].ifBlank { null }, snr, bars)
            }.take(MAX_NEARBY).toList()

        fun load(p: SharedPreferences): WidgetSnapshot = WidgetSnapshot(
            status = runCatching { Status.valueOf(p.getString("status", null) ?: "") }.getOrDefault(Status.Offline),
            nodeName = p.getString("node", null),
            batteryPercent = p.getInt("battery", -1).takeIf { it in 0..100 },
            plaintext = p.getBoolean("plaintext", false),
            signalBars = p.getInt("bars", -1).takeIf { it in 0..4 },
            repeaterHash = p.getString("rep", null),
            repeaterSnr = p.getFloat("snr", Float.NaN).takeUnless { it.isNaN() }?.toDouble(),
            unread = p.getInt("unread", 0).coerceAtLeast(0),
            lastFrom = p.getString("from", null),
            lastText = null,
            lastAtMs = p.getLong("at", 0),
            updatedAtMs = p.getLong("updated", 0),
            nearby = decodeNearby(p.getString("nearby", null)),
        )
    }
}
