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
                .mapNotNull { release -> 
                    Version.parse(release.tag_name)?.let { version -> release to version }
                }
                .maxByOrNull { it.second }
                ?.first
            
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
            println("Looking for asset: $expectedAssetName")
            println("Available assets: ${latestRelease.assets.map { it.name }}")
            
            val asset = latestRelease.assets.find { 
                it.name.equals(expectedAssetName, ignoreCase = true) 
            }
            
            if (asset == null) {
                println("Warning: Expected asset '$expectedAssetName' not found in release")
            } else {
                println("Found asset: ${asset.name} with download URL: ${asset.browser_download_url}")
            }
            
            UpdateInfo(
                available = isUpdateAvailable,
                currentVersion = Version.CURRENT,
                latestVersion = latestVersion,
                releaseNotes = latestRelease.body,
                downloadUrl = asset?.browser_download_url,
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
            val downloadUrl = updateInfo.downloadUrl
            if (downloadUrl == null) {
                println("Error: No download URL available for asset: ${updateInfo.assetName}")
                return null
            }
            
            println("Starting download from: $downloadUrl")
            println("Expected asset: ${updateInfo.assetName} (${updateInfo.assetSize} bytes)")
            
            val response = httpClient.get(downloadUrl)
            if (response.status.value !in 200..299) {
                println("Download failed with HTTP status: ${response.status.value} ${response.status.description}")
                return null
            }
            
            // Get total size from response headers if not available from GitHub API
            val totalSize = response.headers["Content-Length"]?.toLongOrNull() ?: updateInfo.assetSize
            val tempDir = File(System.getProperty("java.io.tmpdir"), "boss-updates")
            tempDir.mkdirs()
            
            val downloadFile = File(tempDir, updateInfo.assetName)
            if (downloadFile.exists()) {
                downloadFile.delete()
            }
            
            println("Download info: totalSize=$totalSize, expectedSize=${updateInfo.assetSize}")
            
            withContext(Dispatchers.IO) {
                val channel = response.bodyAsChannel()
                val outputStream = FileOutputStream(downloadFile)
                
                var downloadedBytes = 0L
                val buffer = ByteArray(8192)
                var lastProgressUpdate = 0L
                var progressUpdateCount = 0
                
                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead > 0) {
                        outputStream.write(buffer, 0, bytesRead)
                        downloadedBytes += bytesRead
                        
                        // Update progress for smooth UI feedback without being too frequent
                        val shouldUpdateProgress = if (totalSize > 0) {
                            // Update every 256KB or every 5% progress change, whichever comes first
                            val bytesThreshold = downloadedBytes - lastProgressUpdate >= 262144 // 256KB
                            val currentProgress = downloadedBytes.toFloat() / totalSize.toFloat()
                            val lastProgress = lastProgressUpdate.toFloat() / totalSize.toFloat()
                            val progressThreshold = (currentProgress - lastProgress) >= 0.05f // 5%
                            bytesThreshold || progressThreshold
                        } else {
                            // Update every 128KB for indeterminate progress
                            downloadedBytes - lastProgressUpdate >= 131072
                        }
                        
                        if (shouldUpdateProgress) {
                            val progress = if (totalSize > 0) {
                                val currentProgress = (downloadedBytes.toFloat() / totalSize.toFloat()).coerceIn(0f, 1f)
                                // Log only major progress milestones for performance
                                if (currentProgress * 100 % 10 < 5) {
                                    println("Progress: ${(currentProgress * 100).toInt()}% (${downloadedBytes / 1024}KB / ${totalSize / 1024}KB)")
                                }
                                currentProgress
                            } else {
                                // Indeterminate progress - cycle between 0.1 and 0.9
                                val cyclicProgress = 0.1f + (downloadedBytes / 1048576f % 0.8f)
                                // Log every MB for indeterminate progress
                                if (downloadedBytes / 1048576 != lastProgressUpdate / 1048576) {
                                    println("Progress: ${downloadedBytes / 1024}KB downloaded (indeterminate)")
                                }
                                cyclicProgress
                            }
                            
                            // Ensure progress updates happen on main thread for UI updates
                            withContext(Dispatchers.Main) {
                                onProgress(progress)
                            }
                            lastProgressUpdate = downloadedBytes
                        }
                        
                        progressUpdateCount++
                    }
                }
                
                outputStream.close()
                channel.cancel()
                
                // Ensure 100% progress is reported on completion on main thread
                withContext(Dispatchers.Main) {
                    onProgress(1f)
                }
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
                println("Starting automated macOS update installation...")
                
                // Get current application bundle path
                val currentAppPath = getCurrentApplicationPath()
                if (currentAppPath == null) {
                    println("⚠️ Could not determine current application path")
                    println("   This is expected when running in development mode (IDE/Gradle)")
                    println("   Falling back to manual DMG installation")
                    return@withContext openDMGForManualInstallation(downloadFile)
                }
                
                println("🎯 Target application path: $currentAppPath")
                
                // Mount the DMG
                val mountResult = ProcessBuilder("hdiutil", "attach", downloadFile.absolutePath, "-nobrowse", "-quiet")
                    .start()
                mountResult.waitFor()
                
                if (mountResult.exitValue() != 0) {
                    println("Failed to mount DMG, falling back to manual installation")
                    return@withContext openDMGForManualInstallation(downloadFile)
                }
                
                // Find the mounted volume
                val volumesDir = File("/Volumes")
                val mountedVolume = volumesDir.listFiles()?.find { 
                    it.name.contains("BOSS", ignoreCase = true) && it.isDirectory 
                }
                
                if (mountedVolume == null) {
                    println("Could not find mounted BOSS volume, falling back to manual installation")
                    return@withContext openDMGForManualInstallation(downloadFile)
                }
                
                // Find the .app bundle in the mounted volume
                val appBundle = mountedVolume.listFiles()?.find { 
                    it.name.endsWith(".app") && it.name.contains("BOSS", ignoreCase = true)
                }
                
                if (appBundle == null) {
                    println("Could not find BOSS.app in mounted volume, falling back to manual installation")
                    cleanupDMG(mountedVolume)
                    return@withContext openDMGForManualInstallation(downloadFile)
                }
                
                // Create backup of current app
                val backupPath = File("${currentAppPath}.backup")
                if (backupPath.exists()) {
                    backupPath.deleteRecursively()
                }
                File(currentAppPath).renameTo(backupPath)
                
                // Copy new app bundle to Applications
                val copyResult = ProcessBuilder("cp", "-R", appBundle.absolutePath, currentAppPath)
                    .start()
                copyResult.waitFor()
                
                // Cleanup
                cleanupDMG(mountedVolume)
                
                if (copyResult.exitValue() == 0) {
                    println("✅ macOS update installed successfully")
                    println("   Backup created at: ${backupPath.absolutePath}")
                    true
                } else {
                    println("Failed to copy new app bundle, restoring backup")
                    // Restore backup if copy failed
                    backupPath.renameTo(File(currentAppPath))
                    false
                }
                
            } catch (e: Exception) {
                println("Error during automated installation: ${e.message}")
                println("Falling back to manual installation")
                openDMGForManualInstallation(downloadFile)
            }
        }
    }
    
    private fun getCurrentApplicationPath(): String? {
        return try {
            println("🔍 Attempting to detect current application path...")
            
            // Method 1: Check java.library.path for .app bundle
            val libraryPath = System.getProperty("java.library.path")
            println("   java.library.path: $libraryPath")
            
            val bundlePath = libraryPath
                ?.split(":")
                ?.find { it.contains(".app") }
                ?.let { "${it.substringBefore(".app")}.app" }
            
            if (bundlePath?.contains(".app") == true && File(bundlePath).exists()) {
                println("✅ Found app bundle via library path: $bundlePath")
                return bundlePath
            }
            
            // Method 2: Try to find app bundle from current JAR/class location
            val jarPath = this::class.java.protectionDomain.codeSource.location.path
            println("   Current code source: $jarPath")
            
            var currentFile = File(jarPath)
            // Walk up the directory tree looking for .app bundle
            for (i in 0..5) { // Max 5 levels up
                println("   Checking parent $i: ${currentFile.absolutePath}")
                if (currentFile.name.endsWith(".app")) {
                    println("✅ Found app bundle via directory traversal: ${currentFile.absolutePath}")
                    return currentFile.absolutePath
                }
                currentFile = currentFile.parentFile ?: break
            }
            
            // Method 3: Check if running from Applications folder
            val applicationsPath = "/Applications/BOSS.app"
            if (File(applicationsPath).exists()) {
                println("✅ Found BOSS in Applications folder: $applicationsPath")
                return applicationsPath
            }
            
            // Method 4: Development mode - try to find existing BOSS.app in common locations
            val commonPaths = listOf(
                "/Applications/BOSS.app",
                System.getProperty("user.home") + "/Applications/BOSS.app",
                System.getProperty("user.home") + "/Desktop/BOSS.app"
            )
            
            for (path in commonPaths) {
                if (File(path).exists()) {
                    println("✅ Found existing BOSS.app for development update: $path")
                    return path
                }
            }
            
            println("❌ Could not determine application path - running in development mode?")
            println("   This is normal when running from IDE/Gradle")
            null
            
        } catch (e: Exception) {
            println("❌ Error getting application path: ${e.message}")
            e.printStackTrace()
            null
        }
    }
    
    private fun openDMGForManualInstallation(downloadFile: File): Boolean {
        return try {
            val process = ProcessBuilder("open", downloadFile.absolutePath).start()
            process.waitFor()
            println("DMG opened for manual installation: ${downloadFile.absolutePath}")
            true
        } catch (e: Exception) {
            println("Failed to open DMG: ${e.message}")
            false
        }
    }
    
    private fun cleanupDMG(mountedVolume: File) {
        try {
            ProcessBuilder("hdiutil", "detach", mountedVolume.absolutePath, "-quiet")
                .start()
                .waitFor()
            println("DMG unmounted successfully")
        } catch (e: Exception) {
            println("Warning: Could not unmount DMG: ${e.message}")
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