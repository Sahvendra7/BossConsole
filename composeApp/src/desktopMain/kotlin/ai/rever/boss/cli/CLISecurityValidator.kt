package ai.rever.boss.cli

/**
 * Security validation utilities for CLI operations.
 *
 * Prevents path traversal attacks, command injection, and other security issues.
 * Based on security validation from UpdateScriptGenerator.kt:72-104
 */
object CLISecurityValidator {

    /**
     * Validates URL format.
     */
    fun isValidUrl(url: String): Boolean {
        return url.startsWith("http://") || url.startsWith("https://")
    }

    /**
     * Validates file path for security.
     * Prevents path traversal attacks and other malicious patterns.
     */
    fun isValidPath(path: String): Boolean {
        // Check for null bytes
        if (path.contains('\u0000')) {
            return false
        }

        // Check for path traversal
        if (path.contains("..")) {
            return false
        }

        // Check for shell metacharacters
        val dangerousChars = listOf(';', '&', '|', '`', '$', '\n', '\r')
        if (dangerousChars.any { path.contains(it) }) {
            return false
        }

        return true
    }

    /**
     * Validates terminal command for security.
     */
    fun isValidCommand(command: String): Boolean {
        // Check for null bytes
        if (command.contains('\u0000')) {
            return false
        }

        // For now, allow all commands
        // Could add whitelist or blacklist in future
        return true
    }
}
