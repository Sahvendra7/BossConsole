package ai.rever.boss.updater

import ai.rever.boss.utils.Version
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.serialization.kotlinx.json.*
import io.ktor.utils.io.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.util.*

actual class UpdateService {
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
    }
    
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val RELEASES_REPO = "risa-labs-inc/BOSS-Releases"
        private const val RELEASES_ENDPOINT = "$GITHUB_API_BASE/repos/$RELEASES_REPO/releases"
    }
    
    actual suspend fun checkForUpdates(): UpdateInfo {
        return try {
            val releases = httpClient.get("$RELEASES_ENDPOINT") {
                headers {
                    append("Accept", "application/vnd.github.v3+json")
                    append("User-Agent", "BOSS-Desktop-${Version.CURRENT}")
                }
            }.body<List<GitHubRelease>>()
            
            // Get the latest non-draft, non-prerelease version
            val latestRelease = releases
                .filter { !it.draft && !it.prerelease }
                .maxByOrNull { Version.parse(it.tag_name)?.toString() ?: "" }
            
            if (latestRelease == null) {
                return UpdateInfo(
                    available = false,
                    currentVersion = Version.CURRENT,
                    latestVersion = Version.CURRENT,
                    releaseNotes = ""
                )
            }
            
            val latestVersion = Version.parse(latestRelease.tag_name)
            if (latestVersion == null) {
                return UpdateInfo(
                    available = false,
                    currentVersion = Version.CURRENT,
                    latestVersion = Version.CURRENT,
                    releaseNotes = ""
                )
            }
            
            val isUpdateAvailable = latestVersion.isNewerThan(Version.CURRENT)
            
            // Find the appropriate asset for the current platform
            val expectedAssetName = getExpectedAssetName(latestVersion)
            val asset = latestRelease.assets.find { 
                it.name.equals(expectedAssetName, ignoreCase = true) 
            }
            
            UpdateInfo(
                available = isUpdateAvailable,
                currentVersion = Version.CURRENT,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = asset?.download_url,
                assetSize = asset?.size ?: 0,
                assetName = asset?.name ?: ""
            )
            
        } catch (e: Exception) {
            // Log error but don't print stack trace for expected serialization issues
            println("Error checking for updates: ${e.message}")
            UpdateInfo(
                available = false,
                currentVersion = Version.CURRENT,
                latestVersion = Version.CURRENT,
                releaseNotes = "Unable to check for updates at this time"
            )
        }
    }
    
    actual suspend fun downloadUpdate(
        updateInfo: UpdateInfo, 
        onProgress: (progress: Float) -> Unit
    ): String? {
        return try {
            val downloadUrl = updateInfo.downloadUrl ?: return null
            
            val response = httpClient.get(downloadUrl)
            if (response.status.value !in 200..299) {
                println("Download failed with status: ${response.status}")
                return null
            }
            
            val totalSize = updateInfo.assetSize
            val tempDir = File(System.getProperty("java.io.tmpdir"), "boss-updates")
            tempDir.mkdirs()
            
            val downloadFile = File(tempDir, updateInfo.assetName)
            if (downloadFile.exists()) {
                downloadFile.delete()
            }
            
            withContext(Dispatchers.IO) {
                val channel = response.bodyAsChannel()
                val outputStream = FileOutputStream(downloadFile)
                
                var downloadedBytes = 0L
                val buffer = ByteArray(8192)
                
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        if (totalSize > 0) {
                            val progress = downloadedBytes.toFloat() / totalSize.toFloat()
                            onProgress(progress)
                        }
                    }
                }
                
                outputStream.close()
                channel.cancel()
            }
            
            if (downloadFile.exists() && downloadFile.length() > 0) {
                println("Update downloaded successfully: ${downloadFile.absolutePath}")
                downloadFile.absolutePath
            } else {
                println("Download failed: file is empty or doesn't exist")
                null
            }
            
        } catch (e: Exception) {
            println("Error downloading update: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    actual suspend fun installUpdate(downloadPath: String): Boolean {
        return try {
            val downloadFile = File(downloadPath)
            if (!downloadFile.exists()) {
                println("Update file not found: $downloadPath")
                return false
            }
            
            when (getCurrentPlatform()) {
                "macOS" -> installMacOSUpdate(downloadFile)
                "Windows" -> installWindowsUpdate(downloadFile)
                else -> installJarUpdate(downloadFile)
            }
        } catch (e: Exception) {
            println("Error installing update: ${e.message}")
            e.printStackTrace()
            false
        }
    }
    
    private suspend fun installMacOSUpdate(downloadFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // For DMG files on macOS, we'll open it and let the user handle installation
                // In the future, we could automate this with AppleScript
                val process = ProcessBuilder("open", downloadFile.absolutePath).start()
                process.waitFor()
                
                println("DMG opened for user installation: ${downloadFile.absolutePath}")
                true
            } catch (e: Exception) {
                println("Failed to open DMG: ${e.message}")
                false
            }
        }
    }
    
    private suspend fun installWindowsUpdate(downloadFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // For MSI files on Windows, we'll launch the installer
                val process = ProcessBuilder("msiexec", "/i", downloadFile.absolutePath).start()
                process.waitFor()
                
                println("MSI installer launched: ${downloadFile.absolutePath}")
                true
            } catch (e: Exception) {
                println("Failed to launch MSI installer: ${e.message}")
                false
            }
        }
    }
    
    private suspend fun installJarUpdate(downloadFile: File): Boolean {
        return withContext(Dispatchers.IO) {
            try {
                // For JAR updates, we need to replace the current JAR
                val currentJar = getCurrentJarPath()
                if (currentJar != null) {
                    // Create backup
                    val backupFile = File(currentJar.parent, "${currentJar.nameWithoutExtension}.backup.jar")
                    currentJar.copyTo(backupFile, overwrite = true)
                    
                    // Replace current JAR
                    downloadFile.copyTo(currentJar, overwrite = true)
                    
                    println("JAR updated successfully. Backup created at: ${backupFile.absolutePath}")
                    true
                } else {
                    println("Could not determine current JAR path")
                    false
                }
            } catch (e: Exception) {
                println("Failed to update JAR: ${e.message}")
                false
            }
        }
    }
    
    private fun getCurrentJarPath(): File? {
        return try {
            val jarPath = UpdateService::class.java.protectionDomain.codeSource.location.toURI().path
            val jarFile = File(jarPath)
            if (jarFile.exists() && jarFile.name.endsWith(".jar")) {
                jarFile
            } else null
        } catch (e: Exception) {
            null
        }
    }
    
    actual fun getCurrentPlatform(): String {
        val osName = System.getProperty("os.name").lowercase(Locale.getDefault())
        return when {
            osName.contains("mac") || osName.contains("darwin") -> "macOS"
            osName.contains("win") -> "Windows"
            osName.contains("linux") -> "Linux"
            else -> "Unknown"
        }
    }
    
    actual fun getExpectedAssetName(version: Version): String {
        return when (getCurrentPlatform()) {
            "macOS" -> "BOSS-${version}-Universal.dmg"
            "Windows" -> "BOSS-${version}.msi"
            else -> "BOSS-${version}-all.jar"
        }
    }
}