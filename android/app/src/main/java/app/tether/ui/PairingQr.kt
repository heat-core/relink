package app.tether.ui

import app.tether.core.PairingCode
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter

internal object PairingQr {
    fun encode(code: PairingCode, legacy: Boolean, size: Int) =
        QRCodeWriter().encode(if (legacy) code.encode() else code.link(), BarcodeFormat.QR_CODE,
            size, size, mapOf(EncodeHintType.MARGIN to 4))
}
