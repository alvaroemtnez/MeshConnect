package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.RichMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RichMessageTest {

    private val key = "b389c985" + "7a1f0c42".repeat(7)

    @Test
    fun `a contact token round-trips for every node type`() {
        for (type in 1..4) {
            val text = RichMessage.contactText(key, type, "Hilltop Rpt")
            assertEquals("<$key:$type:Hilltop Rpt>", text)
            assertEquals(RichMessage.Contact(key, type, "Hilltop Rpt"), RichMessage.parse(text))
        }
    }

    @Test
    fun `a name may contain colons, since the key and type are fixed width`() {
        assertEquals(
            RichMessage.Contact(key, 1, "EA5XX: base"),
            RichMessage.parse("<$key:1:EA5XX: base>"),
        )
    }

    @Test
    fun `the name is cleaned so it cannot end the token early`() {
        val text = RichMessage.contactText(key.uppercase(), 1, "a>b<c\nd")
        assertEquals("<$key:1:abcd>", text)
        assertEquals("abcd", (RichMessage.parse(text) as RichMessage.Contact).name)
    }

    @Test
    fun `the name is held to the firmware's 31 bytes`() {
        val long = "ñ".repeat(40) // 2 bytes each
        val name = (RichMessage.parse(RichMessage.contactText(key, 1, long)) as RichMessage.Contact).name
        assertTrue(name.encodeToByteArray().size <= 31)
    }

    @Test
    fun `hostile contact tokens are plain text`() {
        val bad = listOf(
            "<$key:0:Name>", // type out of range
            "<$key:5:Name>",
            "<${key.dropLast(1)}:1:Name>", // 63 digits
            "<${key}0:1:Name>", // 65 digits
            "<${"g".repeat(64)}:1:Name>", // not hex
            "<${"0".repeat(64)}:1:Name>", // all-zero key
            "<$key:1:>", // no name
            "<$key:1:   >",
            "<$key:1:${"x".repeat(65)}>", // oversized
            "hello <$key:1:Name>", // not the whole message
            "<$key:1:Na<me>",
        )
        for (t in bad) assertNull(t, RichMessage.parse(t))
    }

    @Test
    fun `the channel link from the request is a channel card`() {
        val uri = "meshcore://channel/add?name=Public&secret=8b3387e9c5cdea6ac9e5edbaa115cd72"
        val c = RichMessage.parse(uri) as RichMessage.Channel
        assertEquals("Public", c.name)
        assertEquals("8b3387e9c5cdea6ac9e5edbaa115cd72", c.pskHex.lowercase())
    }

    @Test
    fun `a channel link inside a sentence stays text, and a broken one is refused`() {
        assertNull(RichMessage.parse("join meshcore://channel/add?name=Public&secret=8b3387e9c5cdea6ac9e5edbaa115cd72"))
        assertNull(RichMessage.parse("meshcore://channel/add?name=Public&secret=8b33"))
        assertNull(RichMessage.parse("meshcore://channel/add?name=Public"))
    }

    @Test
    fun `a contact-card link is a contact card too`() {
        val c = RichMessage.parse("meshcore://contact/add?name=Bob&public_key=${key.uppercase()}&type=2")
        assertEquals(RichMessage.Contact(key, 2, "Bob"), c)
    }

    @Test
    fun `a place round-trips at five decimals`() {
        val text = RichMessage.locationText(39.469907, -0.376288)
        assertEquals("39.46991,-0.37629", text)
        assertEquals(RichMessage.Location(39.46991, -0.37629), RichMessage.parse(text))
        assertEquals(RichMessage.Location(42.9, 13.5), RichMessage.parse(" 42.9, 13.5 "))
    }

    @Test
    fun `numbers that are not places stay text`() {
        val bad = listOf(
            "12,5", // a decimal comma
            "12.5,7", // one half without a decimal point
            "12,5.25", // the other half without one
            "91.0,10.0", // latitude out of range
            "10.0,181.0",
            "0.0,0.0", // no fix
            "1.5, 2.5 metres",
            "1.5;2.5",
        )
        for (t in bad) assertNull(t, RichMessage.parse(t))
    }

    @Test
    fun `plain text is plain text`() {
        for (t in listOf("", "hola", "<b>bold</b>", "meshcore://", "39.4699")) assertNull(t, RichMessage.parse(t))
    }

    @Test
    fun `geo uri drops a pin`() {
        assertEquals("geo:39.469910,-0.376290?q=39.469910,-0.376290", RichMessage.geoUri(39.46991, -0.37629))
    }
}
