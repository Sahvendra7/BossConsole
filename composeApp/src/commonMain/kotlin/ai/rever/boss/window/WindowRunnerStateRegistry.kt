package ai.rever.boss.window

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateMapOf

/**
 * CompositionLocal to provide WindowRunnerState to descendant composables.
 * This allows components like BossTopRunBar to access the window-specific runner state.
 */
val LocalWindowRunnerState = compositionLocalOf<WindowRunnerState?> { null }

/**
 * Registry for per-window runner states.
 * Each window has its own independent selected configuration while sharing
 * the global configurations list and detected configurations.
 *
 * Pattern matches WindowProjectStateRegistry for consistency.
 */
object WindowRunnerStateRegistry {
    private val _states = mutableStateMapOf<String, WindowRunnerState>()

    /**
     * Register a new window runner state.
     */
    fun register(windowId: String): WindowRunnerState {
        val state = WindowRunnerState(windowId)
        _states[windowId] = state
        println("WindowRunnerStateRegistry: Registered state for window: $windowId")
        return state
    }

    /**
     * Get the runner state for a window.
     */
    fun get(windowId: String): WindowRunnerState? = _states[windowId]

    /**
     * Get or create the runner state for a window.
     */
    fun getOrCreate(windowId: String): WindowRunnerState =
        _states.getOrPut(windowId) {
            println("WindowRunnerStateRegistry: Creating new state for window: $windowId")
            WindowRunnerState(windowId)
        }

    /**
     * Unregister a window runner state when the window is closed.
     */
    fun unregister(windowId: String) {
        _states.remove(windowId)
        println("WindowRunnerStateRegistry: Unregistered state for window: $windowId")
    }

    /**
     * Get all registered window IDs.
     */
    fun getAllWindowIds(): Set<String> = _states.keys.toSet()
}
