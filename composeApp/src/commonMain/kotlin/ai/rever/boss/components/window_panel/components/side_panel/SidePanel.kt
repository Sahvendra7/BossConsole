package ai.rever.boss.components.window_panel.components.side_panel

import BossDarkBackground
import BossDarkBorder
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.components.registery.PanelComponentStore
import ai.rever.boss.components.window_panel.components.BossPanelTopBar
import ai.rever.boss.plugin.sandbox.PanelSandboxRegistry
import ai.rever.boss.plugin.sandbox.ui.PluginErrorBoundary
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.bars.horizontal.StatusMessageManager
import ai.rever.boss.window.LocalWindowId
import ai.rever.boss.window.MenuActionsHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch

@Composable
fun BossDraggableComponent.SidePanel(
    panel: Panel,
    panelComponentStore: PanelComponentStore
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val pluginContentId = getPanelContentId(panel)
    val component = pluginContentId?.let { panelComponentStore.getOrCreateComponent(it) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BossDarkBackground)
            .hoverable(interactionSource)
    ) {
        val title = component?.panelInfo?.displayName ?: "Default title"//getPanelTitle(panel)
        val windowId = LocalWindowId.current

        BossPanelTopBar(
            title = title,
            isHovered = isHovered,
            onReset = pluginContentId?.let { panelId ->
                {
                    // Reset sandbox health if this panel has a sandbox
                    PanelSandboxRegistry.getSandbox(panelId)?.resetHealth()
                    // Trigger component reset via PanelComponentStore
                    panelComponentStore.resetComponent(panelId)
                }
            },
            onReloadPlugin = pluginContentId?.let { panelId ->
                windowId?.let { wId ->
                    {
                        MenuActionsHandler.triggerReloadPlugin(wId, panelId)
                    }
                }
            },
            onMinimize = {
                setPanelVisible(panel, false)
            }
        )
        Divider(color = BossDarkBorder)

        Box(modifier = Modifier.fillMaxSize()) {
            // Force recomposition when component instance changes (e.g., after reset)
            // This ensures the UI fully refreshes instead of showing stale state
            key(component) {
                // Render panel content with optional error boundary wrapping
                RenderPanelContent(
                    component = component,
                    panelId = pluginContentId
                )
            }
        }
    }
}

/**
 * Renders panel content with optional error boundary wrapping.
 * If the panel has an associated sandbox, wraps content with PluginErrorBoundary
 * to catch errors and show a restart button.
 */
@Composable
private fun RenderPanelContent(
    component: ai.rever.boss.plugin.api.PanelComponentWithUI?,
    panelId: ai.rever.boss.plugin.api.PanelId?
) {
    if (component == null || panelId == null) return

    // Check if this panel has a sandbox for error boundary wrapping
    val sandbox = PanelSandboxRegistry.getSandbox(panelId)
    if (sandbox != null) {
        val scope = rememberCoroutineScope()
        val logger = remember { BossLogger.forComponent("SidePanel") }
        PluginErrorBoundary(
            pluginId = sandbox.pluginId,
            sandbox = sandbox,
            onRestart = {
                // Restart the sandbox when user clicks restart
                scope.launch {
                    val result = sandbox.restart()
                    if (result.isFailure) {
                        logger.error(LogCategory.UI, "Failed to restart plugin", mapOf(
                            "pluginId" to sandbox.pluginId,
                            "error" to (result.exceptionOrNull()?.message ?: "unknown")
                        ))
                        // Show status message to user about failure
                        StatusMessageManager.showMessage(
                            "Failed to restart plugin: ${sandbox.pluginId}",
                            durationMs = 5000
                        )
                    }
                }
            }
        ) {
            component.Content()
        }
    } else {
        // No sandbox - render directly (backwards compatibility)
        component.Content()
    }
}
