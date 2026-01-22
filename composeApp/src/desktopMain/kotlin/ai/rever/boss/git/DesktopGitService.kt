package ai.rever.boss.git

import ai.rever.boss.components.events.GitTerminalEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

/**
 * Desktop implementation of GitService using git CLI.
 *
 * Uses ProcessBuilder to execute git commands and parse their output.
 * All I/O operations run on Dispatchers.IO for non-blocking execution.
 */
actual object GitService {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _currentBranch = MutableStateFlow<String?>(null)
    actual val currentBranch: StateFlow<String?> = _currentBranch.asStateFlow()

    private val _isGitRepository = MutableStateFlow(false)
    actual val isGitRepository: StateFlow<Boolean> = _isGitRepository.asStateFlow()

    private val _localBranches = MutableStateFlow<List<GitBranchInfo>>(emptyList())
    actual val localBranches: StateFlow<List<GitBranchInfo>> = _localBranches.asStateFlow()

    private val _remoteBranches = MutableStateFlow<List<GitBranchInfo>>(emptyList())
    actual val remoteBranches: StateFlow<List<GitBranchInfo>> = _remoteBranches.asStateFlow()

    private val _isGitAvailable = MutableStateFlow(false)
    actual val isGitAvailable: StateFlow<Boolean> = _isGitAvailable.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    actual val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    actual val lastError: StateFlow<String?> = _lastError.asStateFlow()

    // New StateFlows for extended functionality
    private val _fileStatus = MutableStateFlow<List<GitFileStatus>>(emptyList())
    actual val fileStatus: StateFlow<List<GitFileStatus>> = _fileStatus.asStateFlow()

    private val _commitLog = MutableStateFlow<List<GitCommitInfo>>(emptyList())
    actual val commitLog: StateFlow<List<GitCommitInfo>> = _commitLog.asStateFlow()

    private val _stashList = MutableStateFlow<List<GitStashInfo>>(emptyList())
    actual val stashList: StateFlow<List<GitStashInfo>> = _stashList.asStateFlow()

    private var currentProjectPath: String? = null
    private var refreshJob: Job? = null

    init {
        // Check if git is available on system startup
        scope.launch {
            _isGitAvailable.value = checkGitAvailable()
        }
    }

    actual suspend fun refresh(projectPath: String) = withContext(Dispatchers.IO) {
        // Cancel any pending refresh
        refreshJob?.cancel()

        currentProjectPath = projectPath
        _isLoading.value = true
        _lastError.value = null

        try {
            if (!_isGitAvailable.value) {
                _isGitRepository.value = false
                _currentBranch.value = null
                _localBranches.value = emptyList()
                _remoteBranches.value = emptyList()
                return@withContext
            }

            // Check if directory is a git repository
            val isRepo = isGitRepo(projectPath)
            _isGitRepository.value = isRepo

            if (!isRepo) {
                _currentBranch.value = null
                _localBranches.value = emptyList()
                _remoteBranches.value = emptyList()
                return@withContext
            }

            // Get current branch (or short SHA for detached HEAD)
            _currentBranch.value = getCurrentBranchName(projectPath)

            // Get local branches
            _localBranches.value = getLocalBranchList(projectPath)

            // Get remote branches
            _remoteBranches.value = getRemoteBranchList(projectPath)
        } catch (e: Exception) {
            _lastError.value = e.message
            println("[GitService] Error refreshing: ${e.message}")
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun checkout(branchName: String): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            // For remote branches like "origin/feature", extract just "feature"
            // Git will automatically set up tracking
            val localName = if (branchName.contains("/")) {
                branchName.substringAfter("/")
            } else {
                branchName
            }

            val result = runGitCommand(projectPath, "checkout", localName)
            if (result.exitCode == 0) {
                // Refresh state after checkout
                refresh(projectPath)
                GitOperationResult.Success("Switched to branch '$localName'")
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun createBranch(branchName: String, checkout: Boolean): GitOperationResult =
        withContext(Dispatchers.IO) {
            val projectPath = currentProjectPath
                ?: return@withContext GitOperationResult.Error("No project selected")

            _isLoading.value = true
            try {
                val args = if (checkout) {
                    listOf("checkout", "-b", branchName)
                } else {
                    listOf("branch", branchName)
                }

                val result = runGitCommand(projectPath, *args.toTypedArray())
                if (result.exitCode == 0) {
                    // Refresh state after creation
                    refresh(projectPath)
                    GitOperationResult.Success("Created branch '$branchName'")
                } else {
                    val errorMsg = result.error.ifEmpty { result.output }.trim()
                    _lastError.value = errorMsg
                    GitOperationResult.Error(errorMsg, result.exitCode)
                }
            } finally {
                _isLoading.value = false
            }
        }

    actual suspend fun pull(): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val result = runGitCommand(projectPath, "pull")
            if (result.exitCode == 0) {
                // Refresh state after pull
                refresh(projectPath)
                val message = result.output.trim().ifEmpty { "Pull completed successfully" }
                GitOperationResult.Success(message)
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun push(): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            // Use -u to set upstream if not already set
            val result = runGitCommand(projectPath, "push", "-u", "origin", "HEAD")
            if (result.exitCode == 0) {
                val message = result.output.trim().ifEmpty {
                    result.error.trim().ifEmpty { "Push completed successfully" }
                }
                GitOperationResult.Success(message)
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun getCreatePRUrl(): String? = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath ?: return@withContext null
        val branch = _currentBranch.value ?: return@withContext null

        try {
            // Get the remote origin URL
            val result = runGitCommand(projectPath, "remote", "get-url", "origin")
            if (result.exitCode != 0) return@withContext null

            val remoteUrl = result.output.trim()
            val repoUrl = parseRemoteUrl(remoteUrl) ?: return@withContext null

            // Construct the PR creation URL based on the platform
            when {
                repoUrl.contains("github.com") -> {
                    // GitHub: https://github.com/owner/repo/compare/branch?expand=1
                    "$repoUrl/compare/$branch?expand=1"
                }
                repoUrl.contains("gitlab.com") || repoUrl.contains("gitlab") -> {
                    // GitLab: https://gitlab.com/owner/repo/-/merge_requests/new?merge_request[source_branch]=branch
                    "$repoUrl/-/merge_requests/new?merge_request[source_branch]=$branch"
                }
                repoUrl.contains("bitbucket.org") -> {
                    // Bitbucket: https://bitbucket.org/owner/repo/pull-requests/new?source=branch
                    "$repoUrl/pull-requests/new?source=$branch"
                }
                else -> null
            }
        } catch (e: Exception) {
            println("[GitService] Error getting PR URL: ${e.message}")
            null
        }
    }

    actual suspend fun merge(branchName: String): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val result = runGitCommand(projectPath, "merge", branchName)
            if (result.exitCode == 0) {
                // Refresh state after merge
                refresh(projectPath)
                val message = result.output.trim().ifEmpty { "Merged '$branchName' successfully" }
                GitOperationResult.Success(message)
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun rebase(branchName: String): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val result = runGitCommand(projectPath, "rebase", branchName)
            if (result.exitCode == 0) {
                // Refresh state after rebase
                refresh(projectPath)
                val message = result.output.trim().ifEmpty { "Rebased onto '$branchName' successfully" }
                GitOperationResult.Success(message)
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual fun clear() {
        refreshJob?.cancel()
        currentProjectPath = null
        _currentBranch.value = null
        _isGitRepository.value = false
        _localBranches.value = emptyList()
        _remoteBranches.value = emptyList()
        _lastError.value = null
        _isLoading.value = false
        _fileStatus.value = emptyList()
        _commitLog.value = emptyList()
        _stashList.value = emptyList()
    }

    actual fun getCurrentProjectPath(): String? = currentProjectPath

    // ===== File Status & Staging Implementation =====

    actual suspend fun getStatus(): List<GitFileStatus> = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath ?: return@withContext emptyList()

        try {
            // Use porcelain v1 format for stable parsing
            val result = runGitCommand(projectPath, "status", "--porcelain=v1")
            if (result.exitCode != 0) {
                _lastError.value = result.error.ifEmpty { result.output }
                return@withContext emptyList()
            }

            val statuses = result.output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { parseStatusLine(it) }

            _fileStatus.value = statuses
            statuses
        } catch (e: Exception) {
            println("[GitService] Error getting status: ${e.message}")
            emptyList()
        }
    }

    /**
     * Parse a single line from `git status --porcelain=v1`.
     * Format: XY PATH or XY ORIG_PATH -> PATH (for renames)
     * X = index status, Y = worktree status
     */
    private fun parseStatusLine(line: String): GitFileStatus? {
        if (line.length < 3) return null

        val indexChar = line[0]
        val workTreeChar = line[1]
        val pathPart = line.substring(3)

        // Handle rename/copy with arrow
        val (path, originalPath) = if (pathPart.contains(" -> ")) {
            val parts = pathPart.split(" -> ")
            parts[1] to parts[0]
        } else {
            pathPart to null
        }

        val indexStatus = parseStatusChar(indexChar)
        val workTreeStatus = parseStatusChar(workTreeChar)

        // A file is staged if it has an index status (not space or ?)
        val isStaged = indexStatus != null && indexStatus != GitFileStatusType.UNTRACKED
        // A file is unstaged if it has a worktree status (not space)
        val isUnstaged = workTreeStatus != null

        return GitFileStatus(
            path = path,
            indexStatus = indexStatus,
            workTreeStatus = workTreeStatus,
            isStaged = isStaged,
            isUnstaged = isUnstaged,
            originalPath = originalPath
        )
    }

    private fun parseStatusChar(c: Char): GitFileStatusType? {
        return when (c) {
            'M' -> GitFileStatusType.MODIFIED
            'A' -> GitFileStatusType.ADDED
            'D' -> GitFileStatusType.DELETED
            'R' -> GitFileStatusType.RENAMED
            'C' -> GitFileStatusType.COPIED
            '?' -> GitFileStatusType.UNTRACKED
            '!' -> GitFileStatusType.IGNORED
            'U' -> GitFileStatusType.UNMERGED
            ' ' -> null
            else -> null
        }
    }

    actual suspend fun stage(filePath: String): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        val result = runGitCommand(projectPath, "add", "--", filePath)
        if (result.exitCode == 0) {
            getStatus() // Refresh status
            GitOperationResult.Success("Staged '$filePath'")
        } else {
            val errorMsg = result.error.ifEmpty { result.output }.trim()
            GitOperationResult.Error(errorMsg, result.exitCode)
        }
    }

    actual suspend fun stageAll(): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        val result = runGitCommand(projectPath, "add", "-A")
        if (result.exitCode == 0) {
            getStatus() // Refresh status
            GitOperationResult.Success("Staged all changes")
        } else {
            val errorMsg = result.error.ifEmpty { result.output }.trim()
            GitOperationResult.Error(errorMsg, result.exitCode)
        }
    }

    actual suspend fun unstage(filePath: String): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        val result = runGitCommand(projectPath, "restore", "--staged", "--", filePath)
        if (result.exitCode == 0) {
            getStatus() // Refresh status
            GitOperationResult.Success("Unstaged '$filePath'")
        } else {
            val errorMsg = result.error.ifEmpty { result.output }.trim()
            GitOperationResult.Error(errorMsg, result.exitCode)
        }
    }

    actual suspend fun unstageAll(): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        val result = runGitCommand(projectPath, "restore", "--staged", ".")
        if (result.exitCode == 0) {
            getStatus() // Refresh status
            GitOperationResult.Success("Unstaged all changes")
        } else {
            val errorMsg = result.error.ifEmpty { result.output }.trim()
            GitOperationResult.Error(errorMsg, result.exitCode)
        }
    }

    actual suspend fun discardChanges(filePath: String): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        val result = runGitCommand(projectPath, "restore", "--", filePath)
        if (result.exitCode == 0) {
            getStatus() // Refresh status
            GitOperationResult.Success("Discarded changes to '$filePath'")
        } else {
            val errorMsg = result.error.ifEmpty { result.output }.trim()
            GitOperationResult.Error(errorMsg, result.exitCode)
        }
    }

    // ===== Commit Implementation =====

    actual suspend fun commit(message: String, amend: Boolean): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val args = if (amend) {
                listOf("commit", "--amend", "-m", message)
            } else {
                listOf("commit", "-m", message)
            }

            val result = runGitCommand(projectPath, *args.toTypedArray())
            if (result.exitCode == 0) {
                getStatus() // Refresh status
                getLog() // Refresh log
                val action = if (amend) "Amended commit" else "Created commit"
                GitOperationResult.Success(action)
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun getLastCommitMessage(): String? = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath ?: return@withContext null

        try {
            val result = runGitCommand(projectPath, "log", "-1", "--format=%B")
            if (result.exitCode == 0) {
                result.output.trim().ifEmpty { null }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    // ===== Commit Log Implementation =====

    actual suspend fun getLog(limit: Int): List<GitCommitInfo> = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath ?: return@withContext emptyList()

        try {
            // Format: hash|shorthash|author|email|timestamp|subject|parents|refs
            // Using %x00 as separator to handle special characters in subject
            val format = "%H%x00%h%x00%an%x00%ae%x00%at%x00%s%x00%P%x00%D"
            val result = runGitCommand(projectPath, "log", "--format=$format", "-n", limit.toString())

            if (result.exitCode != 0) {
                return@withContext emptyList()
            }

            val commits = result.output.lines()
                .filter { it.isNotBlank() }
                .mapNotNull { parseCommitLine(it) }

            _commitLog.value = commits
            commits
        } catch (e: Exception) {
            println("[GitService] Error getting log: ${e.message}")
            emptyList()
        }
    }

    private fun parseCommitLine(line: String): GitCommitInfo? {
        val parts = line.split("\u0000")
        if (parts.size < 6) return null

        return try {
            GitCommitInfo(
                hash = parts[0],
                shortHash = parts[1],
                author = parts[2],
                authorEmail = parts[3],
                date = parts[4].toLongOrNull() ?: 0,
                subject = parts[5],
                parentHashes = parts.getOrNull(6)?.split(" ")?.filter { it.isNotBlank() } ?: emptyList(),
                refs = parts.getOrNull(7)?.split(", ")?.filter { it.isNotBlank() } ?: emptyList()
            )
        } catch (e: Exception) {
            null
        }
    }

    actual suspend fun cherryPick(commitHash: String): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val result = runGitCommand(projectPath, "cherry-pick", commitHash)
            if (result.exitCode == 0) {
                refresh(projectPath)
                GitOperationResult.Success("Cherry-picked $commitHash")
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun revert(commitHash: String): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val result = runGitCommand(projectPath, "revert", "--no-edit", commitHash)
            if (result.exitCode == 0) {
                refresh(projectPath)
                GitOperationResult.Success("Reverted $commitHash")
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    // ===== Stash Implementation =====

    actual suspend fun stash(message: String?, includeUntracked: Boolean): GitOperationResult =
        withContext(Dispatchers.IO) {
            val projectPath = currentProjectPath
                ?: return@withContext GitOperationResult.Error("No project selected")

            _isLoading.value = true
            try {
                val args = mutableListOf("stash", "push")
                if (includeUntracked) {
                    args.add("-u")
                }
                if (message != null) {
                    args.add("-m")
                    args.add(message)
                }

                val result = runGitCommand(projectPath, *args.toTypedArray())
                if (result.exitCode == 0) {
                    getStatus()
                    refreshStashList()
                    GitOperationResult.Success(result.output.trim().ifEmpty { "Stashed changes" })
                } else {
                    val errorMsg = result.error.ifEmpty { result.output }.trim()
                    _lastError.value = errorMsg
                    GitOperationResult.Error(errorMsg, result.exitCode)
                }
            } finally {
                _isLoading.value = false
            }
        }

    actual suspend fun stashPop(index: Int): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val result = runGitCommand(projectPath, "stash", "pop", "stash@{$index}")
            if (result.exitCode == 0) {
                getStatus()
                refreshStashList()
                GitOperationResult.Success("Popped stash@{$index}")
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun stashApply(index: Int): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val result = runGitCommand(projectPath, "stash", "apply", "stash@{$index}")
            if (result.exitCode == 0) {
                getStatus()
                GitOperationResult.Success("Applied stash@{$index}")
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun stashDrop(index: Int): GitOperationResult = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath
            ?: return@withContext GitOperationResult.Error("No project selected")

        _isLoading.value = true
        try {
            val result = runGitCommand(projectPath, "stash", "drop", "stash@{$index}")
            if (result.exitCode == 0) {
                refreshStashList()
                GitOperationResult.Success("Dropped stash@{$index}")
            } else {
                val errorMsg = result.error.ifEmpty { result.output }.trim()
                _lastError.value = errorMsg
                GitOperationResult.Error(errorMsg, result.exitCode)
            }
        } finally {
            _isLoading.value = false
        }
    }

    actual suspend fun refreshStashList(): List<GitStashInfo> = withContext(Dispatchers.IO) {
        val projectPath = currentProjectPath ?: return@withContext emptyList()

        try {
            // Format: stash@{0}: On branch: message
            val result = runGitCommand(projectPath, "stash", "list")
            if (result.exitCode != 0) {
                return@withContext emptyList()
            }

            val stashes = result.output.lines()
                .filter { it.isNotBlank() }
                .mapIndexedNotNull { index, line -> parseStashLine(index, line) }

            _stashList.value = stashes
            stashes
        } catch (e: Exception) {
            println("[GitService] Error getting stash list: ${e.message}")
            emptyList()
        }
    }

    private fun parseStashLine(index: Int, line: String): GitStashInfo? {
        // Format: stash@{0}: On branch_name: message
        // or: stash@{0}: WIP on branch_name: hash message
        val regex = Regex("""stash@\{(\d+)\}:\s*(?:(?:WIP on|On)\s+(\S+?):\s*)?(.*)""")
        val match = regex.find(line) ?: return null

        return GitStashInfo(
            index = match.groupValues[1].toIntOrNull() ?: index,
            branch = match.groupValues[2].takeIf { it.isNotBlank() },
            message = match.groupValues[3].trim()
        )
    }

    // ===== Terminal Integration Implementation =====

    actual suspend fun pullInTerminal(windowId: String) {
        val projectPath = currentProjectPath ?: return
        GitTerminalEventBus.openGitTerminal(
            command = "git pull",
            workingDirectory = projectPath,
            operationName = "Pull",
            sourceWindowId = windowId
        )
    }

    actual suspend fun pushInTerminal(windowId: String) {
        val projectPath = currentProjectPath ?: return
        GitTerminalEventBus.openGitTerminal(
            command = "git push -u origin HEAD",
            workingDirectory = projectPath,
            operationName = "Push",
            sourceWindowId = windowId
        )
    }

    actual suspend fun mergeInTerminal(windowId: String, branchName: String) {
        val projectPath = currentProjectPath ?: return
        GitTerminalEventBus.openGitTerminal(
            command = "git merge $branchName",
            workingDirectory = projectPath,
            operationName = "Merge",
            sourceWindowId = windowId
        )
    }

    actual suspend fun rebaseInTerminal(windowId: String, branchName: String) {
        val projectPath = currentProjectPath ?: return
        GitTerminalEventBus.openGitTerminal(
            command = "git rebase $branchName",
            workingDirectory = projectPath,
            operationName = "Rebase",
            sourceWindowId = windowId
        )
    }

    actual suspend fun runInTerminal(windowId: String, vararg args: String) {
        val projectPath = currentProjectPath ?: return
        val command = "git ${args.joinToString(" ")}"
        GitTerminalEventBus.openGitTerminal(
            command = command,
            workingDirectory = projectPath,
            operationName = "Git",
            sourceWindowId = windowId
        )
    }

    // ===== Private helper functions =====

    private data class GitCommandResult(
        val output: String,
        val error: String,
        val exitCode: Int
    )

    private fun checkGitAvailable(): Boolean {
        return try {
            val process = ProcessBuilder("git", "--version")
                .redirectErrorStream(true)
                .start()
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            println("[GitService] Git not available: ${e.message}")
            false
        }
    }

    private fun isGitRepo(projectPath: String): Boolean {
        return try {
            val result = runGitCommand(projectPath, "rev-parse", "--is-inside-work-tree")
            result.exitCode == 0 && result.output.trim() == "true"
        } catch (e: Exception) {
            false
        }
    }

    private fun getCurrentBranchName(projectPath: String): String? {
        return try {
            // First try to get the branch name
            val result = runGitCommand(projectPath, "rev-parse", "--abbrev-ref", "HEAD")
            if (result.exitCode == 0) {
                val branch = result.output.trim()
                if (branch.isNotEmpty() && branch != "HEAD") {
                    return branch
                }
                // If HEAD, we're in detached state - get short SHA
                val shaResult = runGitCommand(projectPath, "rev-parse", "--short", "HEAD")
                if (shaResult.exitCode == 0) {
                    shaResult.output.trim().takeIf { it.isNotEmpty() }
                } else {
                    null
                }
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun getLocalBranchList(projectPath: String): List<GitBranchInfo> {
        return try {
            // Get branches with format that includes current marker
            val result = runGitCommand(
                projectPath,
                "branch",
                "--format=%(refname:short)%(HEAD)"
            )
            if (result.exitCode != 0) return emptyList()

            result.output.lines()
                .filter { it.isNotBlank() }
                .map { line ->
                    // The line ends with * if it's the current branch
                    val isCurrent = line.endsWith("*")
                    val name = if (isCurrent) line.dropLast(1) else line
                    GitBranchInfo(name = name, isCurrent = isCurrent, isRemote = false)
                }
                .sortedWith(compareBy({ !it.isCurrent }, { it.name })) // Current branch first
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun getRemoteBranchList(projectPath: String): List<GitBranchInfo> {
        return try {
            val result = runGitCommand(
                projectPath,
                "branch",
                "-r",
                "--format=%(refname:short)"
            )
            if (result.exitCode != 0) return emptyList()

            result.output.lines()
                .filter { it.isNotBlank() }
                .filter { !it.contains("HEAD") } // Exclude origin/HEAD
                .map { name ->
                    GitBranchInfo(name = name, isCurrent = false, isRemote = true)
                }
                .sortedBy { it.name }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun runGitCommand(workingDir: String, vararg args: String): GitCommandResult {
        val process = ProcessBuilder("git", *args)
            .directory(File(workingDir))
            .start()

        val output = BufferedReader(InputStreamReader(process.inputStream)).use { it.readText() }
        val error = BufferedReader(InputStreamReader(process.errorStream)).use { it.readText() }
        val exitCode = process.waitFor()

        return GitCommandResult(output, error, exitCode)
    }

    /**
     * Parse a git remote URL (SSH or HTTPS) into an HTTPS URL for browser access.
     *
     * Supports formats:
     * - git@github.com:owner/repo.git -> https://github.com/owner/repo
     * - https://github.com/owner/repo.git -> https://github.com/owner/repo
     * - ssh://git@github.com/owner/repo.git -> https://github.com/owner/repo
     */
    private fun parseRemoteUrl(remoteUrl: String): String? {
        return try {
            when {
                // SSH format: git@github.com:owner/repo.git
                remoteUrl.startsWith("git@") -> {
                    val withoutPrefix = remoteUrl.removePrefix("git@")
                    val host = withoutPrefix.substringBefore(":")
                    val path = withoutPrefix.substringAfter(":").removeSuffix(".git")
                    "https://$host/$path"
                }
                // SSH URL format: ssh://git@github.com/owner/repo.git
                remoteUrl.startsWith("ssh://") -> {
                    val withoutProtocol = remoteUrl.removePrefix("ssh://")
                    val withoutUser = withoutProtocol.substringAfter("@")
                    val url = withoutUser.removeSuffix(".git")
                    "https://$url"
                }
                // HTTPS format: https://github.com/owner/repo.git
                remoteUrl.startsWith("https://") || remoteUrl.startsWith("http://") -> {
                    remoteUrl.removeSuffix(".git")
                }
                else -> null
            }
        } catch (e: Exception) {
            null
        }
    }
}
