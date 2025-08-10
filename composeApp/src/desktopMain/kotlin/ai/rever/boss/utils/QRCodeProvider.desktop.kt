package ai.rever.boss.utils

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Desktop implementation of QR code provider
 */
actual object QRCodeProvider {
    actual fun generateQRCode(content: String, size: Int): ImageBitmap? {
        return QRCodeGenerator.generateQRCode(content, size)
    }
    
    actual fun generateTOTPUri(
        secret: String,
        accountName: String,
        issuer: String
    ): String {
        return QRCodeGenerator.generateTOTPUri(secret, accountName, issuer)
    }
}