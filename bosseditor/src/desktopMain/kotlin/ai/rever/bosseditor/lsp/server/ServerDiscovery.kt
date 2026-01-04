package ai.rever.bosseditor.lsp.server

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/**
 * Utility for discovering and checking availability of language servers.
 *
 * Provides functionality to:
 * - Check if a command exists in PATH
 * - Find the full path to an executable
 * - Get server version information
 * - Check which servers are installed
 *
 * ## Usage
 * ```kotlin
 * val discovery = ServerDiscovery()
 *
 * // Check if pylsp is installed
 * if (discovery.isCommandAvailable("pylsp")) {
 *     // Can start Python language server
 * }
 *
 * // Get all available servers
 * val available = discovery.discoverAvailableServers()
 * ```
 */
class ServerDiscovery {

    companion object {
        /**
         * Maximum cache size to prevent memory leaks.
         */
        private const val MAX_CACHE_SIZE = 100
    }

    /**
     * Cache of command availability to avoid repeated lookups.
     * Uses LRU eviction with size limit to prevent memory leaks.
     */
    private val availabilityCache = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    /**
     * Cache of command paths.
     * Uses LRU eviction with size limit to prevent memory leaks.
     */
    private val pathCache = object : LinkedHashMap<String, String?>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    /**
     * Lock for thread-safe cache access.
     */
    private val cacheLock = Any()

    /**
     * Check if a command is available in PATH.
     *
     * @param command The command to check (e.g., "pylsp", "rust-analyzer")
     * @param useCache Whether to use cached results (default true)
     * @return true if the command is available
     */
    fun isCommandAvailable(command: String, useCache: Boolean = true): Boolean {
        // Validate command name before searching
        if (!isValidCommandName(command)) {
            println("[ServerDiscovery] Invalid command name rejected: $command")
            return false
        }

        if (useCache) {
            synchronized(cacheLock) {
                availabilityCache[command]?.let { return it }
            }
        }

        val available = findCommandPath(command) != null

        synchronized(cacheLock) {
            availabilityCache[command] = available
        }

        return available
    }

    /**
     * Find the full path to a command.
     *
     * @param command The command to find
     * @param useCache Whether to use cached results
     * @return The full path, or null if not found
     */
    fun findCommandPath(command: String, useCache: Boolean = true): String? {
        if (useCache) {
            synchronized(cacheLock) {
                if (pathCache.containsKey(command)) {
                    return pathCache[command]
                }
            }
        }

        val path = findInPath(command)

        synchronized(cacheLock) {
            pathCache[command] = path
        }

        return path
    }

    /**
     * Search for a command in PATH directories.
     */
    private fun findInPath(command: String): String? {
        val pathEnv = System.getenv("PATH") ?: return null
        val pathSeparator = File.pathSeparator
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")

        val paths = pathEnv.split(pathSeparator)

        for (dir in paths) {
            val file = File(dir, command)

            // Direct match
            if (file.exists() && file.canExecute()) {
                return file.absolutePath
            }

            // On Windows, try with common extensions
            if (isWindows) {
                for (ext in listOf(".exe", ".cmd", ".bat", ".ps1")) {
                    val fileWithExt = File(dir, command + ext)
                    if (fileWithExt.exists() && fileWithExt.canExecute()) {
                        return fileWithExt.absolutePath
                    }
                }
            }
        }

        // Also check common installation directories not in PATH
        val commonDirs = getCommonInstallDirs()
        for (dir in commonDirs) {
            val file = File(dir, command)
            if (file.exists() && file.canExecute()) {
                return file.absolutePath
            }
        }

        return null
    }

    /**
     * Get common directories where language servers might be installed.
     */
    private fun getCommonInstallDirs(): List<String> {
        val home = System.getProperty("user.home")
        val isWindows = System.getProperty("os.name").lowercase().contains("windows")
        val isMac = System.getProperty("os.name").lowercase().contains("mac")

        val dirs = mutableListOf<String>()

        // npm global
        if (isWindows) {
            dirs.add("$home\\AppData\\Roaming\\npm")
        } else {
            dirs.add("$home/.npm-global/bin")
            dirs.add("/usr/local/bin")
            dirs.add("$home/.local/bin")
        }

        // Cargo (Rust)
        dirs.add("$home/.cargo/bin")

        // Go
        dirs.add("$home/go/bin")

        // Homebrew (macOS)
        if (isMac) {
            dirs.add("/opt/homebrew/bin")
            dirs.add("/usr/local/bin")
        }

        // pip (Python)
        if (!isWindows) {
            dirs.add("$home/.local/bin")
        }

        return dirs.filter { File(it).exists() }
    }

