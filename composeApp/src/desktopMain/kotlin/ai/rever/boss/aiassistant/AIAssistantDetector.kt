package ai.rever.boss.aiassistant

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Detects which AI assistants are installed on the system.
 * Uses `which` command on Unix-like systems, `where` on Windows.
 * Includes periodic background refresh to detect installations during runtime.
 *
 * Issue #445: Terminal context menu for AI coding assistants
 */
object AIAssistantDetector {

    /**
     * Installation status for an AI assistant.
     */
    data class InstallationStatus(
        val installed: Boolean,
        val path: String? = null,
        val checkedAt: Long = System.currentTimeMillis()
    )

    private const val PROCESS_TIMEOUT_SECONDS = 5L
    private const val PERIODIC_REFRESH_INTERVAL_MS = 300_000L // 5 minutes
    private const val MIN_REFRESH_INTERVAL_MS = 5_000L // 5 seconds minimum between refreshes

    private val cache = ConcurrentHashMap<AIAssistant, InstallationStatus>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Mutex for atomic cache + StateFlow updates
    private val updateMutex = Mutex()

    // Guard to prevent overlapping periodic refreshes
    private val refreshInProgress = AtomicBoolean(false)

    // Track last refresh time to skip redundant refreshes
    @Volatile
    private var lastRefreshTime = 0L

    private val _installationStatuses = MutableStateFlow<Map<AIAssistant, InstallationStatus>>(emptyMap())

    /**
     * Current installation statuses as a reactive flow.
     * Updated when checks are performed.
     */
    val installationStatuses: StateFlow<Map<AIAssistant, InstallationStatus>> = _installationStatuses.asStateFlow()

    init {
        // Refresh all statuses on initialization
        scope.launch {
            refreshAll()
        }

        // Start periodic background refresh to detect runtime installations
        scope.launch {
            startPeriodicRefresh()
        }
    }

    /**
     * Periodically refresh installation statuses to detect runtime installations.
     * Runs every 5 minutes in the background.
     * Skips refresh if previous one is still in progress to prevent queueing.
     */
    private suspend fun startPeriodicRefresh() {
        while (scope.isActive) {
            delay(PERIODIC_REFRESH_INTERVAL_MS)
            if (refreshInProgress.compareAndSet(false, true)) {
                try {
                    println("[AIAssistantDetector] Periodic refresh triggered")
                    refreshAll()
                } finally {
                    refreshInProgress.set(false)
                }
            } else {
                println("[AIAssistantDetector] Skipping refresh - previous still in progress")
            }
        }
    }

    /**
     * Check if an assistant is installed (with caching).
     *
     * @param assistant The assistant to check
     * @return true if installed, false otherwise
     */
    suspend fun isInstalled(assistant: AIAssistant): Boolean {
        val settings = AIAssistantSettingsManager.currentSettings.value
        val cached = cache[assistant]

        if (cached != null && settings.cacheInstallationStatus) {
            val age = System.currentTimeMillis() - cached.checkedAt
            if (age < settings.installationCacheDurationMs) {
                return cached.installed
            }
        }

        return checkInstallation(assistant).installed
    }

