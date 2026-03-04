package ai.rever.bosseditor.lsp.config

import ai.rever.boss.plugin.pathutils.BossDirectories
import ai.rever.bosseditor.lsp.logging.LspLogger
import ai.rever.bosseditor.lsp.logging.LspLoggerConfig
import ai.rever.bosseditor.lsp.logging.LogCategory
import ai.rever.bosseditor.lsp.server.LanguageServerRegistry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Manages LSP configuration persistence and runtime updates.
 *
 * Configuration is stored in `~/.boss/lsp-settings.json`.
 *
 * Features:
 * - Load/save configuration from disk
 * - Runtime configuration updates via StateFlow
 * - Merge user config with built-in defaults
 * - Validation of configuration
 *
 * ## Usage
 * ```kotlin
 * val manager = LspSettingsManager.instance
 *
 * // Get current configuration
 * val config = manager.configuration.value
 *
 * // Update configuration
 * manager.updateConfiguration { current ->
 *     current.copy(enabled = false)
 * }
 *
 * // Add custom server
 * manager.addCustomServer(CustomLanguageServer(...))
 *
 * // Listen to changes
 * manager.configuration.collect { config ->
 *     applyConfiguration(config)
 * }
 * ```
 */
class LspSettingsManager private constructor() {
    private val logger = LspLogger.forComponent("LspSettingsManager")

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _configuration = MutableStateFlow(LspConfiguration())
    val configuration: StateFlow<LspConfiguration> = _configuration.asStateFlow()

    private val settingsFile: File by lazy {
        BossDirectories.resolve("lsp-settings.json")
    }

    private val logDir: File by lazy {
        BossDirectories.resolve("logs").also { it.mkdirs() }
    }

    init {
        loadConfiguration()
    }

    /**
     * Load configuration from disk.
     */
    fun loadConfiguration() {
        try {
            if (settingsFile.exists()) {
                val content = settingsFile.readText()
                val config = json.decodeFromString<LspConfiguration>(content)
                _configuration.value = config
                applyLoggingConfig(config.logging)
                applyToRegistry(config)
                logger.info(LogCategory.GENERAL, "Configuration loaded from ${settingsFile.absolutePath}")
            } else {
                logger.info(LogCategory.GENERAL, "No configuration file found, using defaults")
                saveConfiguration() // Create default config file
            }
        } catch (e: Exception) {
            logger.error(
                LogCategory.GENERAL,
                "Failed to load configuration, using defaults",
                error = e
            )
            _configuration.value = LspConfiguration()
        }
    }

    /**
     * Save configuration to disk.
     */
    fun saveConfiguration() {
        scope.launch {
            try {
                val content = json.encodeToString(_configuration.value)
                settingsFile.writeText(content)
                logger.debug(LogCategory.GENERAL, "Configuration saved to ${settingsFile.absolutePath}")
            } catch (e: Exception) {
                logger.error(LogCategory.GENERAL, "Failed to save configuration", error = e)
            }
        }
    }

    /**
     * Update configuration atomically.
     */
    fun updateConfiguration(update: (LspConfiguration) -> LspConfiguration) {
        val newConfig = update(_configuration.value)
        _configuration.value = newConfig
        applyLoggingConfig(newConfig.logging)
        applyToRegistry(newConfig)
        saveConfiguration()
    }

    /**
     * Reset configuration to defaults.
     */
    fun resetToDefaults() {
        _configuration.value = LspConfiguration()
        applyLoggingConfig(_configuration.value.logging)
        applyToRegistry(_configuration.value)
        saveConfiguration()
        logger.info(LogCategory.GENERAL, "Configuration reset to defaults")
    }

    /**
     * Enable or disable LSP globally.
     */
    fun setEnabled(enabled: Boolean) {
        updateConfiguration { it.copy(enabled = enabled) }
    }

    /**
     * Set global request timeout.
     */
    fun setRequestTimeout(timeoutMs: Long) {
        updateConfiguration { it.copy(defaultRequestTimeoutMs = timeoutMs) }
    }

    /**
     * Enable or disable message tracing.
     */
    fun setTraceMessages(enabled: Boolean) {
        updateConfiguration { it.copy(traceMessages = enabled) }
    }

