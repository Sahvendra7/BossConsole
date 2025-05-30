package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.registery.TabIcon
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.image.BufferedImage
import java.io.File
import java.net.URL
import javax.imageio.ImageIO
import java.security.MessageDigest

object FaviconLoader {
    private val cacheDir = File(System.getProperty("user.home"), ".boss/favicon-cache").apply {
        mkdirs()
    }
    
    private val faviconCache = mutableMapOf<String, TabIcon>()
    
    suspend fun loadFavicon(faviconUrl: String): TabIcon? = withContext(Dispatchers.IO) {
        try {
            // Check memory cache first
            faviconCache[faviconUrl]?.let { return@withContext it }
            
            // Check disk cache
            val cacheFile = getCacheFile(faviconUrl)
            if (cacheFile.exists()) {
                val bitmap = ImageIO.read(cacheFile)
                bitmap?.let {
                    val tabIcon = TabIcon.Image(BitmapPainter(it.toComposeImageBitmap()))
                    faviconCache[faviconUrl] = tabIcon
                    return@withContext tabIcon
                }
            }
            
            // Download favicon
            val url = URL(faviconUrl)
            val connection = url.openConnection()
            connection.connectTimeout = 5000
            connection.readTimeout = 5000
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            
            val inputStream = connection.getInputStream()
            val image = ImageIO.read(inputStream)
            inputStream.close()
            
            if (image != null) {
                // Resize to 16x16 if needed
                val resized = if (image.width > 32 || image.height > 32) {
                    val scaledImage = BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)
                    val g2d = scaledImage.createGraphics()
                    g2d.setRenderingHint(
                        java.awt.RenderingHints.KEY_INTERPOLATION,
                        java.awt.RenderingHints.VALUE_INTERPOLATION_BICUBIC
                    )
                    g2d.drawImage(image, 0, 0, 16, 16, null)
                    g2d.dispose()
                    scaledImage
                } else {
                    image
                }
                
                // Save to cache
                ImageIO.write(resized, "png", cacheFile)
                
                // Create TabIcon
                val tabIcon = TabIcon.Image(BitmapPainter(resized.toComposeImageBitmap()))
                faviconCache[faviconUrl] = tabIcon
                return@withContext tabIcon
            }
            
            null
        } catch (e: Exception) {
            println("Failed to load favicon from $faviconUrl: ${e.message}")
            null
        }
    }
    
    private fun getCacheFile(url: String): File {
        val hash = MessageDigest.getInstance("MD5")
            .digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }
        return File(cacheDir, "$hash.png")
    }
} 