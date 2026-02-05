package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.config.JxBrowserConfig
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.platform.FileNameSanitizer
import ai.rever.boss.platform.MacOSScreenCapture
import ai.rever.boss.platform.FileSystemUtils
import ai.rever.boss.platform.pickSaveFile
import ai.rever.boss.utils.SystemUtils
import ai.rever.boss.utils.WindowFocusManager
import com.teamdev.jxbrowser.browser.callback.StartDownloadCallback
import com.teamdev.jxbrowser.download.Download
import com.teamdev.jxbrowser.download.event.*
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import com.teamdev.jxbrowser.engine.ProprietaryFeature
import com.teamdev.jxbrowser.engine.UserDataDirectoryAlreadyInUseException
import com.teamdev.jxbrowser.permission.PermissionType
import com.teamdev.jxbrowser.permission.callback.RequestPermissionCallback
import com.teamdev.jxbrowser.browser.callback.StartCaptureSessionCallback
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.awt.Toolkit
import java.nio.file.Files
import java.nio.file.Paths
import java.util.*

/**
 * Classification of engine initialization errors for better user feedback.
 */
sealed class EngineInitError {
    data class LicenseValidation(val message: String) : EngineInitError()
    data class NetworkError(val message: String) : EngineInitError()
    data class Other(val message: String, val cause: Throwable?) : EngineInitError()
}

// Singleton engine for all browser tabs
object FluckEngine {
    private val logger = BossLogger.forComponent("FluckEngine")
    private var _engine: Engine? = null
    private var initializationError: Throwable? = null
    private var attemptCount = 0
    private var proactiveCleanupDone = false

    /**
     * Engine generation counter - increments every time the engine is reinitialized.
     * Browser tabs can use this to detect when their browser instance is stale.
     */
    private var _engineGeneration = 0L
    private val _engineGenerationFlow = MutableStateFlow(0L)

    /**
     * Observable flow of engine generation changes.
     * Browser tabs should collect this and invalidate/reload when generation changes.
     */
    val engineGenerationFlow: StateFlow<Long> = _engineGenerationFlow.asStateFlow()

    /**
     * Current engine generation. Browsers created before this generation are stale.
     */
    val currentEngineGeneration: Long
        get() = _engineGeneration

    /**
     * Classified initialization error for better user feedback.
     * Returns null if no error or engine initialized successfully.
     */
    val initError: EngineInitError?
        get() = initializationError?.let { classifyError(it) }

    /**
     * Classify the initialization error for user-friendly messages.
     */
    private fun classifyError(e: Throwable): EngineInitError {
        val msg = e.message?.lowercase() ?: ""
        val fullStackTrace = e.stackTraceToString().lowercase()

        return when {
            // License validation errors (usually network-related)
            msg.contains("license") || msg.contains("validation") ||
            fullStackTrace.contains("licensecheck") || fullStackTrace.contains("license") ->
                EngineInitError.LicenseValidation(
                    "License validation failed. Please check your internet connection."
                )
            // Network/connection errors
            msg.contains("network") || msg.contains("connect") ||
            msg.contains("timeout") || msg.contains("unreachable") ||
            msg.contains("socket") || msg.contains("host") ||
            e is java.net.UnknownHostException || e is java.net.ConnectException ||
            e is java.net.SocketTimeoutException ->
                EngineInitError.NetworkError(
                    "Network error. Please check your internet connection and try again."
                )
            // Other errors
            else -> EngineInitError.Other(
                e.message ?: "Unknown error occurred",
                e
            )
        }
    }

    /**
     * Reset initialization state to allow retry after fixing network issues.
     */
    fun resetInitializationState() {
        initializationError = null
        attemptCount = 0
    }

    /**
     * Proactively clean up stale lock files and zombie processes on app startup.
     * Call this early in app initialization to ensure session reuse works.
     */
    fun proactiveCleanupOnStartup() {
        if (proactiveCleanupDone) return
        proactiveCleanupDone = true

        val userHome = System.getProperty("user.home")
        val selectedProfile = BrowserSettings.currentProfile
        val profileDirPath = Paths.get(userHome, ".boss", selectedProfile)

        // First, kill any stale Chromium processes from previous sessions
        killStaleChromiumProcesses(userHome)

        if (profileDirPath.toFile().exists()) {
            cleanupStaleLockFiles(profileDirPath)
            // Also clean up any other lock-related files
            cleanupAllLockRelatedFiles(profileDirPath)
        } else {
        }
    }

