package ai.rever.boss.plugin.sandbox.ui

import ai.rever.boss.plugin.sandbox.PluginSandbox
import ai.rever.boss.plugin.logging.BossLogger
import ai.rever.boss.plugin.logging.LogCategory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.util.concurrent.ConcurrentHashMap

/**
 * Composition local for providing an error handler to child composables.
 *
 * Plugins can use this to report errors that should be tracked by the sandbox
 * but don't necessarily crash the component.
 */
val LocalPluginErrorHandler = compositionLocalOf<(Throwable) -> Unit> { { } }

/**
 * Tracks which plugins have crashed during composition.
 *
 * When a plugin crashes during composition (e.g., NoSuchMethodError), the SubcomposeLayout
 * tree is left corrupted and can't recompose. This registry is read at a level ABOVE the
 * corrupted tree (in BossMainPanelContent's key()) to force a complete teardown and rebuild,
 * allowing PluginErrorBoundary to show the fallback UI in a fresh composition.
 */
object PluginCrashRegistry {
    private val logger = BossLogger.forComponent("PluginCrashRegistry")

    private val _crashedPlugins = mutableStateOf(mapOf<String, Throwable>())

    /** Map of pluginId to the error that crashed it. @Composable to establish snapshot read dependency for recomposition. */
    val crashedPlugins: Map<String, Throwable>
        @Composable get() = _crashedPlugins.value

    /**
     * Thread-safe mapping of pluginId → (tabId, closeAction).
     * The closeAction directly calls removeTabById() on the owning BossTabsComponent,
     * avoiding dependency on SplitViewStateRegistry (which may not be populated during
     * the first composition frame when crashes typically occur).
     */
    private val activeTabMappings = ConcurrentHashMap<String, Pair<String, () -> Unit>>()

    /**
     * Notification callback invoked (on EDT via invokeLater) after a crashed tab is closed.
     * Registered by the composeApp module to show a status message.
     * Parameters: (pluginId, error).
     */
    @Volatile
    var onCrashNotify: ((pluginId: String, error: Throwable) -> Unit)? = null

    /**
     * Register the active tab for a plugin with a direct close callback.
     * Called from BossMainPanelContent during composition (via remember{}),
     * BEFORE PluginErrorBoundary renders content. The closeAction captures
     * a direct reference to the owning BossTabsComponent.removeTabById(),
     * so it works even before SplitViewStateRegistry is populated.
     */
    fun registerActiveTab(pluginId: String, tabId: String, closeAction: () -> Unit) {
        activeTabMappings[pluginId] = tabId to closeAction
    }

    /** Unregister the active tab mapping for a plugin (called on dispose). */
    fun unregisterActiveTab(pluginId: String) {
        activeTabMappings.remove(pluginId)
    }

    /**
     * Record a crash for a plugin. Defers to SwingUtilities.invokeLater because this
     * is called from the uncaught exception handler during an active render pass.
     *
     * Instead of trying to recompose inside the corrupted SubcomposeLayout, this
     * closes the crashed tab via Decompose navigation (which lives outside Compose)
     * and shows a status message notification.
     */
    fun recordCrash(pluginId: String, error: Throwable) {
        // Store crash info (kept for watchdog / tracking)
        _crashedPlugins.value = _crashedPlugins.value + (pluginId to error)

        val mapping = activeTabMappings[pluginId]

        if (mapping != null) {
            val (tabId, closeAction) = mapping
            // Close the crashed tab via Decompose navigation on the EDT.
            // closeAction directly calls BossTabsComponent.removeTabById(),
            // bypassing the corrupted SubcomposeLayout entirely.
            javax.swing.SwingUtilities.invokeLater {
                logger.info(LogCategory.UI, "Closing crashed plugin tab", mapOf(
                    "pluginId" to pluginId,
                    "tabId" to tabId
                ))
                try {
                    closeAction()
                } catch (e: Throwable) {
                    logger.warn(LogCategory.UI, "closeAction threw during crash cleanup", mapOf(
                        "pluginId" to pluginId
                    ), e)
                }
                _crashedPlugins.value = _crashedPlugins.value - pluginId
                onCrashNotify?.invoke(pluginId, error)
            }
        } else {
            logger.warn(LogCategory.UI, "Cannot close crashed tab — no active tab registered", mapOf(
                "pluginId" to pluginId
            ))
            // Fallback: force repaint so any composable reading crashedPlugins can react
            javax.swing.SwingUtilities.invokeLater {
                java.awt.Window.getWindows().forEach { it.repaint() }
            }
        }
    }

