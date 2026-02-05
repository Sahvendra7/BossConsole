package ai.rever.boss.components.plugin

import ai.rever.boss.git.GitDataProviderImpl
import ai.rever.boss.plugin.panel.manager.PluginManagerPanelPlugin
import ai.rever.boss.components.plugin.providers.TerminalContentProviderImpl
import ai.rever.boss.components.plugin.providers.PanelEventProviderImpl
import ai.rever.boss.components.plugin.providers.SettingsProviderImpl
import ai.rever.boss.cache.loadFaviconFromCache
import ai.rever.boss.components.events.TerminalEventBus
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.panels.left_top.BookmarksDialogProviderImpl
import ai.rever.boss.components.plugin.panels.left_top.createDownloadDataProvider
import ai.rever.boss.plugin.ui.ContextMenuItemData
import ai.rever.boss.components.plugin.providers.DirectoryPickerProviderImpl
import ai.rever.boss.components.plugin.providers.FileSystemDataProviderImpl
import ai.rever.boss.components.plugin.providers.ProjectDataProviderImpl
import ai.rever.boss.plugin.api.ProjectData
import ai.rever.boss.window.Project
import ai.rever.boss.window.selectProjectInWindow
import ai.rever.boss.components.plugin.providers.SplitViewOperationsImpl
import ai.rever.boss.components.plugin.providers.WorkspaceDataProviderImpl
import ai.rever.boss.components.plugin.providers.createLogDataProvider
import ai.rever.boss.components.plugin.providers.createPerformanceDataProvider
import ai.rever.boss.components.plugin.panels.right_top.BrowserAccessor
import ai.rever.boss.components.plugin.panels.right_top.storeSplitViewState
import ai.rever.boss.components.plugin.panels.right_top.BrowserIntegration as InternalBrowserIntegration
import ai.rever.boss.components.plugin.tab_types.fluck.registerFluck
import ai.rever.boss.components.plugin.tab_types.registerCodeEditor
import ai.rever.boss.components.plugin.tab_types.registerTerminalTab
import ai.rever.boss.components.plugin.tab_types.fluck.SecretChangeNotifier
import ai.rever.boss.services.auth.AuthDataProviderImpl
import ai.rever.boss.services.auth.AuthStateManager
import ai.rever.boss.services.auth.PluginStoreApiKeyProviderImpl
import ai.rever.boss.services.bookmarks.BookmarkDataProviderImpl
import ai.rever.boss.services.supabase.RoleManagementProviderImpl
import ai.rever.boss.services.supabase.SecretDataProviderImpl
import ai.rever.boss.services.supabase.UserManagementProviderImpl
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.utils.logging.LogSanitizer
import ai.rever.boss.window.WindowProjectState
import ai.rever.boss.plugin.api.ActiveTabData
import ai.rever.boss.plugin.api.ActiveTabsProvider
import ai.rever.boss.plugin.api.AuthDataProvider
import ai.rever.boss.plugin.api.FileNodeData
import ai.rever.boss.plugin.api.FileSystemDataProvider
import ai.rever.boss.plugin.api.GitDataProvider
import ai.rever.boss.plugin.api.NodeLoadingStateData
import ai.rever.boss.plugin.api.ContextMenuProvider
import ai.rever.boss.plugin.api.LogDataProvider
import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.PluginStoreApiKeyProvider
import ai.rever.boss.plugin.api.BrowserIntegration as ApiBrowserIntegration
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginSandboxRef
import ai.rever.boss.plugin.api.RoleManagementProvider
import ai.rever.boss.plugin.api.SplitViewOperations
import ai.rever.boss.plugin.api.TabRegistry
import ai.rever.boss.plugin.api.UserManagementProvider
import ai.rever.boss.plugin.api.WorkspaceDataProvider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Tab
import androidx.compose.material.icons.outlined.Terminal
import ai.rever.boss.plugin.browser.BrowserService

