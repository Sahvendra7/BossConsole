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

// For platforms that don't support native file pickers
class NoOpDirectoryPicker : DirectoryPicker {
    override fun pickDirectory() {
        // No-op for platforms without file picker support
    }
}