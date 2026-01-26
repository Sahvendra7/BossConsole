package ai.rever.boss.plugin.panel.gitstatus

import ai.rever.boss.plugin.api.GitDataProvider
import ai.rever.boss.plugin.api.GitFileStatusData
import ai.rever.boss.plugin.api.GitOperationResultData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Git Status panel.
 */
class GitStatusViewModel(
    private val dataProvider: GitDataProvider
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val fileStatus: StateFlow<List<GitFileStatusData>> = dataProvider.fileStatus
    val isGitRepository: StateFlow<Boolean> = dataProvider.isGitRepository
    val isLoading: StateFlow<Boolean> = dataProvider.isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun refreshStatus() {
        scope.launch {
            dataProvider.refreshStatus()
        }
    }

    fun stage(filePath: String) {
        scope.launch {
            val result = dataProvider.stage(filePath)
            handleResult(result)
        }
    }

    fun unstage(filePath: String) {
        scope.launch {
            val result = dataProvider.unstage(filePath)
            handleResult(result)
        }
    }

    fun stageAll() {
        scope.launch {
            val result = dataProvider.stageAll()
            handleResult(result)
        }
    }

    fun unstageAll() {
        scope.launch {
            val result = dataProvider.unstageAll()
            handleResult(result)
        }
    }

    fun discardChanges(filePath: String) {
        scope.launch {
            val result = dataProvider.discardChanges(filePath)
            handleResult(result)
        }
    }

    fun openFile(filePath: String, windowId: String) {
        val projectPath = dataProvider.getCurrentProjectPath()
        if (projectPath != null) {
            val fullPath = "$projectPath/$filePath"
            dataProvider.openFile(fullPath, windowId)
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    private fun handleResult(result: GitOperationResultData) {
        when (result) {
            is GitOperationResultData.Error -> _errorMessage.value = result.message
            is GitOperationResultData.Success -> { /* Success - no action needed */ }
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
