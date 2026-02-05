package ai.rever.boss.components.plugin

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
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelId
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.PerformanceDataProvider
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.plugin.api.PluginSandboxRef
import ai.rever.boss.plugin.api.PluginStoreApiKeyProvider
import ai.rever.boss.plugin.api.RunConfigurationDataProvider
import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.SplitViewOperations
import ai.rever.boss.plugin.api.TabComponentWithUI
import ai.rever.boss.plugin.api.TabInfo
import ai.rever.boss.plugin.api.TabRegistry
import ai.rever.boss.plugin.api.TabTypeId
import ai.rever.boss.plugin.api.TabTypeInfo
import ai.rever.boss.plugin.api.WorkspaceDataProvider
import ai.rever.boss.plugin.browser.BrowserService
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry of all registrations made by dynamic plugins.
 *
 * This tracks which panels and tabs each plugin has registered,
 * enabling automatic cleanup when plugins are unloaded.
 */
class PluginRegistrationTracker {
    /**
     * Panels registered by each plugin.
     */
    private val panelsByPlugin = ConcurrentHashMap<String, MutableSet<PanelId>>()

    /**
     * Tab types registered by each plugin.
     */
    private val tabTypesByPlugin = ConcurrentHashMap<String, MutableSet<TabTypeId>>()

    /**
     * Record a panel registration.
     */
    fun recordPanelRegistration(pluginId: String, panelId: PanelId) {
        panelsByPlugin.getOrPut(pluginId) { ConcurrentHashMap.newKeySet() }.add(panelId)
    }

    /**
     * Record a tab type registration.
     */
    fun recordTabTypeRegistration(pluginId: String, tabTypeId: TabTypeId) {
        tabTypesByPlugin.getOrPut(pluginId) { ConcurrentHashMap.newKeySet() }.add(tabTypeId)
    }

    /**
     * Get all panels registered by a plugin.
     */
    fun getPanelsForPlugin(pluginId: String): Set<PanelId> {
        return panelsByPlugin[pluginId]?.toSet() ?: emptySet()
    }

    /**
     * Get all tab types registered by a plugin.
     */
    fun getTabTypesForPlugin(pluginId: String): Set<TabTypeId> {
        return tabTypesByPlugin[pluginId]?.toSet() ?: emptySet()
    }

    /**
     * Remove all registration records for a plugin.
     *
     * Called after cleanup is complete.
     */
    fun clearPlugin(pluginId: String) {
        panelsByPlugin.remove(pluginId)
        tabTypesByPlugin.remove(pluginId)
    }

    /**
     * Get all plugins that have registered panels or tab types.
     */
    fun getRegisteredPlugins(): Set<String> {
        return panelsByPlugin.keys + tabTypesByPlugin.keys
    }

    /**
     * Check if a plugin has any registrations.
     */
    fun hasRegistrations(pluginId: String): Boolean {
        return (panelsByPlugin[pluginId]?.isNotEmpty() == true) ||
               (tabTypesByPlugin[pluginId]?.isNotEmpty() == true)
    }
}

/**
 * A panel registry wrapper that tracks registrations for a specific plugin.
 */
class TrackingPanelRegistry(
    private val pluginId: String,
    private val delegate: PanelRegistry,
    private val tracker: PluginRegistrationTracker
) : PanelRegistry() {

    override fun registerPanel(
        content: PanelInfo,
        factory: (ComponentContext, PanelInfo) -> PanelComponentWithUI
    ) {
        tracker.recordPanelRegistration(pluginId, content.id)
        delegate.registerPanel(content, factory)
    }

    override fun unregisterPanel(id: PanelId) {
        delegate.unregisterPanel(id)
    }

    override fun addChangeListener(listener: () -> Unit) {
        delegate.addChangeListener(listener)
    }

    override fun removeChangeListener(listener: () -> Unit) {
        delegate.removeChangeListener(listener)
    }

    override fun createComponent(id: PanelId, componentContext: ComponentContext): PanelComponentWithUI? {
        return delegate.createComponent(id, componentContext)
    }

    override fun getPanelContent(id: PanelId): PanelInfo? {
        return delegate.getPanelContent(id)
    }

    override fun getAllPanels(): List<PanelInfo> {
        return delegate.getAllPanels()
    }
}

/**
 * A tab registry wrapper that tracks registrations for a specific plugin.
 */
