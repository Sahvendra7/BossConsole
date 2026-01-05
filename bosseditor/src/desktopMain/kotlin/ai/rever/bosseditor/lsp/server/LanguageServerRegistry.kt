package ai.rever.bosseditor.lsp.server

import ai.rever.bosseditor.lsp.logging.LspLogger
import ai.rever.bosseditor.lsp.logging.LogCategory

/**
 * Registry of known language server configurations.
 *
 * Provides default configurations for popular language servers and allows
 * custom configurations to be added at runtime.
 *
 * ## Built-in Servers
 *
 * | Language | Server | Install Command |
 * |----------|--------|-----------------|
 * | Python | pylsp | `pip install python-lsp-server` |
 * | TypeScript/JavaScript | typescript-language-server | `npm i -g typescript-language-server typescript` |
 * | Rust | rust-analyzer | `rustup component add rust-analyzer` |
 * | Go | gopls | `go install golang.org/x/tools/gopls@latest` |
 * | Java | Eclipse JDT.LS | Manual install |
 * | C/C++ | clangd | Part of LLVM/Clang |
 * | Kotlin | kotlin-language-server | Manual install |
 * | HTML/CSS/JSON | VSCode servers | `npm i -g vscode-langservers-extracted` |
 * | YAML | yaml-language-server | `npm i -g yaml-language-server` |
 * | Bash | bash-language-server | `npm i -g bash-language-server` |
 *
 * ## Usage
 * ```kotlin
 * // Get config for a file
 * val config = LanguageServerRegistry.getConfigForFile("/path/to/file.py")
 *
 * // Get config by language ID
 * val pythonConfig = LanguageServerRegistry.getConfigForLanguage("python")
 *
 * // Add custom configuration
 * LanguageServerRegistry.register(myCustomConfig)
 * ```
 */
object LanguageServerRegistry {

    private val logger = LspLogger.forComponent("LanguageServerRegistry")

    /**
     * Map of language ID to server configuration.
     */
    private val configsByLanguage = mutableMapOf<String, LanguageServerConfig>()

    /**
     * Map of file extension to server configurations.
     * Multiple servers may handle the same extension.
     */
    private val configsByExtension = mutableMapOf<String, MutableList<LanguageServerConfig>>()

    init {
        // Register all built-in configurations
        registerBuiltinConfigs()
    }

