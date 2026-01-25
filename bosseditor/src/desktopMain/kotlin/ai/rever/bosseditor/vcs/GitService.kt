package ai.rever.bosseditor.vcs

import ai.rever.bosseditor.features.BlameInfo
import ai.rever.bosseditor.lsp.logging.LogCategory
import ai.rever.bosseditor.lsp.logging.LspLogger
import ai.rever.bosseditor.features.FileBlameInfo
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

/**
 * Service for interacting with Git.
 *
 * Provides git blame functionality for the editor.
 * Uses ProcessBuilder to run git commands.
 *
 * JVM-only: Desktop target only (see CLAUDE.md).
 */
class GitService {
    private val logger = LspLogger.forComponent("GitService")

    companion object {
        /** Timeout for git operations in seconds */
        private const val GIT_TIMEOUT_SECONDS = 30L
        /** Maximum reasonable line number to accept (sanity check for malformed output) */
        private const val MAX_LINE_NUMBER = 1_000_000
    }

    /**
     * Gets blame information for a file.
     *
     * @param filePath The absolute path to the file
     * @return FileBlameInfo if successful, null if file is not in a git repo or error
     */
    suspend fun blame(filePath: String): FileBlameInfo? {
        // Run git command on IO dispatcher (process/file operations)
        val output = withContext(Dispatchers.IO) {
            var process: Process? = null
            try {
                val file = File(filePath)
                if (!file.exists()) return@withContext null

                val directory = file.parentFile
                if (!isGitRepository(directory)) return@withContext null

                // Run git blame with porcelain format for easy parsing
                // Use "--" separator to prevent filenames starting with "-" from being interpreted as options
                process = ProcessBuilder(
                    "git", "blame", "--porcelain", "--", file.name
                ).apply {
                    directory(directory)
                    redirectErrorStream(true)
                }.start()

                // Read output asynchronously to prevent deadlock if process hangs
                // (readText() blocks until EOF, so timeout would never trigger otherwise)
                val outputFuture = CompletableFuture.supplyAsync {
                    process.inputStream.bufferedReader().use { it.readText() }
                }

                // Wait with timeout to prevent hanging on stuck git processes
                if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                    process.destroyForcibly()
                    outputFuture.cancel(true)
                    logger.warn(LogCategory.GENERAL, "git blame timed out", data = mapOf("timeout" to "${GIT_TIMEOUT_SECONDS}s"))
                    return@withContext null
                }

                val gitOutput = outputFuture.get()
                val exitCode = process.exitValue()
                if (exitCode != 0) {
                    logger.warn(LogCategory.GENERAL, "git blame failed", data = mapOf("exitCode" to exitCode))
                    return@withContext null
                }

                gitOutput
            } catch (e: CancellationException) {
                // Always rethrow CancellationException per THREADING.md
                throw e
            } catch (e: Exception) {
                logger.warn(LogCategory.GENERAL, "Error running git blame", error = e)
                null
            } finally {
                // Ensure process is cleaned up even if exception occurs during waitFor
                process?.let {
                    if (it.isAlive) {
                        it.destroyForcibly()
                    }
                }
            }
        } ?: return null

