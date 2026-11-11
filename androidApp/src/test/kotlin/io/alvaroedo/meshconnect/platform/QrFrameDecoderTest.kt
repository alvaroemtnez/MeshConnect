package io.alvaroedo.meshconnect.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The scanner's decoder against real codes drawn into a camera-shaped Y plane.
 *
 * The inverted case is the one that carries this suite: the scanner this replaced
 * claimed to read white-on-dark codes for months and never read one, because its test
 * grepped the source for a hint instead of decoding anything.
 */
class QrFrameDecoderTest {

    private val text = "meshcore://contact/add?name=Blue%20Base&public_key=" + "ab".repeat(32) + "&type=1"

    /** A Y plane [stride] wide (padding past [width], as camera rows have) holding [text]. */
    private fun frame(inverted: Boolean, width: Int = 480, height: Int = 640, stride: Int = 512): ByteArray {
        val size = 360
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 4))
        val dark: Byte = 20
        val light: Byte = (235).toByte()
        val bg = if (inverted) dark else light
        val fg = if (inverted) light else dark
        val y = ByteArray(stride * height) { bg }
        val ox = (width - size) / 2
        val oy = (height - size) / 2
        for (r in 0 until size) for (c in 0 until size) {
            if (matrix[c, r]) y[(oy + r) * stride + ox + c] = fg
        }
        return y
    }

    @Test
    fun `reads a dark-on-light code`() {
        assertEquals(text, QrFrameDecoder().decode(frame(inverted = false), 512, 480, 640))
    }

    @Test
    fun `reads a light-on-dark code`() {
        assertEquals(text, QrFrameDecoder().decode(frame(inverted = true), 512, 480, 640))
    }

    @Test
    fun `one decoder reads frame after frame, of both kinds`() {
        val d = QrFrameDecoder()
        repeat(3) {
            assertEquals(text, d.decode(frame(inverted = it % 2 == 1), 512, 480, 640))
        }
    }

    @Test
    fun `a frame with no code is null, not an exception`() {
        assertNull(QrFrameDecoder().decode(ByteArray(512 * 640) { 128.toByte() }, 512, 480, 640))
        assertNull(QrFrameDecoder().decode(ByteArray(512 * 640) { (it * 31).toByte() }, 512, 480, 640))
    }
}
