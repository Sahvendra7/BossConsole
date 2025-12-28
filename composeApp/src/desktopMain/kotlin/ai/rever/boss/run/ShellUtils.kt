package ai.rever.boss.run

/**
 * Utility functions for shell command construction and escaping.
 */
object ShellUtils {

    /**
     * Escape a string for safe use inside double quotes in shell.
     * Characters that need escaping in double-quoted strings: $ ` \ " !
     *
     * This prevents:
     * - Variable expansion ($HOME -> literal $HOME)
     * - Command substitution (`cmd` or $(cmd) -> literal text)
     * - Escape sequence interpretation
     * - History expansion (! in interactive shells)
     */
    fun escapeForDoubleQuotes(str: String): String {
        return str
            .replace("\\", "\\\\")  // Backslash must be escaped first
            .replace("\"", "\\\"")  // Double quotes
            .replace("\$", "\\\$")  // Dollar sign (prevents variable expansion)
            .replace("`", "\\`")    // Backticks (prevents command substitution)
            .replace("!", "\\!")    // Exclamation (history expansion in interactive shells)
    }

    /**
     * Build a command with cd to working directory.
     * Working directory is properly escaped for shell safety.
     *
     * @param command The command to run
     * @param workingDirectory Optional working directory to cd into first
     * @return The full command with cd prefix if workingDirectory is provided
     */
    fun buildCommandWithWorkingDirectory(command: String, workingDirectory: String?): String {
        return if (!workingDirectory.isNullOrBlank()) {
            val escapedDir = escapeForDoubleQuotes(workingDirectory)
            "cd \"$escapedDir\" && $command"
        } else {
            command
        }
    }
}
