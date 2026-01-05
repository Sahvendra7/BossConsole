package ai.rever.bosseditor.lsp.config

import ai.rever.bosseditor.lsp.logging.LogLevel
import ai.rever.bosseditor.lsp.logging.LogCategory
import kotlinx.serialization.Serializable

/**
 * Complete LSP configuration for the editor.
 *
 * This includes:
 * - Global LSP settings (enable/disable, timeouts)
 * - Per-language server configurations
 * - Custom user-defined language servers
 * - Logging configuration
 */
@Serializable
data class LspConfiguration(
    /**
     * Whether LSP support is enabled globally.
     */
    val enabled: Boolean = true,

    /**
     * Global default timeout for LSP requests in milliseconds.
     */
    val defaultRequestTimeoutMs: Long = 30_000,

    /**
     * Timeout for server initialization in milliseconds.
     */
    val initializeTimeoutMs: Long = 60_000,

    /**
     * Maximum pending requests before rejecting new ones.
     */
    val maxPendingRequests: Int = 100,

    /**
     * Whether to trace LSP messages (for debugging).
     */
    val traceMessages: Boolean = false,

    /**
     * Language-specific configurations (keyed by language ID).
     */
    val languageConfigs: Map<String, LanguageServerConfiguration> = emptyMap(),

    /**
     * Custom user-defined language servers.
     */
    val customServers: List<CustomLanguageServer> = emptyList(),

    /**
     * Disabled built-in language servers (by language ID).
     */
    val disabledServers: Set<String> = emptySet(),

    /**
     * Logging configuration.
     */
    val logging: LspLoggingConfiguration = LspLoggingConfiguration()
)

/**
 * Configuration for a specific language server.
 */
@Serializable
data class LanguageServerConfiguration(
    /**
     * Language ID (e.g., "python", "kotlin").
     */
    val languageId: String,

    /**
     * Whether this language server is enabled.
     */
    val enabled: Boolean = true,

    /**
     * Override command to start the server (null = use default).
     */
    val commandOverride: List<String>? = null,

    /**
     * Additional environment variables for the server process.
     */
    val environment: Map<String, String> = emptyMap(),

    /**
     * Override request timeout for this server.
     */
    val requestTimeoutMs: Long? = null,

    /**
     * Override initialization timeout for this server.
     */
    val initializeTimeoutMs: Long? = null,

    /**
     * Custom initialization options sent to the server.
     */
    val initializationOptions: Map<String, String> = emptyMap(),

    /**
     * Server-specific settings (sent via workspace/didChangeConfiguration).
     */
    val settings: Map<String, String> = emptyMap(),

    /**
     * File extensions to associate with this server (overrides defaults).
     */
    val fileExtensions: List<String>? = null,

    /**
     * Root markers for workspace detection (e.g., "setup.py", "pyproject.toml").
     */
    val rootMarkers: List<String>? = null
)

/**
 * Custom user-defined language server.
 */
@Serializable
data class CustomLanguageServer(
    /**
     * Unique identifier for this server.
     */
    val id: String,

    /**
     * Display name shown in UI.
     */
    val displayName: String,

    /**
     * Language ID this server handles.
     */
    val languageId: String,

    /**
     * Command to start the server.
     */
    val command: List<String>,

    /**
     * File extensions this server handles.
     */
    val fileExtensions: List<String>,

    /**
     * Whether this server is enabled.
     */
    val enabled: Boolean = true,

    /**
     * Environment variables for the server process.
     */
    val environment: Map<String, String> = emptyMap(),

    /**
     * Request timeout in milliseconds.
     */
    val requestTimeoutMs: Long = 30_000,

    /**
     * Initialization timeout in milliseconds.
     */
    val initializeTimeoutMs: Long = 60_000,

    /**
     * Custom initialization options.
     */
    val initializationOptions: Map<String, String> = emptyMap(),

    /**
     * Server settings.
     */
    val settings: Map<String, String> = emptyMap(),

    /**
     * Root markers for workspace detection.
     */
    val rootMarkers: List<String> = emptyList(),

    /**
     * Description of this server.
     */
    val description: String = ""
)

/**
 * Logging configuration for LSP.
 */