    /** Clear crash state for a plugin (e.g., after restart or tab close). */
    fun clearCrash(pluginId: String) {
        _crashedPlugins.value = _crashedPlugins.value - pluginId
    }

    /** Check if a plugin has crashed (non-composable). */
    fun hasCrashed(pluginId: String): Boolean = _crashedPlugins.value.containsKey(pluginId)
}

/**
 * Composition local for providing the plugin sandbox to child composables.
 */
val LocalPluginSandbox = compositionLocalOf<PluginSandbox?> { null }

/**
 * Error boundary composable that wraps plugin content and handles errors.
 *
 * When an error occurs within the content:
 * 1. The error is recorded in the sandbox's health metrics
 * 2. A fallback UI is shown with a "Restart Plugin" button
 * 3. The sandbox watchdog may trigger an automatic restart
 *
 * ## Error Handling Layers
 *
 * **Layer 1 - Explicit reporting (this boundary)**: Catches errors explicitly reported via
 * [LocalPluginErrorHandler]. Plugins should use `LocalPluginErrorHandler.current.invoke(error)`
 * in their event handlers, callbacks, and try-catch blocks.
 *
 * **Layer 2 - Coroutine exceptions (sandbox)**: The sandbox's [CoroutineExceptionHandler] catches
 * uncaught exceptions in coroutines launched via `sandboxScope`. These are recorded in health
 * metrics and may trigger watchdog restart.
 *
 * **Layer 3 - Composition crash interception (desktop)**: On desktop platforms, a
 * [PluginCrashInterceptor] hooks into the global uncaught exception handler to catch errors
 * thrown during composition (e.g., `NoSuchMethodError` from binary incompatibility). These
 * errors are attributed to the plugin by inspecting the stack trace and set the error state
 * in this boundary, showing the fallback UI instead of crashing the app.
 *
 * ## Mitigation Strategies for Plugin Authors
 *
 * To minimize composition crashes, plugins should:
 * - Use `derivedStateOf` for complex computed values that might throw
 * - Wrap risky operations in `remember { runCatching { ... } }` and handle failures gracefully
 * - Validate data before composition (e.g., null checks, bounds validation)
 * - Use `LaunchedEffect` for operations that might fail, with proper error handling
 * - Prefer loading states over throwing when data is unavailable
 *
 * Example:
 * ```kotlin
 * @Composable
 * fun SafePluginContent() {
 *     val result = remember { runCatching { riskyComputation() } }
 *     result.fold(
 *         onSuccess = { data -> DataDisplay(data) },
 *         onFailure = { LocalPluginErrorHandler.current(it) }
 *     )
 * }
 * ```
 *
 * @param pluginId The ID of the plugin for display purposes
 * @param sandbox The sandbox managing this plugin
 * @param onRestart Callback when the user clicks "Restart Plugin"
 * @param content The plugin content to render
 */
