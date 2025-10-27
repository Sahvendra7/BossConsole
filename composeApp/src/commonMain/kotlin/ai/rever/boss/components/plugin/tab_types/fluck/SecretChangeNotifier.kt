package ai.rever.boss.components.plugin.tab_types.fluck

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Central notification system for secret changes across the application.
 *
 * This notifier provides a way for different components to communicate
 * when secrets are created, updated, or deleted, ensuring all UI components
 * stay synchronized.
 *
 * Components that modify secrets should call notify*() methods.
 * Components that display secrets should observe the secretChangeEvents flow.
 *
 * Used to synchronize:
 * - SecretManagerViewModel (full CRUD panel)
 * - UserSecretListViewModel (read-only panel)
 * - BrowserSecretIntegrationViewModel (browser auto-fill)
 */
object SecretChangeNotifier {

    /**
     * Types of secret change events
     */
    sealed class SecretChangeEvent {
        /** A new secret was created */
        data class Created(val secretId: String, val website: String) : SecretChangeEvent()

        /** An existing secret was updated */
        data class Updated(val secretId: String) : SecretChangeEvent()

        /** A secret was deleted */
        data class Deleted(val secretId: String) : SecretChangeEvent()

        /** Multiple secrets changed (bulk operation or unknown) */
        object Refresh : SecretChangeEvent()
    }

    private val _secretChangeEvents = MutableSharedFlow<SecretChangeEvent>(
        replay = 0,  // Don't replay past events to new subscribers
        extraBufferCapacity = 10  // Buffer up to 10 events if consumers are slow
    )

    /**
     * Flow of secret change events.
     * Subscribe to this to be notified of secret changes.
     */
    val secretChangeEvents: SharedFlow<SecretChangeEvent> = _secretChangeEvents.asSharedFlow()

    /**
     * Notify observers that a secret was created.
     */
    suspend fun notifySecretCreated(secretId: String, website: String) {
        println("📢 [SecretChangeNotifier] Notifying secret created: $secretId ($website)")
        _secretChangeEvents.emit(SecretChangeEvent.Created(secretId, website))
    }

    /**
     * Notify observers that a secret was updated.
     */
    suspend fun notifySecretUpdated(secretId: String) {
        println("📢 [SecretChangeNotifier] Notifying secret updated: $secretId")
        _secretChangeEvents.emit(SecretChangeEvent.Updated(secretId))
    }

    /**
     * Notify observers that a secret was deleted.
     */
    suspend fun notifySecretDeleted(secretId: String) {
        println("📢 [SecretChangeNotifier] Notifying secret deleted: $secretId")
        _secretChangeEvents.emit(SecretChangeEvent.Deleted(secretId))
    }

    /**
     * Notify observers to refresh their secret lists (bulk operation or unknown change).
     */
    suspend fun notifyRefresh() {
        println("📢 [SecretChangeNotifier] Notifying refresh")
        _secretChangeEvents.emit(SecretChangeEvent.Refresh)
    }
}
