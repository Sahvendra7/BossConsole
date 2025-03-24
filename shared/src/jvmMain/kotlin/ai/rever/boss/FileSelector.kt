package ai.rever.boss

import java.awt.FileDialog
import java.awt.Frame

class DesktopFileSelector : FileSelector {
    override suspend fun selectFile(): String? =
        FileDialog(Frame(), "Select File", FileDialog.LOAD).run {
            isVisible = true
            directory?.let { dir ->
                file?.let { file ->
                    "$dir/$file"
                }
            }
        }
}

actual fun getFileSelector(): FileSelector = DesktopFileSelector()