    /**
     * Add or update a language-specific configuration.
     */
    fun setLanguageConfig(languageId: String, config: LanguageServerConfiguration) {
        updateConfiguration { current ->
            current.copy(
                languageConfigs = current.languageConfigs + (languageId to config)
            )
        }
    }

    /**
     * Remove a language-specific configuration.
     */
    fun removeLanguageConfig(languageId: String) {
        updateConfiguration { current ->
            current.copy(
                languageConfigs = current.languageConfigs - languageId
            )
        }
    }

    /**
     * Add a custom language server.
     */
    fun addCustomServer(server: CustomLanguageServer): Result<Unit> {
        // Validate
        val validation = validateCustomServer(server)
        if (validation.isFailure) {
            return validation
        }

        updateConfiguration { current ->
            val existingIndex = current.customServers.indexOfFirst { it.id == server.id }
            val newServers = if (existingIndex >= 0) {
                current.customServers.toMutableList().apply {
                    set(existingIndex, server)
                }
            } else {
                current.customServers + server
            }
            current.copy(customServers = newServers)
        }

        logger.info(
            LogCategory.SERVER,
            "Added custom server: ${server.displayName}",
            languageId = server.languageId
        )
        return Result.success(Unit)
    }

    /**
     * Remove a custom language server.
     */
    fun removeCustomServer(serverId: String) {
        updateConfiguration { current ->
            current.copy(
                customServers = current.customServers.filter { it.id != serverId }
            )
        }
        logger.info(LogCategory.SERVER, "Removed custom server: $serverId")
    }

    /**
     * Enable or disable a custom server.
     */
    fun setCustomServerEnabled(serverId: String, enabled: Boolean) {
        updateConfiguration { current ->
            current.copy(
                customServers = current.customServers.map { server ->
                    if (server.id == serverId) server.copy(enabled = enabled) else server
                }
            )
        }
    }

    /**
     * Disable a built-in language server.
     */
    fun disableBuiltInServer(languageId: String) {
        updateConfiguration { current ->
            current.copy(
                disabledServers = current.disabledServers + languageId
            )
        }
        logger.info(LogCategory.SERVER, "Disabled built-in server for: $languageId")
    }

    /**
     * Enable a built-in language server.
     */
    fun enableBuiltInServer(languageId: String) {
        updateConfiguration { current ->
            current.copy(
                disabledServers = current.disabledServers - languageId
            )
        }
        logger.info(LogCategory.SERVER, "Enabled built-in server for: $languageId")
    }

    /**
     * Check if a built-in server is disabled.
     */
    fun isBuiltInServerDisabled(languageId: String): Boolean {
        return _configuration.value.disabledServers.contains(languageId)
    }

    /**
     * Update logging configuration.
     */
    fun setLoggingConfig(config: LspLoggingConfiguration) {
        updateConfiguration { current ->
            current.copy(logging = config)
        }
    }

    /**
     * Enable file logging.
     */
    fun enableFileLogging(filePath: String? = null) {
        val path = filePath ?: File(logDir, "lsp.log").absolutePath
        updateConfiguration { current ->
            current.copy(
                logging = current.logging.copy(
                    fileLoggingEnabled = true,
                    logFilePath = path
                )
            )
        }
    }

    /**
     * Disable file logging.
     */
    fun disableFileLogging() {
        updateConfiguration { current ->
            current.copy(
                logging = current.logging.copy(fileLoggingEnabled = false)
            )
        }
    }

    /**
     * Set the global log level.
     */
    fun setLogLevel(level: String) {
        updateConfiguration { current ->
            current.copy(
                logging = current.logging.copy(globalLevel = level)
            )
        }
    }

    /**
     * Get effective configuration for a language server.
     */
    fun getEffectiveServerConfig(
        languageId: String,
        defaultCommand: List<String>,
        defaultTimeout: Long
    ): EffectiveServerConfig {
        val config = _configuration.value
        val languageConfig = config.languageConfigs[languageId]

        return EffectiveServerConfig(
            command = languageConfig?.commandOverride ?: defaultCommand,
            environment = languageConfig?.environment ?: emptyMap(),
            requestTimeoutMs = languageConfig?.requestTimeoutMs
                ?: config.defaultRequestTimeoutMs,
            initializeTimeoutMs = languageConfig?.initializeTimeoutMs
                ?: config.initializeTimeoutMs,
            initializationOptions = languageConfig?.initializationOptions ?: emptyMap(),
            settings = languageConfig?.settings ?: emptyMap()
        )
    }

