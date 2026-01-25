package ai.rever.boss.window

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.plugin.panels.left_top.Project
import ai.rever.boss.components.plugin.panels.left_top.WindowProjectState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf

private val windowProjectStateLogger = BossLogger.forComponent("WindowProjectStateRegistry")

/**
 * Helper function to select a project using window-specific state.
 * Window state is required for multi-window support.
 *
 * @param windowProjectState The window project state (should not be null in normal operation)
 * @param project The project to select
 */
fun selectProjectInWindow(windowProjectState: WindowProjectState?, project: Project) {
    if (windowProjectState != null) {
        windowProjectState.selectProject(project)
    } else {
        windowProjectStateLogger.warn(LogCategory.UI, "selectProjectInWindow called without window state - project selection ignored")
    }
}

/**
 * CompositionLocal to provide the window ID to descendant composables.
 * This allows components to identify which window they are in (e.g., for filtering events).
 */
val LocalWindowId = compositionLocalOf<String?> { null }

/**
 * CompositionLocal to provide WindowProjectState to descendant composables.
 * This allows components like BossTopBar to access the window-specific project state.
 */
val LocalWindowProjectState = compositionLocalOf<WindowProjectState?> { null }

/**
 * Registry for per-window project states.
 * Each window has its own independent project state while sharing the global recent projects list.
 *
 * Pattern matches SplitViewStateRegistry for consistency.
 */
object WindowProjectStateRegistry {
    private val _states = mutableStateMapOf<String, WindowProjectState>()

    /**
     * Register a new window project state.
     */
    fun register(windowId: String): WindowProjectState {
        val state = WindowProjectState(windowId)
        _states[windowId] = state
        windowProjectStateLogger.debug(LogCategory.UI, "Registered state for window", mapOf("windowId" to windowId))
        return state
    }

    /**
     * Get the project state for a window.
     */
    fun get(windowId: String): WindowProjectState? = _states[windowId]

    /**
     * Get or create the project state for a window.
     */
    fun getOrCreate(windowId: String): WindowProjectState =
        _states.getOrPut(windowId) {
            windowProjectStateLogger.debug(LogCategory.UI, "Creating new state for window", mapOf("windowId" to windowId))
            WindowProjectState(windowId)
        }

    /**
     * Unregister a window project state when the window is closed.
     */
    fun unregister(windowId: String) {
        _states.remove(windowId)
        windowProjectStateLogger.debug(LogCategory.UI, "Unregistered state for window", mapOf("windowId" to windowId))
    }

    /**
     * Get all registered window IDs.
     */
    fun getAllWindowIds(): Set<String> = _states.keys.toSet()
}
