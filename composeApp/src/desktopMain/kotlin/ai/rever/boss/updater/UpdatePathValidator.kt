package ai.rever.boss.updater

/**
 * The single set of rules for paths and filenames that reach the update
 * installers.
 *
 * Every value validated here is about to be interpolated into a generated
 * shell/batch script that runs with the user's (or, on Linux, elevated)
 * privileges, so validation fails closed: it throws rather than logging. It is
 * shared by [UpdateScriptGenerator] and [UpdateInstaller] so a suspicious
 * artifact is refused no matter which entry point ran (Issue #37).
 */
internal object UpdatePathValidator {
    /**
     * Validate a path or filename for security concerns:
     * - Null bytes (bypass path checks)
     * - Shell metacharacters (command injection / substitution)
     * - Newlines (script injection)
     * - Path traversal sequences
     * - Command separators
     * - Windows batch metacharacters
     *
     * @param path The path or filename to validate
     * @param description Description for error messages (e.g., "DMG path")
     * @throws SecurityException if [path] contains dangerous characters
     */
    fun validate(
        path: String,
        description: String,
    ) {
        // Check for null bytes (can bypass path checks)
        if (path.contains('\u0000')) {
            throw SecurityException("$description contains null byte - possible directory traversal attack")
        }

        // Check for shell metacharacters that could enable command injection
        if (path.contains('$') || path.contains('`')) {
            throw SecurityException("$description contains shell metacharacters - possible command injection")
        }

        // Check for newlines (could inject additional commands)
        if (path.contains('\n') || path.contains('\r')) {
            throw SecurityException("$description contains newline characters - possible script injection")
        }

        // Path traversal is a hard failure: legitimate update paths are always
        // absolute and never use ..
        if (path.contains("..")) {
            throw SecurityException("$description contains path traversal sequence '..' - rejected for security")
        }

        // Command separators are never legitimate in an update path and indicate
        // an attack attempt
        if (path.contains(";") || path.contains("|") || path.contains("&")) {
            throw SecurityException("$description contains command separator characters - rejected for security")
        }

        // Windows batch metacharacters enable variable expansion and command
        // injection in the generated .bat
        if (path.contains("%") || path.contains("^") || path.contains("!")) {
            throw SecurityException("$description contains Windows batch metacharacters - rejected for security")
        }
    }
}