    /**
     * Register built-in language server configurations.
     */
    private fun registerBuiltinConfigs() {
        // Python - pylsp
        register(
            LanguageServerConfig(
                id = "pylsp",
                displayName = "Python Language Server",
                languageId = "python",
                command = listOf("pylsp"),
                fileExtensions = listOf("py", "pyw", "pyi"),
                rootIndicators = listOf("pyproject.toml", "setup.py", "requirements.txt", "Pipfile")
            )
        )

        // TypeScript
        register(
            LanguageServerConfig(
                id = "typescript-language-server",
                displayName = "TypeScript Language Server",
                languageId = "typescript",
                command = listOf("typescript-language-server", "--stdio"),
                fileExtensions = listOf("ts", "tsx", "mts", "cts"),
                rootIndicators = listOf("tsconfig.json", "package.json")
            )
        )

        // JavaScript (uses same server as TypeScript)
        register(
            LanguageServerConfig(
                id = "javascript-language-server",
                displayName = "JavaScript Language Server",
                languageId = "javascript",
                command = listOf("typescript-language-server", "--stdio"),
                fileExtensions = listOf("js", "jsx", "mjs", "cjs"),
                rootIndicators = listOf("package.json", "jsconfig.json")
            )
        )

        // Rust - rust-analyzer
        register(
            LanguageServerConfig(
                id = "rust-analyzer",
                displayName = "Rust Analyzer",
                languageId = "rust",
                command = listOf("rust-analyzer"),
                fileExtensions = listOf("rs"),
                rootIndicators = listOf("Cargo.toml", "rust-project.json")
            )
        )

        // Go - gopls
        register(
            LanguageServerConfig(
                id = "gopls",
                displayName = "Go Language Server",
                languageId = "go",
                command = listOf("gopls", "serve"),
                fileExtensions = listOf("go"),
                rootIndicators = listOf("go.mod", "go.sum")
            )
        )

        // Java - Eclipse JDT.LS
        register(
            LanguageServerConfig(
                id = "jdtls",
                displayName = "Eclipse JDT Language Server",
                languageId = "java",
                command = listOf("jdtls"),
                fileExtensions = listOf("java"),
                rootIndicators = listOf("pom.xml", "build.gradle", "build.gradle.kts", ".project")
            )
        )

        // Kotlin - kotlin-language-server
        register(
            LanguageServerConfig(
                id = "kotlin-language-server",
                displayName = "Kotlin Language Server",
                languageId = "kotlin",
                command = listOf("kotlin-language-server"),
                fileExtensions = listOf("kt", "kts"),
                rootIndicators = listOf("build.gradle.kts", "build.gradle", "settings.gradle.kts")
            )
        )

        // C/C++ - clangd
        register(
            LanguageServerConfig(
                id = "clangd",
                displayName = "Clangd",
                languageId = "cpp",
                command = listOf("clangd", "--background-index"),
                fileExtensions = listOf("c", "cpp", "cc", "cxx", "h", "hpp", "hxx"),
                rootIndicators = listOf("compile_commands.json", "CMakeLists.txt", ".clangd")
            )
        )

        // HTML
        register(
            LanguageServerConfig(
                id = "vscode-html-language-server",
                displayName = "HTML Language Server",
                languageId = "html",
                command = listOf("vscode-html-language-server", "--stdio"),
                fileExtensions = listOf("html", "htm", "xhtml")
            )
        )

        // CSS
        register(
            LanguageServerConfig(
                id = "vscode-css-language-server",
                displayName = "CSS Language Server",
                languageId = "css",
                command = listOf("vscode-css-language-server", "--stdio"),
                fileExtensions = listOf("css", "scss", "sass", "less")
            )
        )

        // JSON
        register(
            LanguageServerConfig(
                id = "vscode-json-language-server",
                displayName = "JSON Language Server",
                languageId = "json",
                command = listOf("vscode-json-language-server", "--stdio"),
                fileExtensions = listOf("json", "jsonc")
            )
        )

        // YAML
        register(
            LanguageServerConfig(
                id = "yaml-language-server",
                displayName = "YAML Language Server",
                languageId = "yaml",
                command = listOf("yaml-language-server", "--stdio"),
                fileExtensions = listOf("yaml", "yml")
            )
        )

        // Bash/Shell
        register(
            LanguageServerConfig(
                id = "bash-language-server",
                displayName = "Bash Language Server",
                languageId = "shellscript",
                command = listOf("bash-language-server", "start"),
                fileExtensions = listOf("sh", "bash", "zsh"),
                filePatterns = listOf(".bashrc", ".bash_profile", ".zshrc", ".profile")
            )
        )

        // Lua - lua-language-server
        register(
            LanguageServerConfig(
                id = "lua-language-server",
                displayName = "Lua Language Server",
                languageId = "lua",
                command = listOf("lua-language-server"),
                fileExtensions = listOf("lua")
            )
        )

        // Ruby - solargraph
        register(
            LanguageServerConfig(
                id = "solargraph",
                displayName = "Ruby Solargraph",
                languageId = "ruby",
                command = listOf("solargraph", "stdio"),
                fileExtensions = listOf("rb", "rake"),
                filePatterns = listOf("Gemfile", "Rakefile"),
                rootIndicators = listOf("Gemfile", ".ruby-version")
            )
        )

        // PHP - intelephense
        register(
            LanguageServerConfig(
                id = "intelephense",
                displayName = "PHP Intelephense",
                languageId = "php",
                command = listOf("intelephense", "--stdio"),
                fileExtensions = listOf("php", "phtml"),
                rootIndicators = listOf("composer.json")
            )
        )

        // Swift - sourcekit-lsp
        register(
            LanguageServerConfig(
                id = "sourcekit-lsp",
                displayName = "Swift SourceKit-LSP",
                languageId = "swift",
                command = listOf("sourcekit-lsp"),
                fileExtensions = listOf("swift"),
                rootIndicators = listOf("Package.swift", ".xcodeproj", ".xcworkspace")
            )
        )

        // Markdown - marksman
        register(
            LanguageServerConfig(
                id = "marksman",
                displayName = "Marksman",
                languageId = "markdown",
                command = listOf("marksman", "server"),
                fileExtensions = listOf("md", "markdown")
            )
        )

        // TOML - taplo
        register(
            LanguageServerConfig(
                id = "taplo",
                displayName = "Taplo TOML Server",
                languageId = "toml",
                command = listOf("taplo", "lsp", "stdio"),
                fileExtensions = listOf("toml")
            )
        )

        // XML - lemminx
        register(
            LanguageServerConfig(
                id = "lemminx",
                displayName = "LemMinX XML Server",
                languageId = "xml",
                command = listOf("lemminx"),
                fileExtensions = listOf("xml", "xsd", "xsl", "xslt", "svg")
            )
        )

        // SQL - sql-language-server
        register(
            LanguageServerConfig(
                id = "sql-language-server",
                displayName = "SQL Language Server",
                languageId = "sql",
                command = listOf("sql-language-server", "up", "--method", "stdio"),
                fileExtensions = listOf("sql")
            )
        )

        // Docker - dockerfile-language-server
        register(
            LanguageServerConfig(
                id = "dockerfile-language-server",
                displayName = "Dockerfile Language Server",
                languageId = "dockerfile",
                command = listOf("docker-langserver", "--stdio"),
                fileExtensions = emptyList(),
                filePatterns = listOf("Dockerfile", "Dockerfile.*", "*.dockerfile")
            )
        )
    }