@Composable
fun PluginErrorBoundary(
    pluginId: String,
    sandbox: PluginSandbox,
    onRestart: () -> Unit,
    content: @Composable () -> Unit
) {
    val logger = remember { BossLogger.forComponent("PluginErrorBoundary") }

    var error by remember { mutableStateOf<Throwable?>(null) }

    // Register crash interceptor eagerly during composition via remember{} so it's
    // active BEFORE content() is invoked. DisposableEffect fires too late — only after
    // the first successful composition — but crashes like NoSuchMethodError happen
    // DURING the first composition of content(). Uses expect/actual: on desktop, this
    // hooks into Thread.UncaughtExceptionHandler; on other platforms, this is a no-op.
    val crashRegistration = remember(pluginId) {
        registerCrashInterceptor(pluginId) { e ->
            logger.error(LogCategory.UI, "Composition crash intercepted for plugin", mapOf(
                "pluginId" to pluginId,
                "errorType" to e.javaClass.simpleName
            ), e)
            sandbox.recordError(e)
            // Record in the crash registry so the parent (BossMainPanelContent) can
            // force a full subtree rebuild via key() change. This is necessary because
            // after a composition crash, the SubcomposeLayout tree is corrupted and
            // can't recompose in-place — we need the parent to tear it down entirely.
            PluginCrashRegistry.recordCrash(pluginId, e)
        }
    }
    DisposableEffect(pluginId) {
        onDispose {
            crashRegistration?.invoke()
        }
    }

    // Check both local error state and the crash registry.
    // The registry is populated by the crash interceptor for composition crashes
    // that corrupt the SubcomposeLayout tree. When the parent rebuilds the tree
    // (via key() change), this boundary is recreated fresh and reads from the registry.
    val registryCrash = PluginCrashRegistry.crashedPlugins[pluginId]
    val activeError = error ?: registryCrash

    if (activeError != null) {
        PluginErrorFallback(
            pluginId = pluginId,
            error = activeError,
            onRestart = {
                logger.info(LogCategory.UI, "User requested plugin restart", mapOf(
                    "pluginId" to pluginId
                ))
                error = null
                PluginCrashRegistry.clearCrash(pluginId)
                onRestart()
            },
            onDismiss = {
                logger.debug(LogCategory.UI, "User dismissed error", mapOf(
                    "pluginId" to pluginId
                ))
                error = null
                PluginCrashRegistry.clearCrash(pluginId)
            }
        )
    } else {
        // Note: Heartbeats are recorded automatically by the sandbox's heartbeat job
        // (InProcessPluginSandbox.startHeartbeatJob), so we don't need to record them here.

        CompositionLocalProvider(
            LocalPluginErrorHandler provides { e ->
                logger.error(LogCategory.UI, "Error reported from plugin", mapOf(
                    "pluginId" to pluginId
                ), e)
                sandbox.recordError(e)
                error = e
            },
            LocalPluginSandbox provides sandbox
        ) {
            content()
        }
    }
}

/**
 * Wraps a composable with error handling via [LocalPluginErrorHandler].
 *
 * **Important Limitation**: Compose doesn't have built-in try-catch for composables.
 * This primarily helps with errors that are explicitly reported via the error handler
 * (e.g., in event handlers, callbacks, or coroutines). Unhandled exceptions during
 * composition may still crash the component.
 *
 * Usage in plugins:
 * ```kotlin
 * val errorHandler = LocalPluginErrorHandler.current
 * Button(onClick = {
 *     try {
 *         riskyOperation()
 *     } catch (e: Exception) {
 *         errorHandler(e)  // Reports error to sandbox
 *     }
 * })
 * ```
 *
 * @param pluginId The ID of the plugin for logging
 * @param sandbox The sandbox managing this plugin
 * @param fallback Composable to show when an error occurs
 * @param content The plugin content to render
 */
@Composable
fun SafePluginContent(
    pluginId: String,
    sandbox: PluginSandbox,
    fallback: @Composable (Throwable) -> Unit,
    content: @Composable () -> Unit
) {
    val logger = remember { BossLogger.forComponent("SafePluginContent") }

    var error by remember { mutableStateOf<Throwable?>(null) }

    if (error != null) {
        fallback(error!!)
    } else {
        // Note: Heartbeats are recorded automatically by the sandbox's heartbeat job
        // (InProcessPluginSandbox.startHeartbeatJob), so we don't need to record them here.

        CompositionLocalProvider(
            LocalPluginErrorHandler provides { e ->
                logger.error(LogCategory.UI, "Error in plugin content", mapOf(
                    "pluginId" to pluginId
                ), e)
                sandbox.recordError(e)
                error = e
            },
            LocalPluginSandbox provides sandbox
        ) {
            content()
        }
    }
}
