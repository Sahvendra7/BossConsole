package ai.rever.boss.plugin

import ai.rever.boss.components.plugin.DynamicPluginManager
import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.services.auth.AuthStateManager
import ai.rever.boss.plugin.api.PluginManifestConstants
import ai.rever.boss.plugin.panel.manager.ExtractedManifest
import ai.rever.boss.plugin.panel.manager.InstalledPluginState
import ai.rever.boss.plugin.panel.manager.PluginManagerComponent
import ai.rever.boss.plugin.panel.manager.PluginManagerOperations
import ai.rever.boss.plugin.repository.PluginInfo
import ai.rever.boss.plugin.repository.PluginRepositoryManager
import ai.rever.boss.plugin.updater.PluginUpdateManager
import ai.rever.boss.plugin.updater.UpdateInfo
import ai.rever.boss.plugin.repository.remote.FinalizeVersionRequest
import ai.rever.boss.plugin.repository.remote.PluginStoreClient
import ai.rever.boss.plugin.repository.remote.PublishPluginRequest
import ai.rever.boss.plugin.repository.remote.PluginStoreConfig
import ai.rever.boss.plugin.repository.remote.PublishVersionRequest
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import java.util.jar.JarFile

/**
 * Desktop implementation of PluginManagerOperations.
 *
 * Connects the Plugin Manager UI to the plugin store infrastructure:
 * - DynamicPluginManager for local plugin operations
 * - RepositoryManager for remote plugin browsing and downloading
 * - UpdateManager for update checking
 */