    /**
     * Register a language server configuration.
     *
     * @param config The configuration to register
     * @param override If true, replaces existing configuration for this language
     * @throws IllegalArgumentException if the command contains invalid characters or shell operators
     */
    fun register(config: LanguageServerConfig, override: Boolean = false) {
        // Validate command to prevent shell injection
        val validation = LanguageServerConfig.validateCommand(config.command)
        if (validation is CommandValidationResult.Invalid) {
            logger.warn(
                LogCategory.SERVER,
                "Rejecting config: ${validation.reason}",
                data = mapOf("configId" to config.id)
            )
            throw IllegalArgumentException(
                "Invalid language server command for '${config.id}': ${validation.reason}"
            )
        }

        if (!override && configsByLanguage.containsKey(config.languageId)) {
            return
        }

        configsByLanguage[config.languageId] = config

        // Index by extensions
        for (ext in config.fileExtensions) {
            configsByExtension.getOrPut(ext.lowercase()) { mutableListOf() }.apply {
                removeAll { it.languageId == config.languageId }
                add(config)
            }
        }
    }

    /**
     * Unregister a language server configuration.
     *
     * @param languageId The language ID to unregister
     */
    fun unregister(languageId: String) {
        val config = configsByLanguage.remove(languageId) ?: return

        for (ext in config.fileExtensions) {
            configsByExtension[ext.lowercase()]?.removeAll { it.languageId == languageId }
        }
    }

    /**
     * Get configuration for a file path.
     *
     * @param filePath Path to the file
     * @return The language server configuration, or null if none found
     */
    fun getConfigForFile(filePath: String): LanguageServerConfig? {
        // First try by extension
        val extension = filePath.substringAfterLast('.', "").lowercase()
        if (extension.isNotEmpty()) {
            val configs = configsByExtension[extension]
            if (!configs.isNullOrEmpty()) {
                return configs.firstOrNull { it.enabled }
            }
        }

        // Then try by file pattern
        return configsByLanguage.values
            .filter { it.enabled }
            .firstOrNull { it.handlesFile(filePath) }
    }

    /**
     * Get configuration for a language ID.
     *
     * @param languageId The LSP language identifier
     * @return The language server configuration, or null if none found
     */
    fun getConfigForLanguage(languageId: String): LanguageServerConfig? {
        return configsByLanguage[languageId]?.takeIf { it.enabled }
    }

    /**
     * Get configuration by server ID.
     *
     * @param serverId The unique server ID
     * @return The language server configuration, or null if none found
     */
    fun getConfigById(serverId: String): LanguageServerConfig? {
        return configsByLanguage.values.firstOrNull { it.id == serverId }
    }

