package ai.rever.boss.platform

import androidx.compose.runtime.Composable

// Platform-specific file picker for selecting directories
@Composable
expect fun rememberDirectoryPicker(
    onDirectorySelected: (path: String?) -> Unit
): DirectoryPicker

interface DirectoryPicker {
    fun pickDirectory()
}

// Platform-specific file picker for selecting files
@Composable
expect fun rememberFilePicker(
    onFileSelected: (path: String?, content: String?) -> Unit,
    fileExtensions: List<String> = listOf("json")
): FilePicker

interface FilePicker {
    fun pickFile()
}

