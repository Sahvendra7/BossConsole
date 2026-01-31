package ai.rever.boss.plugin.sandbox.ui

import ai.rever.boss.plugin.sandbox.PluginSandbox
import ai.rever.boss.plugin.sandbox.SandboxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Wraps panel content with sandbox error handling.
 *
 * This composable should be used when rendering any plugin panel content.
 * It provides:
 * - State-based rendering (disabled, crashed, unhealthy, running)
 * - Error boundary that catches composition/rendering errors
 * - Fallback UI when errors occur
 * - Restart capability
 *
 * @param pluginId The ID of the plugin (for display in fallback UI)
 * @param sandbox The sandbox managing this plugin (optional for backward compatibility)
 * @param onRestart Suspend function to restart the plugin
 * @param onEnable Suspend function to re-enable a disabled plugin
 * @param content The plugin content to render
 */
@Composable
fun SandboxedPanelContent(
    pluginId: String,
    sandbox: PluginSandbox?,
    onRestart: suspend () -> Unit,
    onEnable: suspend () -> Unit = {},
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()

    // If no sandbox, render content directly (backward compatibility)
    if (sandbox == null) {
        content()
        return
    }

    val state by sandbox.state.collectAsState()
    val metrics by sandbox.healthMetrics.collectAsState()

    when (state) {
        SandboxState.DISABLED -> {
            PluginDisabledFallback(
                pluginId = pluginId,
                onEnable = { scope.launch { onEnable() } }
            )
        }

        SandboxState.CRASHED -> {
            PluginErrorFallback(
                pluginId = pluginId,
                error = RuntimeException("Plugin crashed after ${metrics.crashCount} crash(es)"),
                onRestart = { scope.launch { onRestart() } },
                onDismiss = null // Can't dismiss a crashed state
            )
        }

        SandboxState.UNHEALTHY -> {
            // Show warning but still try to render content with error boundary
            PluginUnhealthyBanner(
                pluginId = pluginId,
                consecutiveErrors = metrics.consecutiveErrors,
                onRestart = { scope.launch { onRestart() } }
            )
            PluginErrorBoundary(
                pluginId = pluginId,
                sandbox = sandbox,
                onRestart = { scope.launch { onRestart() } }
            ) {
                content()
            }
        }

        SandboxState.RESTARTING -> {
            PluginRestartingFallback(pluginId = pluginId)
        }

        SandboxState.STOPPED -> {
            PluginStoppedFallback(
                pluginId = pluginId,
                onStart = { scope.launch { onRestart() } }
            )
        }

        SandboxState.RUNNING -> {
            PluginErrorBoundary(
                pluginId = pluginId,
                sandbox = sandbox,
                onRestart = { scope.launch { onRestart() } }
            ) {
                content()
            }
        }
    }
}

/**
 * Simplified wrapper that only provides error boundary without state handling.
 *
 * Use this when you want error handling but don't need the full state machine
 * (e.g., for simpler components).
 *
 * @param pluginId The ID of the plugin
 * @param sandbox The sandbox managing this plugin
 * @param onRestart Callback when restart is requested
 * @param content The content to render
 */
@Composable
fun SimpleSandboxedContent(
    pluginId: String,
    sandbox: PluginSandbox,
    onRestart: () -> Unit,
    content: @Composable () -> Unit
) {
    PluginErrorBoundary(
        pluginId = pluginId,
        sandbox = sandbox,
        onRestart = onRestart
    ) {
        content()
    }
}
