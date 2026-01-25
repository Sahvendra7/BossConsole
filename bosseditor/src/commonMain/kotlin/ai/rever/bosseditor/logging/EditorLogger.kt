package ai.rever.bosseditor.logging

/**
 * Log levels for editor logging.
 */
enum class EditorLogLevel(val priority: Int) {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4)
}

/**
 * Log category for filtering and organizing logs.
 */
enum class EditorLogCategory {
    TRANSPORT,      // LSP transport layer
    PROTOCOL,       // LSP protocol messages
    SERVER,         // Server lifecycle
    DOCUMENT,       // Document synchronization
    COMPLETION,     // Code completion
    DIAGNOSTICS,    // Diagnostics/errors
    NAVIGATION,     // Go to definition, references
    SEMANTIC,       // Semantic tokens
    EDITOR,         // Editor core functionality
    GENERAL         // General/uncategorized
}

/**
 * Multiplatform logger for bosseditor module.
 *
 * On desktop, delegates to LspLogger for full SLF4J integration.
 * On other platforms, provides basic console logging.
 *
 * ## Usage
 * ```kotlin
 * private val logger = EditorLogger.forComponent("MyComponent")
 *
 * logger.info(EditorLogCategory.DOCUMENT, "Document opened")
 * logger.error(EditorLogCategory.PROTOCOL, "Request failed", error = exception)
 * ```
 */
expect object EditorLogger {
    /**
     * Create a component-specific logger.
     */
    fun forComponent(componentName: String): EditorComponentLogger
}

/**
 * Component-specific logger for convenient logging.
 */
expect class EditorComponentLogger {
    fun trace(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>? = null
    )

    fun debug(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>? = null
    )

    fun info(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>? = null
    )

    fun warn(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>? = null,
        error: Throwable? = null
    )

    fun error(
        category: EditorLogCategory,
        message: String,
        data: Map<String, Any?>? = null,
        error: Throwable? = null
    )
}