    /**
     * Get version information for a command.
     *
     * @param command The command to check
     * @param versionFlag The flag to get version (default "--version")
     * @return Version string, or null if command not found or version check fails
     */
    suspend fun getCommandVersion(
        command: String,
        versionFlag: String = "--version"
    ): String? = withContext(Dispatchers.IO) {
        // Validate command and flag before executing
        if (!isValidCommandName(command)) {
            println("[ServerDiscovery] Invalid command name for version check: $command")
            return@withContext null
        }
        if (!isValidArgument(versionFlag)) {
            println("[ServerDiscovery] Invalid version flag rejected: $versionFlag")
            return@withContext null
        }

        val path = findCommandPath(command) ?: return@withContext null

        try {
            val process = ProcessBuilder(path, versionFlag)
                .redirectErrorStream(true)
                .start()

            val completed = process.waitFor(5, TimeUnit.SECONDS)
            if (!completed) {
                process.destroyForcibly()
                return@withContext null
            }

            val output = process.inputStream.bufferedReader().readText().trim()
            // Extract first line which usually contains version
            output.lines().firstOrNull()?.take(100)
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Validate that a command name doesn't contain shell operators or path traversal.
     * Only allows alphanumeric, dash, underscore, and dot.
     */
    private fun isValidCommandName(command: String): Boolean {
        if (command.isEmpty() || command.length > 256) return false
        return command.all { char ->
            char.isLetterOrDigit() || char == '-' || char == '_' || char == '.'
        }
    }

    /**
     * Validate that an argument doesn't contain shell operators.
     */
    private fun isValidArgument(arg: String): Boolean {
        if (arg.length > 256) return false
        val shellOperators = setOf(';', '|', '&', '$', '`', '(', ')', '{', '}', '<', '>', '!', '\n', '\r')
        return arg.none { it in shellOperators }
    }

    /**
     * Discover all available language servers from the registry.
     *
     * @return Map of language ID to availability status
     */
    suspend fun discoverAvailableServers(): Map<String, ServerAvailability> =
        withContext(Dispatchers.IO) {
            val results = mutableMapOf<String, ServerAvailability>()

            for (config in LanguageServerRegistry.getAllConfigs()) {
                val command = config.command.first()
                val available = isCommandAvailable(command)
                val path = if (available) findCommandPath(command) else null
                val version = if (available) getCommandVersion(command) else null

                results[config.languageId] = ServerAvailability(
                    config = config,
                    available = available,
                    executablePath = path,
                    version = version
                )
            }

            results
        }

    /**
     * Get installation instructions for a language server.
     *
     * @param config The server configuration
     * @return Installation instructions
     */
    fun getInstallInstructions(config: LanguageServerConfig): String {
        return when (config.id) {
            "pylsp" -> """
                Python Language Server (pylsp)

                Install with pip:
                  pip install python-lsp-server

                Or with pipx (recommended):
                  pipx install python-lsp-server
            """.trimIndent()

            "typescript-language-server", "javascript-language-server" -> """
                TypeScript Language Server

                Install with npm:
                  npm install -g typescript-language-server typescript
            """.trimIndent()

            "rust-analyzer" -> """
                Rust Analyzer

                Install with rustup (recommended):
                  rustup component add rust-analyzer

                Or download from:
                  https://github.com/rust-lang/rust-analyzer/releases
            """.trimIndent()

            "gopls" -> """
                Go Language Server (gopls)

                Install with go:
                  go install golang.org/x/tools/gopls@latest
            """.trimIndent()

            "clangd" -> """
                Clangd (C/C++ Language Server)

                macOS (Homebrew):
                  brew install llvm

                Ubuntu/Debian:
                  sudo apt install clangd

                Windows:
                  Download from https://releases.llvm.org/
            """.trimIndent()

            "kotlin-language-server" -> """
                Kotlin Language Server

                Download from:
                  https://github.com/fwcd/kotlin-language-server/releases

                Or build from source:
                  git clone https://github.com/fwcd/kotlin-language-server
                  cd kotlin-language-server
                  ./gradlew :server:installDist
            """.trimIndent()

            "jdtls" -> """
                Eclipse JDT Language Server

                Download from:
                  https://download.eclipse.org/jdtls/

                Or install via VS Code Java extension pack which includes it.
            """.trimIndent()

            "vscode-html-language-server", "vscode-css-language-server", "vscode-json-language-server" -> """
                VSCode Language Servers

                Install with npm:
                  npm install -g vscode-langservers-extracted
            """.trimIndent()

            "yaml-language-server" -> """
                YAML Language Server

                Install with npm:
                  npm install -g yaml-language-server
            """.trimIndent()

            "bash-language-server" -> """
                Bash Language Server

                Install with npm:
                  npm install -g bash-language-server
            """.trimIndent()

            "lua-language-server" -> """
                Lua Language Server

                Download from:
                  https://github.com/LuaLS/lua-language-server/releases

                Or install via package manager:
                  brew install lua-language-server (macOS)
            """.trimIndent()

            "solargraph" -> """
                Solargraph (Ruby Language Server)

                Install with gem:
                  gem install solargraph
            """.trimIndent()

            "intelephense" -> """
                Intelephense (PHP Language Server)

                Install with npm:
                  npm install -g intelephense
            """.trimIndent()

            "sourcekit-lsp" -> """
                SourceKit-LSP (Swift Language Server)

                Included with Xcode and Swift toolchain.

                Ensure Xcode Command Line Tools are installed:
                  xcode-select --install
            """.trimIndent()

            else -> """
                ${config.displayName}

                Command: ${config.command.joinToString(" ")}

                Please refer to the official documentation for installation instructions.
            """.trimIndent()
        }
    }

    /**
     * Clear the discovery cache.
     */
    fun clearCache() {
        synchronized(cacheLock) {
            availabilityCache.clear()
            pathCache.clear()
        }
    }
}

/**
 * Information about a language server's availability.
 */
data class ServerAvailability(
    /**
     * The server configuration.
     */
    val config: LanguageServerConfig,

    /**
     * Whether the server command is available.
     */
    val available: Boolean,

    /**
     * Full path to the executable, if available.
     */
    val executablePath: String? = null,

    /**
     * Version string, if available.
     */
    val version: String? = null
)