    /**
     * Kill stale Chromium processes that were spawned by previous BOSS sessions.
     * These zombie processes can prevent profile reuse even without lock files.
     */
    private fun killStaleChromiumProcesses(userHome: String) {
        var killedAny = false
        try {
            // Use explicit paths for more precise matching (security: avoid killing unrelated processes)
            val bossChromiumDir = "$userHome/.boss/jxbrowser-chromium"
            val bossBrandedChromiumDir = "$userHome/.boss/boss-chromium"
            val bossProfileDir = "$userHome/.boss/browser-profile"
            val currentPid = ProcessHandle.current().pid()
            val currentTimeMs = System.currentTimeMillis()

            // Find all processes that match JxBrowser's Chromium
            val staleProcesses = ProcessHandle.allProcesses()
                .filter { process ->
                    try {
                        val command = process.info().command().orElse("")
                        val commandLine = process.info().commandLine().orElse("")

                        // Security: First verify it's actually a Chromium/Chrome executable
                        val isChromiumExecutable = command.contains("chrome", ignoreCase = true) ||
                                command.contains("chromium", ignoreCase = true) ||
                                command.contains("jxbrowser", ignoreCase = true)

                        // Security: Then check if it's from our JxBrowser installation
                        // Use explicit full paths to avoid false positives
                        val isFromBossDir = command.contains(bossChromiumDir) ||
                                command.contains(bossBrandedChromiumDir) ||
                                commandLine.contains(bossChromiumDir) ||
                                commandLine.contains(bossBrandedChromiumDir) ||
                                commandLine.contains(bossProfileDir)

                        val isJxBrowserChromium = isChromiumExecutable && isFromBossDir

                        // Don't kill processes that belong to current BOSS instance
                        val parentPid = process.parent().map { it.pid() }.orElse(-1L)
                        val isOurChild = parentPid == currentPid

                        // Security: Don't kill processes started less than 5 seconds ago
                        // This prevents killing newly spawned legitimate processes
                        val startTimeMs = process.info().startInstant()
                            .map { it.toEpochMilli() }.orElse(currentTimeMs)
                        val processAgeMs = currentTimeMs - startTimeMs
                        val isTooRecent = processAgeMs < 5000

                        isJxBrowserChromium && !isOurChild && !isTooRecent
                    } catch (e: Exception) {
                        false
                    }
                }
                .toList()

            staleProcesses.forEach { process ->
                try {
                    val pid = process.pid()
                    val command = process.info().command().orElse("unknown")
                    val commandLine = process.info().commandLine().orElse("unknown")

                    // Log full command line for debugging before killing

                    // Try graceful termination first with proper timeout handling
                    process.destroy()
                    killedAny = true

                    // Wait for graceful shutdown using ProcessHandle.onExit() with timeout
                    // This is more reliable than Thread.sleep() and doesn't block unnecessarily
                    try {
                        process.onExit().get(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } catch (e: java.util.concurrent.TimeoutException) {
                        // Process didn't exit in time - force kill
                        process.destroyForcibly()
                    } catch (e: Exception) {
                        // Process already exited or other error
                    }
                } catch (e: Exception) {
                }
            }

            if (staleProcesses.isEmpty()) {
            }

            // If we killed any processes, wait for them to fully terminate
            // Using Thread.sleep() is acceptable here since this runs during startup
            // before UI initialization (per code review recommendation)
            if (killedAny) {
                Thread.sleep(500)
            }
        } catch (e: Exception) {
        }
    }

    /**
     * Clean up ALL lock-related files in the profile directory.
     * JxBrowser/Chromium uses multiple files for locking.
     */
    private fun cleanupAllLockRelatedFiles(profileDir: java.nio.file.Path) {

        val lockFiles = listOf(
            "SingletonLock",
            "SingletonSocket",
            "SingletonCookie",
            "lockfile",
            ".org.chromium.Chromium.lock"  // Some versions use this
        )

        lockFiles.forEach { fileName ->
            val file = profileDir.resolve(fileName).toFile()
            if (file.exists()) {
                val deleted = file.delete()
            }
        }

        // Also check for lock files in Default subdirectory
        val defaultDir = profileDir.resolve("Default")
        if (defaultDir.toFile().exists()) {
            lockFiles.forEach { fileName ->
                val file = defaultDir.resolve(fileName).toFile()
                if (file.exists()) {
                    val deleted = file.delete()
                }
            }
        }
    }

    // Track URLs that are being downloaded to prevent popup handler from opening tabs
    private val activeDownloadUrls = Collections.synchronizedSet(mutableSetOf<String>())

    // Track recently opened tabs that might be download redirects
    // Store tab IDs opened in the last few seconds
    private val recentlyOpenedTabIds = Collections.synchronizedList(mutableListOf<Pair<Long, String>>())

    // Callback to close most recent tab
    private var onCloseMostRecentTab: (() -> Unit)? = null

    // Download manager for tracking all downloads
    val downloadManager = DownloadManager()

    // Download settings (can be persisted later)
    private var downloadSettings = DownloadSettings()

    // Track active downloads for pause/resume operations
    private val activeDownloads = Collections.synchronizedMap(mutableMapOf<String, Download>())

    // Expose current engine instance for shutdown purposes
    val currentEngine: Engine?
        get() = _engine

    /**
     * Check if a URL is currently being downloaded.
     * Used by popup handler to prevent opening new tabs for download links.
     */
    fun isActiveDownload(url: String): Boolean {
        return activeDownloadUrls.contains(url)
    }

    /**
     * Notify that a tab was just opened via popup handler.
     * This tab might be a download redirect and should be auto-closed if download starts soon.
     */
    fun notifyTabOpened() {
        val now = System.currentTimeMillis()
        recentlyOpenedTabIds.add(now to "")

        // Clean up old entries (older than 5 seconds)
        val cutoff = now - 5_000
        recentlyOpenedTabIds.removeIf { it.first < cutoff }
    }

    /**
     * Set callback to close the most recently opened tab.
     * Called by BossApp or tab management system.
     */
    fun setCloseMostRecentTabCallback(callback: () -> Unit) {
        onCloseMostRecentTab = callback
    }

    /**
     * Auto-close the most recently opened tab if it was opened within the last 3 seconds.
     * Called when a download starts.
     */
    private fun autoCloseDownloadTab() {
        val now = System.currentTimeMillis()
        val recentCutoff = now - 3_000 // Tabs opened in last 3 seconds

        // Find tabs opened in the last 3 seconds
        val recentTabs = recentlyOpenedTabIds.filter { it.first >= recentCutoff }

        if (recentTabs.isNotEmpty()) {
            onCloseMostRecentTab?.invoke()
            // Clear the entries
            recentlyOpenedTabIds.removeIf { it.first >= recentCutoff }
        }
    }

    /**
     * Pause an active download.
     * @param downloadId The unique ID of the download to pause
     */
    fun pauseDownload(downloadId: String) {
        activeDownloads[downloadId]?.let { download ->
            try {
                download.pause()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Resume a paused download.
     * @param downloadId The unique ID of the download to resume
     */
    fun resumeDownload(downloadId: String) {
        activeDownloads[downloadId]?.let { download ->
            try {
                download.resume()
            } catch (e: Exception) {
            }
        }
    }

    /**
     * Cancel an active or paused download.
     * @param downloadId The unique ID of the download to cancel
     */
    fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.let { download ->
            try {
                download.cancel()
            } catch (e: Exception) {
            }
        }
    }

    // Lock object for thread-safe engine access
    private val engineLock = Any()

    val engine: Engine
        get() = synchronized(engineLock) {
            // Return cached engine if available AND not closed
            _engine?.let { cachedEngine ->
                if (!cachedEngine.isClosed) {
                    return@synchronized cachedEngine
                }
                // Engine was closed (e.g., during app restart/update flow)
                // Clear cache and reinitialize
                _engine = null
                initializationError = null
                attemptCount = 0
                // Increment generation to notify browser tabs that they need to reload
                _engineGeneration++
                _engineGenerationFlow.value = _engineGeneration
            }

            // Throw cached error if initialization failed before and we've tried too many times
            if (attemptCount > 3) {
                initializationError?.let { throw it }
            }

            // Try to initialize
            initializeEngine()
        }
    
    private fun initializeEngine(): Engine {
        attemptCount++

        // Request screen capture permission proactively on macOS
        // This ensures permission is granted before user tries to screen share,
        // preventing repeated permission dialogs while still allowing native picker
        if (!MacOSScreenCapture.hasPermission()) {
            MacOSScreenCapture.requestPermission()
        }

        // Get user's home directory dynamically
        val userHome = System.getProperty("user.home")
        val chromiumDir = getChromiumDir(userHome)

        // Create directories if they don't exist
        chromiumDir.toFile().mkdirs()

        // Clean up old temporary profiles on startup (older than 24 hours)
        cleanupOldTemporaryProfiles(userHome)

        // Try to create engine with profile handling
        return createEngineWithProfile(chromiumDir, userHome)
    }

    /**
     * Get the Chromium directory to use, with priority:
     * 1. Bundled BOSS-branded Chromium (in app resources)
     * 2. Cached BOSS-branded Chromium (~/.boss/boss-chromium/)
     */
    private fun getChromiumDir(userHome: String): java.nio.file.Path {
        // Priority 1: Bundled BOSS-branded Chromium (in app resources)
        val bundledDir = getBundledChromiumPath()
        if (bundledDir != null && isValidChromiumDir(bundledDir)) {
            return bundledDir
        }

        // Priority 2: Cached BOSS-branded Chromium
        val cachedBrandedDir = Paths.get(userHome, ".boss", "boss-chromium")
        if (isValidChromiumDir(cachedBrandedDir)) {
            return cachedBrandedDir
        }

        // No fallback - BOSS-branded Chromium is required
        throw IllegalStateException(
            "BOSS-branded Chromium not found. Please restart the app to trigger auto-download, " +
            "or manually install to ~/.boss/boss-chromium/"
        )
    }

    /**
     * Check if a Chromium directory is valid (contains executable.name file).
     * The executable.name file is critical for JxBrowser to locate the branded binary.
     */
    private fun isValidChromiumDir(dir: java.nio.file.Path): Boolean {
        if (!dir.toFile().exists()) return false
        val executableNameFile = dir.resolve("executable.name").toFile()
        return executableNameFile.exists()
    }

    /**
     * Get the path to bundled BOSS-branded Chromium based on platform.
     * Returns null if no bundled Chromium is found.
     */
    private fun getBundledChromiumPath(): java.nio.file.Path? {
        val osName = System.getProperty("os.name").lowercase()
        return when {
            osName.contains("mac") -> getMacOSBundledChromiumPath()
            osName.contains("win") -> getWindowsBundledChromiumPath()
            else -> getLinuxBundledChromiumPath()
        }
    }

    /**
     * macOS: Look for bundled Chromium in BOSS.app/Contents/Resources/chromium/
     */
    private fun getMacOSBundledChromiumPath(): java.nio.file.Path? {
        // Java home is typically: BOSS.app/Contents/runtime/Contents/Home
        // So app bundle root is 4 levels up
        val javaHome = System.getProperty("java.home") ?: return null
        val javaHomePath = Paths.get(javaHome)

        // Navigate from runtime to app bundle: runtime/Contents/Home -> app/Contents/Resources/chromium
        val appContents = javaHomePath.parent?.parent?.parent ?: return null
        val chromiumPath = appContents.resolve("Resources").resolve("chromium")

        return if (chromiumPath.toFile().exists()) chromiumPath else null
    }

    /**
     * Windows: Look for bundled Chromium in app installation directory/chromium/
     */
    private fun getWindowsBundledChromiumPath(): java.nio.file.Path? {
        // Try relative to app installation directory
        val userDir = System.getProperty("user.dir")
        val chromiumPath = Paths.get(userDir, "chromium")

        if (chromiumPath.toFile().exists()) return chromiumPath

        // Also try relative to Java home for installed apps
        val javaHome = System.getProperty("java.home") ?: return null
        val javaHomePath = Paths.get(javaHome)
        val appChromiumPath = javaHomePath.parent?.resolve("chromium")

        return if (appChromiumPath?.toFile()?.exists() == true) appChromiumPath else null
    }

    /**
     * Linux: Look for bundled Chromium in standard installation directories
     */
    private fun getLinuxBundledChromiumPath(): java.nio.file.Path? {
        // Check common Linux installation paths
        val paths = listOf(
            "/opt/boss/lib/chromium",  // Bundled in lib directory (new packaging)
            "/opt/boss/chromium",
            "/usr/share/boss/chromium",
            "/usr/local/share/boss/chromium"
        )

        for (pathStr in paths) {
            val path = Paths.get(pathStr)
            if (path.toFile().exists()) return path
        }

        // Also try relative to user.dir (for portable installations)
        val userDir = System.getProperty("user.dir")
        val chromiumPath = Paths.get(userDir, "chromium")
        return if (chromiumPath.toFile().exists()) chromiumPath else null
    }
    
    /**
     * Clean up stale lock files from a previous BOSS session that didn't close properly.
     * On Linux, Chromium creates SingletonLock as a symlink to "spark-<hostname>-<pid>".
     * If the PID is no longer running, the lock is stale and can be safely removed.
     */
    private fun cleanupStaleLockFiles(profileDir: java.nio.file.Path): Boolean {
        val lockFile = profileDir.resolve("SingletonLock").toFile()
        val socketFile = profileDir.resolve("SingletonSocket").toFile()
        val cookieFile = profileDir.resolve("SingletonCookie").toFile()


        if (!lockFile.exists()) {
            return false // No lock to clean
        }

        // On Linux, SingletonLock is a symlink to "spark-<hostname>-<pid>"
        // Check if the PID is still running
        try {
            val isSymlink = Files.isSymbolicLink(lockFile.toPath())

            if (isSymlink) {
                val target = Files.readSymbolicLink(lockFile.toPath()).toString()

                // Parse PID from "spark-hostname-12345" or similar format
                val pid = target.substringAfterLast("-").toLongOrNull()

                if (pid != null) {
                    // Check if process is still running
                    val processHandle = ProcessHandle.of(pid)
                    val isRunning = processHandle.isPresent

                    if (isRunning) {
                        // Additional check: verify it's actually a BOSS/JxBrowser process
                        // not just a reused PID from another application
                        val processInfo = processHandle.orElse(null)
                        val command = processInfo?.info()?.command()?.orElse(null)

                        // If it's not a Java process, it's likely a reused PID
                        val isJavaProcess = command?.contains("java", ignoreCase = true) == true
                        if (!isJavaProcess) {
                            deleteLockFiles(lockFile, socketFile, cookieFile)
                            return true
                        }
                    } else {
                        deleteLockFiles(lockFile, socketFile, cookieFile)
                        return true
                    }
                } else {
                    // Couldn't parse PID - try to clean up anyway if lock file is old
                    val lastModified = lockFile.lastModified()
                    val ageMinutes = (System.currentTimeMillis() - lastModified) / (1000 * 60)

                    // If lock is older than 5 minutes, assume it's stale
                    if (ageMinutes > 5) {
                        deleteLockFiles(lockFile, socketFile, cookieFile)
                        return true
                    }
                }
            } else {
                // Not a symlink (Windows or other OS) - check file age
                val lastModified = lockFile.lastModified()
                val ageMinutes = (System.currentTimeMillis() - lastModified) / (1000 * 60)

                // On non-Linux, if lock is older than 5 minutes and we're starting fresh, clean it
                if (ageMinutes > 5) {
                    deleteLockFiles(lockFile, socketFile, cookieFile)
                    return true
                }
            }
        } catch (e: Exception) {
            // Log error without printStackTrace per CLAUDE.md guidelines

            // If we can't check, try to clean up anyway
            try {
                deleteLockFiles(lockFile, socketFile, cookieFile)
                return true
            } catch (e2: Exception) {
            }
        }

        return false
    }

    private fun deleteLockFiles(lockFile: java.io.File, socketFile: java.io.File, cookieFile: java.io.File) {
        if (lockFile.exists()) {
            val deleted = lockFile.delete()
            if (!deleted) {
            } else {
            }
        }
        if (socketFile.exists()) {
            val deleted = socketFile.delete()
            if (!deleted) {
            } else {
            }
        }
        if (cookieFile.exists()) {
            val deleted = cookieFile.delete()
            if (!deleted) {
            } else {
            }
        }
    }

    /**
     * Clean up old temporary profiles to prevent disk space accumulation.
     * Deletes browser-profile-* directories older than 24 hours.
     */
    private fun cleanupOldTemporaryProfiles(userHome: String) {
        try {
            val bossDir = java.io.File(userHome, ".boss")
            val oneDayAgo = System.currentTimeMillis() - (24 * 60 * 60 * 1000)

            bossDir.listFiles()?.filter {
                it.isDirectory &&
                it.name.startsWith("browser-profile-") &&
                it.name != "browser-profile" &&
                it.lastModified() < oneDayAgo
            }?.forEach { dir ->
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
        }
    }

    private fun createEngineWithProfile(chromiumDir: java.nio.file.Path, userHome: String): Engine {
        val selectedProfile = BrowserSettings.currentProfile
        val profileDirPath = Paths.get(userHome, ".boss", selectedProfile)
        profileDirPath.toFile().mkdirs()

        return try {
            createEngineInstance(chromiumDir, profileDirPath, selectedProfile)
        } catch (e: UserDataDirectoryAlreadyInUseException) {
            // Try to clean up stale lock files first
            if (cleanupStaleLockFiles(profileDirPath)) {
                try {
                    return createEngineInstance(chromiumDir, profileDirPath, selectedProfile)
                } catch (e2: Exception) {
                }
            }

            // Profile is genuinely in use by another process, use temporary
            val tempProfile = "browser-profile-${System.currentTimeMillis()}"
            val tempProfilePath = Paths.get(userHome, ".boss", tempProfile)
            tempProfilePath.toFile().mkdirs()

            try {
                createEngineInstance(chromiumDir, tempProfilePath, tempProfile)
            } catch (e2: Exception) {
                throw e2
            }
        } catch (e: Exception) {
            initializationError = e
            throw e
        }
    }
    
    private fun createEngineInstance(chromiumDir: java.nio.file.Path, profileDirPath: java.nio.file.Path, profileName: String): Engine {
        val optionsBuilder = EngineOptions.newBuilder(JxBrowserConfig.renderingMode)
            .licenseKey(JxBrowserConfig.licenseKey)
            .chromiumDir(chromiumDir)
            .userDataDir(profileDirPath)
            // Enable all proprietary codecs for full media support
            .enableProprietaryFeature(ProprietaryFeature.H_264)
            .enableProprietaryFeature(ProprietaryFeature.AAC)
            .enableProprietaryFeature(ProprietaryFeature.HEVC)
            // GPU acceleration and performance flags
            .addSwitch("--enable-gpu-rasterization")
            .addSwitch("--enable-zero-copy")
            .addSwitch("--ignore-gpu-blocklist")
            // Linux-specific hardware video decode
            .apply {
                if (System.getProperty("os.name").lowercase().contains("linux")) {
                    addSwitch("--enable-features=VaapiVideoDecoder,VaapiVideoEncoder")
                }
            }
            // Minimal Chrome flags
            .addSwitch("--disable-dev-shm-usage")
            .addSwitch("--no-sandbox") // May be needed for some environments
        
        // Add user agent if configured
        BrowserSettings.userAgent?.let { ua ->
            val userAgentMapping = mapOf(
                "Chrome" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
                "Firefox" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.15; rv:121.0) Gecko/20100101 Firefox/121.0",
                "Safari" to "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.1 Safari/605.1.15",
                "Edge" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 Edg/120.0.0.0"
            )
            
            val userAgentString = when (ua) {
                "Default" -> null
                "Chrome", "Firefox", "Safari", "Edge" -> userAgentMapping[ua]
                "Custom" -> BrowserSettings.customUserAgent
                else -> ua
            }
            
            userAgentString?.let {
                optionsBuilder.userAgent(it)
            }
        }
        
        val newEngine = Engine.newInstance(optionsBuilder.build())

        // Activate Widevine DRM for protected content (Netflix, Disney+, etc.)
        try {
            val widevineStatus = newEngine.widevine().activate().join()
        } catch (e: Exception) {
        }

        // Set up permission handlers for the engine
        setupPermissionHandlers(newEngine)

        _engine = newEngine


        return newEngine
    }

    private fun setupPermissionHandlers(engine: Engine) {
        // Set up permission handler for all browsers created from this engine
        val profile = engine.profiles().defaultProfile()
        val permissions = profile.permissions()

        permissions.set(RequestPermissionCallback::class.java, object : RequestPermissionCallback {
            override fun on(params: RequestPermissionCallback.Params, action: RequestPermissionCallback.Action) {
                val permissionType = params.permissionType()

                // Auto-grant camera and microphone permissions for video conferencing
                when (permissionType) {
                    PermissionType.VIDEO_CAPTURE -> {
                        action.grant()
                    }
                    PermissionType.AUDIO_CAPTURE -> {
                        action.grant()
                    }
                    PermissionType.NOTIFICATIONS -> {
                        action.grant()
                    }
                    else -> {
                        // For other permissions, auto-grant as well
                        action.grant()
                    }
                }
            }
        })
    }

    /**
     * Sets up screen capture session handler for a browser.
     * This intercepts screen share requests and shows a custom picker dialog for tabs.
     * User can choose to use native picker for windows/screens.
     */
    fun setupCaptureSessionHandler(browser: com.teamdev.jxbrowser.browser.Browser) {
        browser.set(StartCaptureSessionCallback::class.java, StartCaptureSessionCallback { params, tell ->
            // On macOS, check and request screen recording permission
            if (!MacOSScreenCapture.hasPermission()) {
                val granted = MacOSScreenCapture.requestPermission()
                if (!granted) {
                    tell.cancel()
                    return@StartCaptureSessionCallback
                }
            }

            val sources = params.sources()

            // Log available sources for debugging

            // Generate unique request ID
            val requestId = java.util.UUID.randomUUID().toString()

            // Emit to UI for user selection
            ScreenCaptureNotifier.requestCapture(
                requestId = requestId,
                sources = sources,
                tell = tell
            )

            // Set 60-second timeout - if user doesn't respond, cancel
            CoroutineScope(Dispatchers.Default).launch {
                delay(60_000)
                if (ScreenCaptureNotifier.hasPendingRequest(requestId)) {
                    ScreenCaptureNotifier.cancel(requestId)
                }
            }
        })
    }

    /**
     * Sets up fullscreen handler for a browser.
     * When web content requests fullscreen (e.g., YouTube video),
     * opens a fullscreen window with the browser content.
     *
     * @param browser The browser instance to configure
     * @param tabId The unique ID of the tab containing this browser
     * @param onFullscreenEnter Callback when fullscreen mode is entered
     * @param onFullscreenExit Callback when fullscreen mode is exited
     */
    fun setupFullscreenHandler(
        browser: com.teamdev.jxbrowser.browser.Browser,
        tabId: String,
        onFullscreenEnter: () -> Unit,
        onFullscreenExit: () -> Unit
    ) {
        // Handle fullscreen enter request
        browser.fullScreen().on(com.teamdev.jxbrowser.fullscreen.event.FullScreenEntered::class.java) {
            logger.info(LogCategory.BROWSER, "Web content requested fullscreen", mapOf("tabId" to tabId))

            // Show fullscreen window
            ai.rever.boss.tabfullscreen.FullscreenBrowserWindow.showFullscreen(browser, tabId) {
                // This is called when exiting fullscreen via ESC or clicking placeholder
                onFullscreenExit()
            }

            onFullscreenEnter()
        }

        // Handle fullscreen exit using event listener
        browser.fullScreen().on(com.teamdev.jxbrowser.fullscreen.event.FullScreenExited::class.java) {
            logger.info(LogCategory.BROWSER, "Fullscreen exited", mapOf("tabId" to tabId))

            // Close fullscreen window
            ai.rever.boss.tabfullscreen.FullscreenBrowserWindow.exitFullscreen()

            onFullscreenExit()
        }
    }

    /**
     * Sets up keyboard interceptor for a browser to forward menu shortcuts to the native menu bar.
     * This intercepts Cmd+R, Cmd+N, Cmd+T, Cmd+W, etc. (on macOS) or Ctrl+R, Ctrl+N, etc. (on Windows/Linux)
     * before JxBrowser consumes them, and manually triggers the corresponding MenuActionsHandler methods.
     */
    fun setupKeyboardInterceptor(browser: com.teamdev.jxbrowser.browser.Browser) {
        browser.set(com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback::class.java,
            com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback { params ->
                val event = params.event()
                val modifiers = event.keyModifiers()
                val keyCode = event.keyCode()

                // Platform-aware main modifier: Cmd on macOS, Ctrl on Windows/Linux
                val isMainModifierDown = if (SystemUtils.isMacOS) {
                    modifiers.isMetaDown && !modifiers.isControlDown
                } else {
                    modifiers.isControlDown && !modifiers.isMetaDown
                }
                val modifierName = if (SystemUtils.isMacOS) "Cmd" else "Ctrl"

                // Intercept main modifier + key shortcuts
                if (isMainModifierDown && !modifiers.isShiftDown && !modifiers.isAltDown) {
                    // Read focusedWindowId here to minimize race window between read and use
                    val focusedWindowId = WindowFocusManager.focusedWindowFlow.value
                    if (focusedWindowId != null) {
                        when (keyCode) {
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_R -> {
                                ai.rever.boss.window.MenuActionsHandler.triggerReloadBrowser(focusedWindowId)
                                return@PressKeyCallback com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback.Response.suppress()
                            }
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_N -> {
                                ai.rever.boss.window.MenuActionsHandler.triggerNewTab(focusedWindowId)
                                return@PressKeyCallback com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback.Response.suppress()
                            }
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_T -> {
                                ai.rever.boss.window.MenuActionsHandler.triggerNewTab(focusedWindowId)
                                return@PressKeyCallback com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback.Response.suppress()
                            }
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_W -> {
                                ai.rever.boss.window.MenuActionsHandler.triggerCloseTab(focusedWindowId)
                                return@PressKeyCallback com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback.Response.suppress()
                            }
                            else -> {
                                // Let other main modifier + key combos pass through to the native menu bar
                            }
                        }
                    } else {
                        // Log only for shortcuts we handle to avoid spam
                        when (keyCode) {
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_R,
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_N,
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_T,
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_W -> {
                                logger.debug(LogCategory.BROWSER, "No window focused, cannot dispatch shortcut", mapOf("shortcut" to "$modifierName+${keyCode.name}"))
                            }
                            else -> { /* Not a handled shortcut, no logging needed */ }
                        }
                    }
                }

                // Intercept main modifier + Shift + key shortcuts
                if (isMainModifierDown && modifiers.isShiftDown && !modifiers.isAltDown) {
                    // Read focusedWindowId here to minimize race window between read and use
                    val focusedWindowId = WindowFocusManager.focusedWindowFlow.value
                    if (focusedWindowId != null) {
                        when (keyCode) {
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_F -> {
                                ai.rever.boss.window.MenuActionsHandler.triggerToggleFocusMode(focusedWindowId)
                                return@PressKeyCallback com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback.Response.suppress()
                            }
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_S -> {
                                ai.rever.boss.window.MenuActionsHandler.triggerSaveWorkspace(focusedWindowId)
                                return@PressKeyCallback com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback.Response.suppress()
                            }
                            else -> {
                                // Let other main modifier + Shift + key combos pass through
                            }
                        }
                    } else {
                        // Log only for shortcuts we handle to avoid spam
                        when (keyCode) {
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_F,
                            com.teamdev.jxbrowser.ui.KeyCode.KEY_CODE_S -> {
                                logger.debug(LogCategory.BROWSER, "No window focused, cannot dispatch shortcut", mapOf("shortcut" to "$modifierName+Shift+${keyCode.name}"))
                            }
                            else -> { /* Not a handled shortcut, no logging needed */ }
                        }
                    }
                }

                // Let all other key events proceed normally
                com.teamdev.jxbrowser.browser.callback.input.PressKeyCallback.Response.proceed()
            }
        )
    }

    fun setupBrowserDownloadHandler(browser: com.teamdev.jxbrowser.browser.Browser) {
        // Set up download handler for this browser
        browser.set(
            StartDownloadCallback::class.java,
            StartDownloadCallback { params, action ->
                val download = params.download()
                val target = download.target()

                // Mark this URL as an active download IMMEDIATELY to prevent popup handler from opening a new tab
                // This must happen before any other logic because popup handler may execute concurrently
                val downloadUrl = target.url()
                activeDownloadUrls.add(downloadUrl)

                // Auto-close any tabs that were recently opened (likely download redirects)
                autoCloseDownloadTab()

                val suggestedFileName = target.suggestedFileName()
                val sanitizedFileName = FileNameSanitizer.sanitize(suggestedFileName)

                // Check if Shift key is pressed (force save dialog)
                val forceDialog = isShiftPressed()

                // Determine save location based on settings
                val savePath = when {
                    downloadSettings.alwaysAskWhereToSave || forceDialog -> {
                        // Show save dialog
                        pickSaveFile(
                            suggestedFileName = sanitizedFileName,
                            initialDirectory = downloadSettings.lastUsedDirectory
                                ?: downloadSettings.defaultDownloadDirectory
                        )
                    }
                    else -> {
                        // Auto-save to default/last directory
                        val directory = downloadSettings.lastUsedDirectory
                            ?: downloadSettings.defaultDownloadDirectory
                        FileSystemUtils.generateUniqueFilePath(directory, sanitizedFileName)
                    }
                }

                if (savePath != null) {
                    // Ensure parent directory exists
                    if (!FileSystemUtils.ensureParentDirectoryExists(savePath)) {
                        action.cancel()
                        return@StartDownloadCallback
                    }

                    // Warn for executable files
                    if (downloadSettings.warnForExecutables &&
                        FileNameSanitizer.isExecutableFile(sanitizedFileName)) {
                        // TODO: Show user warning dialog (for now, just proceed)
                    }

                    // Start the download
                    val downloadPath = Paths.get(savePath)

                    // Update last used directory
                    val parentDir = downloadPath.parent?.toString()
                    if (parentDir != null) {
                        downloadSettings = downloadSettings.copy(lastUsedDirectory = parentDir)
                    }


                    // Generate unique download ID
                    val downloadId = UUID.randomUUID().toString()

                    // Add download to manager immediately and open Downloads panel
                    CoroutineScope(Dispatchers.Default).launch {
                        downloadManager.addDownload(
                            DownloadItem(
                                id = downloadId,
                                fileName = sanitizedFileName,
                                destinationPath = savePath,
                                url = target.url(),
                                mimeType = target.mimeType().toString(),
                                status = DownloadStatus.DOWNLOADING,
                                receivedBytes = 0,
                                totalBytes = null,
                                speed = 0.0,
                                startedAt = System.currentTimeMillis(),
                                finishedAt = null,
                                canPause = false,
                                canResume = false,
                                errorReason = null
                            )
                        )

                        // Open the Downloads sidebar panel
                        val focusedWindowId = WindowFocusManager.focusedWindowFlow.value
                        if (focusedWindowId != null) {
                            ai.rever.boss.components.events.PanelEventBus.openPanel(
                                ai.rever.boss.components.plugin.PanelIds.DOWNLOADS,
                                sourceWindowId = focusedWindowId
                            )
                        } else {
                        }
                    }

                    // Register event listeners on the download object
                    val downloadObj = download
                    setupDownloadEventListeners(downloadObj, downloadId, sanitizedFileName, savePath, target.url())

                    // Initiate the download
                    action.download(downloadPath)
                } else {
                    // User cancelled save dialog
                    action.cancel()
                }
            }
        )
    }

    private fun setupDownloadEventListeners(
        download: Download,
        downloadId: String,
        fileName: String,
        destinationPath: String,
        url: String
    ) {
        val scope = CoroutineScope(Dispatchers.Default)

        // Track this download for pause/resume operations
        activeDownloads[downloadId] = download

        // Download progress updated
        download.on(DownloadUpdated::class.java) { event ->
            scope.launch {
                val receivedBytes = event.receivedBytes()
                val totalBytes = event.totalBytes()
                val speed = event.currentSpeed().toDouble()

                // Update capabilities based on server support
                // JxBrowser automatically supports pause/resume if the server supports HTTP range requests
                val canPause = !download.isPaused
                val canResume = download.isPaused
                downloadManager.updateCapabilities(downloadId, canPause, canResume)

                // Check if download was resumed (was PAUSED, now actively downloading)
                val currentItem = downloadManager.getDownload(downloadId)
                if (currentItem?.status == DownloadStatus.PAUSED && !download.isPaused && speed > 0) {
                    downloadManager.updateStatus(downloadId, DownloadStatus.DOWNLOADING)
                }

                downloadManager.updateProgress(downloadId, receivedBytes, totalBytes, speed)
            }
        }

        // Download paused
        download.on(DownloadPaused::class.java) { event ->
            scope.launch {
                downloadManager.updateStatus(downloadId, DownloadStatus.PAUSED)
            }
        }

        // Download finished
        download.on(DownloadFinished::class.java) { event ->
            scope.launch {
                downloadManager.updateStatus(downloadId, DownloadStatus.COMPLETED)
                // Remove from tracking maps
                activeDownloadUrls.remove(url)
                activeDownloads.remove(downloadId)
            }
        }

        // Download interrupted (failed)
        download.on(DownloadInterrupted::class.java) { event ->
            scope.launch {
                val reason = event.reason()?.toString() ?: "Unknown error"
                downloadManager.updateStatus(
                    downloadId,
                    DownloadStatus.FAILED,
                    errorReason = "Download failed: $reason"
                )
                FileSystemUtils.cleanupPartialFile(destinationPath)
                // Remove from tracking maps
                activeDownloadUrls.remove(url)
                activeDownloads.remove(downloadId)
            }
        }

        // Download cancelled
        download.on(DownloadCanceled::class.java) { event ->
            scope.launch {
                downloadManager.updateStatus(downloadId, DownloadStatus.CANCELLED)
                FileSystemUtils.cleanupPartialFile(destinationPath)
                // Remove from tracking maps
                activeDownloadUrls.remove(url)
                activeDownloads.remove(downloadId)
            }
        }
    }

    /**
     * Checks if Shift key is currently pressed.
     * Used to force save dialog even when auto-save is enabled.
     *
     * Note: This is a placeholder implementation. Detecting modifier keys
     * outside of event handlers is not reliably supported in AWT.
     * For now, always returns false (user can enable "always ask" in settings).
     */
    private fun isShiftPressed(): Boolean {
        return false // TODO: Implement if needed
    }

    /**
     * Result of browser profile reset operation with detailed step status.
     */
    data class ResetResult(
        val success: Boolean,
        val engineClosed: Boolean = false,
        val profileDeleted: Boolean = false,
        val tempProfilesCleaned: Boolean = false,
        val errorMessage: String? = null,
        val failedStep: String? = null
    )

    /**
     * Reset browser profile to fix persistent browser issues.
     * This will:
     * 1. Close the current engine (if running)
     * 2. Delete the browser profile directory
     * 3. Clear cached state so engine reinitializes on next use
     *
     * IMPORTANT: This is a suspend function that runs blocking I/O on Dispatchers.IO
     * to avoid freezing the UI thread.
     *
     * @return ResetResult with detailed status of each step
     */
    suspend fun resetBrowserProfile(): ResetResult = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {

        var engineClosed = false
        var profileDeleted = false
        var tempProfilesCleaned = false

        try {
            // Step 1: Close current engine if it exists
            _engine?.let { engine ->
                if (!engine.isClosed) {
                    try {
                        engine.close()
                        engineClosed = true
                    } catch (e: Exception) {
                        // Continue anyway - engine may be in bad state
                        engineClosed = true // Mark as closed since we tried
                    }
                } else {
                    engineClosed = true // Already closed
                }
            } ?: run {
                engineClosed = true // No engine to close
            }

            // Step 2: Clear cached state (must happen even if engine close had issues)
            _engine = null
            initializationError = null
            attemptCount = 0
            // Increment generation to notify browser tabs that they need to reload
            _engineGeneration++
            _engineGenerationFlow.value = _engineGeneration

            // Step 3: Kill any stale Chromium processes
            val userHome = System.getProperty("user.home")
            try {
                killStaleChromiumProcesses(userHome)
            } catch (e: Exception) {
                // Continue - not critical
            }

            // Step 4: Delete browser profile directory
            val selectedProfile = BrowserSettings.currentProfile
            val profileDir = java.io.File(userHome, ".boss/$selectedProfile")

            if (profileDir.exists()) {
                profileDeleted = profileDir.deleteRecursively()
                if (profileDeleted) {
                } else {
                    // This is a partial failure - return with details
                    return@withContext ResetResult(
                        success = false,
                        engineClosed = engineClosed,
                        profileDeleted = false,
                        tempProfilesCleaned = false,
                        errorMessage = "Could not delete all files in profile directory. Some files may be locked.",
                        failedStep = "Delete profile directory"
                    )
                }
            } else {
                profileDeleted = true // Nothing to delete is success
            }

            // Step 5: Also clean up temporary profiles
            try {
                cleanupOldTemporaryProfiles(userHome)
                tempProfilesCleaned = true
            } catch (e: Exception) {
                // Not critical - continue
                tempProfilesCleaned = false
            }

            ResetResult(
                success = true,
                engineClosed = engineClosed,
                profileDeleted = profileDeleted,
                tempProfilesCleaned = tempProfilesCleaned
            )

        } catch (e: Exception) {
            ResetResult(
                success = false,
                engineClosed = engineClosed,
                profileDeleted = profileDeleted,
                tempProfilesCleaned = tempProfilesCleaned,
                errorMessage = e.message,
                failedStep = "Unknown"
            )
        }
    }

    /**
     * Synchronous wrapper for resetBrowserProfile for simple use cases.
     * Runs the reset on a background thread and blocks until complete.
     *
     * @return true if reset was successful, false otherwise
     */
    fun resetBrowserProfileBlocking(): Boolean {
        return kotlinx.coroutines.runBlocking {
            resetBrowserProfile().success
        }
    }

    /**
     * Check if browser engine is in a healthy state.
     * Used to determine if reset might be needed.
     */
    fun isEngineHealthy(): Boolean {
        return _engine?.let { !it.isClosed } ?: true // null engine is "healthy" (will initialize on demand)
    }
}


