package ai.rever.boss.components.plugin.panels.bottom.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/**
 * Android stub implementation - tabbed terminal not supported on mobile.
 */
@Composable
actual fun TabbedTerminalContent(
    onExit: () -> Unit,
    onShowSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Terminal is not supported on Android",
            color = Color.Gray
        )
    }
}

/**
 * Android stub implementation - terminal not supported on mobile.
 */
@Composable
actual fun TerminalContent(
    terminalId: String?,
    initialCommand: String?,
    onExit: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Terminal is not supported on Android",
            color = Color.Gray
        )
    }
}

/**
 * Android stub implementation - persistent tabbed terminal not supported on mobile.
 */
@Composable
actual fun PersistentTabbedTerminalContent(
    terminalId: String,
    initialCommand: String?,
    onExit: () -> Unit,
    onShowSettings: () -> Unit,
    onTitleChange: ((String) -> Unit)?
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Terminal is not supported on Android",
            color = Color.Gray
        )
    }
}
