package io.alvaroedo.meshconnect.ui.model

import io.github.thatsfguy.meshcore.protocol.Codes
import io.github.thatsfguy.meshcore.protocol.ShareUri
import java.util.Locale

/**
 * Messages that are really a shared thing (a contact, a channel, a place)
 * and are drawn as a card instead of text, the way Signal and WhatsApp do.
 *
 * The wire forms are plain text so every other MeshCore client still shows
 * something readable:
 *
 * - contact: `<public_key:type:name>`, the key as 64 hex digits, type 1-4
 *   (companion, repeater, room server, sensor);
 * - channel: the `meshcore://channel/add?name=…&secret=…` link other apps
 *   already scan (ShareUri, after MeshCore's docs/qr_codes.md);
 * - place: `lat,lon` in decimal degrees.
 *
 * A card is drawn only when the WHOLE message is one of these. Everything
 * here arrives from the mesh, so it is attacker-controlled: a name is
 * display text, nothing more, and parsing refuses rather than guesses.
 */
object RichMessage {

    sealed interface Content
    data class Contact(val publicKeyHex: String, val type: Int, val name: String) : Content
    data class Channel(val name: String, val pskHex: String, val uri: String) : Content
    data class Location(val lat: Double, val lon: Double) : Content

    private val CONTACT = Regex("""^<([0-9a-fA-F]{64}):([1-4]):([^<>\r\n]+)>$""")

    // Both halves need a decimal point: "12,5" is a number in half of
    // Europe, not a place.
    private val LOCATION = Regex("""^(-?\d{1,2}\.\d{1,8}),\s?(-?\d{1,3}\.\d{1,8})$""")

    /** Names longer than this are refused on receipt (the firmware stores 31 bytes). */
    private const val MAX_NAME_CHARS = 64

    fun parse(text: String): Content? {
        val t = text.trim()
        if (t.isEmpty() || t.length > 400) return null
        CONTACT.matchEntire(t)?.let { m ->
            val key = m.groupValues[1].lowercase()
            val name = m.groupValues[3].trim()
            if (key.all { it == '0' } || name.isEmpty() || name.length > MAX_NAME_CHARS) return null
            return Contact(key, m.groupValues[2].toInt(), name)
        }
        if (t.startsWith(ShareUri.SCHEME + ShareUri.CHANNEL_PATH, ignoreCase = true) && t.none { it.isWhitespace() }) {
            val d = ShareUri.decode(t)
            if (d is ShareUri.Decoded.ChannelShare) return Channel(d.name, d.pskHex, t)
            return null
        }
        if (t.startsWith(ShareUri.SCHEME + ShareUri.CONTACT_PATH, ignoreCase = true) && t.none { it.isWhitespace() }) {
            val d = ShareUri.decode(t)
            if (d is ShareUri.Decoded.Contact && d.type in Codes.ADV_TYPE_CHAT..Codes.ADV_TYPE_SENSOR) {
                return Contact(d.pubKeyHex.lowercase(), d.type, d.name)
            }
            return null
        }
        LOCATION.matchEntire(t)?.let { m ->
            val lat = m.groupValues[1].toDoubleOrNull() ?: return null
            val lon = m.groupValues[2].toDoubleOrNull() ?: return null
            if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
            // Null Island is what a radio with no fix reports.
            if (lat == 0.0 && lon == 0.0) return null
            return Location(lat, lon)
        }
        return null
    }

    /** `<key:type:name>`; the name loses anything that would end the token early. */
    fun contactText(publicKeyHex: String, type: Int, name: String): String {
        val clean = ShareUri.truncateToBytes(
            name.replace(Regex("""[<>\r\n]"""), "").trim(),
            ShareUri.MAX_NAME_BYTES,
        ).ifBlank { "?" }
        return "<${publicKeyHex.lowercase()}:$type:$clean>"
    }

    /** Five decimals is about a metre, which is more than a phone's fix is worth. */
    fun locationText(lat: Double, lon: Double): String =
        String.format(Locale.ROOT, "%.5f,%.5f", lat, lon)

    /** A `geo:` URI that drops a pin in the phone's own map app. */
    fun geoUri(lat: Double, lon: Double): String {
        val p = String.format(Locale.ROOT, "%.6f,%.6f", lat, lon)
        return "geo:$p?q=$p"
    }
}