class PluginManagerOperationsImpl(
    private val dynamicPluginManager: DynamicPluginManager,
    private val repositoryManagerProvider: () -> PluginRepositoryManager?,
    private val updateManagerProvider: () -> PluginUpdateManager?,
    private val onInstalledPluginsChanged: (List<InstalledPluginState>) -> Unit,
    private val onAvailablePluginsChanged: (List<PluginInfo>) -> Unit,
    private val onUpdatesChanged: (List<UpdateInfo>) -> Unit
) : PluginManagerOperations {

    private val logger = BossLogger.forComponent("PluginManagerOperationsImpl")

    private val repositoryManager: PluginRepositoryManager?
        get() = repositoryManagerProvider()

    private val updateManager: PluginUpdateManager?
        get() = updateManagerProvider()

    init {
        // Set up realtime callback to refresh available plugins when changes occur
        PluginStoreSetup.setOnPluginsChangedCallback {
            logger.debug(LogCategory.NETWORK, "Realtime plugin change detected, refreshing available plugins")
            refreshAvailablePlugins()
        }
    }

    /**
     * Refresh only the available plugins from remote repository.
     * This is called by the realtime service when plugins change.
     */
    private suspend fun refreshAvailablePlugins() {
        try {
            repositoryManager?.let { repoManager ->
                val remoteRepo = repoManager.getRepository("supabase-store")
                if (remoteRepo != null && remoteRepo.isAvailable) {
                    val listResult = remoteRepo.listPlugins()
                    if (listResult.isSuccess) {
                        onAvailablePluginsChanged(listResult.getOrThrow())
                        logger.debug(LogCategory.NETWORK, "Available plugins refreshed via realtime", mapOf(
                            "count" to listResult.getOrThrow().size
                        ))
                    }
                }
            }
        } catch (e: Exception) {
            logger.error(LogCategory.NETWORK, "Error refreshing plugins from realtime", error = e)
        }
    }

    override suspend fun installPlugin(jarPath: String, sourceUrl: String?, version: String?): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Installing plugin from JAR", mapOf(
                "jarPath" to jarPath,
                "sourceUrl" to (sourceUrl ?: "none")
            ))

            val sourceFile = File(jarPath)
            if (!sourceFile.exists()) {
                return Result.failure(Exception("JAR file not found: $jarPath"))
            }

            // Copy JAR to plugins directory if not already there
            val pluginDir = PluginStoreSetup.getPluginDir()
            val targetFile = File(pluginDir, sourceFile.name)
            val installPath = if (sourceFile.parentFile?.absolutePath != pluginDir.absolutePath) {
                // Copy to plugins directory
                sourceFile.copyTo(targetFile, overwrite = true)
                logger.info(LogCategory.SYSTEM, "Copied JAR to plugins directory", mapOf(
                    "source" to jarPath,
                    "target" to targetFile.absolutePath
                ))
                targetFile.absolutePath
            } else {
                jarPath
            }

            val result = dynamicPluginManager.installPlugin(installPath, enabled = true)
            if (result.isSuccess) {
                val manifest = result.getOrNull()?.manifest
                val pluginId = manifest?.pluginId
                val installedVersion = version ?: manifest?.version
                logger.info(LogCategory.SYSTEM, "Plugin installed successfully", mapOf(
                    "pluginId" to pluginId,
                    "version" to (installedVersion ?: "unknown"),
                    "sourceUrl" to (sourceUrl ?: "none")
                ))
                // Persist installed state with source URL for update tracking
                if (pluginId != null) {
                    PluginPersistence.addInstalledPlugin(
                        pluginId = pluginId,
                        jarPath = installPath,
                        enabled = true,
                        sourceUrl = sourceUrl,
                        installedVersion = installedVersion
                    )
                }
                Result.success(Unit)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown install error")
                logger.error(LogCategory.SYSTEM, "Failed to install plugin", error = error)
                // Clean up copied file on failure
                if (installPath != jarPath) {
                    File(installPath).delete()
                }
                Result.failure(error)
            }
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception installing plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun uninstallPlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Uninstalling plugin", mapOf(
                "pluginId" to pluginId
            ))

            // Get the JAR path before uninstalling so we can delete it after
            val pluginInfo = dynamicPluginManager.getPluginInfo(pluginId)
            val jarPath = pluginInfo?.jarPath

            val result = dynamicPluginManager.uninstallPlugin(pluginId, force = false)
            if (result.isSuccess) {
                logger.info(LogCategory.SYSTEM, "Plugin uninstalled successfully", mapOf(
                    "pluginId" to pluginId
                ))

                // Delete the JAR file after successful uninstall
                if (jarPath != null) {
                    try {
                        val jarFile = File(jarPath)
                        if (jarFile.exists()) {
                            val deleted = jarFile.delete()
                            if (deleted) {
                                logger.info(LogCategory.SYSTEM, "Plugin JAR deleted", mapOf(
                                    "pluginId" to pluginId,
                                    "jarPath" to jarPath
                                ))
                            } else {
                                logger.warn(LogCategory.SYSTEM, "Failed to delete plugin JAR", mapOf(
                                    "pluginId" to pluginId,
                                    "jarPath" to jarPath
                                ))
                            }
                        }
                    } catch (e: Exception) {
                        logger.warn(LogCategory.SYSTEM, "Error deleting plugin JAR", mapOf(
                            "pluginId" to pluginId,
                            "jarPath" to jarPath,
                            "error" to (e.message ?: "unknown")
                        ))
                    }
                }

                // Remove from persistence
                PluginPersistence.removeInstalledPlugin(pluginId)
            } else {
                val error = result.exceptionOrNull() ?: Exception("Unknown uninstall error")
                logger.error(LogCategory.SYSTEM, "Failed to uninstall plugin", error = error)
            }
            result
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception uninstalling plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun enablePlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Enabling plugin", mapOf(
                "pluginId" to pluginId
            ))
            val result = dynamicPluginManager.enablePlugin(pluginId)
            if (result.isSuccess) {
                PluginPersistence.setPluginEnabled(pluginId, true)
            }
            result
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception enabling plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun disablePlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Disabling plugin", mapOf(
                "pluginId" to pluginId
            ))
            val result = dynamicPluginManager.disablePlugin(pluginId)
            if (result.isSuccess) {
                PluginPersistence.setPluginEnabled(pluginId, false)
            }
            result
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception disabling plugin", error = e)
            Result.failure(e)
        }
    }

    override suspend fun updatePlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Updating plugin", mapOf(
                "pluginId" to pluginId
            ))

            // Check if this plugin was installed from GitHub
            val sourceUrl = PluginPersistence.getSourceUrl(pluginId)
            if (!sourceUrl.isNullOrBlank() && sourceUrl.contains("github.com")) {
                // Update from GitHub
                return updatePluginFromGitHub(pluginId, sourceUrl)
            }

            // Fall back to plugin store update
            val manager = updateManager
                ?: return Result.failure(Exception("Update manager not available"))

            // Find the update info
            val updates = manager.availableUpdates.value
            val updateInfo = updates.find { it.pluginId == pluginId }
                ?: return Result.failure(Exception("No update available for plugin: $pluginId"))

            // Download from remote repository
            val repoManager = repositoryManager
                ?: return Result.failure(Exception("Repository manager not available"))

            val remoteRepo = repoManager.getRepository("supabase-store")
                ?: return Result.failure(Exception("Remote repository not available"))

            // Get target path
            val pluginDir = PluginStoreSetup.getPluginDir()
            val targetPath = File(pluginDir, "${pluginId}_${updateInfo.newVersion}.jar").absolutePath

            // Download the plugin
            val downloadResult = remoteRepo.downloadPlugin(pluginId, updateInfo.newVersion, targetPath)
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
            }

            // Uninstall old version
            val uninstallResult = dynamicPluginManager.uninstallPlugin(pluginId, force = true)
            if (uninstallResult.isFailure) {
                // Delete downloaded file
                File(targetPath).delete()
                return Result.failure(uninstallResult.exceptionOrNull() ?: Exception("Uninstall failed"))
            }

            // Install new version
            val installResult = dynamicPluginManager.installPlugin(targetPath, enabled = true)
            if (installResult.isFailure) {
                return Result.failure(installResult.exceptionOrNull() ?: Exception("Install failed"))
            }

            // Update persistence with new version
            PluginPersistence.addInstalledPlugin(
                pluginId = pluginId,
                jarPath = targetPath,
                enabled = true,
                sourceUrl = null,
                installedVersion = updateInfo.newVersion
            )

            logger.info(LogCategory.SYSTEM, "Plugin updated successfully", mapOf(
                "pluginId" to pluginId,
                "oldVersion" to updateInfo.currentVersion,
                "newVersion" to updateInfo.newVersion
            ))

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception updating plugin", error = e)
            Result.failure(e)
        }
    }

    /**
     * Update a plugin from GitHub.
     */
    private suspend fun updatePluginFromGitHub(pluginId: String, githubUrl: String): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Updating plugin from GitHub", mapOf(
                "pluginId" to pluginId,
                "githubUrl" to githubUrl
            ))

            // Fetch the latest version from GitHub
            val fetchResult = fetchFromGitHub(
                githubUrl = githubUrl,
                buildIfNoRelease = false,
                onProgress = { },
                onStatus = { }
            )

            if (fetchResult.isFailure) {
                return Result.failure(fetchResult.exceptionOrNull() ?: Exception("Failed to fetch from GitHub"))
            }

            val (jarPath, manifest) = fetchResult.getOrThrow()

            // Uninstall old version
            val uninstallResult = dynamicPluginManager.uninstallPlugin(pluginId, force = true)
            if (uninstallResult.isFailure) {
                return Result.failure(uninstallResult.exceptionOrNull() ?: Exception("Uninstall failed"))
            }

            // Install new version with GitHub source URL preserved
            val installResult = installPlugin(jarPath, sourceUrl = githubUrl, version = manifest.version)
            if (installResult.isFailure) {
                return Result.failure(installResult.exceptionOrNull() ?: Exception("Install failed"))
            }

            logger.info(LogCategory.SYSTEM, "Plugin updated from GitHub successfully", mapOf(
                "pluginId" to pluginId,
                "newVersion" to manifest.version
            ))

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception updating plugin from GitHub", error = e)
            Result.failure(e)
        }
    }

    override suspend fun updateAllPlugins(): Map<String, Result<Unit>> {
        val results = mutableMapOf<String, Result<Unit>>()

        val manager = updateManager ?: return results
        val updates = manager.availableUpdates.value

        for (update in updates) {
            results[update.pluginId] = updatePlugin(update.pluginId)
        }

        return results
    }

    override suspend fun refresh() {
        try {
            logger.debug(LogCategory.SYSTEM, "Refreshing plugin lists")

            // Check if current user is admin
            val isAdmin = AuthStateManager.currentUser.value?.isAdmin == true

            // Refresh installed plugins (filter by admin status)
            val installedPlugins = dynamicPluginManager.getVisibleInstalledPlugins()
            val installedStates = installedPlugins.map { info ->
                val canUnloadResult = dynamicPluginManager.checkCanUnload(info.manifest.pluginId)
                InstalledPluginState(
                    pluginId = info.manifest.pluginId,
                    displayName = info.manifest.displayName,
                    version = info.manifest.version,
                    description = info.manifest.description,
                    enabled = info.enabled,
                    healthy = info.state == ai.rever.boss.plugin.api.PluginState.LOADED,
                    canUnload = canUnloadResult.isAllowed,
                    jarPath = info.jarPath,
                    url = info.manifest.url,
                    requiresAdmin = info.manifest.requiresAdmin
                )
            }
            onInstalledPluginsChanged(installedStates)

            // Refresh available plugins from remote repository (filter by admin status)
            repositoryManager?.let { repoManager ->
                val remoteRepo = repoManager.getRepository("supabase-store")
                if (remoteRepo != null && remoteRepo.isAvailable) {
                    val listResult = remoteRepo.listPlugins()
                    if (listResult.isSuccess) {
                        // Filter out admin-only plugins for non-admin users
                        val filteredPlugins = listResult.getOrThrow()
                            .filter { !it.requiresAdmin || isAdmin }
                        onAvailablePluginsChanged(filteredPlugins)
                    }
                }
            }

            logger.debug(LogCategory.SYSTEM, "Plugin lists refreshed", mapOf(
                "installedCount" to installedStates.size,
                "isAdmin" to isAdmin
            ))
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error refreshing plugin lists", error = e)
        }
    }

    override suspend fun checkForUpdates() {
        try {
            logger.debug(LogCategory.SYSTEM, "Checking for plugin updates")

            val allUpdates = mutableListOf<UpdateInfo>()

            updateManager?.let { manager ->
                // Build map of installed plugins for update check (plugin store)
                val installedPlugins = dynamicPluginManager.getInstalledPlugins()
                    .associate { it.manifest.pluginId to it.manifest.version }
                manager.checkForUpdates(installedPlugins)
                allUpdates.addAll(manager.availableUpdates.value)
            }

            // Also check GitHub releases for plugins installed from GitHub
            val githubUpdates = checkGitHubUpdates()
            allUpdates.addAll(githubUpdates)

            // Remove duplicates (prefer plugin store updates)
            val uniqueUpdates = allUpdates.distinctBy { it.pluginId }
            onUpdatesChanged(uniqueUpdates)

            logger.debug(LogCategory.SYSTEM, "Update check complete", mapOf(
                "updatesAvailable" to uniqueUpdates.size,
                "fromStore" to (uniqueUpdates.size - githubUpdates.size),
                "fromGitHub" to githubUpdates.size
            ))
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Error checking for updates", error = e)
        }
    }

    /**
     * Check GitHub releases for updates to plugins installed from GitHub.
     */
    private suspend fun checkGitHubUpdates(): List<UpdateInfo> = withContext(Dispatchers.IO) {
        val updates = mutableListOf<UpdateInfo>()

        // Get all installed plugins with GitHub source URLs
        val installedEntries = PluginPersistence.getInstalledPlugins()
        val installedPlugins = dynamicPluginManager.getInstalledPlugins()
            .associateBy { it.manifest.pluginId }

        for (entry in installedEntries) {
            val sourceUrl = entry.sourceUrl
            if (sourceUrl.isNullOrBlank() || !sourceUrl.contains("github.com")) {
                continue
            }

            try {
                // Parse GitHub URL to get owner/repo
                val regex = Regex("https://github\\.com/([^/]+)/([^/]+)(?:/.*)?")
                val match = regex.matchEntire(sourceUrl.trimEnd('/')) ?: continue
                val owner = match.groupValues[1]
                val repo = match.groupValues[2].removeSuffix(".git")

                // Get latest release version from GitHub
                val latestVersion = getGitHubLatestVersion(owner, repo)
                if (latestVersion == null) {
                    logger.debug(LogCategory.SYSTEM, "No releases found for GitHub plugin", mapOf(
                        "pluginId" to entry.pluginId,
                        "sourceUrl" to sourceUrl
                    ))
                    continue
                }

                // Compare with installed version
                val installedVersion = entry.installedVersion
                    ?: installedPlugins[entry.pluginId]?.manifest?.version
                    ?: continue

                if (isNewerVersion(latestVersion, installedVersion)) {
                    val plugin = installedPlugins[entry.pluginId]
                    updates.add(UpdateInfo(
                        pluginId = entry.pluginId,
                        displayName = plugin?.manifest?.displayName ?: entry.pluginId,
                        currentVersion = installedVersion,
                        newVersion = latestVersion,
                        changelog = "",
                        size = 0,
                        critical = false,
                        releaseDate = 0,
                        downloadUrl = sourceUrl, // Store GitHub URL for download
                        requiresRestart = false
                    ))
                    logger.info(LogCategory.SYSTEM, "GitHub update available", mapOf(
                        "pluginId" to entry.pluginId,
                        "currentVersion" to installedVersion,
                        "newVersion" to latestVersion
                    ))
                }
            } catch (e: Exception) {
                logger.debug(LogCategory.SYSTEM, "Failed to check GitHub update", mapOf(
                    "pluginId" to entry.pluginId,
                    "error" to (e.message ?: "unknown")
                ))
            }
        }

        updates
    }

    /**
     * Get the latest release version from GitHub.
     */
    private fun getGitHubLatestVersion(owner: String, repo: String): String? {
        return try {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val connection = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "BOSS-Plugin-Manager")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200) {
                return null
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            val json = Json { ignoreUnknownKeys = true }
            val releaseData = json.parseToJsonElement(responseText).jsonObject

            // Get version from tag_name (remove 'v' prefix if present)
            val tagName = releaseData["tag_name"]?.jsonPrimitive?.content ?: return null
            tagName.removePrefix("v").removePrefix("V")
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Check if version1 is newer than version2 using semantic versioning.
     */
    private fun isNewerVersion(version1: String, version2: String): Boolean {
        val v1Parts = version1.split(".").mapNotNull { it.toIntOrNull() }
        val v2Parts = version2.split(".").mapNotNull { it.toIntOrNull() }

        for (i in 0 until maxOf(v1Parts.size, v2Parts.size)) {
            val v1 = v1Parts.getOrElse(i) { 0 }
            val v2 = v2Parts.getOrElse(i) { 0 }
            if (v1 > v2) return true
            if (v1 < v2) return false
        }
        return false
    }

    override suspend fun browseForPlugin(): String? {
        return withContext(Dispatchers.Main) {
            try {
                val dialog = FileDialog(null as Frame?, "Select Plugin JAR", FileDialog.LOAD)
                dialog.filenameFilter = FilenameFilter { _, name ->
                    name.endsWith(".jar", ignoreCase = true)
                }
                dialog.isVisible = true

                val directory = dialog.directory
                val file = dialog.file

                if (directory != null && file != null) {
                    File(directory, file).absolutePath
                } else {
                    null
                }
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Error showing file picker", error = e)
                null
            }
        }
    }

    override suspend fun extractManifestFromJar(jarPath: String): ExtractedManifest? {
        return withContext(Dispatchers.IO) {
            try {
                val jarFile = JarFile(File(jarPath))
                jarFile.use { jar ->
                    val manifestEntry = jar.getJarEntry(PluginManifestConstants.MANIFEST_PATH)
                    if (manifestEntry == null) {
                        logger.debug(LogCategory.SYSTEM, "No plugin manifest found in JAR", mapOf(
                            "jarPath" to jarPath
                        ))
                        return@withContext null
                    }

                    val manifestJson = jar.getInputStream(manifestEntry).bufferedReader().readText()
                    val json = Json { ignoreUnknownKeys = true }
                    val manifest = json.decodeFromString<PluginManifest>(manifestJson)

                    logger.debug(LogCategory.SYSTEM, "Extracted manifest from JAR", mapOf(
                        "jarPath" to jarPath,
                        "pluginId" to manifest.pluginId,
                        "version" to manifest.version,
                        "apiVersion" to manifest.apiVersion,
                        "minBossVersion" to manifest.minBossVersion,
                        "type" to manifest.type.name
                    ))

                    ExtractedManifest(
                        pluginId = manifest.pluginId,
                        displayName = manifest.displayName,
                        version = manifest.version,
                        description = manifest.description,
                        author = manifest.author,
                        url = manifest.url,
                        apiVersion = manifest.apiVersion,
                        minBossVersion = manifest.minBossVersion,
                        type = manifest.type
                    )
                }
            } catch (e: Exception) {
                logger.warn(LogCategory.SYSTEM, "Failed to extract manifest from JAR", mapOf(
                    "jarPath" to jarPath,
                    "error" to (e.message ?: "unknown")
                ))
                null
            }
        }
    }

    override suspend fun fetchFromGitHub(
        githubUrl: String,
        buildIfNoRelease: Boolean,
        onProgress: (Float) -> Unit,
        onStatus: (String) -> Unit
    ): Result<Pair<String, ExtractedManifest>> {
        return withContext(Dispatchers.IO) {
            try {
                logger.info(LogCategory.SYSTEM, "Fetching plugin from GitHub", mapOf(
                    "url" to githubUrl,
                    "buildIfNoRelease" to buildIfNoRelease
                ))

                onProgress(0.1f)
                onStatus("Parsing GitHub URL...")

                // Parse GitHub URL to get owner/repo
                val trimmedUrl = githubUrl.trim()
                if (trimmedUrl.isBlank()) {
                    return@withContext Result.failure(Exception("GitHub URL cannot be empty"))
                }
                val regex = Regex("https://github\\.com/([^/]+)/([^/]+)(?:/.*)?")
                val match = regex.matchEntire(trimmedUrl.trimEnd('/'))
                    ?: return@withContext Result.failure(Exception("Invalid GitHub URL format"))

                val owner = match.groupValues[1]
                val repo = match.groupValues[2].removeSuffix(".git")

                logger.info(LogCategory.SYSTEM, "Checking GitHub releases", mapOf(
                    "owner" to owner,
                    "repo" to repo
                ))

                onProgress(0.2f)
                onStatus("Checking GitHub releases for JAR...")

                // Check GitHub releases API for JAR files
                val releaseJarResult = tryDownloadFromRelease(owner, repo, onProgress, onStatus)

                if (releaseJarResult != null) {
                    // Found and downloaded JAR from releases
                    val manifest = extractManifestFromJar(releaseJarResult)
                        ?: return@withContext Result.failure(Exception("Downloaded JAR does not contain valid plugin manifest"))

                    onProgress(1.0f)
                    onStatus("Plugin ready!")

                    logger.info(LogCategory.SYSTEM, "Plugin downloaded from GitHub release", mapOf(
                        "pluginId" to manifest.pluginId,
                        "version" to manifest.version
                    ))

                    return@withContext Result.success(Pair(releaseJarResult, manifest))
                }

                // No release found
                if (!buildIfNoRelease) {
                    return@withContext Result.failure(Exception(
                        "No plugin JAR found in GitHub releases. Enable 'Build locally' to clone and build the plugin."
                    ))
                }

                // Build locally
                onStatus("No release found, building locally...")
                buildFromGitHub(githubUrl, onProgress, onStatus)
            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Exception fetching plugin from GitHub", error = e)
                Result.failure(e)
            }
        }
    }

    /**
     * Try to download a JAR from GitHub releases.
     * Returns the path to the downloaded JAR, or null if no suitable release found.
     */
    private suspend fun tryDownloadFromRelease(
        owner: String,
        repo: String,
        onProgress: (Float) -> Unit,
        onStatus: (String) -> Unit
    ): String? {
        return try {
            // Use GitHub API to get latest release
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"

            val connection = java.net.URL(apiUrl).openConnection() as java.net.HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "BOSS-Plugin-Manager")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != 200) {
                logger.debug(LogCategory.SYSTEM, "No releases found", mapOf(
                    "responseCode" to connection.responseCode
                ))
                return null
            }

            val responseText = connection.inputStream.bufferedReader().readText()
            val json = Json { ignoreUnknownKeys = true }

            // Parse response to find JAR asset
            val releaseData = json.parseToJsonElement(responseText).jsonObject
            val assets = releaseData["assets"]?.jsonArray ?: return null

            // Find a JAR file (not sources or javadoc)
            var jarAsset: kotlinx.serialization.json.JsonObject? = null
            var jarName = ""
            var downloadUrl = ""

            for (asset in assets) {
                val assetObj = asset.jsonObject
                val name = assetObj["name"]?.jsonPrimitive?.content ?: continue
                if (name.endsWith(".jar") && !name.contains("-sources") && !name.contains("-javadoc")) {
                    jarAsset = assetObj
                    jarName = name
                    downloadUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: continue
                    break
                }
            }

            if (jarAsset == null || downloadUrl.isEmpty()) {
                logger.debug(LogCategory.SYSTEM, "No JAR asset found in release")
                return null
            }

            onProgress(0.4f)
            onStatus("Downloading $jarName...")

            logger.info(LogCategory.SYSTEM, "Downloading JAR from release", mapOf(
                "name" to jarName,
                "url" to downloadUrl
            ))

            // Download the JAR
            val jarConnection = java.net.URL(downloadUrl).openConnection() as java.net.HttpURLConnection
            jarConnection.setRequestProperty("User-Agent", "BOSS-Plugin-Manager")
            jarConnection.connectTimeout = 30000
            jarConnection.readTimeout = 60000

            if (jarConnection.responseCode != 200) {
                logger.warn(LogCategory.SYSTEM, "Failed to download JAR", mapOf(
                    "responseCode" to jarConnection.responseCode
                ))
                return null
            }

            // Save to cache directory
            val pluginCacheDir = File(System.getProperty("java.io.tmpdir"), "boss-plugin-cache")
            pluginCacheDir.mkdirs()
            val cachedJar = File(pluginCacheDir, jarName)

            jarConnection.inputStream.use { input ->
                cachedJar.outputStream().use { output ->
                    input.copyTo(output)
                }
            }

            onProgress(0.8f)
            onStatus("Verifying JAR...")

            logger.info(LogCategory.SYSTEM, "JAR downloaded from release", mapOf(
                "path" to cachedJar.absolutePath,
                "size" to cachedJar.length()
            ))

            cachedJar.absolutePath
        } catch (e: Exception) {
            logger.debug(LogCategory.SYSTEM, "Failed to fetch from releases", mapOf(
                "error" to (e.message ?: "unknown")
            ))
            null
        }
    }

    /**
     * Clone and build plugin from GitHub repository.
     */
    private suspend fun buildFromGitHub(
        githubUrl: String,
        onProgress: (Float) -> Unit,
        onStatus: (String) -> Unit
    ): Result<Pair<String, ExtractedManifest>> {
        // Create temp directory for clone
        val tempDir = File(System.getProperty("java.io.tmpdir"), "boss-plugin-build-${System.currentTimeMillis()}")
        if (!tempDir.mkdirs()) {
            return Result.failure(Exception("Failed to create temp directory"))
        }

        return try {
            // Clone the repository
            onProgress(0.3f)
            onStatus("Cloning repository...")
            logger.info(LogCategory.SYSTEM, "Cloning repository", mapOf("dir" to tempDir.absolutePath))

            val cloneProcess = ProcessBuilder("git", "clone", "--depth", "1", githubUrl, tempDir.absolutePath)
                .redirectErrorStream(true)
                .start()

            val cloneOutput = cloneProcess.inputStream.bufferedReader().readText()
            val cloneExitCode = cloneProcess.waitFor()

            if (cloneExitCode != 0) {
                logger.error(LogCategory.SYSTEM, "Git clone failed", mapOf(
                    "exitCode" to cloneExitCode,
                    "output" to cloneOutput
                ))
                return Result.failure(Exception("Git clone failed: $cloneOutput"))
            }

            onProgress(0.5f)
            onStatus("Building with Gradle...")

            // Find gradlew and make it executable
            val gradlew = File(tempDir, if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew")
            if (!gradlew.exists()) {
                return Result.failure(Exception("No gradlew found in repository"))
            }
            gradlew.setExecutable(true)

            // Run gradle build
            logger.info(LogCategory.SYSTEM, "Building plugin with Gradle")

            val buildProcess = ProcessBuilder(gradlew.absolutePath, "build", "-x", "test", "--no-daemon")
                .directory(tempDir)
                .redirectErrorStream(true)
                .start()

            val buildOutput = buildProcess.inputStream.bufferedReader().readText()
            val buildExitCode = buildProcess.waitFor()

            if (buildExitCode != 0) {
                logger.error(LogCategory.SYSTEM, "Gradle build failed", mapOf(
                    "exitCode" to buildExitCode,
                    "output" to buildOutput.takeLast(2000)
                ))
                return Result.failure(Exception("Gradle build failed. Check repository has valid build.gradle.kts"))
            }

            onProgress(0.8f)
            onStatus("Locating built JAR...")

            // Find the built JAR in build/libs
            val libsDir = File(tempDir, "build/libs")
            if (!libsDir.exists()) {
                return Result.failure(Exception("No build/libs directory found after build"))
            }

            val jarFiles = libsDir.listFiles { file ->
                file.extension == "jar" && !file.name.contains("-sources") && !file.name.contains("-javadoc")
            }

            if (jarFiles.isNullOrEmpty()) {
                return Result.failure(Exception("No JAR file found in build/libs"))
            }

            // Use the first (or largest) JAR
            val builtJar = jarFiles.maxByOrNull { it.length() }!!

            // Copy JAR to a more permanent location
            val pluginCacheDir = File(System.getProperty("java.io.tmpdir"), "boss-plugin-cache")
            pluginCacheDir.mkdirs()
            val cachedJar = File(pluginCacheDir, builtJar.name)
            builtJar.copyTo(cachedJar, overwrite = true)

            logger.info(LogCategory.SYSTEM, "Plugin built successfully", mapOf(
                "jarPath" to cachedJar.absolutePath,
                "size" to cachedJar.length()
            ))

            onProgress(0.9f)
            onStatus("Extracting manifest...")

            // Extract manifest from the JAR
            val manifest = extractManifestFromJar(cachedJar.absolutePath)
                ?: return Result.failure(Exception("Built JAR does not contain valid plugin manifest at META-INF/boss-plugin/plugin.json"))

            onProgress(1.0f)
            onStatus("Plugin ready!")

            logger.info(LogCategory.SYSTEM, "Plugin fetched and built from GitHub", mapOf(
                "pluginId" to manifest.pluginId,
                "version" to manifest.version
            ))

            Result.success(Pair(cachedJar.absolutePath, manifest))
        } finally {
            // Clean up temp directory
            try {
                tempDir.deleteRecursively()
            } catch (e: Exception) {
                logger.warn(LogCategory.SYSTEM, "Failed to clean up temp directory", mapOf(
                    "dir" to tempDir.absolutePath
                ))
            }
        }
    }

    override suspend fun installFromRemote(pluginId: String, version: String?): Result<Unit> {
        return try {
            logger.info(LogCategory.SYSTEM, "Installing plugin from remote", mapOf(
                "pluginId" to pluginId,
                "version" to (version ?: "latest")
            ))

            val repoManager = repositoryManager
                ?: return Result.failure(Exception("Repository manager not available"))

            val remoteRepo = repoManager.getRepository("supabase-store")
                ?: return Result.failure(Exception("Remote repository not available"))

            // Download to plugin directory
            val pluginDir = PluginStoreSetup.getPluginDir()
            val targetVersion = version ?: "latest"
            val targetPath = File(pluginDir, "${pluginId.replace(".", "_")}_$targetVersion.jar").absolutePath

            // Download the plugin
            val downloadResult = remoteRepo.downloadPlugin(pluginId, version, targetPath)
            if (downloadResult.isFailure) {
                return Result.failure(downloadResult.exceptionOrNull() ?: Exception("Download failed"))
            }

            logger.info(LogCategory.SYSTEM, "Plugin downloaded from remote", mapOf(
                "pluginId" to pluginId,
                "path" to targetPath
            ))

            // Install using dynamic plugin manager
            val installResult = dynamicPluginManager.installPlugin(targetPath, enabled = true)
            if (installResult.isFailure) {
                return Result.failure(installResult.exceptionOrNull() ?: Exception("Install failed"))
            }

            // Persist installed state
            val installedPluginId = installResult.getOrNull()?.manifest?.pluginId ?: pluginId
            PluginPersistence.addInstalledPlugin(installedPluginId, targetPath)

            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception installing plugin from remote", error = e)
            Result.failure(e)
        }
    }


    override suspend fun publishPlugin(
        jarPath: String,
        pluginId: String,
        displayName: String,
        version: String,
        homepageUrl: String,
        authorName: String,
        description: String?,
        changelog: String?,
        tags: List<String>,
        iconUrl: String?,
        pluginType: String,
        apiVersion: String,
        minBossVersion: String,
        onProgress: (Float) -> Unit
    ): Result<String> {
        return try {
            logger.info(LogCategory.SYSTEM, "Publishing plugin to store", mapOf(
                "pluginId" to pluginId,
                "version" to version,
                "jarPath" to jarPath,
                "type" to pluginType,
                "apiVersion" to apiVersion
            ))

            onProgress(0.1f)

            val jarFile = File(jarPath)
            if (!jarFile.exists()) {
                return Result.failure(Exception("JAR file not found: $jarPath"))
            }

            // Read JAR bytes
            val jarBytes = jarFile.readBytes()
            val jarSize = jarBytes.size.toLong()

            // Calculate SHA256
            val sha256 = calculateSha256(jarBytes)

            onProgress(0.2f)

            // Check if plugin already exists
            val pluginExists = PluginStoreClient.pluginExists(pluginId)

            onProgress(0.3f)

            // Step 1: Create plugin entry if it doesn't exist
            if (!pluginExists) {
                logger.info(LogCategory.SYSTEM, "Creating new plugin entry", mapOf("pluginId" to pluginId))
                val publishRequest = PublishPluginRequest(
                    pluginId = pluginId,
                    displayName = displayName,
                    description = description ?: "",
                    authorName = authorName,
                    homepageUrl = homepageUrl,
                    iconUrl = iconUrl ?: "",
                    type = pluginType,
                    apiVersion = apiVersion,
                    tags = tags
                )
                val publishResult = PluginStoreClient.publishPlugin(publishRequest)
                if (!publishResult.success) {
                    return Result.failure(Exception(publishResult.error ?: "Failed to create plugin"))
                }
            }

            onProgress(0.4f)

            // Step 2: Create version and get upload URL
            logger.info(LogCategory.SYSTEM, "Creating version entry", mapOf("version" to version))
            val versionRequest = PublishVersionRequest(
                version = version,
                changelog = changelog ?: "",
                minBossVersion = minBossVersion
            )
            val versionResult = PluginStoreClient.publishVersion(pluginId, versionRequest)
            val uploadUrl = versionResult.uploadUrl
            val versionId = versionResult.versionId
            if (!versionResult.success || uploadUrl == null || versionId == null) {
                return Result.failure(Exception(versionResult.error ?: "Failed to create version"))
            }

            onProgress(0.5f)

            // Step 3: Upload JAR to signed URL
            logger.info(LogCategory.SYSTEM, "Uploading JAR file", mapOf("size" to jarSize))
            PluginStoreClient.uploadJar(uploadUrl, jarBytes)

            onProgress(0.8f)

            // Step 4: Finalize version with SHA256 and size
            logger.info(LogCategory.SYSTEM, "Finalizing version", mapOf("sha256" to sha256))
            val finalizeRequest = FinalizeVersionRequest(
                versionId = versionId,
                sha256 = sha256,
                jarSize = jarSize
            )
            val finalizeResult = PluginStoreClient.finalizeVersion(finalizeRequest)
            if (!finalizeResult.success) {
                return Result.failure(Exception(finalizeResult.error ?: "Failed to finalize version"))
            }

            onProgress(1.0f)

            logger.info(LogCategory.SYSTEM, "Plugin published successfully", mapOf(
                "pluginId" to pluginId,
                "version" to version
            ))

            Result.success(pluginId)
        } catch (e: Exception) {
            logger.error(LogCategory.SYSTEM, "Exception publishing plugin", error = e)
            Result.failure(e)
        }
    }

    private fun calculateSha256(bytes: ByteArray): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(bytes)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }


    // ============================================================================
    // Admin Operations
    // ============================================================================

    override suspend fun isCurrentUserAdmin(): Boolean {
        return PluginStoreConfig.isAdmin
    }

    override suspend fun adminDeletePlugin(pluginId: String): Result<Unit> {
        return try {
            logger.info(LogCategory.NETWORK, "Admin deleting plugin from store", mapOf("pluginId" to pluginId))
            PluginStoreClient.deletePlugin(pluginId)
            logger.info(LogCategory.NETWORK, "Admin deleted plugin from store", mapOf("pluginId" to pluginId))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.NETWORK, "Failed to delete plugin from store", mapOf("pluginId" to pluginId), error = e)
            Result.failure(e)
        }
    }

    override suspend fun adminSetPluginPublished(pluginId: String, published: Boolean): Result<Unit> {
        return try {
            logger.info(LogCategory.NETWORK, "Admin setting plugin published status", mapOf("pluginId" to pluginId, "published" to published))
            PluginStoreClient.setPluginPublished(pluginId, published)
            logger.info(LogCategory.NETWORK, "Admin set plugin published status", mapOf("pluginId" to pluginId, "published" to published))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.NETWORK, "Failed to set plugin published status", mapOf("pluginId" to pluginId), error = e)
            Result.failure(e)
        }
    }

    override suspend fun adminSetPluginVerified(pluginId: String, verified: Boolean): Result<Unit> {
        return try {
            logger.info(LogCategory.NETWORK, "Admin setting plugin verified status", mapOf("pluginId" to pluginId, "verified" to verified))
            PluginStoreClient.setPluginVerified(pluginId, verified)
            logger.info(LogCategory.NETWORK, "Admin set plugin verified status", mapOf("pluginId" to pluginId, "verified" to verified))
            Result.success(Unit)
        } catch (e: Exception) {
            logger.error(LogCategory.NETWORK, "Failed to set plugin verified status", mapOf("pluginId" to pluginId), error = e)
            Result.failure(e)
        }
    }
}

