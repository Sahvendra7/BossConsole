package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.config.JxBrowserConfig
import ai.rever.boss.platform.FileNameSanitizer
import ai.rever.boss.platform.MacOSScreenCapture
import ai.rever.boss.platform.FileSystemUtils
import ai.rever.boss.platform.pickSaveFile
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

        println("=== Proactive JxBrowser lock cleanup ===")
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
            println("Profile directory does not exist yet: $profileDirPath")
        }
        println("=== End proactive cleanup ===")
    }

    /**
     * Kill stale Chromium processes that were spawned by previous BOSS sessions.
     * These zombie processes can prevent profile reuse even without lock files.
     */
    private fun killStaleChromiumProcesses(userHome: String) {
        println("Checking for stale Chromium processes...")
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
                    println("  Found stale Chromium process:")
                    println("    PID=$pid")
                    println("    Command=$command")
                    println("    CommandLine=$commandLine")

                    // Try graceful termination first with proper timeout handling
                    process.destroy()
                    killedAny = true

                    // Wait for graceful shutdown using ProcessHandle.onExit() with timeout
                    // This is more reliable than Thread.sleep() and doesn't block unnecessarily
                    try {
                        process.onExit().get(100, java.util.concurrent.TimeUnit.MILLISECONDS)
                    } catch (e: java.util.concurrent.TimeoutException) {
                        // Process didn't exit in time - force kill
                        println("  Process $pid didn't exit gracefully, force killing...")
                        process.destroyForcibly()
                    } catch (e: Exception) {
                        // Process already exited or other error
                    }
                    println("  Killed stale process: $pid")
                } catch (e: Exception) {
                    println("  Failed to kill process: ${e.message}")
                }
            }

            if (staleProcesses.isEmpty()) {
                println("  No stale Chromium processes found")
            }

            // If we killed any processes, wait for them to fully terminate
            // Using Thread.sleep() is acceptable here since this runs during startup
            // before UI initialization (per code review recommendation)
            if (killedAny) {
                println("  Waiting for processes to fully terminate...")
                Thread.sleep(500)
            }
        } catch (e: Exception) {
            println("Error checking for stale processes: ${e.message}")
        }
    }

    /**
     * Clean up ALL lock-related files in the profile directory.
     * JxBrowser/Chromium uses multiple files for locking.
     */
    private fun cleanupAllLockRelatedFiles(profileDir: java.nio.file.Path) {
        println("Cleaning up all lock-related files in: $profileDir")

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
                println("  $fileName: deleted=$deleted")
            }
        }

        // Also check for lock files in Default subdirectory
        val defaultDir = profileDir.resolve("Default")
        if (defaultDir.toFile().exists()) {
            lockFiles.forEach { fileName ->
                val file = defaultDir.resolve(fileName).toFile()
                if (file.exists()) {
                    val deleted = file.delete()
                    println("  Default/$fileName: deleted=$deleted")
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
        println("FluckEngine: Notified that a tab was just opened (timestamp: $now)")

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
            println("FluckEngine: Auto-closing most recently opened tab (opened ${now - recentTabs.last().first}ms ago)")
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
                println("FluckEngine: Paused download: $downloadId")
            } catch (e: Exception) {
                println("FluckEngine: Error pausing download $downloadId: ${e.message}")
            }
        } ?: println("FluckEngine: Cannot pause download $downloadId - not found in active downloads")
    }

    /**
     * Resume a paused download.
     * @param downloadId The unique ID of the download to resume
     */
    fun resumeDownload(downloadId: String) {
        activeDownloads[downloadId]?.let { download ->
            try {
                download.resume()
                println("FluckEngine: Resumed download: $downloadId")
            } catch (e: Exception) {
                println("FluckEngine: Error resuming download $downloadId: ${e.message}")
            }
        } ?: println("FluckEngine: Cannot resume download $downloadId - not found in active downloads")
    }

    /**
     * Cancel an active or paused download.
     * @param downloadId The unique ID of the download to cancel
     */
    fun cancelDownload(downloadId: String) {
        activeDownloads[downloadId]?.let { download ->
            try {
                download.cancel()
                println("FluckEngine: Cancelled download: $downloadId")
            } catch (e: Exception) {
                println("FluckEngine: Error cancelling download $downloadId: ${e.message}")
            }
        } ?: println("FluckEngine: Cannot cancel download $downloadId - not found in active downloads")
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
                println("⚠️ FluckEngine: Cached engine was closed, reinitializing...")
                _engine = null
                initializationError = null
                attemptCount = 0
                // Increment generation to notify browser tabs that they need to reload
                _engineGeneration++
                _engineGenerationFlow.value = _engineGeneration
                println("🔄 FluckEngine: Engine generation incremented to $_engineGeneration")
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
            println("Requesting screen capture permission proactively...")
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
            println("Using bundled BOSS-branded Chromium: $bundledDir")
            return bundledDir
        }

        // Priority 2: Cached BOSS-branded Chromium
        val cachedBrandedDir = Paths.get(userHome, ".boss", "boss-chromium")
        if (isValidChromiumDir(cachedBrandedDir)) {
            println("Using cached BOSS-branded Chromium: $cachedBrandedDir")
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

        println("Checking for stale lock files in: $profileDir")
        println("  SingletonLock exists: ${lockFile.exists()}")

        if (!lockFile.exists()) {
            println("  No lock file found - profile is available")
            return false // No lock to clean
        }

        // On Linux, SingletonLock is a symlink to "spark-<hostname>-<pid>"
        // Check if the PID is still running
        try {
            val isSymlink = Files.isSymbolicLink(lockFile.toPath())
            println("  SingletonLock is symlink: $isSymlink")

            if (isSymlink) {
                val target = Files.readSymbolicLink(lockFile.toPath()).toString()
                println("  Symlink target: $target")

                // Parse PID from "spark-hostname-12345" or similar format
                val pid = target.substringAfterLast("-").toLongOrNull()
                println("  Extracted PID: $pid")

                if (pid != null) {
                    // Check if process is still running
                    val processHandle = ProcessHandle.of(pid)
                    val isRunning = processHandle.isPresent
                    println("  Process $pid is running: $isRunning")

                    if (isRunning) {
                        // Additional check: verify it's actually a BOSS/JxBrowser process
                        // not just a reused PID from another application
                        val processInfo = processHandle.orElse(null)
                        val command = processInfo?.info()?.command()?.orElse(null)
                        println("  Process command: $command")

                        // If it's not a Java process, it's likely a reused PID
                        val isJavaProcess = command?.contains("java", ignoreCase = true) == true
                        if (!isJavaProcess) {
                            println("  PID $pid is not a Java process - lock is stale (PID reused)")
                            deleteLockFiles(lockFile, socketFile, cookieFile)
                            return true
                        }
                    } else {
                        println("  PID $pid no longer running - cleaning up stale lock files")
                        deleteLockFiles(lockFile, socketFile, cookieFile)
                        return true
                    }
                } else {
                    // Couldn't parse PID - try to clean up anyway if lock file is old
                    val lastModified = lockFile.lastModified()
                    val ageMinutes = (System.currentTimeMillis() - lastModified) / (1000 * 60)
                    println("  Could not parse PID from symlink. Lock file age: $ageMinutes minutes")

                    // If lock is older than 5 minutes, assume it's stale
                    if (ageMinutes > 5) {
                        println("  Lock file is old - assuming stale and cleaning up")
                        deleteLockFiles(lockFile, socketFile, cookieFile)
                        return true
                    }
                }
            } else {
                // Not a symlink (Windows or other OS) - check file age
                val lastModified = lockFile.lastModified()
                val ageMinutes = (System.currentTimeMillis() - lastModified) / (1000 * 60)
                println("  Lock file (not symlink) age: $ageMinutes minutes")

                // On non-Linux, if lock is older than 5 minutes and we're starting fresh, clean it
                if (ageMinutes > 5) {
                    println("  Lock file is old - assuming stale and cleaning up")
                    deleteLockFiles(lockFile, socketFile, cookieFile)
                    return true
                }
            }
        } catch (e: Exception) {
            // Log error without printStackTrace per CLAUDE.md guidelines
            println("  Error checking lock file: ${e.message}")

            // If we can't check, try to clean up anyway
            println("  Attempting cleanup despite error...")
            try {
                deleteLockFiles(lockFile, socketFile, cookieFile)
                return true
            } catch (e2: Exception) {
                println("  Cleanup failed: ${e2.message}")
            }
        }

        println("  Lock appears to be held by active process - cannot clean up")
        return false
    }

    private fun deleteLockFiles(lockFile: java.io.File, socketFile: java.io.File, cookieFile: java.io.File) {
        println("  Deleting lock files...")
        if (lockFile.exists()) {
            val deleted = lockFile.delete()
            if (!deleted) {
                println("    WARNING: Failed to delete SingletonLock - may cause browser initialization issues")
            } else {
                println("    SingletonLock deleted: $deleted")
            }
        }
        if (socketFile.exists()) {
            val deleted = socketFile.delete()
            if (!deleted) {
                println("    WARNING: Failed to delete SingletonSocket - may cause browser initialization issues")
            } else {
                println("    SingletonSocket deleted: $deleted")
            }
        }
        if (cookieFile.exists()) {
            val deleted = cookieFile.delete()
            if (!deleted) {
                println("    WARNING: Failed to delete SingletonCookie - may cause browser initialization issues")
            } else {
                println("    SingletonCookie deleted: $deleted")
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
                println("Cleaning up old temporary profile: ${dir.name}")
                dir.deleteRecursively()
            }
        } catch (e: Exception) {
            println("Failed to clean up old profiles: ${e.message}")
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
                println("Retrying with cleaned profile '$selectedProfile'...")
                try {
                    return createEngineInstance(chromiumDir, profileDirPath, selectedProfile)
                } catch (e2: Exception) {
                    println("Still failed after cleanup: ${e2.message}")
                }
            }

            // Profile is genuinely in use by another process, use temporary
            println("Profile '$selectedProfile' is already in use, trying with temporary profile...")
            val tempProfile = "browser-profile-${System.currentTimeMillis()}"
            val tempProfilePath = Paths.get(userHome, ".boss", tempProfile)
            tempProfilePath.toFile().mkdirs()

            try {
                createEngineInstance(chromiumDir, tempProfilePath, tempProfile)
            } catch (e2: Exception) {
                println("Failed to create engine with temporary profile: ${e2.message}")
                throw e2
            }
        } catch (e: Exception) {
            println("JxBrowser initialization failed:")
            println("- Error: ${e.message}")
            println("- Type: ${e.javaClass.name}")
            println("- User home: $userHome")
            println("- OS: ${System.getProperty("os.name")} ${System.getProperty("os.version")}")
            println("- Arch: ${System.getProperty("os.arch")}")
            println("- Java: ${System.getProperty("java.version")}")
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
            println("Widevine activation status: $widevineStatus")
        } catch (e: Exception) {
            println("Widevine activation failed: ${e.message}")
        }

        // Set up permission handlers for the engine
        setupPermissionHandlers(newEngine)

        _engine = newEngine

        println("JxBrowser initialized with profile: $profileName")

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
                println("Screen capture permission not granted, requesting...")
                val granted = MacOSScreenCapture.requestPermission()
                if (!granted) {
                    println("Screen capture permission denied by user")
                    tell.cancel()
                    return@StartCaptureSessionCallback
                }
            }

            val sources = params.sources()

            // Log available sources for debugging
            println("Screen capture requested - Screens: ${sources.screens().size}, Windows: ${sources.applicationWindows().size}, Browsers: ${sources.browsers().size}")

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
                    println("Screen capture request timed out after 60 seconds")
                    ScreenCaptureNotifier.cancel(requestId)
                }
            }
        })
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
                println("FluckEngine: Marked URL as active download: $downloadUrl")

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
                        println("Failed to create download directory for: $savePath")
                        action.cancel()
                        return@StartDownloadCallback
                    }

                    // Warn for executable files
                    if (downloadSettings.warnForExecutables &&
                        FileNameSanitizer.isExecutableFile(sanitizedFileName)) {
                        println("Warning: Downloading executable file: $sanitizedFileName")
                        // TODO: Show user warning dialog (for now, just proceed)
                    }

                    // Start the download
                    val downloadPath = Paths.get(savePath)

                    // Update last used directory
                    val parentDir = downloadPath.parent?.toString()
                    if (parentDir != null) {
                        downloadSettings = downloadSettings.copy(lastUsedDirectory = parentDir)
                    }

                    println("Download starting: $sanitizedFileName -> $savePath")

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
                                ai.rever.boss.components.plugin.panels.left_top.DownloadInfo.id,
                                sourceWindowId = focusedWindowId
                            )
                        } else {
                            println("FluckEngine: No window focused, cannot open Downloads panel")
                        }
                    }

                    // Register event listeners on the download object
                    val downloadObj = download
                    setupDownloadEventListeners(downloadObj, downloadId, sanitizedFileName, savePath, target.url())

                    // Initiate the download
                    action.download(downloadPath)
                } else {
                    // User cancelled save dialog
                    println("Download cancelled by user: $sanitizedFileName")
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
                    println("Download resumed: $fileName")
                }

                downloadManager.updateProgress(downloadId, receivedBytes, totalBytes, speed)
            }
        }

        // Download paused
        download.on(DownloadPaused::class.java) { event ->
            scope.launch {
                downloadManager.updateStatus(downloadId, DownloadStatus.PAUSED)
                println("Download paused: $fileName")
            }
        }

        // Download finished
        download.on(DownloadFinished::class.java) { event ->
            scope.launch {
                downloadManager.updateStatus(downloadId, DownloadStatus.COMPLETED)
                println("Download completed: $fileName")
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
                println("Download failed: $fileName - $reason")
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
                println("Download cancelled: $fileName")
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
        println("🔄 [FluckEngine] Resetting browser profile...")

        var engineClosed = false
        var profileDeleted = false
        var tempProfilesCleaned = false

        try {
            // Step 1: Close current engine if it exists
            _engine?.let { engine ->
                if (!engine.isClosed) {
                    println("   Closing current engine...")
                    try {
                        engine.close()
                        engineClosed = true
                    } catch (e: Exception) {
                        println("   Warning: Error closing engine: ${e.message}")
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
            println("🔄 FluckEngine: Engine generation incremented to $_engineGeneration (reset)")

            // Step 3: Kill any stale Chromium processes
            val userHome = System.getProperty("user.home")
            try {
                killStaleChromiumProcesses(userHome)
            } catch (e: Exception) {
                println("   Warning: Error killing stale processes: ${e.message}")
                // Continue - not critical
            }

            // Step 4: Delete browser profile directory
            val selectedProfile = BrowserSettings.currentProfile
            val profileDir = java.io.File(userHome, ".boss/$selectedProfile")

            if (profileDir.exists()) {
                println("   Deleting profile directory: ${profileDir.absolutePath}")
                profileDeleted = profileDir.deleteRecursively()
                if (profileDeleted) {
                    println("   ✅ Profile directory deleted successfully")
                } else {
                    println("   ⚠️ Could not delete all files in profile directory")
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
                println("   Profile directory does not exist, nothing to delete")
                profileDeleted = true // Nothing to delete is success
            }

            // Step 5: Also clean up temporary profiles
            try {
                cleanupOldTemporaryProfiles(userHome)
                tempProfilesCleaned = true
            } catch (e: Exception) {
                println("   Warning: Error cleaning temp profiles: ${e.message}")
                // Not critical - continue
                tempProfilesCleaned = false
            }

            println("✅ [FluckEngine] Browser profile reset complete")
            ResetResult(
                success = true,
                engineClosed = engineClosed,
                profileDeleted = profileDeleted,
                tempProfilesCleaned = tempProfilesCleaned
            )

        } catch (e: Exception) {
            println("❌ [FluckEngine] Error resetting browser profile: ${e.message}")
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


