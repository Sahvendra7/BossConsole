package ai.rever.boss.plugin.sandbox.ui

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull

/**
 * How much of each plugin's UI is currently mounted in a composition.
 *
 * This exists for ONE question, asked at exactly one moment: **has this plugin's UI actually been
 * disposed yet?** - and the answer has to be true before its classloader closes.
 *
 * Closing a plugin's tabs is not that answer. Removing a tab mutates the tab model on the EDT and
 * returns; Compose disposes the subtree on a LATER render frame, and it is that frame which runs
 * the plugin's own `onDispose` lambdas. Unloading between the two resolves those lambdas against a
 * closed loader, which is a `NoClassDefFoundError` the loader itself explains:
 *
 * > Plugin classloader for '...' is UNLOADED; refusing to resolve '...' against the host
 * > classloader. Something still referenced the plugin after it was unloaded - that reference is
 * > the bug.
 *
 * The reference in that message is the still-mounted composition. Waiting on this registry is how
 * the unload path stops racing it.
 *
 * Counted rather than flagged, because one plugin can have several surfaces up at once - a tab in
 * each of two windows, a tab and a sidebar panel - and they dispose independently.
 */
object PluginUiMountRegistry {
    private val _mounted = MutableStateFlow<Map<String, Int>>(emptyMap())

    /** Plugin id to the number of its boundaries currently mounted. Absent means none. */
    val mounted: StateFlow<Map<String, Int>> = _mounted.asStateFlow()

    /** A plugin boundary entered the composition. */
    fun onMounted(pluginId: String) {
        _mounted.update { it + (pluginId to (it[pluginId] ?: 0) + 1) }
    }

    /** A plugin boundary left the composition, for real - this runs from `onDispose`. */
    fun onDisposed(pluginId: String) {
        _mounted.update { current ->
            val next = (current[pluginId] ?: 0) - 1
            if (next <= 0) current - pluginId else current + (pluginId to next)
        }
    }

    /** Whether any of [pluginId]'s UI is mounted, or any plugin's when null. */
    fun isMounted(pluginId: String? = null): Boolean =
        if (pluginId == null) _mounted.value.isNotEmpty() else _mounted.value.containsKey(pluginId)

    /**
     * Suspend until [pluginId]'s UI is fully disposed - or every plugin's, when null.
     *
     * Returns true when it got there, false on timeout. **The timeout is not a formality**: nothing
     * guarantees a render frame will arrive. A minimised or fully occluded window may not draw at
     * all, and blocking an unload on a frame that never comes would hang the app instead of risking
     * a fault the crash boundary already contains. A caller that times out should say so and carry
     * on.
     */
    suspend fun awaitDisposed(
        pluginId: String? = null,
        timeoutMillis: Long,
    ): Boolean =
        withTimeoutOrNull(timeoutMillis) {
            _mounted.first { current ->
                if (pluginId == null) current.isEmpty() else !current.containsKey(pluginId)
            }
            true
        } ?: false

    /** For tests, and for a headless host that never mounts anything. */
    fun reset() {
        _mounted.value = emptyMap()
    }
}
