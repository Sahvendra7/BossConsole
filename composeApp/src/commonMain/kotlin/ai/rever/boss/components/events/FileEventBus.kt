package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FileOpenEvent(
    val filePath: String,
    val fileName: String
)

object FileEventBus {
    private val _fileOpenEvents = MutableSharedFlow<FileOpenEvent>(
        replay = 0,  // Don't replay past events to new subscribers (new windows)
        extraBufferCapacity = 10  // Buffer up to 10 events if collector not ready yet
    )
    val fileOpenEvents: SharedFlow<FileOpenEvent> = _fileOpenEvents.asSharedFlow()
    
    suspend fun openFile(filePath: String) {
        val fileName = filePath.substringAfterLast('/').ifEmpty { "untitled" }
        _fileOpenEvents.emit(FileOpenEvent(filePath, fileName))
    }
}
