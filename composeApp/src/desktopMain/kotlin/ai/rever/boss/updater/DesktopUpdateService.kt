package ai.rever.boss.updater

import ai.rever.boss.config.GitHubConfig
import ai.rever.boss.utils.ApplicationRestarter
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
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
            // Log authentication status
            if (GitHubConfig.hasToken) {
                println("✅ Using authenticated GitHub API (5,000 requests/hour)")
            } else {
                println("⚠️ Using unauthenticated GitHub API (60 requests/hour)")
                println("   Add GITHUB_TOKEN to local.properties for higher rate limits")
            }

            val response = httpClient.get("$RELEASES_ENDPOINT") {
                headers {
                    append("Accept", "application/vnd.github.v3+json")
                    append("User-Agent", "BOSS-Desktop-${Version.CURRENT}")

                    // Add authentication token if available
                    GitHubConfig.token?.let { token ->
                        append("Authorization", "Bearer $token")
                    }
                }
            }

            // Check for error responses (rate limits, etc.)
            if (response.status.value !in 200..299) {
                val errorBody = response.bodyAsText()
                val errorMessage = when {
                    errorBody.contains("rate limit", ignoreCase = true) ->
                        "GitHub API rate limit exceeded. Please try again later."
                    else -> "Unable to check for updates (HTTP ${response.status.value})"
                }
                println("Update check failed: $errorMessage")
                return UpdateInfo(
                    available = false,
                    currentVersion = Version.CURRENT,
                    latestVersion = Version.CURRENT,
                    releaseNotes = ""
                )
            }

            val releases = response.body<List<GitHubRelease>>()
            
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
            // Handle JSON parsing errors (rate limits, malformed responses, etc.)
            val errorMessage = when {
                e.message?.contains("rate limit", ignoreCase = true) == true ->
                    "GitHub API rate limit exceeded. Please try again later."
                e.message?.contains("Expected start of the array", ignoreCase = true) == true ->
                    "Unexpected API response format. Please try again later."
                e.message?.contains("JSON", ignoreCase = true) == true ->
                    "Error parsing update information. Please try again later."
                else -> "Unable to check for updates: ${e.message?.take(100) ?: "Unknown error"}"
            }
            println("Error checking for updates: $errorMessage")

            UpdateInfo(
                available = false,
                currentVersion = Version.CURRENT,
                latestVersion = Version.CURRENT,
                releaseNotes = ""
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
            null
        }
    }
    
    actual suspend fun installUpdate(downloadPath: String): Boolean {
        // Delegate to UpdateInstaller
        val result = UpdateInstaller.installUpdate(downloadPath)

        return when (result) {
            is InstallResult.Success -> {
                println("✅ ${result.message}")
                true
            }
            is InstallResult.RequiresRestart -> {
                println("🔄 ${result.message}")
                println("   Helper script is waiting for app to quit...")

                // The helper script is now running and waiting for this process to exit
                // We need to quit the app so the script can proceed with installation
                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                GlobalScope.launch {
                    // Give the UI a moment to show the "installing" message
                    delay(1000)

                    // Quit the application cleanly
                    ApplicationRestarter.quitForUpdate()
                }

                true
            }
            is InstallResult.Error -> {
                println("❌ ${result.message}")
                false
            }
        }
    }
    
    actual fun getCurrentPlatform(): String {
        return UpdateInstaller.getCurrentPlatform()
    }

    actual fun getExpectedAssetName(version: Version): String {
        return when (getCurrentPlatform()) {
            "macOS" -> "BOSS-${version}-Universal.dmg"
            "Windows" -> "BOSS-${version}.msi"
            else -> "BOSS-${version}-all.jar"
        }
    }
}
