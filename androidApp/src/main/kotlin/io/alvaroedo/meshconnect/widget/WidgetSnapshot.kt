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
) {
    enum class Status { Offline, Connecting, Connected }

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
            .apply()
    }

    companion object {
        /** Twice the service's battery poll: past this the data is not live. */
        const val STALE_MS = 10L * 60 * 1000

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
        )
    }
}
