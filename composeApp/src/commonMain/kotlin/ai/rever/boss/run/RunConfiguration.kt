package ai.rever.boss.run

import kotlinx.serialization.Serializable

/**
 * Represents a run configuration for executing code.
 */
@Serializable
data class RunConfiguration(
    val id: String,
    val name: String,
    val type: RunConfigurationType,
    val filePath: String,
    val lineNumber: Int,
    val language: Language,
    val command: String,
    val workingDirectory: String,
    val environmentVariables: Map<String, String> = emptyMap(),
    val arguments: String = "",
    val isAutoDetected: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Types of run configurations.
 */
@Serializable
enum class RunConfigurationType {
    MAIN_FUNCTION,
    SCRIPT,
    TEST,
    CUSTOM
}

/**
 * Supported programming languages for run detection.
 */
@Serializable
enum class Language(val displayName: String, val extensions: List<String>) {
    KOTLIN("Kotlin", listOf("kt", "kts")),
    JAVA("Java", listOf("java")),
    PYTHON("Python", listOf("py")),
    JAVASCRIPT("JavaScript", listOf("js", "jsx", "mjs")),
    TYPESCRIPT("TypeScript", listOf("ts", "tsx")),
    GO("Go", listOf("go")),
    RUST("Rust", listOf("rs")),
    UNKNOWN("Unknown", emptyList());

    companion object {
        fun fromExtension(extension: String): Language {
            return entries.find { it.extensions.contains(extension.lowercase()) } ?: UNKNOWN
        }

        fun fromFileName(fileName: String): Language {
            val extension = fileName.substringAfterLast('.', "")
            return fromExtension(extension)
        }
    }
}

/**
 * Represents a detected main function in source code.
 */
data class DetectedMainFunction(
    val lineNumber: Int,
    val functionName: String,
    val className: String?,
    val packageName: String?,
    val language: Language,
    val filePath: String
) {
    /**
     * Creates a display name for this detected function.
     */
    fun toDisplayName(): String {
        return when {
            className != null && packageName != null -> "$packageName.$className.$functionName"
            className != null -> "$className.$functionName"
            packageName != null -> "$packageName.$functionName"
            else -> functionName
        }
    }

    /**
     * Creates a short display name for UI.
     */
    fun toShortName(): String {
        val fileName = filePath.substringAfterLast('/')
        return when {
            className != null -> "$className.$functionName (${fileName})"
            else -> "$functionName (${fileName})"
        }
    }
}

/**
 * Settings for run configurations, persisted to disk.
 */
@Serializable
data class RunConfigurationSettings(
    val configurations: List<RunConfiguration> = emptyList(),
    val lastUsedConfigId: String? = null,
    val recentConfigIds: List<String> = emptyList(),
    val maxRecentConfigs: Int = 10
)

/**
 * Status of a running process.
 */
enum class ProcessStatus {
    STARTING,
    RUNNING,
    STOPPING,
    STOPPED,
    FAILED
}

/**
 * Represents a currently running process.
 */
data class RunningProcess(
    val id: String,
    val configId: String,
    val configName: String,
    val command: String,
    val startTime: Long,
    val status: ProcessStatus
)
