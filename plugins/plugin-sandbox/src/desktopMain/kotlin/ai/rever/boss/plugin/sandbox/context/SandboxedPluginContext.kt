package ai.rever.boss.plugin.sandbox.context

import ai.rever.boss.plugin.api.ActiveTabsProvider
import ai.rever.boss.plugin.api.AuthDataProvider
import ai.rever.boss.plugin.api.BookmarkDataProvider
import ai.rever.boss.plugin.api.ContextMenuProvider
import ai.rever.boss.plugin.api.DownloadDataProvider
import ai.rever.boss.plugin.api.FileSystemDataProvider
import ai.rever.boss.plugin.api.GitDataProvider
import ai.rever.boss.plugin.api.LogDataProvider
import ai.rever.boss.plugin.api.PanelEventProvider
import ai.rever.boss.plugin.api.RoleManagementProvider
import ai.rever.boss.plugin.api.SettingsProvider
import ai.rever.boss.plugin.api.TerminalContentProvider
import ai.rever.boss.plugin.api.UserManagementProvider
import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.PerformanceDataProvider
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.plugin.api.PluginSandboxRef
import ai.rever.boss.plugin.api.PluginStoreApiKeyProvider
import ai.rever.boss.plugin.api.RunConfigurationDataProvider
import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.SplitViewOperations
import ai.rever.boss.plugin.api.TabRegistry
import ai.rever.boss.plugin.api.WorkspaceDataProvider
import ai.rever.boss.plugin.browser.BrowserService
import ai.rever.boss.plugin.sandbox.PluginSandbox
import kotlinx.coroutines.CoroutineScope

/**
 * A PluginContext wrapper that provides sandboxed registries.
 *
 * This context wraps the original PanelRegistry and TabRegistry with
 * error boundary wrappers, ensuring that plugin crashes are isolated.
 */
class SandboxedPluginContext(
    private val _sandbox: PluginSandbox,
    private val delegate: PluginContext,
    private val sandboxedPanelRegistry: SandboxedPanelRegistry,
    private val sandboxedTabRegistry: SandboxedTabRegistry
) : PluginContext {

    override val panelRegistry: PanelRegistry
        get() = sandboxedPanelRegistry

    override val tabRegistry: TabRegistry
        get() = sandboxedTabRegistry

    /**
     * The pluginScope is provided by the sandbox, ensuring all plugin
     * coroutines run within the sandboxed scope with SupervisorJob.
     */
    override val pluginScope: CoroutineScope
        get() = _sandbox.sandboxScope

    /**
     * The sandbox reference for health reporting.
     */
    override val sandbox: PluginSandboxRef
        get() = _sandbox

    /**
     * Browser service for plugins needing embedded browser capabilities.
     * Delegates to the underlying context's browserService.
     */
    override val browserService: BrowserService?
        get() = delegate.browserService

    /**
     * The plugin's manifest, providing access to configuration declared in plugin.json.
     * Delegates to the underlying context's manifest.
     */
    override val manifest: PluginManifest?
        get() = delegate.manifest

    // Service providers - delegate to underlying context
    override val performanceDataProvider: PerformanceDataProvider?
        get() = delegate.performanceDataProvider

    override val downloadDataProvider: DownloadDataProvider?
        get() = delegate.downloadDataProvider

    override val bookmarkDataProvider: BookmarkDataProvider?
        get() = delegate.bookmarkDataProvider

    override val workspaceDataProvider: WorkspaceDataProvider?
        get() = delegate.workspaceDataProvider

    override val splitViewOperations: SplitViewOperations?
        get() = delegate.splitViewOperations

    override val gitDataProvider: GitDataProvider?
        get() = delegate.gitDataProvider

    override val fileSystemDataProvider: FileSystemDataProvider?
        get() = delegate.fileSystemDataProvider

    override val secretDataProvider: SecretDataProvider?
        get() = delegate.secretDataProvider

    override val runConfigurationDataProvider: RunConfigurationDataProvider?
        get() = delegate.runConfigurationDataProvider

    override val activeTabsProvider: ActiveTabsProvider?
        get() = delegate.activeTabsProvider

    override val windowId: String?
        get() = delegate.windowId

    override val projectPath: String?
        get() = delegate.projectPath

    override val authDataProvider: AuthDataProvider?
        get() = delegate.authDataProvider

    override val userManagementProvider: UserManagementProvider?
        get() = delegate.userManagementProvider

    override val roleManagementProvider: RoleManagementProvider?
        get() = delegate.roleManagementProvider

    // Terminal providers - delegate to underlying context
    override val terminalContentProvider: TerminalContentProvider?
        get() = delegate.terminalContentProvider

    override val panelEventProvider: PanelEventProvider?
        get() = delegate.panelEventProvider

    override val settingsProvider: SettingsProvider?
        get() = delegate.settingsProvider

    // Context menu provider - delegate to underlying context
    override val contextMenuProvider: ContextMenuProvider?
        get() = delegate.contextMenuProvider

    // Log data provider - delegate to underlying context
    override val logDataProvider: LogDataProvider?
        get() = delegate.logDataProvider

    // Plugin Store API key provider - delegate to underlying context
    override val pluginStoreApiKeyProvider: PluginStoreApiKeyProvider?
        get() = delegate.pluginStoreApiKeyProvider

    /**
     * Get the underlying sandbox for this context.
     */
    fun getSandbox(): PluginSandbox = _sandbox
}
