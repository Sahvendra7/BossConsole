package ai.rever.boss.plugin.panel.downloads

import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.DownloadItemData
import ai.rever.boss.plugin.api.DownloadStatusData
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * ViewModel for the Downloads panel.
 */
class DownloadsViewModel(
    private val dataProvider: DownloadDataProvider
) {
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    val downloads: StateFlow<List<DownloadItemData>> = dataProvider.downloads

    fun pauseDownload(id: String) {
        scope.launch {
            dataProvider.pauseDownload(id)
        }
    }

    fun resumeDownload(id: String) {
        scope.launch {
            dataProvider.resumeDownload(id)
        }
    }

    fun cancelDownload(id: String) {
        scope.launch {
            dataProvider.cancelDownload(id)
        }
    }

    fun removeDownload(id: String) {
        scope.launch {
            dataProvider.removeDownload(id)
        }
    }

    fun clearCompleted() {
        scope.launch {
            dataProvider.clearCompleted()
        }
    }

    fun revealInFolder(path: String) {
        dataProvider.revealInFolder(path)
    }

    fun openFile(path: String) {
        dataProvider.openFile(path)
    }

    fun dispose() {
        scope.cancel()
    }
}
