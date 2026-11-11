package io.alvaroedo.meshconnect.ui

import io.alvaroedo.meshconnect.ui.model.LinkMeter
import io.alvaroedo.meshconnect.widget.WidgetSnapshot
import io.alvaroedo.meshconnect.widget.WidgetSnapshot.Nearby
import io.github.thatsfguy.meshcore.model.Contact
import io.github.thatsfguy.meshcore.protocol.Codes
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The widget's nearby-repeaters card: its saved form, and who gets a name. */
class WidgetNearbyTest {

    @Test
    fun `the list round-trips`() {
        val list = listOf(
            Nearby("b389", "Blue Base", 9.0, 4),
            Nearby("c985", null, -3.0, 1),
        )
        assertEquals(list, WidgetSnapshot.decodeNearby(WidgetSnapshot.encodeNearby(list)))
    }

    @Test
    fun `a name cannot forge another row`() {
        // Advert text is attacker-controlled; a tab or newline in it must not
        // split into fields or rows.
        val list = listOf(Nearby("b389", "Evil\tc985\t9.0\t4\nRow", 1.0, 2))
        val back = WidgetSnapshot.decodeNearby(WidgetSnapshot.encodeNearby(list))
        assertEquals(1, back.size)
        assertEquals("b389", back[0].hash)
        assertEquals("Evil c985 9.0 4 Row", back[0].name)
    }

    @Test
    fun `malformed lines are dropped, not fatal`() {
        val text = listOf(
            "b389\t9.0\t4\tGood",
            "short\t1.0",
            "zz!!\t1.0\t2\tbad hash",
            "c985\tNaN\t2\tbad snr",
            "c985\t1.0\t7\tbad bars",
            "",
        ).joinToString("\n")
        assertEquals(listOf(Nearby("b389", "Good", 9.0, 4)), WidgetSnapshot.decodeNearby(text))
        assertEquals(emptyList<Nearby>(), WidgetSnapshot.decodeNearby(null))
    }

    @Test
    fun `at most six are kept`() {
        val many = (0 until 10).map { Nearby("a%03d".format(it), null, it.toDouble(), 2) }
        assertEquals(WidgetSnapshot.MAX_NEARBY, WidgetSnapshot.decodeNearby(WidgetSnapshot.encodeNearby(many)).size)
    }

    private fun contact(keyHex: String, name: String, type: Int = Codes.ADV_TYPE_REPEATER) = Contact(
        publicKey = ByteArray(32) { keyHex.padEnd(64, '0').substring(it * 2, it * 2 + 2).toInt(16).toByte() },
        type = type, flags = 0, pathLen = -1, path = ByteArray(0), name = name,
        timestamp = 0, latitude = null, longitude = null, lastModified = 0,
    )

    @Test
    fun `a repeater is named on a unique match`() {
        val cs = listOf(contact("b38911", "Blue Base"), contact("c98522", "Hill"))
        assertEquals("Blue Base", LinkMeter.nameFor("b389", cs))
        assertEquals("Blue Base", LinkMeter.nameFor("B389", cs))
    }

    @Test
    fun `an ambiguous or non-repeater match gets no name`() {
        val cs = listOf(
            contact("b38911", "One"),
            contact("b38922", "Two"),
            contact("c98533", "A person", type = Codes.ADV_TYPE_CHAT),
        )
        assertNull(LinkMeter.nameFor("b389", cs))
        assertNull(LinkMeter.nameFor("c985", cs))
        assertNull(LinkMeter.nameFor("dddd", cs))
    }

    @Test
    fun `both picker entries are declared and draw the same widget`() {
        val manifest = File("src/main/AndroidManifest.xml").readText()
        assertTrue(manifest.contains(".widget.MeshWidgetReceiver"))
        assertTrue(manifest.contains(".widget.MeshWidgetCompactReceiver"))
        assertTrue(manifest.contains("@xml/mesh_widget_compact_info"))
        val compact = File("src/main/res/xml/mesh_widget_compact_info.xml").readText()
        assertTrue(compact.contains("""android:targetCellHeight="1""""))
        val widget = File("src/main/kotlin/io/alvaroedo/meshconnect/widget/MeshWidget.kt").readText()
        val receiver = widget.substringAfter("class MeshWidgetCompactReceiver")
        assertTrue(receiver.contains("glanceAppWidget: GlanceAppWidget = MeshWidget()"))
    }
}
