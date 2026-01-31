package ai.rever.boss.plugin.sandbox.notification

import ai.rever.boss.plugin.sandbox.PluginSandboxListener
import ai.rever.boss.plugin.sandbox.PluginSandboxManager
import ai.rever.boss.plugin.sandbox.PluginSandboxManagerImpl
import ai.rever.boss.plugin.sandbox.notification.PluginNotificationService
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Connects sandbox lifecycle events to the notification system.
 *
 * This listener bridges [PluginSandboxManager] events with [BossPluginNotificationService]
 * to show toast notifications when plugins encounter issues, restart, or get disabled.
 *
 * Usage:
 * ```kotlin
 * val toastState = PluginToastState(scope)
 * val notificationService = BossPluginNotificationService(
 *     toastController = toastState,
 *     onDisablePlugin = { sandboxManager.disablePlugin(it) },
 *     onEnablePlugin = { sandboxManager.enablePlugin(it) }
 * )
 * val listener = PluginSandboxNotificationListener(notificationService)
 * (sandboxManager as PluginSandboxManagerImpl).addListener(listener)
 * ```
 *
 * @param notificationService The notification service to use for displaying notifications
 */
class PluginSandboxNotificationListener(
    private val notificationService: PluginNotificationService
) : PluginSandboxListener {

    private val logger = BossLogger.forComponent("PluginSandboxNotificationListener")

    // Track restart attempts for showing attempt number in notifications
    private val restartAttempts = mutableMapOf<String, Int>()

    override fun onPluginRestarting(pluginId: String) {
        val attempt = restartAttempts.getOrDefault(pluginId, 0) + 1
        restartAttempts[pluginId] = attempt

        logger.debug(LogCategory.SYSTEM, "Plugin restarting notification", mapOf(
            "pluginId" to pluginId,
            "attempt" to attempt
        ))

        notificationService.notifyPluginRestarting(pluginId, attempt)
    }

    override fun onPluginRestarted(pluginId: String) {
        // Reset attempt counter on successful restart
        restartAttempts.remove(pluginId)

        logger.debug(LogCategory.SYSTEM, "Plugin restarted notification", mapOf(
            "pluginId" to pluginId
        ))

        notificationService.notifyPluginRestartSuccess(pluginId)
    }

    override fun onPluginDisabled(pluginId: String) {
        // Clear attempt counter
        restartAttempts.remove(pluginId)

        logger.debug(LogCategory.SYSTEM, "Plugin disabled notification", mapOf(
            "pluginId" to pluginId
        ))

        notificationService.notifyPluginDisabled(pluginId)
    }

    override fun onPluginError(pluginId: String, error: Throwable) {
        logger.debug(LogCategory.SYSTEM, "Plugin error notification", mapOf(
            "pluginId" to pluginId,
            "error" to (error.message ?: "Unknown error")
        ))

        notificationService.notifyPluginError(pluginId, error)
    }
}

/**
 * Factory function to create a fully wired notification system for plugin sandboxes.
 *
 * This creates:
 * - A [PluginToastState] for managing the toast queue
 * - A [BossPluginNotificationService] that uses the toast state
 * - A [PluginSandboxNotificationListener] that connects sandbox events to notifications
 *
 * @param scope CoroutineScope for toast timing
 * @param sandboxManager The sandbox manager to connect to (must be PluginSandboxManagerImpl)
 * @param onShowErrorDetails Optional callback when user wants to see error details
 * @return The created [PluginToastState] for use in UI
 */
fun createPluginNotificationSystem(
    scope: CoroutineScope,
    sandboxManager: PluginSandboxManager,
    onShowErrorDetails: (pluginId: String, error: Throwable) -> Unit = { _, _ -> }
): PluginToastState {
    val toastState = PluginToastState(scope)

    val notificationService = BossPluginNotificationService(
        toastController = toastState,
        onDisablePlugin = { pluginId ->
            scope.launchCatching {
                sandboxManager.disablePlugin(pluginId)
            }
        },
        onEnablePlugin = { pluginId ->
            scope.launchCatching {
                sandboxManager.enablePlugin(pluginId)
            }
        },
        onShowErrorDetails = onShowErrorDetails
    )

    val listener = PluginSandboxNotificationListener(notificationService)

    // Register the listener if the manager supports it
    if (sandboxManager is PluginSandboxManagerImpl) {
        sandboxManager.addListener(listener)
    }

    return toastState
}

/**
 * Extension function to launch a suspend function and catch exceptions.
 */
private fun CoroutineScope.launchCatching(block: suspend () -> Unit) {
    this.launch {
        try {
            block()
        } catch (e: Exception) {
            // Log but don't propagate - notification actions should be fire-and-forget
            BossLogger.forComponent("PluginNotifications")
                .warn(LogCategory.SYSTEM, "Notification action failed", error = e)
        }
    }
}