class TrackingTabRegistry(
    private val pluginId: String,
    private val delegate: TabRegistry,
    private val tracker: PluginRegistrationTracker
) : TabRegistry() {

    override fun registerTabType(
        content: TabTypeInfo,
        factory: (TabInfo, ComponentContext) -> TabComponentWithUI
    ) {
        tracker.recordTabTypeRegistration(pluginId, content.typeId)
        delegate.registerTabType(content, factory)
    }

    override fun unregisterTabType(typeId: TabTypeId) {
        delegate.unregisterTabType(typeId)
    }

    override fun addChangeListener(listener: () -> Unit) {
        delegate.addChangeListener(listener)
    }

    override fun removeChangeListener(listener: () -> Unit) {
        delegate.removeChangeListener(listener)
    }

    override fun createTabComponent(config: TabInfo, componentContext: ComponentContext): TabComponentWithUI? {
        return delegate.createTabComponent(config, componentContext)
    }

    override fun getTabTypeInfo(typeId: TabTypeId): TabTypeInfo? {
        return delegate.getTabTypeInfo(typeId)
    }

    override fun getAllTabTypes(): List<TabTypeInfo> {
        return delegate.getAllTabTypes()
    }

    override fun isRegistered(typeId: TabTypeId): Boolean {
        return delegate.isRegistered(typeId)
    }
}

/**
 * A plugin context that tracks all registrations made by a dynamic plugin.
 *
 * This enables automatic cleanup when the plugin is unloaded.
 *
 * @param pluginId The ID of the plugin using this context
 * @param delegate The underlying plugin context
 * @param tracker The registration tracker
 * @param pluginManifest The plugin's manifest (optional, for external plugins)
 */
class TrackingPluginContext(
    val pluginId: String,
    private val delegate: PluginContext,
    private val tracker: PluginRegistrationTracker,
    private val pluginManifest: PluginManifest? = null
) : PluginContext {

    private val _panelRegistry = TrackingPanelRegistry(pluginId, delegate.panelRegistry, tracker)
    private val _tabRegistry = TrackingTabRegistry(pluginId, delegate.tabRegistry, tracker)

    override val panelRegistry: PanelRegistry get() = _panelRegistry
    override val tabRegistry: TabRegistry get() = _tabRegistry
    override val pluginScope: CoroutineScope get() = delegate.pluginScope
    override val sandbox: PluginSandboxRef? get() = delegate.sandbox
    override val browserService: BrowserService? get() = delegate.browserService
    override val manifest: PluginManifest? get() = pluginManifest ?: delegate.manifest

    // Service providers - delegate to underlying context
    override val performanceDataProvider: PerformanceDataProvider? get() = delegate.performanceDataProvider
    override val downloadDataProvider: DownloadDataProvider? get() = delegate.downloadDataProvider
    override val bookmarkDataProvider: BookmarkDataProvider? get() = delegate.bookmarkDataProvider
    override val workspaceDataProvider: WorkspaceDataProvider? get() = delegate.workspaceDataProvider
    override val splitViewOperations: SplitViewOperations? get() = delegate.splitViewOperations
    override val gitDataProvider: GitDataProvider? get() = delegate.gitDataProvider
    override val fileSystemDataProvider: FileSystemDataProvider? get() = delegate.fileSystemDataProvider
    override val secretDataProvider: SecretDataProvider? get() = delegate.secretDataProvider
    override val runConfigurationDataProvider: RunConfigurationDataProvider? get() = delegate.runConfigurationDataProvider
    override val activeTabsProvider: ActiveTabsProvider? get() = delegate.activeTabsProvider
    override val windowId: String? get() = delegate.windowId
    override val projectPath: String? get() = delegate.projectPath
    override val authDataProvider: AuthDataProvider? get() = delegate.authDataProvider
    override val userManagementProvider: UserManagementProvider? get() = delegate.userManagementProvider
    override val roleManagementProvider: RoleManagementProvider? get() = delegate.roleManagementProvider

    // Terminal providers - delegate to underlying context
    override val terminalContentProvider: TerminalContentProvider? get() = delegate.terminalContentProvider
    override val panelEventProvider: PanelEventProvider? get() = delegate.panelEventProvider
    override val settingsProvider: SettingsProvider? get() = delegate.settingsProvider

    // Context menu provider - delegate to underlying context
    override val contextMenuProvider: ContextMenuProvider? get() = delegate.contextMenuProvider

    // Log data provider - delegate to underlying context
    override val logDataProvider: LogDataProvider? get() = delegate.logDataProvider

    // Plugin Store API key provider - delegate to underlying context
    override val pluginStoreApiKeyProvider: PluginStoreApiKeyProvider? get() = delegate.pluginStoreApiKeyProvider

    /**
     * Get the panels registered by this plugin.
     */
    fun getRegisteredPanels(): Set<PanelId> = tracker.getPanelsForPlugin(pluginId)

    /**
     * Get the tab types registered by this plugin.
     */
    fun getRegisteredTabTypes(): Set<TabTypeId> = tracker.getTabTypesForPlugin(pluginId)

    /**
     * Unregister all panels and tab types registered by this plugin.
     */
    fun unregisterAll() {
        // Unregister all panels
        for (panelId in tracker.getPanelsForPlugin(pluginId)) {
            delegate.panelRegistry.unregisterPanel(panelId)
        }

        // Unregister all tab types
        for (tabTypeId in tracker.getTabTypesForPlugin(pluginId)) {
            delegate.tabRegistry.unregisterTabType(tabTypeId)
        }

        // Clear tracking records
        tracker.clearPlugin(pluginId)
    }
}
