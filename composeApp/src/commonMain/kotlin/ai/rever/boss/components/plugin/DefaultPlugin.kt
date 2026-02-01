package ai.rever.boss.components.plugin

import ai.rever.boss.git.GitDataProviderImpl
import ai.rever.boss.plugin.panel.console.ConsolePanelPlugin
import ai.rever.boss.plugin.panel.downloads.DownloadsPanelPlugin
import ai.rever.boss.plugin.panel.gitlog.GitLogInfo
import ai.rever.boss.plugin.panel.gitlog.GitLogPanelPlugin
import ai.rever.boss.plugin.panel.gitstatus.GitStatusInfo
import ai.rever.boss.plugin.panel.gitstatus.GitStatusPanelPlugin
import ai.rever.boss.plugin.panel.performance.PerformancePanelPlugin
import ai.rever.boss.plugin.panel.runconfigurations.RunConfigurationsPanelPlugin
import ai.rever.boss.plugin.panel.runconfigurations.WindowContextProviderForPlugin
import ai.rever.boss.plugin.panel.secretmanager.SecretManagerPanelPlugin
import ai.rever.boss.plugin.panel.usersecretlist.UserSecretListInfo
import ai.rever.boss.plugin.panel.usersecretlist.UserSecretListPanelPlugin
import ai.rever.boss.plugin.panel.adminrolemanagement.AdminRoleManagementInfo
import ai.rever.boss.plugin.panel.adminrolemanagement.AdminRoleManagementPanelPlugin
import ai.rever.boss.plugin.panel.rolecreation.RoleCreationInfo
import ai.rever.boss.plugin.panel.rolecreation.RoleCreationPanelPlugin
import ai.rever.boss.plugin.panel.terminal.TerminalPanelPlugin
import ai.rever.boss.plugin.panel.topofmind.TopOfMindPanelPlugin
import ai.rever.boss.plugin.panel.manager.PluginManagerPanelPlugin
import ai.rever.boss.plugin.panel.bookmarks.BookmarksPanelPlugin
import ai.rever.boss.plugin.panel.codebase.CodeBasePanelPlugin
import ai.rever.boss.plugin.panel.fluck.FluckPanelPlugin
import ai.rever.boss.plugin.panel.llmrpa.LLMRpaPanelPlugin
import ai.rever.boss.plugin.panel.rparecorder.RpaRecorderPanelPlugin
import ai.rever.boss.plugin.panel.rpaengine.RpaEnginePanelPlugin
import ai.rever.boss.components.plugin.providers.TerminalContentProviderImpl
import ai.rever.boss.components.plugin.providers.PanelEventProviderImpl
import ai.rever.boss.components.plugin.providers.SettingsProviderImpl
import ai.rever.boss.components.plugin.providers.TopOfMindDataProvider
import ai.rever.boss.cache.loadFaviconFromCache
import ai.rever.boss.components.events.TerminalEventBus
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.panels.left_top.BookmarksDialogProviderImpl
import ai.rever.boss.plugin.ui.ContextMenuItemData
import ai.rever.boss.components.plugin.panels.left_top.createDownloadDataProvider
import ai.rever.boss.components.plugin.providers.DirectoryPickerProviderImpl
import ai.rever.boss.components.plugin.providers.FileSystemDataProviderImpl
import ai.rever.boss.components.plugin.providers.ProjectDataProviderImpl
import ai.rever.boss.plugin.panel.codebase.ProjectData
import ai.rever.boss.window.Project
import ai.rever.boss.window.selectProjectInWindow
import ai.rever.boss.components.plugin.providers.FluckPanelContentProviderImpl
import ai.rever.boss.components.plugin.panels.right_top.LLMRpaFactory
import ai.rever.boss.components.plugin.panels.right_top.RpaRecorderFactory
import ai.rever.boss.components.plugin.panels.right_top.RpaEngineFactory
import ai.rever.boss.components.plugin.tab_types.fluck.registerFluck
import ai.rever.boss.components.plugin.tab_types.registerCodeEditor
import ai.rever.boss.components.plugin.tab_types.registerTerminalTab
import ai.rever.boss.components.plugin.tab_types.fluck.SecretChangeNotifier
import ai.rever.boss.services.auth.AuthDataProviderImpl
import ai.rever.boss.services.auth.AuthStateManager
import ai.rever.boss.services.supabase.RoleManagementProviderImpl
import ai.rever.boss.services.supabase.SecretDataProviderImpl
import ai.rever.boss.services.supabase.UserManagementProviderImpl
import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.utils.logging.LogSanitizer
import ai.rever.boss.window.WindowProjectState
import ai.rever.boss.plugin.api.AuthDataProvider
import ai.rever.boss.plugin.api.GitDataProvider
import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginSandboxRef
import ai.rever.boss.plugin.api.RoleManagementProvider
import ai.rever.boss.plugin.api.TabRegistry
import ai.rever.boss.plugin.api.UserManagementProvider
import ai.rever.boss.plugin.browser.BrowserService
import ai.rever.boss.plugin.panel.secretmanager.SecretManagerInfo
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.io.File