import ai.rever.boss.plugin.sandbox.PluginSandboxManager
import ai.rever.boss.plugin.sandbox.PluginSandboxManagerImpl
import ai.rever.boss.plugin.sandbox.SandboxConfig
import ai.rever.boss.plugin.sandbox.context.SandboxedPanelRegistry
import ai.rever.boss.plugin.sandbox.context.SandboxedPluginContext
import ai.rever.boss.plugin.sandbox.context.SandboxedTabRegistry
import ai.rever.boss.plugin.sandbox.health.PluginHealthSummary
import ai.rever.boss.plugin.sandbox.notification.BossPluginNotificationService
import ai.rever.boss.plugin.sandbox.notification.PluginSandboxNotificationListener
import ai.rever.boss.plugin.sandbox.notification.PluginToastState
import ai.rever.boss.window.WindowGitState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class DefaultPlugin(
    override val panelRegistry: PanelRegistry,
    override val tabRegistry: TabRegistry,
    val windowProjectState: WindowProjectState?,
    val windowGitState: WindowGitState? = null,
    private val _windowId: String? = null,
    private val workspaceManager: ai.rever.boss.components.workspaces.WorkspaceManager? = null,
    private val splitViewState: ai.rever.boss.components.window_panel.SplitViewState? = null
) : PluginContext {

    companion object {
        // Plugin IDs for sandboxing - using consistent naming
        private const val PLUGIN_ID_BOOKMARKS = "panel-bookmarks"
        private const val PLUGIN_ID_DOWNLOADS = "panel-downloads"
        private const val PLUGIN_ID_CODEBASE = "panel-codebase"
        private const val PLUGIN_ID_TERMINAL = "panel-terminal"
        private const val PLUGIN_ID_CONSOLE = "panel-console"
        private const val PLUGIN_ID_PERFORMANCE = "panel-performance"
        private const val PLUGIN_ID_GIT_STATUS = "panel-git-status"
        private const val PLUGIN_ID_GIT_LOG = "panel-git-log"
        private const val PLUGIN_ID_TOP_OF_MIND = "panel-top-of-mind"
        private const val PLUGIN_ID_RUN_CONFIGS = "panel-run-configurations"
        private const val PLUGIN_ID_FLUCK = "panel-fluck"
        private const val PLUGIN_ID_LLM_RPA = "panel-llm-rpa"
        private const val PLUGIN_ID_RPA_RECORDER = "panel-rpa-recorder"
        private const val PLUGIN_ID_RPA_ENGINE = "panel-rpa-engine"
        private const val PLUGIN_ID_ADMIN_ROLE_MGMT = "panel-admin-role-management"
        private const val PLUGIN_ID_ROLE_CREATION = "panel-role-creation"
        private const val PLUGIN_ID_SECRET_MANAGER = "panel-secret-manager"
        private const val PLUGIN_ID_USER_SECRET_LIST = "panel-user-secret-list"
        private const val PLUGIN_ID_PLUGIN_MANAGER = "panel-plugin-manager"

        // Tab plugin IDs
        private const val PLUGIN_ID_TAB_FLUCK = "tab-fluck"
        private const val PLUGIN_ID_TAB_CODE_EDITOR = "tab-code-editor"
        private const val PLUGIN_ID_TAB_TERMINAL = "tab-terminal"

        // Persisted plugins loading state
        @Volatile
        private var persistedPluginsLoaded = false

        /**
         * Load persisted plugins. This is called automatically when DynamicPluginManager is first accessed.
         * Platform-specific implementation should set this callback.
         */
        var loadPersistedPluginsInternal: suspend (DynamicPluginManager) -> Unit = { _ ->
            // Default no-op - platform-specific code should set this
        }
    }

    private val logger = BossLogger.forComponent("DefaultPlugin")
    // Lifecycle-aware scope for long-running operations like dynamic panel registration
    // This scope should be cancelled when the plugin is disposed
    override val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Sandbox manager for plugin crash isolation
    private val sandboxManager: PluginSandboxManager = PluginSandboxManagerImpl()

    /**
     * Health summary across all sandboxed plugins.
     * Use this to monitor plugin health from the UI.
     */
    val pluginHealthSummary: StateFlow<PluginHealthSummary> = sandboxManager.healthSummary

    /**
     * Manager for dynamic plugin loading and unloading at runtime.
     * Use this to install, uninstall, enable, or disable plugins dynamically.
     */
    val dynamicPluginManager: DynamicPluginManager by lazy {
        val manager = DynamicPluginManager(
            panelRegistry = panelRegistry,
            tabRegistry = tabRegistry,
            sandboxManager = sandboxManager,
            createSandboxedContext = { pluginId, config ->
                createSandboxedContext(pluginId, config)
            }
        )

        // Load persisted plugins on first access (only once globally)
        if (!persistedPluginsLoaded) {
            persistedPluginsLoaded = true
            pluginScope.launch {
                loadPersistedPluginsInternal(manager)
            }
        }

        manager
    }

    /**
     * Toast state for plugin notifications.
     * Use this with PluginToastHost in your UI to display plugin status notifications.
     */
    val pluginToastState: PluginToastState = PluginToastState(pluginScope)

    // Notification service and listener for plugin events
    private val notificationService = BossPluginNotificationService(
        toastController = pluginToastState,
        onDisablePlugin = { pluginId ->
            pluginScope.launch {
                sandboxManager.disablePlugin(pluginId)
            }
        },
        onEnablePlugin = { pluginId ->
            pluginScope.launch {
                sandboxManager.enablePlugin(pluginId)
            }
        }
    )

    private val notificationListener = PluginSandboxNotificationListener(notificationService).also {
        (sandboxManager as? PluginSandboxManagerImpl)?.addListener(it)
    }

    // No sandbox for the default context (backward compatibility)
    override val sandbox: PluginSandboxRef? = null

    // Browser service for plugins needing embedded browser capabilities
    override val browserService: BrowserService? = getBrowserServiceInstance()

    // Git data provider for plugins that display git information
    override val gitDataProvider: GitDataProvider? by lazy {
        if (windowGitState != null) {
            GitDataProviderImpl(windowGitState) { _windowId }
        } else {
            null
        }
    }

    // Window ID for window-scoped operations
    override val windowId: String?
        get() = _windowId

    // Project path for project-specific operations
    override val projectPath: String?
        get() = windowProjectState?.selectedProject?.value?.path

    // Auth data provider for plugins that need authentication state
    override val authDataProvider: AuthDataProvider by lazy {
        AuthDataProviderImpl()
    }

    // User management provider for admin plugins
    override val userManagementProvider: UserManagementProvider by lazy {
        UserManagementProviderImpl()
    }

    // Role management provider for admin plugins
    override val roleManagementProvider: RoleManagementProvider by lazy {
        RoleManagementProviderImpl()
    }

    // File system data provider for codebase plugin
    override val fileSystemDataProvider: FileSystemDataProvider by lazy {
        FileSystemDataProviderImpl()
    }

    // Workspace data provider for plugins that manage workspaces
    override val workspaceDataProvider: WorkspaceDataProvider? by lazy {
        if (workspaceManager != null) {
            WorkspaceDataProviderImpl(workspaceManager)
        } else {
            null
        }
    }

    // Bookmark data provider for plugins that manage bookmarks
    override val bookmarkDataProvider: ai.rever.boss.plugin.api.BookmarkDataProvider by lazy {
        BookmarkDataProviderImpl()
    }

    // Split view operations for plugins that need tab/panel operations
    override val splitViewOperations: SplitViewOperations? by lazy {
        if (splitViewState != null) {
            SplitViewOperationsImpl(splitViewState)
        } else {
            null
        }
    }

    // Active tabs provider for topofmind plugin
    override val activeTabsProvider: ActiveTabsProvider? by lazy {
        if (splitViewState != null && workspaceManager != null) {
            ApiActiveTabsProviderAdapter(splitViewState, workspaceManager, _windowId ?: "unknown", pluginScope)
        } else {
            null
        }
    }

    // Run configuration data provider for run-configurations plugin
    override val runConfigurationDataProvider: ai.rever.boss.plugin.api.RunConfigurationDataProvider by lazy {
        ai.rever.boss.run.RunConfigurationDataProviderImpl()
    }

    // Performance data provider for performance plugin
    override val performanceDataProvider: ai.rever.boss.plugin.api.PerformanceDataProvider by lazy {
        createPerformanceDataProvider()
    }

    // Download data provider for downloads plugin
    override val downloadDataProvider: ai.rever.boss.plugin.api.DownloadDataProvider by lazy {
        createDownloadDataProvider()
    }

    // Secret data provider for secret manager and user secret list plugins
    override val secretDataProvider: ai.rever.boss.plugin.api.SecretDataProvider by lazy {
        ai.rever.boss.services.supabase.SecretDataProviderImpl()
    }

    // Terminal content provider for terminal plugin
    override val terminalContentProvider: ai.rever.boss.plugin.api.TerminalContentProvider by lazy {
        ai.rever.boss.components.plugin.providers.TerminalContentProviderImpl()
    }

    // Panel event provider for plugins that need to trigger panel events
    override val panelEventProvider: ai.rever.boss.plugin.api.PanelEventProvider by lazy {
        ai.rever.boss.components.plugin.providers.PanelEventProviderImpl()
    }

    // Settings provider for plugins that need to open settings
    override val settingsProvider: ai.rever.boss.plugin.api.SettingsProvider by lazy {
        ai.rever.boss.components.plugin.providers.SettingsProviderImpl()
    }

    // Context menu provider for plugins that need context menu functionality
    override val contextMenuProvider: ContextMenuProvider by lazy {
        DefaultContextMenuProvider()
    }

    // Log data provider for console plugin
    override val logDataProvider: LogDataProvider by lazy {
        createLogDataProvider()
    }

    // Plugin Store API key provider for secret manager and other plugins
    override val pluginStoreApiKeyProvider: PluginStoreApiKeyProvider by lazy {
        PluginStoreApiKeyProviderImpl()
    }

    /**
     * Create a sandboxed plugin context for a specific plugin.
     *
     * The sandboxed context provides:
     * - Isolated coroutine scope (errors don't propagate)
     * - Health monitoring and automatic restart
     * - Error boundary integration for UI components
     *
     * @param pluginId Unique identifier for the plugin
     * @param config Optional sandbox configuration
     * @return A sandboxed PluginContext for the plugin
     */
    fun createSandboxedContext(
        pluginId: String,
        config: SandboxConfig = SandboxConfig()
    ): PluginContext {
        val sandbox = sandboxManager.createSandbox(pluginId, config)

        // Start the sandbox asynchronously to avoid blocking UI thread
        pluginScope.launch {
            sandbox.start().onFailure { error ->
                logger.error(LogCategory.SYSTEM, "Failed to start sandbox", mapOf(
                    "pluginId" to pluginId
                ), error)
            }
        }

        // Create wrapped registries that record errors to the sandbox
        val sandboxedPanelRegistry = SandboxedPanelRegistry(sandbox, panelRegistry)
        val sandboxedTabRegistry = SandboxedTabRegistry(sandbox, tabRegistry)

        return SandboxedPluginContext(
            _sandbox = sandbox,
            delegate = this,
            sandboxedPanelRegistry = sandboxedPanelRegistry,
            sandboxedTabRegistry = sandboxedTabRegistry
        )
    }

    /**
     * Get the sandbox for a specific plugin.
     *
     * @param pluginId Plugin identifier
     * @return The sandbox, or null if not found
     */
    fun getPluginSandbox(pluginId: String) = sandboxManager.getSandbox(pluginId)

    /**
     * Restart a sandboxed plugin.
     *
     * @param pluginId Plugin identifier
     * @return Result indicating success or failure
     */
    suspend fun restartPlugin(pluginId: String) = sandboxManager.restartPlugin(pluginId)

    /**
     * Disable a sandboxed plugin.
     * Disabled plugins will not auto-restart and show a disabled fallback UI.
     *
     * @param pluginId Plugin identifier
     * @return Result indicating success or failure
     */
    suspend fun disablePlugin(pluginId: String) = sandboxManager.disablePlugin(pluginId)

    /**
     * Enable a previously disabled plugin.
     *
     * @param pluginId Plugin identifier
     * @return Result indicating success or failure
     */
    suspend fun enablePlugin(pluginId: String) = sandboxManager.enablePlugin(pluginId)

    /**
     * Check if a plugin is disabled.
     *
     * @param pluginId Plugin identifier
     * @return True if the plugin is disabled
     */
    fun isPluginDisabled(pluginId: String) = sandboxManager.isPluginDisabled(pluginId)

    init {
        logger.info(LogCategory.SYSTEM, "Initializing DefaultPlugin with sandboxed contexts")

        // ============================================================
        // SANDBOXED PANEL PLUGINS
        // Each plugin gets its own sandbox for crash isolation
        // NOTE: Most panel plugins are now loaded dynamically from JARs.
        // Only Plugin Manager remains bundled.
        // ============================================================

        // DYNAMIC: Bookmarks panel - loaded from boss-plugin-bookmarks JAR
        // val bookmarksContext = createSandboxedContext(PLUGIN_ID_BOOKMARKS)
        // BookmarksPanelPlugin.registerWithProviders(...)

        // DYNAMIC: Downloads panel - loaded from boss-plugin-downloads JAR
        // val downloadsContext = createSandboxedContext(PLUGIN_ID_DOWNLOADS)
        // DownloadsPanelPlugin.register(...)

        // DYNAMIC: CodeBase panel - loaded from boss-plugin-codebase JAR
        // val codebaseContext = createSandboxedContext(PLUGIN_ID_CODEBASE)
        // CodeBasePanelPlugin.registerWithProviders(...)

        // DYNAMIC: Terminal panel - loaded from boss-plugin-terminal JAR
        // val terminalPanelContext = createSandboxedContext(PLUGIN_ID_TERMINAL)
        // TerminalPanelPlugin.registerWithProviders(...)

        // DYNAMIC: Console panel - loaded from boss-plugin-console JAR
        // val consoleContext = createSandboxedContext(PLUGIN_ID_CONSOLE)
        // ConsolePanelPlugin.register(consoleContext)

        // DYNAMIC: Performance panel - loaded from boss-plugin-performance JAR
        // val performanceContext = createSandboxedContext(PLUGIN_ID_PERFORMANCE)
        // PerformancePanelPlugin.register(performanceContext)

        // DYNAMIC: Git panels - loaded from boss-plugin-git-log and boss-plugin-git-status JARs
        // registerGitPanels()

        // DYNAMIC: Top of Mind panel - loaded from boss-plugin-topofmind JAR
        // val topOfMindContext = createSandboxedContext(PLUGIN_ID_TOP_OF_MIND)
        // TopOfMindPanelPlugin.registerWithProviders(...)

        // DYNAMIC: Run Configurations plugin - loaded from boss-plugin-run-configurations JAR
        // val runConfigsContext = createSandboxedContext(PLUGIN_ID_RUN_CONFIGS)
        // RunConfigurationsPanelPlugin.register(...)

        // DYNAMIC: Fluck (ChatGPT) panel - loaded from boss-plugin-fluck JAR
        // val fluckPanelContext = createSandboxedContext(PLUGIN_ID_FLUCK)
        // FluckPanelPlugin.registerWithProviders(...)

        // DYNAMIC: LLM RPA panel - loaded from boss-plugin-llmrpa JAR
        // val llmRpaContext = createSandboxedContext(PLUGIN_ID_LLM_RPA)
        // LLMRpaPanelPlugin.register(...)

        // DYNAMIC: RPA Recorder panel - loaded from boss-plugin-rparecorder JAR
        // val rpaRecorderContext = createSandboxedContext(PLUGIN_ID_RPA_RECORDER)
        // RpaRecorderPanelPlugin.register(...)

        // DYNAMIC: RPA Engine panel - loaded from boss-plugin-rpaengine JAR
        // val rpaEngineContext = createSandboxedContext(PLUGIN_ID_RPA_ENGINE)
        // RpaEnginePanelPlugin.register(...)

        // ============================================================
        // BUNDLED PLUGIN: Plugin Manager
        // This is the ONLY bundled panel plugin - used for managing dynamic plugins
        // ============================================================
        val pluginManagerContext = createSandboxedContext(PLUGIN_ID_PLUGIN_MANAGER)
        PluginManagerSetup.registerPluginManagerPanel(
            pluginManagerContext,
            dynamicPluginManager,
            activeTabsProvider
        )

        // ============================================================
        // DYNAMIC PANEL PLUGINS (loaded from JARs)
        // Previously registered based on auth/state, now loaded dynamically
        // ============================================================

        // DYNAMIC: Admin Role Management panel - loaded from boss-plugin-admin-role-management JAR
        // registerAdminRoleManagementPlugin()

        // DYNAMIC: Role Creation panel - loaded from boss-plugin-role-creation JAR
        // registerRoleCreationPlugin()

        // DYNAMIC: Secret Manager panel - loaded from boss-plugin-secret-manager JAR
        // registerSecretManagerPlugin()

        // DYNAMIC: User Secret List panel - loaded from boss-plugin-user-secret-list JAR
        // registerUserSecretListPlugin()

        // ============================================================
        // TAB TYPE PLUGINS
        // ============================================================

        // Tab Types - using extension functions (they handle complex callback wiring)
        // Note: Tab types currently use the main context; sandbox integration is future work
        registerFluck()
        registerCodeEditor()
        registerTerminalTab()

        // ============================================================
        // EXTERNAL PLUGINS (loaded from ~/.boss/plugins/)
        // ============================================================
        loadExternalPlugins()

        logger.info(LogCategory.SYSTEM, "DefaultPlugin initialization complete", mapOf(
            "sandboxedPlugins" to sandboxManager.getAllSandboxes().size
        ))
    }

    /**
     * Dispose the plugin and cancel all coroutines
     * Should be called when the plugin is no longer needed
     */
    fun dispose() {
        // Dispose dynamic plugin manager and sandbox manager
        runBlocking {
            dynamicPluginManager.dispose()
            sandboxManager.dispose()
        }
        pluginScope.cancel()
    }

    /**
     * Load external plugins from the local plugins directory (~/.boss/plugins/).
     *
     * This scans for JAR files and installs them via DynamicPluginManager.
     */
    private fun loadExternalPlugins() {
        val pluginDir = File(System.getProperty("user.home"), ".boss/plugins")

        if (!pluginDir.exists() || !pluginDir.isDirectory) {
            logger.debug(LogCategory.SYSTEM, "External plugins directory not found", mapOf(
                "path" to pluginDir.absolutePath
            ))
            return
        }

        val jarFiles = pluginDir.listFiles { file ->
            file.isFile && file.extension == "jar"
        } ?: emptyArray()

        if (jarFiles.isEmpty()) {
            logger.debug(LogCategory.SYSTEM, "No external plugins found", mapOf(
                "path" to pluginDir.absolutePath
            ))
            return
        }

        logger.info(LogCategory.SYSTEM, "Loading external plugins", mapOf(
            "count" to jarFiles.size,
            "path" to pluginDir.absolutePath
        ))

        // Load each plugin asynchronously
        pluginScope.launch {
            for (jarFile in jarFiles) {
                try {
                    logger.info(LogCategory.SYSTEM, "Installing external plugin", mapOf(
                        "file" to jarFile.name
                    ))

                    val result = dynamicPluginManager.installPlugin(jarFile.absolutePath)

                    if (result.isSuccess) {
                        val info = result.getOrThrow()
                        logger.info(LogCategory.SYSTEM, "External plugin loaded successfully", mapOf(
                            "pluginId" to info.manifest.pluginId,
                            "version" to info.manifest.version,
                            "displayName" to info.manifest.displayName
                        ))
                    } else {
                        logger.error(LogCategory.SYSTEM, "Failed to load external plugin", mapOf(
                            "file" to jarFile.name,
                            "error" to (result.exceptionOrNull()?.message ?: "unknown")
                        ))
                    }
                } catch (e: Exception) {
                    logger.error(LogCategory.SYSTEM, "Exception loading external plugin", mapOf(
                        "file" to jarFile.name
                    ), e)
                }
            }
        }
    }

    // ============================================================
    // REMOVED BUNDLED PLUGINS
    // The following registration methods have been removed as these
    // panels are now loaded dynamically from JARs:
    // - registerSecretManagerPlugin()
    // - registerUserSecretListPlugin()
    // - registerAdminRoleManagementPlugin()
    // - registerRoleCreationPlugin()
    // - registerGitPanels()
    // ============================================================
}



