package ai.rever.boss.plugin.panel.gitlog

import ai.rever.boss.plugin.api.GitCommitInfoData
import ai.rever.boss.plugin.api.GitDataProvider
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
 * ViewModel for the Git Log panel.
 */
class GitLogViewModel(
    private val dataProvider: GitDataProvider
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val commitLog: StateFlow<List<GitCommitInfoData>> = dataProvider.commitLog
    val isGitRepository: StateFlow<Boolean> = dataProvider.isGitRepository
    val isLoading: StateFlow<Boolean> = dataProvider.isLoading

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _successMessage = MutableStateFlow<String?>(null)
    val successMessage: StateFlow<String?> = _successMessage.asStateFlow()

    fun refreshLog() {
        scope.launch {
            dataProvider.refreshLog()
        }
    }

    fun cherryPick(commitHash: String, shortHash: String) {
        scope.launch {
            val result = dataProvider.cherryPick(commitHash)
            handleResult(result, "Cherry-picked $shortHash")
        }
    }

    fun revert(commitHash: String, shortHash: String) {
        scope.launch {
            val result = dataProvider.revert(commitHash)
            handleResult(result, "Reverted $shortHash")
        }
    }

    fun checkout(commitHash: String, shortHash: String) {
        scope.launch {
            val result = dataProvider.checkout(commitHash)
            handleResult(result, "Checked out $shortHash")
        }
    }

    fun clearMessages() {
        _errorMessage.value = null
        _successMessage.value = null
    }

    fun showSuccess(message: String) {
        _successMessage.value = message
    }

    private fun handleResult(result: GitOperationResultData, successMsg: String) {
        when (result) {
            is GitOperationResultData.Error -> _errorMessage.value = result.message
            is GitOperationResultData.Success -> _successMessage.value = successMsg
        }
    }

    fun dispose() {
        scope.cancel()
    }
}
