package ai.rever.bosseditor.lsp.logging

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Log levels for LSP logging.
 */
enum class LogLevel(val priority: Int) {
    TRACE(0),
    DEBUG(1),
    INFO(2),
    WARN(3),
    ERROR(4),
    OFF(5);

    companion object {
        fun fromString(value: String): LogLevel {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: INFO
        }
    }
}

/**
 * Log category for filtering and organizing logs.
 */
enum class LogCategory {
    TRANSPORT,      // LSP transport layer (JSON-RPC framing)
    PROTOCOL,       // LSP protocol messages
    SERVER,         // Server lifecycle
    DOCUMENT,       // Document synchronization
    COMPLETION,     // Code completion
    DIAGNOSTICS,    // Diagnostics/errors
    NAVIGATION,     // Go to definition, references
    SEMANTIC,       // Semantic tokens
    GENERAL         // General/uncategorized
}

/**
 * Log entry for structured logging.
 */
data class LogEntry(
    val timestamp: LocalDateTime,
    val level: LogLevel,
    val category: LogCategory,
    val component: String,
    val message: String,
    val languageId: String? = null,
    val data: Map<String, Any?>? = null,
    val error: Throwable? = null
)

/**
 * Listener for log events.
 */
fun interface LogListener {
    fun onLog(entry: LogEntry)
}

/**
 * Central LSP logging facility with structured logging support.
 *
 * Features:
 * - Configurable log levels (global and per-category)
 * - Structured log entries with metadata
 * - Log listeners for UI integration
 * - File logging support
 * - SLF4J integration
 *
 * ## Usage
 * ```kotlin
 * // Get a logger for a component
 * val logger = LspLogger.forComponent("DesktopLspClient")
 *
 * // Log messages
 * logger.info(LogCategory.SERVER, "Server started", languageId = "python")
 * logger.error(LogCategory.TRANSPORT, "Connection failed", error = exception)
 * logger.debug(LogCategory.PROTOCOL, "Request sent", data = mapOf("method" to "initialize"))
 * ```
 */
object LspLogger {
    private val slf4jLogger: Logger = LoggerFactory.getLogger("ai.rever.bosseditor.lsp")

    /** Global log level - messages below this level are ignored */
    @Volatile
    var globalLevel: LogLevel = LogLevel.INFO
        private set

    /** Per-category log levels (override global) */
    private val categoryLevels = mutableMapOf<LogCategory, LogLevel>()
    private val categoryLock = Any()

    /** Log listeners for UI integration */
    private val listeners = mutableListOf<LogListener>()
    private val listenersLock = Any()

    /** File logging */
    private var logFile: File? = null
    private var fileLoggingEnabled = false
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    /** Maximum log entries to keep in memory (for UI) */
    private const val MAX_LOG_ENTRIES = 1000
    private val recentLogs = ArrayDeque<LogEntry>(MAX_LOG_ENTRIES)
    private val recentLogsLock = Any()

    /**
     * Configure the logger.
     */
    fun configure(config: LspLoggerConfig) {
        globalLevel = config.globalLevel

        synchronized(categoryLock) {
            categoryLevels.clear()
            categoryLevels.putAll(config.categoryLevels)
        }

        if (config.fileLoggingEnabled && config.logFilePath != null) {
            enableFileLogging(File(config.logFilePath))
        } else {
            disableFileLogging()
        }
    }

    /**
     * Set the global log level.
     */
    fun setGlobalLevel(level: LogLevel) {
        globalLevel = level
    }

    /**
     * Set log level for a specific category.
     */
    fun setCategoryLevel(category: LogCategory, level: LogLevel) {
        synchronized(categoryLock) {
            categoryLevels[category] = level
        }
    }

    /**
     * Clear category-specific log level (use global).
     */
    fun clearCategoryLevel(category: LogCategory) {
        synchronized(categoryLock) {
            categoryLevels.remove(category)
        }
    }

    /**
     * Get effective log level for a category.
     */
    fun getEffectiveLevel(category: LogCategory): LogLevel {
        return synchronized(categoryLock) {
            categoryLevels[category] ?: globalLevel
        }
    }

    /**
     * Enable file logging.
     */
    fun enableFileLogging(file: File) {
        try {
            file.parentFile?.mkdirs()
            logFile = file
            fileLoggingEnabled = true
        } catch (e: Exception) {
            slf4jLogger.warn("Failed to enable file logging: ${e.message}")
        }
    }

    /**
     * Disable file logging.
     */
    fun disableFileLogging() {
        fileLoggingEnabled = false
        logFile = null
    }

    /**
     * Add a log listener.
     */
    fun addListener(listener: LogListener) {
        synchronized(listenersLock) {
            listeners.add(listener)
        }
    }

    /**
     * Remove a log listener.
     */
    fun removeListener(listener: LogListener) {
        synchronized(listenersLock) {
            listeners.remove(listener)
        }
    }

    /**
     * Get recent log entries.
     */
    fun getRecentLogs(
        limit: Int = 100,
        category: LogCategory? = null,
        minLevel: LogLevel = LogLevel.TRACE
    ): List<LogEntry> {
        return synchronized(recentLogsLock) {
            recentLogs
                .filter { entry ->
                    entry.level.priority >= minLevel.priority &&
                    (category == null || entry.category == category)
                }
                .takeLast(limit)
        }
    }

