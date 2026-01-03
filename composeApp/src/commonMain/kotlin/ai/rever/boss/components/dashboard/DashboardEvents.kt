package ai.rever.boss.components.dashboard

import ai.rever.boss.dashboard.SplitTemplate
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Event bus for Dashboard actions that need to be handled at a higher level (BossApp).
 * Similar to MenuActionsHandler, this decouples Dashboard UI from window-level operations.
 */
object DashboardEvents {

    /**
     * Event for applying a split template.
     * The String is the window ID, SplitTemplate is the template to apply.
     */
    private val _applySplitTemplateEvents = MutableSharedFlow<Pair<String, SplitTemplate>>(extraBufferCapacity = 10)
    val applySplitTemplateEvents: SharedFlow<Pair<String, SplitTemplate>> = _applySplitTemplateEvents.asSharedFlow()

    /**
     * Event for opening a file by path.
     * Pair: (windowId, filePath)
     */
    private val _openFileEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 10)
    val openFileEvents: SharedFlow<Pair<String, String>> = _openFileEvents.asSharedFlow()

    /**
     * Event for opening a URL.
     * Pair: (windowId, url)
     */
    private val _openUrlEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 10)
    val openUrlEvents: SharedFlow<Pair<String, String>> = _openUrlEvents.asSharedFlow()

    /**
     * Event for activating a plugin.
     * Pair: (windowId, pluginId)
     */
    private val _activatePluginEvents = MutableSharedFlow<Pair<String, String>>(extraBufferCapacity = 10)
    val activatePluginEvents: SharedFlow<Pair<String, String>> = _activatePluginEvents.asSharedFlow()

    /**
     * Trigger a split template application.
     */
    fun triggerApplySplitTemplate(windowId: String, template: SplitTemplate) {
        _applySplitTemplateEvents.tryEmit(Pair(windowId, template))
    }

    /**
     * Trigger opening a file.
     */
    fun triggerOpenFile(windowId: String, filePath: String) {
        _openFileEvents.tryEmit(Pair(windowId, filePath))
    }

    /**
     * Trigger opening a URL.
     */
    fun triggerOpenUrl(windowId: String, url: String) {
        _openUrlEvents.tryEmit(Pair(windowId, url))
    }

    /**
     * Trigger activating a plugin.
     */
    fun triggerActivatePlugin(windowId: String, pluginId: String) {
        _activatePluginEvents.tryEmit(Pair(windowId, pluginId))
    }
}
