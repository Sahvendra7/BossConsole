package ai.rever.boss.utils

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.client.j2se.MatrixToImageWriter
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import org.jetbrains.skia.Image
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

/**
 * QR Code generator utility for desktop applications
 */
object QRCodeGenerator {
    
    /**
     * Generate a QR code as ImageBitmap for Compose
     * @param content The content to encode in the QR code
     * @param size The size of the QR code (width and height)
     * @return ImageBitmap that can be displayed in Compose
     */
    fun generateQRCode(content: String, size: Int = 300): ImageBitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size)
            
            // Convert to BufferedImage
            val bufferedImage = MatrixToImageWriter.toBufferedImage(bitMatrix)
            
            // Convert BufferedImage to ImageBitmap for Compose
            val outputStream = ByteArrayOutputStream()
            ImageIO.write(bufferedImage, "PNG", outputStream)
            val imageBytes = outputStream.toByteArray()
            
            Image.makeFromEncoded(imageBytes).toComposeImageBitmap()
        } catch (e: Exception) {
            println("Error generating QR code: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    /**
     * Generate TOTP URI for authenticator apps
     * @param secret The TOTP secret
     * @param accountName The account name (usually email)
     * @param issuer The service name
     * @return TOTP URI string
     */
    fun generateTOTPUri(
        secret: String,
        accountName: String,
        issuer: String = "BOSS"
    ): String {
        return "otpauth://totp/$issuer:$accountName?secret=$secret&issuer=$issuer&algorithm=SHA1&digits=6&period=30"
    }
}