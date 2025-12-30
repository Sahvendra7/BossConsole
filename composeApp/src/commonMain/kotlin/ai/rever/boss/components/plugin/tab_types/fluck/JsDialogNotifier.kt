package ai.rever.boss.components.plugin.tab_types.fluck

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Notifier for JavaScript dialog events from JxBrowser.
 *
 * Since JxBrowser callbacks run on a separate thread and Compose dialogs
 * must be in the composition tree, we use this flow-based approach:
 * 1. JxBrowser callback calls tell.ok() immediately (prevents freeze)
 * 2. Emits an event to this notifier
 * 3. Browser composable observes this flow and shows BOSS-styled dialog
 */
object JsDialogNotifier {

    /**
     * Types of JavaScript dialog events
     */
    sealed class JsDialogEvent {
        /** JavaScript alert() was auto-accepted */
        data class Alert(val title: String, val message: String) : JsDialogEvent()

        /** JavaScript confirm() was auto-confirmed */
        data class Confirm(val title: String, val message: String) : JsDialogEvent()

        /** JavaScript prompt() was auto-accepted with default value */
        data class Prompt(val title: String, val message: String, val value: String) : JsDialogEvent()
    }

    private val _dialogEvents = MutableSharedFlow<JsDialogEvent>(
        replay = 0,
        extraBufferCapacity = 5
    )

    /**
     * Flow of JS dialog events.
     * Subscribe to this to show BOSS-styled informational dialogs.
     */
    val dialogEvents: SharedFlow<JsDialogEvent> = _dialogEvents.asSharedFlow()

    /**
     * Notify that an alert was auto-accepted.
     */
    fun notifyAlert(title: String, message: String) {
        _dialogEvents.tryEmit(JsDialogEvent.Alert(title, message))
    }

    /**
     * Notify that a confirm was auto-confirmed.
     */
    fun notifyConfirm(title: String, message: String) {
        _dialogEvents.tryEmit(JsDialogEvent.Confirm(title, message))
    }

    /**
     * Notify that a prompt was auto-accepted with a value.
     */
    fun notifyPrompt(title: String, message: String, value: String) {
        _dialogEvents.tryEmit(JsDialogEvent.Prompt(title, message, value))
    }
}
