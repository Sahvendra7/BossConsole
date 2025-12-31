package ai.rever.boss.components.events

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

data class FileOpenEvent(
    val filePath: String,
    val fileName: String,
    val line: Int = 0,    // 1-based line to navigate to (0 = don't navigate)
    val column: Int = 0   // 1-based column to navigate to (0 = don't navigate)
)

object FileEventBus {
    private val _fileOpenEvents = MutableSharedFlow<FileOpenEvent>(
        replay = 0,  // Don't replay past events to new subscribers (new windows)
        extraBufferCapacity = 10  // Buffer up to 10 events if collector not ready yet
    )
    val fileOpenEvents: SharedFlow<FileOpenEvent> = _fileOpenEvents.asSharedFlow()
    
    suspend fun openFile(filePath: String, line: Int = 0, column: Int = 0) {
        val fileName = filePath.substringAfterLast('/').ifEmpty { "untitled" }
        println("[FileEventBus] openFile: $filePath:$line:$column")
        _fileOpenEvents.emit(FileOpenEvent(filePath, fileName, line, column))
    }
}