    /**
     * Get all registered configurations.
     *
     * @return List of all registered configurations
     */
    fun getAllConfigs(): List<LanguageServerConfig> {
        return configsByLanguage.values.toList()
    }

    /**
     * Get all enabled configurations.
     *
     * @return List of enabled configurations
     */
    fun getEnabledConfigs(): List<LanguageServerConfig> {
        return configsByLanguage.values.filter { it.enabled }
    }

    /**
     * Get supported file extensions.
     *
     * @return Set of all file extensions that have language server support
     */
    fun getSupportedExtensions(): Set<String> {
        return configsByExtension.keys.toSet()
    }

    /**
     * Check if there's a language server for a file extension.
     *
     * @param extension File extension (without dot)
     * @return true if a server is available for this extension
     */
    fun hasServerForExtension(extension: String): Boolean {
        return configsByExtension[extension.lowercase()]?.any { it.enabled } == true
    }

    /**
     * Apply configuration from LspSettingsManager.
     *
     * This should be called when the configuration changes to update:
     * - Disabled built-in servers
     * - Custom servers
     * - Per-language settings
     *
     * @param disabledServers Set of language IDs for disabled built-in servers
     * @param customServers List of custom server configurations to register
     */
    fun applyConfiguration(
        disabledServers: Set<String>,
        customServers: List<ai.rever.bosseditor.lsp.config.CustomLanguageServer>
    ) {
        logger.info(
            LogCategory.SERVER,
            "Applying configuration",
            data = mapOf(
                "disabledCount" to disabledServers.size,
                "customCount" to customServers.size
            )
        )

        // Update enabled status for built-in servers
        configsByLanguage.forEach { (languageId, config) ->
            if (!config.id.endsWith("-custom")) { // Don't modify custom servers here
                val shouldBeDisabled = disabledServers.contains(languageId)
                if (config.enabled == shouldBeDisabled) {
                    configsByLanguage[languageId] = config.copy(enabled = !shouldBeDisabled)
                }
            }
        }

        // Remove old custom servers
        val customServerIds = configsByLanguage.values
            .filter { it.id.endsWith("-custom") || it.id.startsWith("custom-") }
            .map { it.languageId }

        customServerIds.forEach { languageId ->
            unregister(languageId)
        }

        // Register new custom servers
        customServers.filter { it.enabled }.forEach { customServer ->
            val config = LanguageServerConfig(
                id = customServer.id,
                displayName = customServer.displayName,
                languageId = customServer.languageId,
                command = customServer.command,
                fileExtensions = customServer.fileExtensions,
                rootIndicators = customServer.rootMarkers,
                enabled = true
            )

            try {
                register(config, override = true)
                logger.info(
                    LogCategory.SERVER,
                    "Registered custom server",
                    languageId = customServer.languageId,
                    data = mapOf("id" to customServer.id)
                )
            } catch (e: IllegalArgumentException) {
                logger.error(
                    LogCategory.SERVER,
                    "Failed to register custom server",
                    languageId = customServer.languageId,
                    error = e
                )
            }
        }
    }

    /**
     * Enable a built-in language server.
     *
     * @param languageId The language ID to enable
     */
    fun enableServer(languageId: String) {
        configsByLanguage[languageId]?.let { config ->
            if (!config.enabled) {
                configsByLanguage[languageId] = config.copy(enabled = true)
                logger.info(LogCategory.SERVER, "Enabled server", languageId = languageId)
            }
        }
    }

    /**
     * Disable a built-in language server.
     *
     * @param languageId The language ID to disable
     */
    fun disableServer(languageId: String) {
        configsByLanguage[languageId]?.let { config ->
            if (config.enabled) {
                configsByLanguage[languageId] = config.copy(enabled = false)
                logger.info(LogCategory.SERVER, "Disabled server", languageId = languageId)
            }
        }
    }

    /**
     * Get all language IDs that have servers registered.
     *
     * @return Set of language IDs
     */
    fun getRegisteredLanguages(): Set<String> {
        return configsByLanguage.keys.toSet()
    }
}
