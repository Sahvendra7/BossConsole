package ai.rever.boss.crash

import ai.rever.boss.config.GitHubConfig
import ai.rever.boss.utils.AppVersion
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Service for submitting crash reports to GitHub Issues.
 *
 * Submits crash reports to the BossConsole-Releases repository
 * with deduplication support (adds comment to existing issue if same crash).
 */
object CrashReportService {
    private val logger = BossLogger.forComponent("CrashReportService")

    private const val GITHUB_API_BASE = "https://api.github.com"
    private const val CRASH_REPO = "risa-labs-inc/BossConsole-Releases"
    private const val ISSUES_ENDPOINT = "$GITHUB_API_BASE/repos/$CRASH_REPO/issues"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }

        install(HttpTimeout) {
            requestTimeoutMillis = 30_000
            connectTimeoutMillis = 15_000
            socketTimeoutMillis = 15_000
        }
    }

    /**
     * Result of submitting a crash report.
     */
    sealed class SubmitResult {
        data class Success(val issueUrl: String, val isNewIssue: Boolean) : SubmitResult()
        data class Error(val message: String) : SubmitResult()
    }

    /**
     * Submit a crash report to GitHub Issues.
     *
     * @param report The crash report to submit
     * @return Result indicating success with issue URL, or error
     */
    suspend fun submitCrashReport(report: CrashReport): SubmitResult = withContext(Dispatchers.IO) {
        try {
            val authContext = GitHubConfig.getAuthContext()

            if (!authContext.isAuthenticated) {
                return@withContext SubmitResult.Error(
                    "GitHub authentication required. Please configure a GitHub token."
                )
            }

            // Search for existing issue with same signature
            val existingIssue = searchForExistingIssue(report.signature, authContext)

            return@withContext if (existingIssue != null) {
                // Add comment to existing issue
                addCommentToIssue(existingIssue.number, report, authContext)
            } else {
                // Create new issue
                createNewIssue(report, authContext)
            }

        } catch (e: Exception) {
            logger.error(LogCategory.NETWORK, "Failed to submit crash report", error = e)
            SubmitResult.Error("Failed to submit: ${e.message ?: "Unknown error"}")
        }
    }

    /**
     * Search for an existing issue with the same crash signature.
     */
    private suspend fun searchForExistingIssue(
        signature: String,
        authContext: GitHubConfig.GitHubAuthContext
    ): GitHubIssue? {
        try {
            val searchQuery = "repo:$CRASH_REPO is:issue [$signature] in:title"
            val searchUrl = "$GITHUB_API_BASE/search/issues?q=${searchQuery.encodeURLParameter()}"

            val response = httpClient.get(searchUrl) {
                headers {
                    append("Accept", "application/vnd.github.v3+json")
                    append("User-Agent", "BOSS-Desktop-${AppVersion.CURRENT}")
                    append("Authorization", "Bearer ${authContext.token}")
                }
            }

            if (response.status.value in 200..299) {
                val searchResult = response.body<GitHubSearchResult>()
                return searchResult.items.firstOrNull()
            }
        } catch (e: Exception) {
            logger.warn(LogCategory.NETWORK, "Failed to search for existing issue", error = e)
        }

        return null
    }

    /**
     * Create a new issue for the crash report.
     */
    private suspend fun createNewIssue(
        report: CrashReport,
        authContext: GitHubConfig.GitHubAuthContext
    ): SubmitResult {
        val title = "${CrashSignature.formatForTitle(report.signature)} Crash: ${report.exceptionType}"
        val body = formatIssueBody(report, isNewReport = true)

        val response = httpClient.post(ISSUES_ENDPOINT) {
            headers {
                append("Accept", "application/vnd.github.v3+json")
                append("User-Agent", "BOSS-Desktop-${AppVersion.CURRENT}")
                append("Authorization", "Bearer ${authContext.token}")
            }
            contentType(ContentType.Application.Json)
            setBody(CreateIssueRequest(
                title = title,
                body = body,
                labels = listOf("crash-report", "automated")
            ))
        }

        return when {
            response.status.value in 200..299 -> {
                val issue = response.body<GitHubIssue>()
                logger.info(LogCategory.NETWORK, "Created crash report issue", mapOf(
                    "issue" to issue.number,
                    "signature" to report.signature
                ))
                SubmitResult.Success(issue.htmlUrl, isNewIssue = true)
            }
            response.status.value == 401 -> {
                SubmitResult.Error("GitHub authentication failed. Token may be invalid or expired.")
            }
            response.status.value == 403 -> {
                SubmitResult.Error("Permission denied. Token may lack 'repo' scope.")
            }
            else -> {
                val errorBody = response.bodyAsText()
                logger.error(LogCategory.NETWORK, "Failed to create issue", mapOf(
                    "status" to response.status.value,
                    "error" to errorBody.take(200)
                ))
                SubmitResult.Error("Failed to create issue (HTTP ${response.status.value})")
            }
        }
    }

    /**
     * Add a comment to an existing issue.
     */
    private suspend fun addCommentToIssue(
        issueNumber: Int,
        report: CrashReport,
        authContext: GitHubConfig.GitHubAuthContext
    ): SubmitResult {
        val commentBody = formatIssueBody(report, isNewReport = false)
        val commentsUrl = "$ISSUES_ENDPOINT/$issueNumber/comments"

        val response = httpClient.post(commentsUrl) {
            headers {
                append("Accept", "application/vnd.github.v3+json")
                append("User-Agent", "BOSS-Desktop-${AppVersion.CURRENT}")
                append("Authorization", "Bearer ${authContext.token}")
            }
            contentType(ContentType.Application.Json)
            setBody(CreateCommentRequest(body = commentBody))
        }

        return when {
            response.status.value in 200..299 -> {
                logger.info(LogCategory.NETWORK, "Added comment to existing crash issue", mapOf(
                    "issue" to issueNumber,
                    "signature" to report.signature
                ))
                SubmitResult.Success("$GITHUB_API_BASE/repos/$CRASH_REPO/issues/$issueNumber".replace(
                    "api.github.com/repos",
                    "github.com"
                ), isNewIssue = false)
            }
            else -> {
                SubmitResult.Error("Failed to add comment (HTTP ${response.status.value})")
            }
        }
    }

    /**
     * Format the issue body in markdown.
     */
    private fun formatIssueBody(report: CrashReport, isNewReport: Boolean): String {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss z")
        val timestamp = Instant.ofEpochMilli(report.timestamp)
            .atZone(ZoneId.systemDefault())
            .format(dateFormatter)

        return buildString {
            if (!isNewReport) {
                appendLine("## Additional Occurrence")
                appendLine()
            }

            // Signature and timestamp
            appendLine("**Signature:** `${report.signature}`")
            appendLine("**Timestamp:** $timestamp")
            appendLine()

            // User description if provided
            report.userNotes?.let { notes ->
                appendLine("## User Description")
                appendLine(notes)
                appendLine()
            }

            // Environment table
            appendLine("## Environment")
            appendLine()
            appendLine("| Property | Value |")
            appendLine("|----------|-------|")
            appendLine("| BOSS Version | ${report.appInfo.version} |")
            appendLine("| Platform | ${report.appInfo.platform} |")
            appendLine("| OS | ${report.systemInfo.osName} ${report.systemInfo.osVersion} |")
            appendLine("| Architecture | ${report.systemInfo.osArch} |")
            appendLine("| Java | ${report.systemInfo.javaVersion} (${report.systemInfo.javaVendor}) |")
            appendLine("| Heap Memory | ${report.systemInfo.heapUsedMB} MB / ${report.systemInfo.heapMaxMB} MB |")
            appendLine("| Non-Heap Memory | ${report.systemInfo.nonHeapUsedMB} MB |")
            appendLine("| CPUs | ${report.systemInfo.availableProcessors} |")
            appendLine("| Debug Mode | ${report.appInfo.isDebug} |")
            appendLine()

            // Exception info
            appendLine("## Exception")
            appendLine()
            appendLine("**Type:** `${report.exceptionType}`")
            appendLine("**Message:** ${report.exceptionMessage}")
            appendLine()

            // Stack trace
            appendLine("## Stack Trace")
            appendLine()
            appendLine("```")
            appendLine(report.stackTrace.take(5000)) // Limit stack trace length
            if (report.stackTrace.length > 5000) {
                appendLine("... (truncated)")
            }
            appendLine("```")

            // Recent logs in collapsible section (if included)
            report.recentLogs?.let { logs ->
                if (logs.isNotEmpty()) {
                    appendLine()
                    appendLine("<details>")
                    appendLine("<summary>Recent Activity Logs (${logs.size} entries)</summary>")
                    appendLine()
                    appendLine("```")
                    logs.forEach { entry ->
                        val entryTime = Instant.ofEpochMilli(entry.timestamp)
                            .atZone(ZoneId.systemDefault())
                            .format(DateTimeFormatter.ofPattern("HH:mm:ss.SSS"))
                        appendLine("[$entryTime] [${entry.level}] [${entry.category}] ${entry.component}: ${entry.message}")
                    }
                    appendLine("```")
                    appendLine()
                    appendLine("</details>")
                }
            }
        }
    }

    // Data classes for GitHub API

    @Serializable
    private data class CreateIssueRequest(
        val title: String,
        val body: String,
        val labels: List<String>
    )

    @Serializable
    private data class CreateCommentRequest(
        val body: String
    )

    @Serializable
    private data class GitHubSearchResult(
        @SerialName("total_count") val totalCount: Int,
        val items: List<GitHubIssue>
    )

    @Serializable
    private data class GitHubIssue(
        val number: Int,
        val title: String,
        @SerialName("html_url") val htmlUrl: String,
        val state: String
    )
}