/**
 * Factory for creating PluginManagerOperations instances bound to a component.
 */
object PluginManagerOperationsFactory {

    /**
     * Create an operations provider for the PluginManagerPanelPlugin.
     *
     * The returned factory creates an operations instance that is bound to
     * a specific component through callbacks. This allows the operations
     * to update the component's state after operations complete.
     *
     * @param dynamicPluginManager The dynamic plugin manager
     * @param getComponent Function that returns the component (resolved lazily)
     * @return Factory function that creates PluginManagerOperationsImpl instances
     */
    fun createProvider(
        dynamicPluginManager: DynamicPluginManager,
        getComponent: () -> PluginManagerComponent?
    ): () -> PluginManagerOperations {
        return {
            PluginManagerOperationsImpl(
                dynamicPluginManager = dynamicPluginManager,
                repositoryManagerProvider = { PluginStoreSetup.repositoryManager },
                updateManagerProvider = { PluginStoreSetup.updateManager },
                onInstalledPluginsChanged = { plugins ->
                    getComponent()?.updateInstalledPlugins(plugins)
                },
                onAvailablePluginsChanged = { plugins ->
                    getComponent()?.updateAvailablePlugins(plugins)
                },
                onUpdatesChanged = { updates ->
                    getComponent()?.updateAvailableUpdates(updates)
                }
            )
        }
    }
}
