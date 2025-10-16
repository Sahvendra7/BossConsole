package ai.rever.boss.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

@Composable
actual fun rememberFilePicker(
    onFileSelected: (path: String?, content: String?) -> Unit,
    fileExtensions: List<String>
): FilePicker {
    return remember {
        DesktopFilePicker(onFileSelected, fileExtensions)
    }
}

class DesktopFilePicker(
    private val onFileSelected: (path: String?, content: String?) -> Unit,
    private val fileExtensions: List<String>
) : FilePicker {
    override fun pickFile() {
        try {
            val fileDialog = FileDialog(null as Frame?, "Select Configuration File", FileDialog.LOAD).apply {
                // Set file filter for JSON files
                if (fileExtensions.isNotEmpty()) {
                    setFilenameFilter { _, name ->
                        fileExtensions.any { name.endsWith(".$it", ignoreCase = true) }
                    }
                }
                isVisible = true
            }
            
            val selectedFile = fileDialog.file
            val selectedDir = fileDialog.directory
            
            if (selectedFile != null && selectedDir != null) {
                val file = File(selectedDir, selectedFile)
                val content = file.readText()
                onFileSelected(file.absolutePath, content)
            } else {
                onFileSelected(null, null)
            }
        } catch (e: Exception) {
            onFileSelected(null, null)
        }
    }
}
