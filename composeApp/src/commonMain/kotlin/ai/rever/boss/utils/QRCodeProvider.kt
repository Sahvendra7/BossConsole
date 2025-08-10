package ai.rever.boss.utils

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Platform-specific QR code provider interface
 */
expect object QRCodeProvider {
    /**
     * Generate a QR code as ImageBitmap
     * @param content The content to encode
     * @param size The size of the QR code
     * @return ImageBitmap or null if generation fails
     */
    fun generateQRCode(content: String, size: Int = 300): ImageBitmap?
    
    /**
     * Generate TOTP URI for authenticator apps
     * @param secret The TOTP secret
     * @param accountName The account name
     * @param issuer The service name
     * @return TOTP URI string
     */
    fun generateTOTPUri(
        secret: String,
        accountName: String,
        issuer: String = "BOSS"
    ): String
}