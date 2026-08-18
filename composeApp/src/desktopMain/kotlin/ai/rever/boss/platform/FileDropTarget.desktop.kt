package ai.rever.boss.platform

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.awtTransferable
import java.awt.datatransfer.DataFlavor
import java.io.File

private val logger = BossLogger.forComponent("FileDropTarget")

actual fun Modifier.bossFileDropTarget(onFilesDropped: (List<String>) -> Unit): Modifier =
    composed {
        val target =
            remember(onFilesDropped) {
                object : DragAndDropTarget {
                    override fun onDrop(event: DragAndDropEvent): Boolean {
                        val paths = event.filePaths()
                        if (paths.isEmpty()) return false
                        onFilesDropped(paths)
                        return true
                    }
                }
            }
        dragAndDropTarget(
            // Answered for every drag that crosses this composable, including drags of BOSS's
            // own tabs, so it has to be cheap and it has to say no to anything that is not a
            // file. Returning true here is what makes onDrop reachable at all.
            shouldStartDragAndDrop = { event -> event.carriesFiles() },
            target = target,
        )
    }

/** Whether [this] is an OS file drag, without deserialising the payload. */
@OptIn(ExperimentalComposeUiApi::class)
private fun DragAndDropEvent.carriesFiles(): Boolean =
    runCatching {
        awtTransferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)
    }.getOrDefault(false)

/**
 * The dropped files as absolute paths, or empty if the payload is not readable.
 *
 * `getTransferData` reaches across to the source application and is documented to throw, so a
 * malformed or already-revoked drag must not take the UI thread down with it - a failed drop
 * is a no-op, not a crash.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Suppress("TooGenericExceptionCaught") // A drag source is another process; any failure is a no-op.
private fun DragAndDropEvent.filePaths(): List<String> =
    try {
        val transferable = awtTransferable
        if (!transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            emptyList()
        } else {
            @Suppress("UNCHECKED_CAST")
            val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as? List<File>
            files.orEmpty().map { it.absolutePath }
        }
    } catch (e: Exception) {
        logger.warn(LogCategory.FILE, "Could not read a dropped file list", error = e)
        emptyList()
    }
