package ai.rever.boss.window

import ai.rever.boss.components.plugin.panels.left_top.WindowProjectState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf

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
        println("WindowProjectStateRegistry: Registered state for window: $windowId")
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
            println("WindowProjectStateRegistry: Creating new state for window: $windowId")
            WindowProjectState(windowId)
        }

    /**
     * Unregister a window project state when the window is closed.
     */
    fun unregister(windowId: String) {
        _states.remove(windowId)
        println("WindowProjectStateRegistry: Unregistered state for window: $windowId")
    }

    /**
     * Get all registered window IDs.
     */
    fun getAllWindowIds(): Set<String> = _states.keys.toSet()
}
