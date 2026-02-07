package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.ApplicationEvent
import ai.rever.boss.plugin.api.ApplicationEventBus
import ai.rever.boss.plugin.api.AuthEvent
import ai.rever.boss.plugin.api.CustomPluginEvent
import ai.rever.boss.plugin.api.FileChangeEvent
import ai.rever.boss.plugin.api.PluginLifecycleEvent
import ai.rever.boss.plugin.api.ProjectChangeEvent
import ai.rever.boss.plugin.api.TabEvent
import ai.rever.boss.plugin.api.WindowFocusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.filterIsInstance

/**
 * Desktop implementation of ApplicationEventBus factory.
 */
actual fun createApplicationEventBus(scope: CoroutineScope): ApplicationEventBus {
    return ApplicationEventBusImpl.getInstance(scope)
}

/**
 * Desktop implementation of ApplicationEventBus.
 *
 * This is a singleton that manages application-wide event distribution.
 * All events are broadcast to all subscribers via SharedFlow.
 */
class ApplicationEventBusImpl private constructor(
    private val scope: CoroutineScope
) : ApplicationEventBus {

    companion object {
        @Volatile
        private var instance: ApplicationEventBusImpl? = null

        fun getInstance(scope: CoroutineScope): ApplicationEventBusImpl {
            return instance ?: synchronized(this) {
                instance ?: ApplicationEventBusImpl(scope).also { instance = it }
            }
        }

        /**
         * Get the current instance if it exists.
         * Used by internal components to publish events.
         */
        fun getInstanceOrNull(): ApplicationEventBusImpl? = instance
    }

    // Replay = 0 means events are only delivered to active subscribers
    // Buffer = 64 should handle most burst scenarios
    private val _events = MutableSharedFlow<ApplicationEvent>(
        replay = 0,
        extraBufferCapacity = 64
    )

    override fun events(): Flow<ApplicationEvent> = _events.asSharedFlow()

    override fun <T : ApplicationEvent> eventsOfType(eventType: Class<T>): Flow<T> {
        @Suppress("UNCHECKED_CAST")
        return when (eventType) {
            FileChangeEvent::class.java -> _events.filterIsInstance<FileChangeEvent>() as Flow<T>
            ProjectChangeEvent::class.java -> _events.filterIsInstance<ProjectChangeEvent>() as Flow<T>
            WindowFocusEvent::class.java -> _events.filterIsInstance<WindowFocusEvent>() as Flow<T>
            PluginLifecycleEvent::class.java -> _events.filterIsInstance<PluginLifecycleEvent>() as Flow<T>
            TabEvent::class.java -> _events.filterIsInstance<TabEvent>() as Flow<T>
            AuthEvent::class.java -> _events.filterIsInstance<AuthEvent>() as Flow<T>
            CustomPluginEvent::class.java -> _events.filterIsInstance<CustomPluginEvent>() as Flow<T>
            else -> _events.filterIsInstance(eventType.kotlin)
        }
    }

    override fun publish(event: ApplicationEvent) {
        // Only allow plugins to publish custom events
        // System events should only be published internally
        if (event is CustomPluginEvent) {
            _events.tryEmit(event)
        }
    }

    /**
     * Internal method for the host application to publish system events.
     * This is not exposed through the public interface.
     */
    internal fun publishInternal(event: ApplicationEvent) {
        _events.tryEmit(event)
    }
}
