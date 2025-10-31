package ai.rever.boss.window

import BossDarkSurface
import ai.rever.boss.BossAppWithAuth
import ai.rever.boss.components.window_panel.components.main_window_panels.createBossAppContext
import ai.rever.boss.utils.WindowFocusManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.window.*
import java.awt.Color
import java.awt.Frame
import java.awt.event.KeyEvent

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
        WindowFocusManager.registerWindow(windowState.id, window)

        // macOS MenuBar - provides native menu integration
        // Note: Keyboard shortcuts (Cmd+N, Cmd+T, etc.) are handled in BossApp.kt via onPreviewKeyEvent
        MenuBar {
            // File Menu
            Menu("File") {
                Item(
                    "New Window",
                    onClick = { WindowOperations.createNewWindow() }
                )
                Item(
                    "New Tab",
                    onClick = {
                        MenuActionsHandler.triggerNewTab(windowState.id)
                    }
                )

                Separator()

                Item(
                    "Close Tab",
                    onClick = {
                        MenuActionsHandler.triggerCloseTab(windowState.id)
                    }
                )
                Item(
                    "Close Window",
                    onClick = { WindowOperations.closeWindow(windowState.id) }
                )

                Separator()

                Item(
                    "Quit BOSS",
                    onClick = { exitApplication() }
                )
            }

            // Edit Menu
            Menu("Edit") {
                Item(
                    "Cut",
                    onClick = {
                        ClipboardHelper.cut()
                    }
                )
                Item(
                    "Copy",
                    onClick = {
                        ClipboardHelper.copy()
                    }
                )
                Item(
                    "Paste",
                    onClick = {
                        ClipboardHelper.paste()
                    }
                )

                Separator()

                Item(
                    "Select All",
                    onClick = {
                        ClipboardHelper.selectAll()
                    }
                )
            }

            // View Menu
            Menu("View") {
                Item(
                    "Actual Size",
                    onClick = {
                        MenuActionsHandler.triggerActualSize(windowState.id)
                    }
                )
                Item(
                    "Zoom In",
                    onClick = {
                        MenuActionsHandler.triggerZoomIn(windowState.id)
                    }
                )
                Item(
                    "Zoom Out",
                    onClick = {
                        MenuActionsHandler.triggerZoomOut(windowState.id)
                    }
                )

                Separator()

                Item(
                    "Enter Full Screen",
                    onClick = {
                        window.extendedState = if (window.extendedState == Frame.MAXIMIZED_BOTH) {
                            Frame.NORMAL
                        } else {
                            Frame.MAXIMIZED_BOTH
                        }
                    }
                )
            }

            // Window Menu
            Menu("Window") {
                Item(
                    "Minimize",
                    onClick = {
                        window.extendedState = Frame.ICONIFIED
                    }
                )
                Item(
                    "Zoom",
                    onClick = {
                        window.extendedState = if (window.extendedState == Frame.MAXIMIZED_BOTH) {
                            Frame.NORMAL
                        } else {
                            Frame.MAXIMIZED_BOTH
                        }
                    }
                )

                Separator()

                Item(
                    "Bring All to Front",
                    onClick = {
                        // Get all AWT windows and bring BOSS windows to front
                        val allWindows = java.awt.Window.getWindows()
                        allWindows.forEach { awtWindow ->
                            if (awtWindow.isShowing) {
                                awtWindow.toFront()
                            }
                        }
                    }
                )

                // Dynamic window list
                if (WindowManager.windows.size > 1) {
                    Separator()
                    WindowManager.windows.forEachIndexed { index, win ->
                        Item(
                            text = "Window ${index + 1}",
                            onClick = {
                                // Focus would be handled here if we had window focus API
                                // For now, this shows the window in the list
                            },
                            enabled = win.id != windowState.id  // Disable current window
                        )
                    }
                }
            }
        }

        // Note: Keyboard shortcuts are ALSO handled at the Compose level in BossApp.kt
        // using onPreviewKeyEvent. The MenuBar shortcuts work alongside those handlers
        // and provide visual feedback for users discovering available shortcuts.

        // Create independent component context for this window
        // Each window gets its own Decompose context tree
        with(createBossAppContext) {
            // Only the first window should load "Last Session" workspace (Issue #129)
            val isFirstWindow = WindowManager.windowCount == 1
            BossAppWithAuth(
                windowId = windowState.id,
                isFirstWindow = isFirstWindow
            )
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
