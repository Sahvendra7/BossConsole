package ai.rever.bosseditor.lsp.server

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/**
 * Configuration for a language server.
 *
 * Defines how to start and communicate with a language server for a specific language.
 *
 * @property id Unique identifier for this server configuration
 * @property displayName Human-readable name for display in UI
 * @property languageId LSP language identifier (e.g., "python", "kotlin")
 * @property command Command to start the server (e.g., ["pylsp"] or ["node", "server.js", "--stdio"])
 * @property fileExtensions List of file extensions this server handles (without dots)
 * @property filePatterns Optional glob patterns for more complex file matching
 * @property initializationOptions Server-specific initialization options
 * @property settings Server-specific settings to send after initialization
 * @property rootIndicators Files that indicate a project root (e.g., "pyproject.toml", "Cargo.toml")
 * @property enabled Whether this server configuration is enabled
 */
@Serializable
data class LanguageServerConfig(
    val id: String,
    val displayName: String,
    val languageId: String,
    val command: List<String>,
    val fileExtensions: List<String>,
    val filePatterns: List<String> = emptyList(),
    val initializationOptions: JsonElement? = null,
    val settings: JsonElement? = null,
    val rootIndicators: List<String> = emptyList(),
    val enabled: Boolean = true
) {
    /**
     * Checks if this server can handle a file with the given extension.
     *
     * @param extension File extension (without dot, e.g., "py" not ".py")
     * @return true if this server handles this extension
     */
    fun handlesExtension(extension: String): Boolean {
        return fileExtensions.any { it.equals(extension, ignoreCase = true) }
    }

    /**
     * Checks if this server can handle a file at the given path.
     *
     * @param filePath Full path to the file
     * @return true if this server handles this file
     */
    fun handlesFile(filePath: String): Boolean {
        val extension = filePath.substringAfterLast('.', "")
        if (extension.isNotEmpty() && handlesExtension(extension)) {
            return true
        }

        // Check file patterns if defined
        if (filePatterns.isNotEmpty()) {
            val fileName = filePath.substringAfterLast('/')
            return filePatterns.any { pattern ->
                matchGlobPattern(pattern, fileName)
            }
        }

        return false
    }

    /**
     * Simple glob pattern matching for file names.
     */
    private fun matchGlobPattern(pattern: String, fileName: String): Boolean {
        // Convert glob to regex
        val regex = pattern
            .replace(".", "\\.")
            .replace("*", ".*")
            .replace("?", ".")
            .toRegex()
        return regex.matches(fileName)
    }

    companion object {
        /**
         * Shell operators that could be used for command injection.
         * These characters are forbidden in command names and arguments.
         */
        private val SHELL_OPERATORS = setOf(
            ';',  // Command separator
            '|',  // Pipe
            '&',  // Background/AND
            '$',  // Variable substitution
            '`',  // Command substitution
            '(',  // Subshell start
            ')',  // Subshell end
            '{',  // Command grouping start
            '}',  // Command grouping end
            '<',  // Input redirection
            '>',  // Output redirection
            '!',  // History expansion
            '\n', // Newline (command separator)
            '\r', // Carriage return
        )

        /**
         * Pattern for valid command/argument characters.
         * Allows alphanumeric, dash, underscore, dot, forward slash (paths), equals (flags),
         * colon (paths), and at-sign (npm scoped packages).
         */
        private val VALID_COMMAND_PATTERN = Regex("^[a-zA-Z0-9_./@:=-]+$")

        /**
         * Validate that a command list doesn't contain shell operators or injection attempts.
         *
         * @param command The command list to validate
         * @return ValidationResult indicating success or failure with message
         */
        fun validateCommand(command: List<String>): CommandValidationResult {
            if (command.isEmpty()) {
                return CommandValidationResult.Invalid("Command list cannot be empty")
            }

            for ((index, part) in command.withIndex()) {
                if (part.isEmpty()) {
                    return CommandValidationResult.Invalid(
                        "Command part at index $index is empty"
                    )
                }

                // Check for shell operators
                for (char in part) {
                    if (char in SHELL_OPERATORS) {
                        return CommandValidationResult.Invalid(
                            "Command contains forbidden shell operator '$char' in: $part"
                        )
                    }
                }

                // First element (the command itself) must match strict pattern
                if (index == 0 && !VALID_COMMAND_PATTERN.matches(part)) {
                    return CommandValidationResult.Invalid(
                        "Command name contains invalid characters: $part"
                    )
                }
            }

            return CommandValidationResult.Valid
        }

        /**
         * Check if a command is valid (convenience method).
         */
        fun isValidCommand(command: List<String>): Boolean {
            return validateCommand(command) is CommandValidationResult.Valid
        }
    }
}

/**
 * Result of command validation.
 */
sealed class CommandValidationResult {
    /**
     * Command is valid and safe to execute.
     */
    data object Valid : CommandValidationResult()

    /**
     * Command is invalid with a reason message.
     */
    data class Invalid(val reason: String) : CommandValidationResult()
}

/**
 * Capabilities that a language server may support.
 * Used to track what features are available after initialization.
 */
data class LanguageServerCapabilities(
    val supportsSemanticTokens: Boolean = false,
    val supportsCompletion: Boolean = false,
    val supportsHover: Boolean = false,
    val supportsDefinition: Boolean = false,
    val supportsReferences: Boolean = false,
    val supportsDocumentHighlight: Boolean = false,
    val supportsDocumentSymbol: Boolean = false,
    val supportsCodeAction: Boolean = false,
    val supportsFormatting: Boolean = false,
    val supportsRename: Boolean = false,
    val supportsDiagnostics: Boolean = true // Most servers support this
) {
    companion object {
        /**
         * Extract capabilities from LSP ServerCapabilities.
         */
        fun fromServerCapabilities(
            capabilities: ai.rever.bosseditor.lsp.protocol.ServerCapabilities
        ): LanguageServerCapabilities {
            return LanguageServerCapabilities(
                supportsSemanticTokens = capabilities.semanticTokensProvider != null,
                supportsCompletion = capabilities.completionProvider != null,
                supportsHover = capabilities.hoverProvider == true,
                supportsDefinition = capabilities.definitionProvider == true,
                supportsReferences = capabilities.referencesProvider == true,
                supportsDocumentHighlight = capabilities.documentHighlightProvider == true,
                supportsDocumentSymbol = capabilities.documentSymbolProvider == true,
                supportsCodeAction = capabilities.codeActionProvider != null,
                supportsFormatting = capabilities.documentFormattingProvider == true,
                supportsRename = capabilities.renameProvider != null
            )
        }
    }
}

/**
 * State of a running language server.
 */
enum class LanguageServerState {
    /**
     * Server is not running.
     */
    STOPPED,

    /**
     * Server process is starting.
     */
    STARTING,

    /**
     * Server is running and initializing (waiting for initialize response).
     */
    INITIALIZING,

    /**
     * Server is fully initialized and ready for requests.
     */
    RUNNING,

    /**
     * Server is shutting down.
     */
    STOPPING,

    /**
     * Server encountered an error.
     */
    ERROR
}

/**
 * Information about a running language server instance.
 */
data class LanguageServerInstance(
    val config: LanguageServerConfig,
    val state: LanguageServerState,
    val capabilities: LanguageServerCapabilities? = null,
    val workspaceRoot: String? = null,
    val errorMessage: String? = null,
    val processId: Long? = null
)