    /**
     * Get all custom servers for a language.
     */
    fun getCustomServersForLanguage(languageId: String): List<CustomLanguageServer> {
        return _configuration.value.customServers.filter {
            it.languageId == languageId && it.enabled
        }
    }

    /**
     * Get all enabled custom servers.
     */
    fun getAllEnabledCustomServers(): List<CustomLanguageServer> {
        return _configuration.value.customServers.filter { it.enabled }
    }

    /**
     * Validate a custom server configuration.
     */
    private fun validateCustomServer(server: CustomLanguageServer): Result<Unit> {
        if (server.id.isBlank()) {
            return Result.failure(IllegalArgumentException("Server ID cannot be blank"))
        }
        if (server.displayName.isBlank()) {
            return Result.failure(IllegalArgumentException("Display name cannot be blank"))
        }
        if (server.languageId.isBlank()) {
            return Result.failure(IllegalArgumentException("Language ID cannot be blank"))
        }
        if (server.command.isEmpty()) {
            return Result.failure(IllegalArgumentException("Command cannot be empty"))
        }
        if (server.fileExtensions.isEmpty()) {
            return Result.failure(IllegalArgumentException("File extensions cannot be empty"))
        }
        // Validate command doesn't contain shell operators
        val shellOperators = setOf(';', '|', '&', '$', '`', '(', ')', '{', '}', '<', '>', '!')
        for (part in server.command) {
            if (part.any { it in shellOperators }) {
                return Result.failure(IllegalArgumentException("Command contains invalid shell operators"))
            }
        }
        return Result.success(Unit)
    }

    /**
     * Apply logging configuration to LspLogger.
     */
    private fun applyLoggingConfig(config: LspLoggingConfiguration) {
        val loggerConfig = LspLoggerConfig(
            globalLevel = config.getGlobalLogLevel(),
            categoryLevels = config.getCategoryLogLevels(),
            fileLoggingEnabled = config.fileLoggingEnabled,
            logFilePath = config.logFilePath ?: File(logDir, "lsp.log").absolutePath
        )
        LspLogger.configure(loggerConfig)
    }

    /**
     * Apply configuration to LanguageServerRegistry.
     */
    private fun applyToRegistry(config: LspConfiguration) {
        LanguageServerRegistry.applyConfiguration(
            disabledServers = config.disabledServers,
            customServers = config.customServers
        )
    }

    /**
     * Export configuration to a file.
     */
    fun exportConfiguration(file: File) {
        try {
            val content = json.encodeToString(_configuration.value)
            file.writeText(content)
            logger.info(LogCategory.GENERAL, "Configuration exported to ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error(LogCategory.GENERAL, "Failed to export configuration", error = e)
            throw e
        }
    }

    /**
     * Import configuration from a file.
     */
    fun importConfiguration(file: File) {
        try {
            val content = file.readText()
            val config = json.decodeFromString<LspConfiguration>(content)
            _configuration.value = config
            applyLoggingConfig(config.logging)
            applyToRegistry(config)
            saveConfiguration()
            logger.info(LogCategory.GENERAL, "Configuration imported from ${file.absolutePath}")
        } catch (e: Exception) {
            logger.error(LogCategory.GENERAL, "Failed to import configuration", error = e)
            throw e
        }
    }

    companion object {
        /**
         * Singleton instance.
         */
        val instance: LspSettingsManager by lazy { LspSettingsManager() }
    }
}

/**
 * Effective configuration for a language server after merging defaults and overrides.
 */
data class EffectiveServerConfig(
    val command: List<String>,
    val environment: Map<String, String>,
    val requestTimeoutMs: Long,
    val initializeTimeoutMs: Long,
    val initializationOptions: Map<String, String>,
    val settings: Map<String, String>
)
