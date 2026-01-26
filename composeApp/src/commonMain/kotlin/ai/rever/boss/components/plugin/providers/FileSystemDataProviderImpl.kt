package ai.rever.boss.components.plugin.providers

import ai.rever.boss.components.events.FileEventBus
import ai.rever.boss.components.plugin.panels.left_top.directoryHasChildren
import ai.rever.boss.components.plugin.panels.left_top.scanDirectory
import ai.rever.boss.components.plugin.panels.left_top.scanDirectoryWithDepth as platformScanDirectoryWithDepth
import ai.rever.boss.plugin.panel.codebase.FileNode
import ai.rever.boss.plugin.panel.codebase.FileSystemDataProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Implementation of FileSystemDataProvider that wraps platform-specific file operations.
 * This allows plugins to access file system without direct platform coupling.
 */
class FileSystemDataProviderImpl : FileSystemDataProvider {

    private val ioScope = CoroutineScope(Dispatchers.IO)

    override suspend fun scanDirectory(path: String): FileNode? {
        return ai.rever.boss.components.plugin.panels.left_top.scanDirectory(path)
    }

    override suspend fun scanDirectoryWithDepth(path: String, maxDepth: Int, startDepth: Int): FileNode? {
        return platformScanDirectoryWithDepth(path, maxDepth, startDepth)
    }

    override fun directoryHasChildren(path: String): Boolean {
        return ai.rever.boss.components.plugin.panels.left_top.directoryHasChildren(path)
    }

    override fun openFile(path: String, windowId: String) {
        ioScope.launch {
            FileEventBus.openFile(path, sourceWindowId = windowId)
        }
    }
}
