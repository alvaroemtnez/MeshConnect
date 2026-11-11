package io.alvaroedo.meshconnect.platform

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.InvertedLuminanceSource
import com.google.zxing.LuminanceSource
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader

/**
 * QR text out of one camera frame's luminance plane, dark-on-light OR light-on-dark.
 *
 * The inverted half is not optional. Roughly half the MeshCore codes in circulation are
 * white-on-black, because apps draw them on a dark background (MESHCORE_PROTOCOL §11),
 * and a scanner that cannot read them looks exactly like a code that is bad. The
 * zxing-android-embedded scanner this replaced (1.1) lost it twice upstream, to a hint
 * the library silently overwrote; here it is one plain branch, and QrFrameDecoderTest
 * decodes a real inverted code rather than grepping for a hint.
 *
 * Pure JVM (ZXing core only), so it is tested off-device. Not thread-safe: one per
 * analysis thread.
 */
class QrFrameDecoder {

    private val reader = QRCodeReader()
    private val hints = mapOf(
        DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
        DecodeHintType.TRY_HARDER to true,
    )

    /** [luma] is a Y plane [rowStride] bytes per row; null when no QR is in it. */
    fun decode(luma: ByteArray, rowStride: Int, width: Int, height: Int): String? {
        val source = PlanarYUVLuminanceSource(luma, rowStride, height, 0, 0, width, height, false)
        return decode(source) ?: decode(InvertedLuminanceSource(source))
    }

    private fun decode(source: LuminanceSource): String? = try {
        reader.decode(BinaryBitmap(HybridBinarizer(source)), hints).text
    } catch (_: ReaderException) {
        null
    } catch (_: IllegalArgumentException) {
        null
    } finally {
        reader.reset()
    }
}
