package ai.rever.boss

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame

class DesktopFileSelector : FileSelector {
    override suspend fun selectFile(): String? =
        withContext(Dispatchers.IO) {
            val frame = Frame()
            try {
                FileDialog(frame, "Select File", FileDialog.LOAD).run {
                    isVisible = true
                    directory?.let { dir ->
                        file?.let { file ->
                            "$dir/$file"
                        }
                    }
                }
            } finally {
                frame.dispose()
            }
        }
}

actual fun getFileSelector(): FileSelector = DesktopFileSelector()