/**
 * Adapter that implements the plugin-api ActiveTabsProvider interface
 * by wrapping the SplitViewState for tab collection.
 */
private class ApiActiveTabsProviderAdapter(
    private val splitViewState: ai.rever.boss.components.window_panel.SplitViewState,
    private val workspaceManager: ai.rever.boss.components.workspaces.WorkspaceManager,
    private val windowId: String,
    private val scope: CoroutineScope
) : ActiveTabsProvider {

    private val tabsLogger = BossLogger.forComponent("ActiveTabsProvider")
    private val _activeTabs = kotlinx.coroutines.flow.MutableStateFlow<List<ActiveTabData>>(emptyList())
    override val activeTabs: kotlinx.coroutines.flow.StateFlow<List<ActiveTabData>> = _activeTabs

    init {
        // Start polling loop (like bundled LLMRpaIntegration.kt does)
        // This ensures dynamic plugins receive tab updates
        scope.launch {
            var consecutiveFailures = 0
            while (isActive) {
                try {
                    refreshTabs()
                    consecutiveFailures = 0
                } catch (e: Exception) {
                    consecutiveFailures++
                    tabsLogger.warn(LogCategory.GENERAL, "Failed to refresh tabs", mapOf(
                        "consecutiveFailures" to consecutiveFailures
                    ), error = e)
                }
                // Base interval 2s, +1s per failure, max 10s
                delay(minOf(2000L + (consecutiveFailures * 1000L), 10000L))
            }
        }
    }

    override suspend fun refreshTabs() {
        val tabs = splitViewState.collectAllActiveTabs(workspaceManager, windowId)
        _activeTabs.value = tabs.map { convertToActiveTabData(it) }
    }

    override fun selectTab(tabId: String, panelId: String) {
        splitViewState.selectTabInPanel(tabId, panelId)
    }

    override fun getTabUrl(tabId: String): String? {
        val tabs = splitViewState.collectAllActiveTabs(workspaceManager, windowId)
        val tab = tabs.find { it.tabInfo.id == tabId }
        return (tab?.tabInfo as? ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo)?.currentUrl
    }

    override fun getFaviconCacheKey(tabId: String): String? {
        val tabs = splitViewState.collectAllActiveTabs(workspaceManager, windowId)
        val tab = tabs.find { it.tabInfo.id == tabId }
        return (tab?.tabInfo as? ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo)?.faviconCacheKey
    }

    @androidx.compose.runtime.Composable
    override fun loadFavicon(cacheKey: String?): androidx.compose.ui.graphics.painter.Painter? {
        return loadFaviconFromCache(cacheKey)?.painter
    }

    override fun getFallbackIcon(typeId: String): androidx.compose.ui.graphics.vector.ImageVector? {
        // Return a generic tab icon based on type
        return when {
            typeId.contains("fluck", ignoreCase = true) -> Icons.Outlined.Language
            typeId.contains("terminal", ignoreCase = true) -> Icons.Outlined.Terminal
            typeId.contains("editor", ignoreCase = true) -> Icons.Outlined.Code
            else -> Icons.Outlined.Tab
        }
    }

    override fun getBrowserIntegration(tabId: String): ApiBrowserIntegration? {
        // Set the selected tab ID for the accessor
        BrowserAccessor.selectedTabId = tabId

        // Store the split view state so BrowserAccessor can find the browser
        // This is critical for dynamic plugins that don't have access to LocalSplitViewState
        storeSplitViewState(splitViewState)

        // Get the internal browser integration
        val internalIntegration = BrowserAccessor().getActiveBrowserIntegration()
            ?: return null

        // Wrap it in an adapter that implements the plugin-api interface
        return BrowserIntegrationAdapter(internalIntegration)
    }

    override fun createBrowserTab(url: String, title: String): String? {
        return try {
            val activeComponent = splitViewState.getActiveTabsComponent() ?: return null
            val timestamp = kotlin.time.Clock.System.now().toEpochMilliseconds()
            val tabId = "plugin-tab-$timestamp"

            val fluckTab = ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo(
                id = tabId,
                typeId = ai.rever.boss.plugin.tab.fluck.FluckTabType.typeId,
                _title = title,
                _icon = androidx.compose.material.icons.Icons.Outlined.Language,
                url = url
            )

            val tabIndex = activeComponent.addTab(fluckTab)
            if (tabIndex >= 0) {
                activeComponent.selectTab(tabIndex)
                tabId
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    override fun closeTab(tabId: String): Boolean {
        return try {
            val allPanels = splitViewState.getAllPanels()
            for (panel in allPanels) {
                val tabsComponent = panel.tabsComponent
                // Check if the tab exists by trying to get its component
                val component = tabsComponent.getComponentById(tabId)
                if (component != null) {
                    tabsComponent.removeTabById(tabId)
                    return true
                }
            }
            false
        } catch (e: Exception) {
            false
        }
    }

    private fun convertToActiveTabData(tab: ai.rever.boss.topofmind.ActiveTab): ActiveTabData {
        val tabInfo = tab.tabInfo
        val fluckTab = tabInfo as? ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
        return ActiveTabData(
            tabId = tabInfo.id,
            typeId = tabInfo.typeId.typeId,
            title = tabInfo.title,
            workspaceId = tab.workspaceId,
            workspaceName = tab.workspaceName,
            panelId = tab.panelId,
            windowId = tab.windowId,
            splitPosition = tab.splitPosition,
            url = fluckTab?.currentUrl,
            faviconCacheKey = fluckTab?.faviconCacheKey
        )
    }
}

/**
 * Adapter that bridges the internal BrowserIntegration to the plugin-api BrowserIntegration.
 * This allows dynamic plugins to use browser capabilities through the PluginContext API.
 */
private class BrowserIntegrationAdapter(
    private val internal: InternalBrowserIntegration
) : ApiBrowserIntegration {

    override suspend fun executeJavaScript(script: String): Any? {
        return internal.executeJavaScript(script)
    }

    override fun isBrowserAvailable(): Boolean {
        return internal.isBrowserAvailable()
    }

    override suspend fun getCurrentUrl(): String? {
        return internal.getCurrentUrl()
    }
}

/**
 * Default implementation of ContextMenuProvider that bridges the plugin API
 * to the app's native context menu implementation.
 *
 * Converts ContextMenuItemData (from plugin-ui-core) to ContextMenuItem (from app)
 * and applies the contextMenu modifier.
 */
private class DefaultContextMenuProvider : ContextMenuProvider {

    @androidx.compose.runtime.Composable
    override fun applyContextMenu(
        modifier: androidx.compose.ui.Modifier,
        items: List<ContextMenuItemData>
    ): androidx.compose.ui.Modifier {
        // Convert plugin API items to app's ContextMenuItem format
        val appItems = items.map { data ->
            if (data.isDivider) {
                ContextMenuItem(isDivider = true)
            } else {
                ContextMenuItem(
                    text = data.label,
                    icon = data.icon,
                    onClick = data.onClick
                )
            }
        }
        return modifier.contextMenu(items = appItems)
    }
}

