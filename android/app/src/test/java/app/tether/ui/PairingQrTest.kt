package app.tether.ui

import app.tether.core.PairingCode
import com.google.zxing.BinaryBitmap
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeReader
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class PairingQrTest {
    @Test fun productionMatrixRoundTripsLegacyAndLinkIncludingPersian() {
        // Standard P-256 base point and fixed non-secret dummy token.
        val key = ("046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296" +
            "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5")
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        val token = ByteArray(16) { it.toByte() }
        val code = PairingCode(key, token, "Galaxy آزمایشی")
        for (legacy in listOf(true, false)) {
            val matrix = PairingQr.encode(code, legacy, 720)
            val pixels = IntArray(720 * 720) { if (matrix[it % 720, it / 720]) 0x000000 else 0xffffff }
            val scanned = QRCodeReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(720, 720, pixels)))).text
            val parsed = PairingCode.parse(scanned)
            assertNotNull(parsed)
            assertArrayEquals(key, parsed!!.peripheralKey)
            assertArrayEquals(token, parsed.token)
            assertEquals(code.deviceName, parsed.deviceName)
            println("PAIR_QR_FIXTURE $scanned")
        }
    }
}
