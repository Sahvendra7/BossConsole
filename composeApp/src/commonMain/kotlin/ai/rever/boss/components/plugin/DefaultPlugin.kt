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
import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.TabRegistry
import ai.rever.boss.plugin.panel.secretmanager.SecretManagerInfo
import ai.rever.boss.window.WindowGitState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

class DefaultPlugin(
    override val panelRegistry: PanelRegistry,
    override val tabRegistry: TabRegistry,
    val windowProjectState: WindowProjectState?,
    val windowGitState: WindowGitState? = null,
    val windowId: String? = null
) : PluginContext {
    // Lifecycle-aware scope for long-running operations like dynamic panel registration
    // This scope should be cancelled when the plugin is disposed
    override val pluginScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    init {
        // Panels - using plugin system with component factories

        // Bookmarks panel - Priority 1 - First position
        // Uses registerWithProviders for clean plugin architecture with dialog provider
        BookmarksPanelPlugin.registerWithProviders(
            context = this,
            faviconLoaderProvider = { cacheKey -> loadFaviconFromCache(cacheKey) },
            contextMenuProvider = { modifier, items ->
                modifier.contextMenu(
                    items = items.map { item ->
                        ContextMenuItem(
                            text = item.label,
                            icon = item.icon,
                            isDivider = item.isDivider,
                            onClick = item.onClick
                        )
                    }
                )
            },
            dialogProvider = BookmarksDialogProviderImpl
        )

        // Downloads panel - Priority 2 - Below bookmarks
        val downloadDataProvider = createDownloadDataProvider()
        DownloadsPanelPlugin.register(this, downloadDataProvider)

        // CodeBase panel - uses registerWithProviders for clean plugin architecture
        val fileSystemProvider = FileSystemDataProviderImpl()
        val projectDataProvider = ProjectDataProviderImpl()
        val directoryPickerProvider = DirectoryPickerProviderImpl()

        CodeBasePanelPlugin.registerWithProviders(
            context = this,
            fileSystemProvider = fileSystemProvider,
            projectDataProvider = projectDataProvider,
            getWindowId = { windowId },
            getSelectedProject = {
                windowProjectState?.selectedProject?.value?.let { project ->
                    ProjectData(
                        name = project.name,
                        path = project.path,
                        lastOpened = project.lastOpened
                    )
                }
            },
            onSelectProject = { projectData ->
                selectProjectInWindow(
                    windowProjectState,
                    Project(
                        name = projectData.name,
                        path = projectData.path,
                        lastOpened = projectData.lastOpened
                    )
                )
            },
            directoryPickerProvider = directoryPickerProvider,
            contextMenuProvider = { modifier, items ->
                modifier.contextMenu(
                    items = items.map { item ->
                        ContextMenuItem(
                            text = item.label,
                            icon = item.icon,
                            onClick = item.onClick
                        )
                    }
                )
            },
            openTerminalTab = { workingDirectory ->
                windowId?.let { wid ->
                    pluginScope.launch {
                        TerminalEventBus.openTerminal(
                            command = null,
                            sourceWindowId = wid,
                            workingDirectory = workingDirectory
                        )
                    }
                }
            }
        )

        // Terminal panel - uses registerWithProviders for clean plugin architecture
        TerminalPanelPlugin.registerWithProviders(
            context = this,
            terminalContentProvider = TerminalContentProviderImpl(),
            panelEventProvider = PanelEventProviderImpl(),
            settingsProvider = SettingsProviderImpl()
        )

        ConsolePanelPlugin.register(this)
        PerformancePanelPlugin.register(this)

        // Git panels - dynamically registered based on project and git repository status
        registerGitPanels()

        // Top of Mind panel - uses registerWithProviders for clean plugin architecture
        TopOfMindPanelPlugin.registerWithProviders(
            context = this,
            collectAllActiveTabs = { TopOfMindDataProvider.collectAllActiveTabs() },
            getAllPanelStates = { TopOfMindDataProvider.getAllPanelStates() },
            faviconLoader = { cacheKey -> TopOfMindDataProvider.loadFavicon(cacheKey) },
            getTabUrl = { activeTab -> TopOfMindDataProvider.getTabUrl(activeTab) },
            getFaviconCacheKey = { activeTab -> TopOfMindDataProvider.getFaviconCacheKey(activeTab) },
            getFallbackIcon = { activeTab -> TopOfMindDataProvider.getFallbackIcon(activeTab) }
        )

        // Run Configurations plugin - requires window context
        RunConfigurationsPanelPlugin.register(this, object : WindowContextProviderForPlugin {
            override fun getWindowId(): String? = windowId
            override fun getProjectPath(): String = windowProjectState?.selectedProject?.value?.path ?: ""
        })

        // Fluck (ChatGPT) panel - uses registerWithProviders for clean plugin architecture
        FluckPanelPlugin.registerWithProviders(
            context = this,
            contentProviderFactory = { FluckPanelContentProviderImpl() }
        )

        // LLM RPA panel
        LLMRpaPanelPlugin.register(this) { ctx, panelInfo ->
            LLMRpaFactory().createComponent(ctx, panelInfo)
        }

        // RPA Recorder panel
        RpaRecorderPanelPlugin.register(this) { ctx, panelInfo ->
            RpaRecorderFactory().createComponent(ctx, panelInfo)
        }

        // RPA Engine panel
        RpaEnginePanelPlugin.register(this) { ctx, panelInfo ->
            RpaEngineFactory().createComponent(ctx, panelInfo)
        }

        // Admin Role Management panel - uses new plugin with dynamic registration based on admin status
        registerAdminRoleManagementPlugin()
        // Role Creation panel - uses new plugin with dynamic registration based on admin status
        registerRoleCreationPlugin()

        // Secret Manager panel - uses new plugin with dynamic registration based on auth state
        registerSecretManagerPlugin()

        // User Secret List panel - uses new plugin with dynamic registration based on auth state
        registerUserSecretListPlugin()

        // Tab Types - using extension functions (they handle complex callback wiring)
        registerFluck()
        registerCodeEditor()
        registerTerminalTab()
    }

    /**
     * Dispose the plugin and cancel all coroutines
     * Should be called when the plugin is no longer needed
     */
    fun dispose() {
        pluginScope.cancel()
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
                        // User has permission - register panel using the plugin
                        logger.info(LogCategory.AUTH, "Registering secret manager panel", mapOf(
                            "user" to LogSanitizer.maskEmail(user?.email ?: "")
                        ))
                        SecretManagerPanelPlugin.register(
                            context = this@DefaultPlugin,
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
                        // User is authenticated - register panel using the plugin
                        logger.info(LogCategory.UI, "Registering user secret list panel", mapOf(
                            "user" to LogSanitizer.maskEmail(user?.email ?: "")
                        ))
                        UserSecretListPanelPlugin.register(
                            context = this@DefaultPlugin,
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
                        // User is admin - register panel using the plugin
                        logger.info(LogCategory.UI, "Registering admin role management panel", mapOf(
                            "user" to LogSanitizer.maskEmail(user?.email ?: "")
                        ))
                        AdminRoleManagementPanelPlugin.register(
                            context = this@DefaultPlugin,
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
                        // User is admin - register panel using the plugin
                        logger.info(LogCategory.UI, "Registering role creation panel", mapOf(
                            "user" to LogSanitizer.maskEmail(user?.email ?: "")
                        ))
                        RoleCreationPanelPlugin.register(
                            context = this@DefaultPlugin,
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
        val gitDataProvider = GitDataProviderImpl(windowGitState) { windowId }

        val gitState = windowGitState
        val projectState = windowProjectState

        if (gitState == null || projectState == null) {
            logger.debug(LogCategory.UI, "Git panels require window context, skipping registration")
            return
        }

        logger.debug(LogCategory.UI, "Initializing git panels dynamic registration")

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
                        // Project is a git repository - register panels
                        logger.info(LogCategory.UI, "Registering git panels")
                        GitStatusPanelPlugin.register(this@DefaultPlugin, gitDataProvider) { windowId }
                        GitLogPanelPlugin.register(this@DefaultPlugin, gitDataProvider)
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

