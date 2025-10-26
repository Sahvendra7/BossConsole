package ai.rever.boss.config

import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Configuration for GitHub API access.
 *
 * GitHub API rate limits:
 * - Unauthenticated: 60 requests/hour
 * - Authenticated: 5,000 requests/hour
 *
 * The GitHub token is obtained from multiple sources (in order):
 * 1. Environment variable: GITHUB_TOKEN
 * 2. System property: GITHUB_TOKEN
 * 3. local.properties file: GITHUB_TOKEN=ghp_...
 * 4. GitHub CLI (gh auth token)
 * 5. No token (fallback to unauthenticated access)
 *
 * To set up authentication:
 * - **Option 1 (Easiest)**: Run `gh auth login` to authenticate via GitHub CLI
 * - **Option 2**: Create token at https://github.com/settings/tokens (no scopes needed)
 *                 and add to local.properties: GITHUB_TOKEN=ghp_your_token_here
 */
object GitHubConfig {
    /**
     * GitHub Personal Access Token loaded from secure sources.
     * Attempts to use GitHub CLI if no token is explicitly configured.
     * Returns null if not configured (will use unauthenticated access).
     */
    val token: String? by lazy {
        // Try explicit configuration first
        ConfigLoader.getConfig("GITHUB_TOKEN")
            ?: getTokenFromGitHubCLI()
    }

    /**
     * Check if GitHub token is configured
     */
    val hasToken: Boolean
        get() = token != null

    /**
     * Attempt to retrieve token from GitHub CLI (gh auth token)
     * Returns null if gh is not installed or not authenticated
     */
    private fun getTokenFromGitHubCLI(): String? {
        return try {
            val process = ProcessBuilder("gh", "auth", "token")
                .redirectErrorStream(true)
                .start()

            val output = BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                reader.readText().trim()
            }

            val exitCode = process.waitFor()

            if (exitCode == 0 && output.isNotBlank() && !output.contains("not logged in", ignoreCase = true)) {
                println("✅ Using GitHub token from GitHub CLI (gh)")
                output
            } else {
                null
            }
        } catch (e: Exception) {
            // gh command not found or other error - silently ignore
            null
        }
    }
}
