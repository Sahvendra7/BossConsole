package ai.rever.boss.plugin.sandbox.ui

import ai.rever.boss.plugin.sandbox.PluginSandbox
import ai.rever.boss.plugin.sandbox.PluginSandboxManager
import ai.rever.boss.plugin.sandbox.SandboxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch

/**
 * Renders a panel with sandbox crash isolation.
 *
 * This is a convenience composable that wraps panel content with:
 * - State-based rendering for different sandbox states
 * - Error boundary for catching composition errors
 * - Automatic fallback UI for crashed/disabled/restarting states
 * - One-click restart and re-enable actions
 *
 * Usage:
 * ```kotlin
 * SandboxedPanel(
 *     pluginId = "my-plugin",
 *     sandboxManager = defaultPlugin.sandboxManager,
 *     onShowErrorDialog = { pluginId, error -> /* show dialog */ }
 * ) {
 *     // Your panel content here
 *     MyPanelContent()
 * }
 * ```
 *
 * @param pluginId The unique identifier of the plugin
 * @param sandboxManager The sandbox manager containing the plugin's sandbox
 * @param onShowErrorDialog Optional callback to show detailed error dialog
 * @param content The panel content to render
 */
@Composable
fun SandboxedPanel(
    pluginId: String,
    sandboxManager: PluginSandboxManager,
    onShowErrorDialog: ((pluginId: String, error: Throwable) -> Unit)? = null,
    content: @Composable () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sandbox = remember(pluginId) { sandboxManager.getSandbox(pluginId) }

    SandboxedPanelContent(
        pluginId = pluginId,
        sandbox = sandbox,
        onRestart = { sandboxManager.restartPlugin(pluginId) },
        onEnable = { sandboxManager.enablePlugin(pluginId) },
        content = content
    )
}

/**
 * Renders a panel with an existing sandbox reference.
 *
 * Use this when you already have a reference to the sandbox.
 *
 * @param sandbox The plugin's sandbox
 * @param onRestart Suspend function to restart the plugin
 * @param onEnable Suspend function to enable the plugin
 * @param content The panel content to render
 */
@Composable
fun SandboxedPanel(
    sandbox: PluginSandbox,
    onRestart: suspend () -> Unit,
    onEnable: suspend () -> Unit = {},
    content: @Composable () -> Unit
) {
    SandboxedPanelContent(
        pluginId = sandbox.pluginId,
        sandbox = sandbox,
        onRestart = onRestart,
        onEnable = onEnable,
        content = content
    )
}

/**
 * Renders panel content only when the sandbox is in a healthy state.
 *
 * Unlike [SandboxedPanel], this does not show fallback UI - it simply
 * doesn't render anything when the sandbox is not running.
 *
 * @param sandbox The plugin's sandbox
 * @param content The panel content to render
 */
@Composable
fun SandboxedPanelIfRunning(
    sandbox: PluginSandbox,
    content: @Composable () -> Unit
) {
    val state by sandbox.state.collectAsState()

    if (state == SandboxState.RUNNING) {
        PluginErrorBoundary(
            pluginId = sandbox.pluginId,
            sandbox = sandbox,
            onRestart = { /* handled externally */ }
        ) {
            content()
        }
    }
}

/**
 * Extension function to easily wrap content with sandbox error handling.
 *
 * Usage:
 * ```kotlin
 * sandbox.withErrorBoundary {
 *     MyContent()
 * }
 * ```
 */
@Composable
fun PluginSandbox.withErrorBoundary(
    onRestart: () -> Unit = {},
    content: @Composable () -> Unit
) {
    PluginErrorBoundary(
        pluginId = this.pluginId,
        sandbox = this,
        onRestart = onRestart,
        content = content
    )
}