@Serializable
data class LspLoggingConfiguration(
    /**
     * Global log level.
     */
    val globalLevel: String = "INFO",

    /**
     * Per-category log levels.
     */
    val categoryLevels: Map<String, String> = emptyMap(),

    /**
     * Whether file logging is enabled.
     */
    val fileLoggingEnabled: Boolean = false,

    /**
     * Path to log file (null = default location).
     */
    val logFilePath: String? = null,

    /**
     * Maximum log file size in bytes before rotation.
     */
    val maxLogFileSizeBytes: Long = 10 * 1024 * 1024, // 10MB

    /**
     * Number of rotated log files to keep.
     */
    val maxLogFiles: Int = 5
) {
    /**
     * Convert to LogLevel enum.
     */
    fun getGlobalLogLevel(): LogLevel = LogLevel.fromString(globalLevel)

    /**
     * Get category log levels as enum map.
     */
    fun getCategoryLogLevels(): Map<LogCategory, LogLevel> {
        return categoryLevels.mapNotNull { (key, value) ->
            try {
                LogCategory.valueOf(key.uppercase()) to LogLevel.fromString(value)
            } catch (e: IllegalArgumentException) {
                null
            }
        }.toMap()
    }
}

/**
 * Predefined language server templates for easy configuration.
 */
object LanguageServerTemplates {
    val PYTHON_PYLSP = CustomLanguageServer(
        id = "pylsp-custom",
        displayName = "Python (pylsp)",
        languageId = "python",
        command = listOf("pylsp"),
        fileExtensions = listOf("py", "pyw", "pyi"),
        rootMarkers = listOf("setup.py", "pyproject.toml", "requirements.txt", ".git"),
        description = "Python Language Server (pylsp)"
    )

    val PYTHON_PYRIGHT = CustomLanguageServer(
        id = "pyright-custom",
        displayName = "Python (Pyright)",
        languageId = "python",
        command = listOf("pyright-langserver", "--stdio"),
        fileExtensions = listOf("py", "pyw", "pyi"),
        rootMarkers = listOf("pyrightconfig.json", "pyproject.toml", ".git"),
        description = "Microsoft Pyright Language Server"
    )

    val TYPESCRIPT = CustomLanguageServer(
        id = "typescript-custom",
        displayName = "TypeScript/JavaScript",
        languageId = "typescript",
        command = listOf("typescript-language-server", "--stdio"),
        fileExtensions = listOf("ts", "tsx", "js", "jsx"),
        rootMarkers = listOf("tsconfig.json", "package.json", ".git"),
        description = "TypeScript Language Server"
    )

    val RUST_ANALYZER = CustomLanguageServer(
        id = "rust-analyzer-custom",
        displayName = "Rust (rust-analyzer)",
        languageId = "rust",
        command = listOf("rust-analyzer"),
        fileExtensions = listOf("rs"),
        rootMarkers = listOf("Cargo.toml", ".git"),
        description = "Rust Analyzer Language Server"
    )

    val GO_GOPLS = CustomLanguageServer(
        id = "gopls-custom",
        displayName = "Go (gopls)",
        languageId = "go",
        command = listOf("gopls", "serve"),
        fileExtensions = listOf("go", "mod"),
        rootMarkers = listOf("go.mod", "go.sum", ".git"),
        description = "Go Language Server (gopls)"
    )

    val KOTLIN = CustomLanguageServer(
        id = "kotlin-custom",
        displayName = "Kotlin",
        languageId = "kotlin",
        command = listOf("kotlin-language-server"),
        fileExtensions = listOf("kt", "kts"),
        rootMarkers = listOf("build.gradle.kts", "build.gradle", "settings.gradle.kts", ".git"),
        description = "Kotlin Language Server"
    )

    val JAVA_JDTLS = CustomLanguageServer(
        id = "jdtls-custom",
        displayName = "Java (Eclipse JDT)",
        languageId = "java",
        command = listOf("jdtls"),
        fileExtensions = listOf("java"),
        rootMarkers = listOf("pom.xml", "build.gradle", ".git"),
        description = "Eclipse JDT Language Server"
    )

    val CLANGD = CustomLanguageServer(
        id = "clangd-custom",
        displayName = "C/C++ (clangd)",
        languageId = "cpp",
        command = listOf("clangd"),
        fileExtensions = listOf("c", "cpp", "cc", "cxx", "h", "hpp", "hxx"),
        rootMarkers = listOf("compile_commands.json", "CMakeLists.txt", ".git"),
        description = "Clang Language Server"
    )

    /**
     * All available templates.
     */
    val ALL = listOf(
        PYTHON_PYLSP,
        PYTHON_PYRIGHT,
        TYPESCRIPT,
        RUST_ANALYZER,
        GO_GOPLS,
        KOTLIN,
        JAVA_JDTLS,
        CLANGD
    )

    /**
     * Get template by ID.
     */
    fun getById(id: String): CustomLanguageServer? = ALL.find { it.id == id }

    /**
     * Get templates for a language.
     */
    fun getForLanguage(languageId: String): List<CustomLanguageServer> =
        ALL.filter { it.languageId == languageId }
}
