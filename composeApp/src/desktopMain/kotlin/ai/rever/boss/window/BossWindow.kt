package ai.rever.boss.window

import BossDarkSurface
import ai.rever.boss.BossAppWithAuth
import ai.rever.boss.components.window_panel.components.main_window_panels.createBossAppContext
import ai.rever.boss.utils.WindowFocusManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.Color

/**
 * Individual BOSS window composable
 *
 * Creates a single window instance with its own independent state, tabs, and context.
 * Each window has its own BossAppWithAuth instance, allowing multiple independent
 * workspaces to coexist.
 *
 * @param windowState The state for this window (position, size, tabs, etc.)
 * @param onCloseRequest Callback when the window should be closed
 */
@Composable
fun ApplicationScope.BossWindow(
    windowState: BossWindowState,
    onCloseRequest: () -> Unit
) {
    // Remember window state for Compose Window
    val composeWindowState = rememberWindowState(
        position = windowState.position ?: WindowPosition.Aligned(Alignment.Center),
        size = windowState.size
    )

    Window(
        onCloseRequest = onCloseRequest,
        title = windowState.title,
        state = composeWindowState
    ) {
        // Set window appearance properties
        window.background = Color(BossDarkSurface.value.toInt())
        window.rootPane.putClientProperty("apple.awt.fullWindowContent", true)
        window.rootPane.putClientProperty("apple.awt.transparentTitleBar", true)
        window.rootPane.putClientProperty("apple.awt.windowTitleVisible", false)

        // Register window for focus management (deep links, etc.)
        WindowFocusManager.registerWindow(window)

        // Note: All keyboard shortcuts (Cmd+N, Cmd+T, Cmd+W, etc.) are handled
        // at the Compose level in BossApp.kt using onPreviewKeyEvent.
        // AWT's registerKeyboardAction doesn't work with Compose's event system.

        // Create independent component context for this window
        // Each window gets its own Decompose context tree
        with(createBossAppContext) {
            BossAppWithAuth(windowId = windowState.id)
        }
    }
}

/**
 * Update window title
 *
 * Allows dynamic window title updates based on content
 *
 * @param newTitle The new title for the window
 */
fun BossWindowState.updateTitle(newTitle: String) {
    this.title = newTitle
}
