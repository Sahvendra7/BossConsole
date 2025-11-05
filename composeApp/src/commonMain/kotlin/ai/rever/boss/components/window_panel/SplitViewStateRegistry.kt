package ai.rever.boss.components.window_panel

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Global registry for tracking SplitViewState instances across all open windows.
 * Used by features like Top of Mind that need to access tabs from all windows.
 *
 * Thread-safe singleton that maintains a registry of all active window states.
 */
object SplitViewStateRegistry {
    private val _states = MutableStateFlow<Map<String, SplitViewState>>(emptyMap())

    /**
     * StateFlow of all registered window states.
     * Emits a new map whenever states are registered or unregistered.
     */
    val states: StateFlow<Map<String, SplitViewState>> = _states.asStateFlow()

    /**
     * Register a SplitViewState for a specific window.
     * Should be called when a window is created.
     *
     * @param windowId Unique identifier for the window
     * @param state The SplitViewState instance for this window
     */
    fun register(windowId: String, state: SplitViewState) {
        println("[SplitViewStateRegistry] Registering state for window: $windowId")
        _states.value = _states.value + (windowId to state)
        println("[SplitViewStateRegistry] Total registered windows: ${_states.value.size}")
    }

    /**
     * Unregister a SplitViewState for a specific window.
     * Should be called when a window is closed.
     *
     * @param windowId Unique identifier for the window
     */
    fun unregister(windowId: String) {
        println("[SplitViewStateRegistry] Unregistering state for window: $windowId")
        _states.value = _states.value - windowId
        println("[SplitViewStateRegistry] Total registered windows: ${_states.value.size}")
    }

    /**
     * Get all currently registered window states.
     *
     * @return Map of windowId to SplitViewState
     */
    fun getAllStates(): Map<String, SplitViewState> {
        return _states.value
    }

    /**
     * Get the SplitViewState for a specific window.
     *
     * @param windowId Unique identifier for the window
     * @return The SplitViewState for the window, or null if not found
     */
    fun getState(windowId: String): SplitViewState? {
        return _states.value[windowId]
    }

    /**
     * Check if a window is registered.
     *
     * @param windowId Unique identifier for the window
     * @return true if the window is registered, false otherwise
     */
    fun isRegistered(windowId: String): Boolean {
        return _states.value.containsKey(windowId)
    }
}
