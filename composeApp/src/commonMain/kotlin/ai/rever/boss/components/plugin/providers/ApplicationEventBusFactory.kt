package ai.rever.boss.components.plugin.providers

import ai.rever.boss.plugin.api.ApplicationEventBus
import kotlinx.coroutines.CoroutineScope

/**
 * Factory function to create platform-specific ApplicationEventBus.
 * Desktop implementation provides a singleton event bus.
 *
 * @param scope CoroutineScope for event processing
 * @return ApplicationEventBus implementation
 */
expect fun createApplicationEventBus(scope: CoroutineScope): ApplicationEventBus