    /**
     * Clear recent logs.
     */
    fun clearLogs() {
        synchronized(recentLogsLock) {
            recentLogs.clear()
        }
    }

    /**
     * Log a message.
     */
    internal fun log(entry: LogEntry) {
        val effectiveLevel = getEffectiveLevel(entry.category)
        if (entry.level.priority < effectiveLevel.priority) {
            return
        }

        // Store in recent logs
        synchronized(recentLogsLock) {
            if (recentLogs.size >= MAX_LOG_ENTRIES) {
                recentLogs.removeFirst()
            }
            recentLogs.addLast(entry)
        }

        // Format message for SLF4J
        val formattedMessage = buildString {
            append("[${entry.category}]")
            if (entry.languageId != null) {
                append("[${entry.languageId}]")
            }
            append(" ${entry.component}: ${entry.message}")
            if (entry.data != null) {
                append(" | ${entry.data}")
            }
        }

        // Log to SLF4J
        when (entry.level) {
            LogLevel.TRACE -> slf4jLogger.trace(formattedMessage, entry.error)
            LogLevel.DEBUG -> slf4jLogger.debug(formattedMessage, entry.error)
            LogLevel.INFO -> slf4jLogger.info(formattedMessage, entry.error)
            LogLevel.WARN -> slf4jLogger.warn(formattedMessage, entry.error)
            LogLevel.ERROR -> slf4jLogger.error(formattedMessage, entry.error)
            LogLevel.OFF -> { /* no-op */ }
        }

        // Log to file
        if (fileLoggingEnabled) {
            writeToFile(entry)
        }

        // Notify listeners
        notifyListeners(entry)
    }

    private fun writeToFile(entry: LogEntry) {
        val file = logFile ?: return
        try {
            val line = buildString {
                append(entry.timestamp.format(dateFormatter))
                append(" [${entry.level.name.padEnd(5)}]")
                append(" [${entry.category.name}]")
                if (entry.languageId != null) {
                    append(" [${entry.languageId}]")
                }
                append(" ${entry.component}: ${entry.message}")
                if (entry.data != null) {
                    append(" | ${entry.data}")
                }
                if (entry.error != null) {
                    append("\n  Exception: ${entry.error.message}")
                    entry.error.stackTrace.take(10).forEach { frame ->
                        append("\n    at $frame")
                    }
                }
                append("\n")
            }
            file.appendText(line)
        } catch (e: Exception) {
            // Avoid recursive logging
            slf4jLogger.warn("Failed to write to log file: ${e.message}")
        }
    }

    private fun notifyListeners(entry: LogEntry) {
        val listenersCopy = synchronized(listenersLock) {
            listeners.toList()
        }
        listenersCopy.forEach { listener ->
            try {
                listener.onLog(entry)
            } catch (e: Exception) {
                // Avoid recursive logging
                slf4jLogger.warn("Log listener error: ${e.message}")
            }
        }
    }

    /**
     * Create a component-specific logger.
     */
    fun forComponent(componentName: String): ComponentLogger {
        return ComponentLogger(componentName)
    }
}

/**
 * Configuration for LspLogger.
 */
data class LspLoggerConfig(
    val globalLevel: LogLevel = LogLevel.INFO,
    val categoryLevels: Map<LogCategory, LogLevel> = emptyMap(),
    val fileLoggingEnabled: Boolean = false,
    val logFilePath: String? = null
)

/**
 * Component-specific logger for convenient logging.
 */
class ComponentLogger(private val componentName: String) {

    fun trace(
        category: LogCategory,
        message: String,
        languageId: String? = null,
        data: Map<String, Any?>? = null
    ) {
        log(LogLevel.TRACE, category, message, languageId, data, null)
    }

    fun debug(
        category: LogCategory,
        message: String,
        languageId: String? = null,
        data: Map<String, Any?>? = null
    ) {
        log(LogLevel.DEBUG, category, message, languageId, data, null)
    }

    fun info(
        category: LogCategory,
        message: String,
        languageId: String? = null,
        data: Map<String, Any?>? = null
    ) {
        log(LogLevel.INFO, category, message, languageId, data, null)
    }

    fun warn(
        category: LogCategory,
        message: String,
        languageId: String? = null,
        data: Map<String, Any?>? = null,
        error: Throwable? = null
    ) {
        log(LogLevel.WARN, category, message, languageId, data, error)
    }

    fun error(
        category: LogCategory,
        message: String,
        languageId: String? = null,
        data: Map<String, Any?>? = null,
        error: Throwable? = null
    ) {
        log(LogLevel.ERROR, category, message, languageId, data, error)
    }

    private fun log(
        level: LogLevel,
        category: LogCategory,
        message: String,
        languageId: String?,
        data: Map<String, Any?>?,
        error: Throwable?
    ) {
        val entry = LogEntry(
            timestamp = LocalDateTime.now(),
            level = level,
            category = category,
            component = componentName,
            message = message,
            languageId = languageId,
            data = data,
            error = error
        )
        LspLogger.log(entry)
    }
}