    /**
     * Force check installation status (bypasses cache).
     * First checks common installation paths, then falls back to shell-based detection.
     *
     * @param assistant The assistant to check
     * @return InstallationStatus with installed flag and path if found
     */
    suspend fun checkInstallation(assistant: AIAssistant): InstallationStatus = withContext(Dispatchers.IO) {
        val config = AIAssistantSettingsManager.currentSettings.value.getConfig(assistant)
        val command = config.getCommand()

        // First, check common installation paths directly (faster and more reliable)
        val directPath = checkCommonPaths(assistant, command)
        if (directPath != null) {
            val status = InstallationStatus(installed = true, path = directPath)
            updateMutex.withLock {
                cache[assistant] = status
                _installationStatuses.value = cache.toMap()
            }
            println("[AIAssistantDetector] ${assistant.displayName}: installed=true (direct path), path=$directPath")
            return@withContext status
        }

        // Second, try simple which/where (uses inherited PATH)
        val simpleWhichResult = trySimpleWhich(command)
        if (simpleWhichResult != null) {
            val status = InstallationStatus(installed = true, path = simpleWhichResult)
            updateMutex.withLock {
                cache[assistant] = status
                _installationStatuses.value = cache.toMap()
            }
            println("[AIAssistantDetector] ${assistant.displayName}: installed=true (which), path=$simpleWhichResult")
            return@withContext status
        }

        // Third, fall back to shell-based detection with config sourcing
        var process: Process? = null
        try {
            // Source shell config to inherit user's PATH
            process = if (isWindows()) {
                // Windows where already tried above, try with shell
                ProcessBuilder("cmd", "/c", "where $command")
                    .redirectErrorStream(true)
                    .start()
            } else {
                // Explicitly source shell config files to get user's PATH
                val home = System.getProperty("user.home")
                val shell = System.getenv("SHELL") ?: "/bin/bash"
                val sourceCmd = if (shell.endsWith("zsh")) {
                    "source $home/.zshrc 2>/dev/null; "
                } else {
                    "source $home/.bashrc 2>/dev/null; "
                }
                ProcessBuilder(shell, "-c", "${sourceCmd}which $command")
                    .redirectErrorStream(true)
                    .start()
            }

            // Use timeout to prevent hanging
            val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)

            val status = if (completed) {
                val exitCode = process.exitValue()
                val output = process.inputStream.bufferedReader().use { it.readText().trim() }

                InstallationStatus(
                    installed = exitCode == 0,
                    path = if (exitCode == 0) output.lines().firstOrNull() else null
                )
            } else {
                // Timeout - destroy process and return not installed
                process.destroyForcibly()
                println("[AIAssistantDetector] Timeout checking ${assistant.displayName}")
                InstallationStatus(installed = false)
            }

            // Atomic update of cache and StateFlow
            updateMutex.withLock {
                cache[assistant] = status
                _installationStatuses.value = cache.toMap()
            }

            println("[AIAssistantDetector] ${assistant.displayName}: installed=${status.installed}, path=${status.path}")
            status
        } catch (e: Exception) {
            println("[AIAssistantDetector] Error checking ${assistant.displayName}: ${e.message}")
            val status = InstallationStatus(installed = false)

            updateMutex.withLock {
                cache[assistant] = status
                _installationStatuses.value = cache.toMap()
            }

            status
        } finally {
            // Ensure process is destroyed and streams are closed
            process?.let { p ->
                runCatching { p.destroyForcibly() }
                runCatching { p.inputStream.close() }
                runCatching { p.errorStream.close() }
                runCatching { p.outputStream.close() }
            }
        }
    }

    /**
     * Refresh all installation statuses in parallel.
     * Updates lastRefreshTime on completion.
     */
    suspend fun refreshAll() = withContext(Dispatchers.IO) {
        println("[AIAssistantDetector] Refreshing all installation statuses...")

        // Check all assistants in parallel
        val results = AIAssistant.entries.map { assistant ->
            async { assistant to checkInstallation(assistant) }
        }.awaitAll()

        // Update last refresh time
        lastRefreshTime = System.currentTimeMillis()

        // Update all at once (already done individually, but log summary)
        val installedCount = results.count { it.second.installed }
        println("[AIAssistantDetector] Refresh complete: $installedCount/${results.size} assistants installed")
    }

    /**
     * Refresh if not recently refreshed (within MIN_REFRESH_INTERVAL_MS).
     * Useful for UI components that want to refresh on mount without causing
     * duplicate refreshes if the user opens the screen multiple times quickly.
     */
    suspend fun refreshIfStale() {
        val timeSinceLastRefresh = System.currentTimeMillis() - lastRefreshTime
        if (timeSinceLastRefresh >= MIN_REFRESH_INTERVAL_MS) {
            refreshAll()
        } else {
            println("[AIAssistantDetector] Skipping refresh - refreshed ${timeSinceLastRefresh}ms ago")
        }
    }

    /**
     * Clear the cache, forcing fresh checks on next access.
     */
    suspend fun clearCache() {
        updateMutex.withLock {
            cache.clear()
            _installationStatuses.value = emptyMap()
        }
        println("[AIAssistantDetector] Cache cleared")
    }

    /**
     * Get cached status for an assistant without triggering a check.
     *
     * @param assistant The assistant to get status for
     * @return InstallationStatus if cached, null otherwise
     */
    fun getCachedStatus(assistant: AIAssistant): InstallationStatus? {
        return cache[assistant]
    }

    private fun isWindows(): Boolean =
        System.getProperty("os.name").lowercase().contains("windows")

    /**
     * Try simple which/where command using inherited PATH.
     * Returns the path if found, null otherwise.
     */
    private fun trySimpleWhich(command: String): String? {
        var process: Process? = null
        return try {
            val checkCmd = if (isWindows()) "where" else "which"
            process = ProcessBuilder(checkCmd, command)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (completed && process.exitValue() == 0) {
                val output = process.inputStream.bufferedReader().use { it.readText().trim() }
                output.lines().firstOrNull()?.takeIf { it.isNotEmpty() }
            } else {
                if (!completed) process.destroyForcibly()
                null
            }
        } catch (e: Exception) {
            null
        } finally {
            process?.let { p ->
                runCatching { p.destroyForcibly() }
                runCatching { p.inputStream.close() }
                runCatching { p.errorStream.close() }
                runCatching { p.outputStream.close() }
            }
        }
    }

    /**
     * Check common installation paths for an assistant.
     * Returns the path if found and executable, null otherwise.
     */
    private fun checkCommonPaths(assistant: AIAssistant, command: String): String? {
        val home = System.getProperty("user.home")

        // Common paths where AI assistants might be installed
        val commonPaths = when (assistant) {
            AIAssistant.OPENCODE -> listOf(
                "$home/.opencode/bin/opencode",
                "$home/.local/bin/opencode",
                "/usr/local/bin/opencode"
            )
            AIAssistant.CLAUDE_CODE -> listOf(
                "$home/.claude/local/claude",
                "$home/.local/bin/claude",
                "/usr/local/bin/claude"
            )
            AIAssistant.CODEX -> listOf(
                "$home/.local/bin/codex",
                "/usr/local/bin/codex"
            )
            AIAssistant.GEMINI_CLI -> listOf(
                "$home/.local/bin/gemini",
                "/usr/local/bin/gemini"
            )
        }

        // Also check npm global bin paths
        val npmPaths = listOf(
            "$home/.nvm/versions/node/*/bin/$command",  // nvm
            "$home/.npm-global/bin/$command",           // npm custom prefix
            "/usr/local/lib/node_modules/.bin/$command" // system npm
        )

        // Check direct paths first
        for (path in commonPaths) {
            val file = java.io.File(path)
            if (file.exists() && file.canExecute()) {
                return path
            }
        }

        // Check npm paths with glob pattern support
        for (pattern in npmPaths) {
            if (pattern.contains("*")) {
                // Handle glob patterns (e.g., nvm versions)
                val parts = pattern.split("*")
                if (parts.size == 2) {
                    // parts[0] is the directory containing the wildcard dirs (e.g., ~/.nvm/versions/node/)
                    val baseDir = java.io.File(parts[0].trimEnd('/'))
                    if (baseDir.exists() && baseDir.isDirectory) {
                        baseDir.listFiles()?.forEach { dir ->
                            if (dir.isDirectory) {
                                val fullPath = "${dir.absolutePath}${parts[1]}"
                                val file = java.io.File(fullPath)
                                if (file.exists() && file.canExecute()) {
                                    return fullPath
                                }
                            }
                        }
                    }
                }
            } else {
                val file = java.io.File(pattern)
                if (file.exists() && file.canExecute()) {
                    return pattern
                }
            }
        }

        return null
    }
}