        // Parse on Default dispatcher (CPU-bound string processing per CLAUDE.md)
        return withContext(Dispatchers.Default) {
            parseBlameOutput(output, filePath)
        }
    }

    /**
     * Checks if a directory is part of a git repository.
     */
    suspend fun isGitRepository(directory: File?): Boolean = withContext(Dispatchers.IO) {
        if (directory == null || !directory.exists()) return@withContext false

        var process: Process? = null
        try {
            process = ProcessBuilder("git", "rev-parse", "--git-dir")
                .directory(directory)
                .redirectErrorStream(true)
                .start()

            // Drain output asynchronously to prevent deadlock if process hangs
            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }

            // Wait with timeout to prevent hanging
            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                outputFuture.cancel(true)
                return@withContext false
            }
            outputFuture.get() // Ensure stream is fully drained
            process.exitValue() == 0
        } catch (e: CancellationException) {
            // Always rethrow CancellationException per THREADING.md
            throw e
        } catch (e: Exception) {
            false
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
        }
    }

    /**
     * Gets the repository root directory.
     */
    suspend fun getRepositoryRoot(directory: File): File? = withContext(Dispatchers.IO) {
        var process: Process? = null
        try {
            process = ProcessBuilder("git", "rev-parse", "--show-toplevel")
                .directory(directory)
                .redirectErrorStream(true)
                .start()

            // Read output asynchronously to prevent deadlock if process hangs
            val outputFuture = CompletableFuture.supplyAsync {
                process.inputStream.bufferedReader().use { it.readText() }
            }

            // Wait with timeout to prevent hanging
            if (!process.waitFor(GIT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                outputFuture.cancel(true)
                return@withContext null
            }

            val output = outputFuture.get().trim()
            val exitCode = process.exitValue()
            if (exitCode == 0 && output.isNotEmpty()) {
                File(output)
            } else {
                null
            }
        } catch (e: CancellationException) {
            // Always rethrow CancellationException per THREADING.md
            throw e
        } catch (e: Exception) {
            null
        } finally {
            process?.let { if (it.isAlive) it.destroyForcibly() }
        }
    }

    /**
     * Parses git blame --porcelain output.
     *
     * Porcelain format:
     * ```
     * <sha1> <orig-line> <final-line> <num-lines>
     * author <author-name>
     * author-mail <author-email>
     * author-time <timestamp>
     * author-tz <timezone>
     * committer <committer-name>
     * committer-mail <committer-email>
     * committer-time <timestamp>
     * committer-tz <timezone>
     * summary <commit-summary>
     * previous <sha1> <filename>
     * filename <filename>
     * \t<line-content>
     * ```
     */
    private fun parseBlameOutput(output: String, filePath: String): FileBlameInfo {
        val lines = output.lines()
        val blameMap = mutableMapOf<Int, BlameInfo>()

        var i = 0
        var currentCommit: String? = null
        var currentAuthor: String? = null
        var currentEmail: String? = null
        var currentTimestamp: Long = 0
        var currentSummary: String? = null
        var currentLine: Int = 0

        // Cache for commit info (commit hash -> parsed info)
        val commitCache = mutableMapOf<String, BlameInfo>()

        while (i < lines.size) {
            val line = lines[i]

            when {
                // New commit line: <sha1> <orig-line> <final-line> [<num-lines>]
                line.matches(Regex("^[0-9a-f]{40} \\d+ \\d+.*")) -> {
                    val parts = line.split(" ")
                    // Safety check: ensure parts has at least 3 elements
                    if (parts.size < 3) {
                        i++
                        continue
                    }
                    currentCommit = parts[0]
                    // Convert to 0-indexed; skip entry if parsing fails to avoid incorrect attribution
                    val parsedLine = parts[2].toIntOrNull()?.minus(1)
                    // Bounds check: null (parse failure), negative, or unreasonably large
                    if (parsedLine == null || parsedLine < 0 || parsedLine > MAX_LINE_NUMBER) {
                        logger.debug(LogCategory.GENERAL, "Invalid line number in blame output", data = mapOf("line" to parts[2]))
                        i++
                        continue
                    }
                    currentLine = parsedLine

                    // Check if we have cached info for this commit
                    val cached = commitCache[currentCommit]
                    if (cached != null) {
                        blameMap[currentLine] = cached.copy(line = currentLine)
                    }
                }

                line.startsWith("author ") -> {
                    currentAuthor = line.substringAfter("author ")
                }

                line.startsWith("author-mail ") -> {
                    currentEmail = line.substringAfter("author-mail ")
                        .trim('<', '>')
                }

                line.startsWith("author-time ") -> {
                    currentTimestamp = line.substringAfter("author-time ").toLongOrNull() ?: 0
                }

                line.startsWith("summary ") -> {
                    currentSummary = line.substringAfter("summary ")
                }

                // Content line (starts with tab) - end of this entry
                line.startsWith("\t") -> {
                    if (currentCommit != null && currentAuthor != null) {
                        val blameInfo = BlameInfo(
                            commitHash = currentCommit,
                            author = currentAuthor,
                            authorEmail = currentEmail,
                            timestamp = currentTimestamp,
                            summary = currentSummary ?: "",
                            line = currentLine
                        )

                        blameMap[currentLine] = blameInfo

                        // Cache for future lines with same commit
                        if (!commitCache.containsKey(currentCommit)) {
                            commitCache[currentCommit] = blameInfo
                        }
                    }
                }
            }

            i++
        }

        return FileBlameInfo(filePath, blameMap)
    }
}
