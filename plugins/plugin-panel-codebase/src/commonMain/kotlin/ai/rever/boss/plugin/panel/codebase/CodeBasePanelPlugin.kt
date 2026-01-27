package ai.rever.boss.plugin.panel.codebase

import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.Plugin
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.ui.ContextMenuItemData
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.arkivanov.decompose.ComponentContext

/**
 * Plugin for CodeBase panel
 *
 * This plugin provides the CodeBase panel which allows users to:
 * - Browse project files in a tree view
 * - Open files in the editor
 * - Navigate directory structure with IntelliJ-style compact paths
 *
 * Access Control:
 * - Available to all users
 *
 * Note: This plugin requires providers for:
 * - FileSystemDataProvider: For file system operations
 * - ProjectDataProvider: For project management
 * - Window context for multi-window support
 */
object CodeBasePanelPlugin : Plugin {
    override val pluginId = "codebase-panel"
    override val displayName = "CodeBase Panel"

    /**
     * Register the plugin with a component factory.
     *
     * This is the preferred registration method when the component is implemented
     * in composeApp and uses CompositionLocals for providers.
     *
     * @param context The plugin context for registration
     * @param componentFactory Factory to create the codebase component
     */
    fun register(
        context: PluginContext,
        componentFactory: (ctx: ComponentContext, panelInfo: PanelInfo) -> PanelComponentWithUI
    ) {
        context.panelRegistry.registerPanel(CodeBaseInfo) { ctx, panelInfo ->
            componentFactory(ctx, panelInfo)
        }
    }

    /**
     * Register the plugin with required providers.
     *
     * @param context The plugin context for registration
     * @param fileSystemProvider Provider for file system operations
     * @param projectDataProvider Provider for project data
     * @param getWindowId Function to get current window ID
     * @param getSelectedProject Function to get selected project for a window
     * @param onSelectProject Callback when a project is selected
     * @param directoryPickerProvider Provider for directory picker functionality
     * @param contextMenuProvider Provider for context menu functionality
     * @param openTerminalTab Callback to open a terminal tab at the specified directory
     */
    fun registerWithProviders(
        context: PluginContext,
        fileSystemProvider: FileSystemDataProvider,
        projectDataProvider: ProjectDataProvider,
        getWindowId: () -> String?,
        getSelectedProject: () -> ProjectData?,
        onSelectProject: (ProjectData) -> Unit,
        directoryPickerProvider: DirectoryPickerProvider,
        contextMenuProvider: @Composable (Modifier, List<ContextMenuItemData>) -> Modifier,
        openTerminalTab: (workingDirectory: String) -> Unit
    ) {
        context.panelRegistry.registerPanel(CodeBaseInfo) { ctx, panelInfo ->
            CodeBaseComponent(
                ctx = ctx,
                panelInfo = panelInfo,
                fileSystemProvider = fileSystemProvider,
                projectDataProvider = projectDataProvider,
                getWindowId = getWindowId,
                getSelectedProject = getSelectedProject,
                onSelectProject = onSelectProject,
                directoryPickerProvider = directoryPickerProvider,
                contextMenuProvider = contextMenuProvider,
                openTerminalTab = openTerminalTab
            )
        }
    }

    /**
     * Unregister the panel.
     *
     * @param context The plugin context for unregistration
     */
    fun unregister(context: PluginContext) {
        context.panelRegistry.unregisterPanel(CodeBaseInfo.id)
    }

    override fun register(context: PluginContext) {
        // No-op: This plugin requires explicit registration with providers
        // Use register(context, fileSystemProvider, ...) instead
    }
}

/**
 * Provider interface for directory picker functionality.
 */
interface DirectoryPickerProvider {
    /**
     * Pick a directory.
     *
     * @param onResult Callback with the selected directory path, or null if cancelled
     */
    fun pickDirectory(onResult: (String?) -> Unit)
}
