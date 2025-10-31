package ai.rever.boss.window

import androidx.compose.runtime.mutableStateListOf
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPosition
import java.util.UUID

/**
 * Central manager for all BOSS windows
 *
 * Manages the lifecycle of multiple application windows, including creation,
 * removal, and tab transfer between windows. Follows macOS-style lifecycle
 * where the app stays running even when all windows are closed.
 */
object WindowManager {

    /**
     * List of all open windows
     * Using SnapshotStateList for reactive Compose updates
     */
    private val _windows = mutableStateListOf<BossWindowState>()

    /**
     * Read-only access to the list of windows
     */
    val windows: List<BossWindowState>
        get() = _windows

    /**
     * Create a new window
     *
     * @param position Window position (null for default cascade)
     * @param size Window size (default 1280x800)
     * @return The newly created window state
     */
    fun createNewWindow(
        position: WindowPosition? = null,
        size: DpSize = DpSize(1280.dp, 800.dp)
    ): BossWindowState {
        val windowId = UUID.randomUUID().toString()

        // Calculate cascade position if not specified
        val windowPosition = position ?: calculateCascadePosition()

        val windowState = BossWindowState(
            id = windowId,
            title = "BOSS - Business Operating System Service",
            position = windowPosition,
            size = size
        )

        _windows.add(windowState)
        println("Created new window: $windowId (total windows: ${_windows.size})")

        return windowState
    }

    /**
     * Close a window by ID
     *
     * @param windowId The ID of the window to close
     */
    fun closeWindow(windowId: String) {
        val window = _windows.find { it.id == windowId }
        if (window != null) {
            _windows.remove(window)
            println("Closed window: $windowId (remaining windows: ${_windows.size})")
        }
    }

    /**
     * Close window if it has no tabs
     *
     * Note: Tabs are now managed by BossApp/SplitViewState, not WindowManager.
     * This method is kept for backward compatibility but no longer checks tabs.
     * Windows should be closed explicitly via closeWindow() or by user action.
     *
     * @param windowId The window ID to potentially close
     */
    @Deprecated("Tabs are managed by BossApp/SplitViewState, not WindowManager")
    fun closeWindowIfEmpty(windowId: String) {
        // No-op: tab management moved to BossApp/SplitViewState
        // Windows are closed explicitly or by user action
    }

    /**
     * Move a tab to a new window
     *
     * Note: Tab management moved to BossApp/SplitViewState.
     * This method now just creates an empty window.
     * The caller should handle moving the tab through BossApp/SplitViewState.
     *
     * @param sourceWindowId The window the tab is currently in (for logging)
     * @return The newly created window
     */
    @Deprecated("Tab management moved to BossApp/SplitViewState")
    fun moveTabToNewWindow(
        sourceWindowId: String
    ): BossWindowState {
        println("Creating new window (tab will be moved by caller)")

        // Create new window - tab will be moved by caller through BossApp
        val position = calculateCascadePosition()
        return createNewWindow(position = position)
    }

    /**
     * Check if the app should quit
     *
     * For macOS-style behavior, always return false to keep app running
     * even when all windows are closed.
     *
     * @return false to prevent app quit
     */
    fun shouldQuitApp(): Boolean {
        return false // Keep app running (macOS style)
    }

    /**
     * Get window by ID
     *
     * @param windowId The window ID to search for
     * @return The window state, or null if not found
     */
    fun getWindow(windowId: String): BossWindowState? {
        return _windows.find { it.id == windowId }
    }

    /**
     * Calculate cascade position for new windows
     *
     * Each new window is offset by 30dp from the previous window position.
     *
     * @return Window position for cascade effect
     */
    private fun calculateCascadePosition(): WindowPosition {
        val cascadeOffset = _windows.size * 30
        return WindowPosition(
            x = (100 + cascadeOffset).dp,
            y = (100 + cascadeOffset).dp
        )
    }

    /**
     * Get total number of open windows
     */
    val windowCount: Int
        get() = _windows.size
}

/**
 * State for a single BOSS window
 *
 * Note: Tabs are managed by each window's BossApp/SplitViewState,
 * not by WindowManager. This class only tracks window-level properties.
 *
 * @property id Unique identifier for this window
 * @property title Window title
 * @property position Window position on screen
 * @property size Window size
 */
data class BossWindowState(
    val id: String,
    var title: String,
    val position: WindowPosition?,
    val size: DpSize
)
