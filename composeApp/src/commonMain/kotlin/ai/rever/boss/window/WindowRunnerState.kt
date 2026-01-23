package ai.rever.boss.window

import ai.rever.boss.run.RunConfiguration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Window-scoped state for the selected run configuration.
 * Each window maintains its own selected configuration independently.
 *
 * This allows different windows to have different configurations selected
 * in their top run bar dropdowns without affecting each other.
 */
class WindowRunnerState(val windowId: String) {
    private val _selectedConfiguration = MutableStateFlow<RunConfiguration?>(null)
    val selectedConfiguration: StateFlow<RunConfiguration?> = _selectedConfiguration.asStateFlow()

    /**
     * Select a configuration for this window.
     * This does not affect other windows' selections.
     */
    fun selectConfiguration(config: RunConfiguration?) {
        _selectedConfiguration.value = config
        println("WindowRunnerState[$windowId]: Selected configuration '${config?.name}'")
    }

    /**
     * Get the currently selected configuration.
     */
    fun currentConfiguration(): RunConfiguration? = _selectedConfiguration.value
}
