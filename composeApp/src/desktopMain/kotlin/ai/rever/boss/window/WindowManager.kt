package ai.rever.boss.window

import ai.rever.boss.components.registery.TabInfo
import ai.rever.boss.services.URLHandlerService
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
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
     * Flag to track if the first window has been created
     */
    private var firstWindowCreated = false

    /**
     * Create a new window
     *
     * @param initialTab Optional tab to open in the new window
     * @param position Window position (null for default cascade)
     * @param size Window size (default 1280x800)
     * @return The newly created window state
     */
    fun createNewWindow(
        initialTab: TabInfo? = null,
        position: WindowPosition? = null,
        size: DpSize = DpSize(1280.dp, 800.dp)
    ): BossWindowState {
        val windowId = UUID.randomUUID().toString()

        // Calculate cascade position if not specified
        val windowPosition = position ?: calculateCascadePosition()

        val tabs = mutableStateListOf<TabInfo>()
        if (initialTab != null) {
            tabs.add(initialTab)
        }

        val windowState = BossWindowState(
            id = windowId,
            title = "BOSS - Business Operating System Service",
            tabs = tabs,
            position = windowPosition,
            size = size
        )

        _windows.add(windowState)
        println("Created new window: $windowId (total windows: ${_windows.size})")

        // Mark app as ready after first window is created
        if (!firstWindowCreated) {
            firstWindowCreated = true
            println("First window created, marking app as ready for URL handling")
            URLHandlerService.markAppReady()
        }

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
     * Used when the last tab is closed via Cmd+W.
     * If the window has no tabs remaining, it will be closed.
     *
     * @param windowId The window ID to check and potentially close
     */
    fun closeWindowIfEmpty(windowId: String) {
        val window = _windows.find { it.id == windowId }
        if (window != null && window.tabs.isEmpty()) {
            closeWindow(windowId)
            println("Closed empty window: $windowId")
        }
    }

    /**
     * Move a tab to a new window
     *
     * Creates a new window and moves the specified tab there.
     * The tab should be removed from the source window by the caller.
     *
     * @param sourceWindowId The window the tab is currently in
     * @param tabInfo The tab to move
     * @return The newly created window containing the tab
     */
    fun moveTabToNewWindow(
        sourceWindowId: String,
        tabInfo: TabInfo
    ): BossWindowState {
        println("Moving tab '${tabInfo.title}' from window $sourceWindowId to new window")

        // Create new window with the tab
        val position = calculateCascadePosition()
        return createNewWindow(
            initialTab = tabInfo,
            position = position
        )
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
 * @property id Unique identifier for this window
 * @property title Window title
 * @property tabs List of tabs in this window
 * @property position Window position on screen
 * @property size Window size
 */
data class BossWindowState(
    val id: String,
    var title: String,
    val tabs: SnapshotStateList<TabInfo>,
    val position: WindowPosition?,
    val size: DpSize
)
