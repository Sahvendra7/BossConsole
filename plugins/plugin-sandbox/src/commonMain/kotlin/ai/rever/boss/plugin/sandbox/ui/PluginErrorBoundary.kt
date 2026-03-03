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
import androidx.compose.runtime.snapshots.Snapshot

/**
 * Composition local for providing an error handler to child composables.
 *
 * Plugins can use this to report errors that should be tracked by the sandbox
 * but don't necessarily crash the component.
 */
val LocalPluginErrorHandler = compositionLocalOf<(Throwable) -> Unit> { { } }

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

    // Register a composition-scoped crash interceptor to catch errors thrown during
    // composition (e.g., NoSuchMethodError from binary incompatibility).
    // Uses expect/actual: on desktop, this hooks into Thread.UncaughtExceptionHandler;
    // on other platforms, this is a no-op.
    DisposableEffect(pluginId) {
        val registration = registerCrashInterceptor(pluginId) { e ->
            logger.error(LogCategory.UI, "Composition crash intercepted for plugin", mapOf(
                "pluginId" to pluginId,
                "errorType" to e.javaClass.simpleName
            ), e)
            sandbox.recordError(e)
            // Callback runs on the UncaughtExceptionHandler thread, not the main thread.
            // Use Snapshot.withMutableSnapshot to safely mutate Compose state from any thread.
            Snapshot.withMutableSnapshot {
                error = e
            }
        }
        onDispose {
            registration?.invoke()
        }
    }

    if (error != null) {
        PluginErrorFallback(
            pluginId = pluginId,
            error = error!!,
            onRestart = {
                logger.info(LogCategory.UI, "User requested plugin restart", mapOf(
                    "pluginId" to pluginId
                ))
                error = null
                onRestart()
            },
            onDismiss = {
                logger.debug(LogCategory.UI, "User dismissed error", mapOf(
                    "pluginId" to pluginId
                ))
                error = null
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