class DefaultPlugin(
    override val panelRegistry: PanelRegistry,
    override val tabRegistry: TabRegistry,
    val windowProjectState: WindowProjectState?,
    val windowGitState: WindowGitState? = null,
    private val _windowId: String? = null
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

    // Note: fileSystemDataProvider, secretDataProvider, runConfigurationDataProvider, and activeTabsProvider
    // are implemented on SandboxedPluginContext which has access to window-scoped services
    // through the DynamicPluginManager createSandboxedContext mechanism

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
        PluginManagerSetup.registerPluginManagerPanel(pluginManagerContext, dynamicPluginManager)

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

    /**
     * Register the Secret Manager panel plugin with dynamic auth-based registration.
     *
     * Panel is only shown to users with admin role (or secrets.write permission once implemented).
     * Uses pluginScope to tie lifecycle to the plugin.
     */
    private fun registerSecretManagerPlugin() {
        val logger = BossLogger.forComponent("SecretManagerRegistration")
        val secretDataProvider = SecretDataProviderImpl()
        val userManagementProvider = UserManagementProviderImpl()

        logger.debug(LogCategory.AUTH, "Initializing secret manager panel registration")

        // Create sandboxed context (reused across registrations)
        var secretManagerContext: PluginContext? = null

        // Observe auth state and dynamically register/unregister panel
        pluginScope.launch(Dispatchers.Main) {
            AuthStateManager.currentUser
                .map { user ->
                    // Allow access if user is admin
                    // Note: Add secrets.write permission check when RBAC permission system is ready
                    // See docs/RBAC_GUIDE.md for permission system documentation
                    user?.isAdmin == true
                }
                .distinctUntilChanged()
                .collect { hasPermission ->
                    val user = AuthStateManager.currentUser.value
                    logger.debug(LogCategory.AUTH, "Permission status changed", mapOf(
                        "hasPermission" to hasPermission,
                        "user" to LogSanitizer.maskEmail(user?.email ?: "")
                    ))

                    if (hasPermission) {
                        // User has permission - register panel with sandboxed context
                        logger.info(LogCategory.AUTH, "Registering secret manager panel", mapOf(
                            "user" to LogSanitizer.maskEmail(user?.email ?: "")
                        ))

                        // Create sandboxed context if not already created
                        val ctx = secretManagerContext ?: createSandboxedContext(PLUGIN_ID_SECRET_MANAGER).also {
                            secretManagerContext = it
                        }

                        SecretManagerPanelPlugin.register(
                            context = ctx,
                            secretDataProvider = secretDataProvider,
                            userManagementProvider = userManagementProvider,
                            onSecretChanged = { SecretChangeNotifier.notifyRefreshSync() }
                        )
                    } else {
                        // User does not have permission - unregister panel
                        logger.info(LogCategory.AUTH, "Unregistering secret manager panel")
                        panelRegistry.unregisterPanel(SecretManagerInfo.id)
                    }
                }
        }
    }

    /**
     * Register the User Secret List panel plugin with dynamic auth-based registration.
     *
     * Panel is shown to all authenticated users.
     * Uses pluginScope to tie lifecycle to the plugin.
     */
    private fun registerUserSecretListPlugin() {
        val logger = BossLogger.forComponent("UserSecretListRegistration")
        val secretDataProvider = SecretDataProviderImpl()

        logger.debug(LogCategory.UI, "Initializing user secret list panel registration")

        // Create sandboxed context (reused across registrations)
        var userSecretListContext: PluginContext? = null

        // Observe auth state and dynamically register/unregister panel
        pluginScope.launch(Dispatchers.Main) {
            AuthStateManager.currentUser
                .map { user -> user != null }
                .distinctUntilChanged()
                .collect { isAuthenticated ->
                    val user = AuthStateManager.currentUser.value
                    logger.debug(LogCategory.UI, "Auth status changed for user secret list", mapOf(
                        "isAuthenticated" to isAuthenticated,
                        "user" to LogSanitizer.maskEmail(user?.email ?: "")
                    ))

                    if (isAuthenticated) {
                        // User is authenticated - register panel with sandboxed context
                        logger.info(LogCategory.UI, "Registering user secret list panel", mapOf(
                            "user" to LogSanitizer.maskEmail(user?.email ?: "")
                        ))

                        // Create sandboxed context if not already created
                        val ctx = userSecretListContext ?: createSandboxedContext(PLUGIN_ID_USER_SECRET_LIST).also {
                            userSecretListContext = it
                        }

                        UserSecretListPanelPlugin.register(
                            context = ctx,
                            secretDataProvider = secretDataProvider,
                            secretChangeEvents = SecretChangeNotifier.secretChangeEvents
                        )
                    } else {
                        // User is not authenticated - unregister panel
                        logger.info(LogCategory.UI, "Unregistering user secret list panel")
                        panelRegistry.unregisterPanel(UserSecretListInfo.id)
                    }
                }
        }
    }

    /**
     * Register the Admin Role Management panel plugin with dynamic admin-based registration.
     *
     * Panel is only shown to admin users.
     * Uses pluginScope to tie lifecycle to the plugin.
     */
    private fun registerAdminRoleManagementPlugin() {
        val logger = BossLogger.forComponent("AdminRoleManagementRegistration")
        val userManagementProvider = UserManagementProviderImpl()
        val authDataProvider = AuthDataProviderImpl()

        logger.debug(LogCategory.UI, "Initializing admin role management panel registration")

        // Create sandboxed context (reused across registrations)
        var adminRoleMgmtContext: PluginContext? = null

        // Observe auth state and dynamically register/unregister panel
        pluginScope.launch(Dispatchers.Main) {
            AuthStateManager.currentUser
                .map { user -> user?.isAdmin == true }
                .distinctUntilChanged()
                .collect { isAdmin ->
                    val user = AuthStateManager.currentUser.value
                    logger.debug(LogCategory.UI, "Admin status changed for role management", mapOf(
                        "isAdmin" to isAdmin,
                        "user" to LogSanitizer.maskEmail(user?.email ?: "")
                    ))

                    if (isAdmin) {
                        // User is admin - register panel with sandboxed context
                        logger.info(LogCategory.UI, "Registering admin role management panel", mapOf(
                            "user" to LogSanitizer.maskEmail(user?.email ?: "")
                        ))

                        // Create sandboxed context if not already created
                        val ctx = adminRoleMgmtContext ?: createSandboxedContext(PLUGIN_ID_ADMIN_ROLE_MGMT).also {
                            adminRoleMgmtContext = it
                        }

                        AdminRoleManagementPanelPlugin.register(
                            context = ctx,
                            userManagementProvider = userManagementProvider,
                            authDataProvider = authDataProvider
                        )
                    } else {
                        // User is not admin - unregister panel
                        logger.info(LogCategory.UI, "Unregistering admin role management panel")
                        panelRegistry.unregisterPanel(AdminRoleManagementInfo.id)
                    }
                }
        }
    }

    /**
     * Register the Role Creation panel plugin with dynamic admin-based registration.
     *
     * Panel is only shown to admin users.
     * Uses pluginScope to tie lifecycle to the plugin.
     */
    private fun registerRoleCreationPlugin() {
        val logger = BossLogger.forComponent("RoleCreationRegistration")
        val roleManagementProvider = RoleManagementProviderImpl()

        logger.debug(LogCategory.UI, "Initializing role creation panel registration")

        // Create sandboxed context (reused across registrations)
        var roleCreationContext: PluginContext? = null

        // Observe auth state and dynamically register/unregister panel
        pluginScope.launch(Dispatchers.Main) {
            AuthStateManager.currentUser
                .map { user -> user?.isAdmin == true }
                .distinctUntilChanged()
                .collect { isAdmin ->
                    val user = AuthStateManager.currentUser.value
                    logger.debug(LogCategory.UI, "Admin status changed for role creation", mapOf(
                        "isAdmin" to isAdmin,
                        "user" to LogSanitizer.maskEmail(user?.email ?: "")
                    ))

                    if (isAdmin) {
                        // User is admin - register panel with sandboxed context
                        logger.info(LogCategory.UI, "Registering role creation panel", mapOf(
                            "user" to LogSanitizer.maskEmail(user?.email ?: "")
                        ))

                        // Create sandboxed context if not already created
                        val ctx = roleCreationContext ?: createSandboxedContext(PLUGIN_ID_ROLE_CREATION).also {
                            roleCreationContext = it
                        }

                        RoleCreationPanelPlugin.register(
                            context = ctx,
                            roleManagementProvider = roleManagementProvider
                        )
                    } else {
                        // User is not admin - unregister panel
                        logger.info(LogCategory.UI, "Unregistering role creation panel")
                        panelRegistry.unregisterPanel(RoleCreationInfo.id)
                    }
                }
        }
    }

    /**
     * Register the Git panels with dynamic registration based on project and git repository status.
     *
     * Panels are only shown when a project is selected AND it's a git repository.
     * Uses pluginScope to tie lifecycle to the plugin.
     */
    private fun registerGitPanels() {
        val logger = BossLogger.forComponent("GitPanelsRegistration")
        val gitDataProvider = GitDataProviderImpl(windowGitState) { _windowId }

        val gitState = windowGitState
        val projectState = windowProjectState

        if (gitState == null || projectState == null) {
            logger.debug(LogCategory.UI, "Git panels require window context, skipping registration")
            return
        }

        logger.debug(LogCategory.UI, "Initializing git panels dynamic registration")

        // Create sandboxed contexts for git panels (reused across registrations)
        var gitStatusContext: PluginContext? = null
        var gitLogContext: PluginContext? = null

        // Observe both project state and git repository status
        pluginScope.launch(Dispatchers.Main) {
            combine(
                projectState.selectedProject,
                gitState.isGitRepository
            ) { project, isGitRepo ->
                // Show git panels when project has a path and is a git repository
                project.path.isNotEmpty() && isGitRepo
            }
                .distinctUntilChanged()
                .collect { shouldShow ->
                    logger.debug(LogCategory.UI, "Git panels visibility changed", mapOf(
                        "shouldShow" to shouldShow
                    ))

                    if (shouldShow) {
                        // Project is a git repository - register panels with sandboxed contexts
                        logger.info(LogCategory.UI, "Registering git panels")

                        // Create sandboxed contexts if not already created
                        val statusCtx = gitStatusContext ?: createSandboxedContext(PLUGIN_ID_GIT_STATUS).also {
                            gitStatusContext = it
                        }
                        val logCtx = gitLogContext ?: createSandboxedContext(PLUGIN_ID_GIT_LOG).also {
                            gitLogContext = it
                        }

                        GitStatusPanelPlugin.register(statusCtx, gitDataProvider) { _windowId }
                        GitLogPanelPlugin.register(logCtx, gitDataProvider)
                    } else {
                        // Not a git repository or no project - unregister panels
                        logger.info(LogCategory.UI, "Unregistering git panels")
                        panelRegistry.unregisterPanel(GitStatusInfo.id)
                        panelRegistry.unregisterPanel(GitLogInfo.id)
                    }
                }
        }
    }
}

