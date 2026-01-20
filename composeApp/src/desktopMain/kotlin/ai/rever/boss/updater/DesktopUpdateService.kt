package ai.rever.boss.updater

import ai.rever.boss.config.GitHubConfig
import ai.rever.boss.utils.ApplicationRestarter
import ai.rever.boss.utils.Version
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.network.sockets.*
import io.ktor.client.plugins.*
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
    
    // HTTP client for GitHub API calls - fast timeouts
    private val apiClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }

        install(HttpTimeout) {
            // API calls should complete quickly or fail fast
            requestTimeoutMillis = 30_000   // 30 seconds for entire API request
            connectTimeoutMillis = 15_000   // 15 seconds to establish connection
            socketTimeoutMillis = 15_000    // 15 seconds between data packets
        }
    }

    // HTTP client for file downloads - long timeouts for large files
    private val downloadClient = HttpClient(CIO) {
        install(HttpTimeout) {
            // Allow up to 15 minutes for entire download (for slow connections)
            // 275MB at 500KB/s = ~9 minutes, so 15 min provides buffer
            requestTimeoutMillis = 900_000  // 15 minutes

            // Connection establishment should be quick
            connectTimeoutMillis = 30_000   // 30 seconds

            // Socket timeout: max time between data packets
            // Ensures connection stays alive during continuous download
            socketTimeoutMillis = 60_000    // 60 seconds
        }
    }
    
    companion object {
        private const val GITHUB_API_BASE = "https://api.github.com"
        private const val RELEASES_REPO = "risa-labs-inc/BossConsole-Releases"
        private const val RELEASES_ENDPOINT = "$GITHUB_API_BASE/repos/$RELEASES_REPO/releases"
    }

    /**
     * Make GitHub API request with automatic fallback to unauthenticated access.
     * Since BossConsole-Releases is a public repo, authentication is optional.
     * If authenticated request fails with 401, automatically retry without token.
     */
    private suspend fun makeGitHubRequest(
        url: String,
        authContext: GitHubConfig.GitHubAuthContext
    ): HttpResponse {
        // Try authenticated request first if token available
        if (authContext.isAuthenticated) {
            val authenticatedResponse = apiClient.get(url) {
                headers {
                    append("Accept", "application/vnd.github.v3+json")
                    append("User-Agent", "BOSS-Desktop-${Version.CURRENT}")
                    append("Authorization", "Bearer ${authContext.token}")
                }
            }

            // If authenticated request succeeds, return it
            if (authenticatedResponse.status.value in 200..299) {
                return authenticatedResponse
            }

            // If 401 error with token, fall back to unauthenticated
            if (authenticatedResponse.status.value == 401) {
                println("⚠️ Authenticated request failed (token expired/invalid)")
                println("   Retrying without authentication (public repo access)...")
                // Fall through to unauthenticated request
            } else {
                // Other error, return it
                return authenticatedResponse
            }
        }

        // Make unauthenticated request (or fallback after 401)
        return apiClient.get(url) {
            headers {
                append("Accept", "application/vnd.github.v3+json")
                append("User-Agent", "BOSS-Desktop-${Version.CURRENT}")
            }
        }
    }

    actual suspend fun checkForUpdates(): UpdateInfo {
        return try {
            // Single token retrieval with validation
            val authContext = GitHubConfig.getAuthContext()

            // Accurate status message
            when {
                authContext.isAuthenticated -> {
                    println("✅ Using authenticated GitHub API (${authContext.rateLimit} requests/hour)")
                    println("   Token source: ${authContext.source}")
                    println("   Note: Public repo - will fallback to unauthenticated if token fails")
                }
                authContext.token != null && !authContext.isValid -> {
                    println("⚠️ GitHub token found but format is invalid")
                    println("   Using unauthenticated API (60 requests/hour)")
                    println("   Token source: ${authContext.source}")
                }
                else -> {
                    println("ℹ️ Using unauthenticated GitHub API (60 requests/hour)")
                    println("   Public repo access - no authentication needed")
                    println("   Optional: Add GITHUB_TOKEN for higher rate limits (5,000/hour)")
                }
            }

            // Use helper function with automatic fallback for public repo
            val response = makeGitHubRequest(RELEASES_ENDPOINT, authContext)

            // Check for error responses (rate limits, etc.)
            if (response.status.value !in 200..299) {
                val errorBody = response.bodyAsText()
                val errorMessage = when {
                    response.status.value == 401 ->
                        "GitHub API authentication failed. This is unexpected for public repository access."
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
            val platform = getCurrentPlatform()
            val expectedAssetName = getExpectedAssetName(latestVersion)
            println("Looking for asset: $expectedAssetName (platform: $platform)")
            println("Available assets: ${latestRelease.assets.map { it.name }}")

            var asset = latestRelease.assets.find {
                it.name.equals(expectedAssetName, ignoreCase = true)
            }

            // Fallback: If platform-specific package (.deb/.rpm) not found, try JAR
            if (asset == null && (platform == "Linux-deb" || platform == "Linux-rpm")) {
                val jarAssetName = "BOSS-${latestVersion}-${getLinuxArchSuffix()}.jar"
                println("Platform package not found, trying JAR fallback: $jarAssetName")
                asset = latestRelease.assets.find {
                    it.name.equals(jarAssetName, ignoreCase = true)
                }
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
            println("Download timeout configuration: request=15min, connect=30s, socket=60s")

            val response = downloadClient.get(downloadUrl)
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
            val errorMessage = when (e) {
                is HttpRequestTimeoutException -> "Download timeout: File too large or connection too slow. Please try again with a faster connection."
                is ConnectTimeoutException -> "Connection timeout: Unable to reach download server. Check your internet connection."
                is SocketTimeoutException -> "Network timeout: Download interrupted. Check your network stability."
                else -> e.message ?: "Unknown error"
            }
            println("Error downloading update: $errorMessage")
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

    /**
     * Get the Linux architecture suffix based on the current system.
     * Returns "arm64" for ARM64/aarch64 systems, "amd64" for x86_64 systems.
     */
    private fun getLinuxArchSuffix(): String {
        val arch = System.getProperty("os.arch")
        return when {
            arch == "aarch64" || arch == "arm64" -> "arm64"
            else -> "amd64"
        }
    }

    actual fun getExpectedAssetName(version: Version): String {
        return when (getCurrentPlatform()) {
            "macOS" -> "BOSS-${version}-Universal.dmg"
            "Windows" -> "BOSS-${version}.msi"
            "Linux", "Linux-deb" -> "BOSS-${version}-${getLinuxArchSuffix()}.deb"
            "Linux-rpm" -> "BOSS-${version}-${getLinuxArchSuffix()}.rpm"
            else -> "BOSS-${version}-${getLinuxArchSuffix()}.jar"  // JAR with arch for native deps
        }
    }

    /**
     * Fetch all releases from GitHub with pagination support
     */
    actual suspend fun fetchAllReleases(): List<VersionInfo> = withContext(Dispatchers.IO) {
        try {
            val authContext = GitHubConfig.getAuthContext()
            val allReleases = mutableListOf<GitHubRelease>()
            var page = 1
            val perPage = 100 // GitHub max per page

            // Fetch all pages until we get an empty response
            while (true) {
                val url = "$RELEASES_ENDPOINT?page=$page&per_page=$perPage"
                val response = makeGitHubRequest(url, authContext)

                if (response.status.value !in 200..299) {
                    println("Failed to fetch releases page $page: HTTP ${response.status.value}")
                    break
                }

                val releases = response.body<List<GitHubRelease>>()
                if (releases.isEmpty()) break

                allReleases.addAll(releases)

                // If we got fewer than perPage, we've reached the last page
                if (releases.size < perPage) break
                page++
            }

            // Convert to VersionInfo
            allReleases.mapNotNull { release ->
                try {
                    val version = Version.parse(release.tag_name) ?: return@mapNotNull null
                    val expectedAssetName = getExpectedAssetName(version)
                    val asset = release.assets.find {
                        it.name.equals(expectedAssetName, ignoreCase = true)
                    }

                    if (asset != null) {
                        VersionInfo(
                            version = version,
                            releaseDate = release.published_at,
                            downloadSize = asset.size,
                            releaseNotes = release.body,
                            downloadUrl = asset.browser_download_url ?: "",
                            isDraft = release.draft,
                            isPrerelease = release.prerelease
                        )
                    } else null
                } catch (e: Exception) {
                    println("Failed to parse release ${release.tag_name}: ${e.message}")
                    null
                }
            }
        } catch (e: Exception) {
            println("Error fetching all releases: ${e.message}")
            emptyList()
        }
    }

    /**
     * Fetch details for a specific version
     */
    actual suspend fun fetchVersionDetails(version: Version): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val authContext = GitHubConfig.getAuthContext()
            val tagName = "v$version"
            val response = makeGitHubRequest("$RELEASES_ENDPOINT/tags/$tagName", authContext)

            if (response.status.value !in 200..299) {
                println("Failed to fetch version $version: HTTP ${response.status.value}")
                return@withContext null
            }

            val release = response.body<GitHubRelease>()
            val expectedAssetName = getExpectedAssetName(version)
            val asset = release.assets.find {
                it.name.equals(expectedAssetName, ignoreCase = true)
            }

            UpdateInfo(
                available = true,
                currentVersion = Version.CURRENT,
                latestVersion = version,
                releaseNotes = release.body,
                downloadUrl = asset?.browser_download_url,
                assetSize = asset?.size ?: 0,
                assetName = asset?.name ?: ""
            )
        } catch (e: Exception) {
            println("Error fetching version $version details: ${e.message}")
            null
        }
    }
}
