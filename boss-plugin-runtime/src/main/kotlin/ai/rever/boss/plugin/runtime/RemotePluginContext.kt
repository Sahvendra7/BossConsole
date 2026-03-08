package ai.rever.boss.plugin.runtime

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import org.slf4j.LoggerFactory

/**
 * Stub plugin context for out-of-process plugins.
 *
 * Out-of-process plugins run in a separate JVM process and cannot access
 * Compose-dependent host services directly. This context provides:
 * - A dedicated coroutine scope tied to the plugin process lifecycle
 * - All data providers returning null initially (wired with IPC proxies in future phases)
 * - Panel/tab registration forwarded to the kernel via [uiService]
 *
 * This class mirrors the shape of `ai.rever.boss.plugin.api.PluginContext` but does not
 * formally implement it, since PanelRegistry/TabRegistry require Compose + Decompose
 * which are not available in a pure-JVM plugin runtime process.
 *
 * When running in-process (testing or embedded mode), use the host's PluginContext directly.
 */
class RemotePluginContext(
    val processId: String,
    val uiService: PluginUIServiceImpl,
) {
    private val logger = LoggerFactory.getLogger(RemotePluginContext::class.java)

    private val job: Job = SupervisorJob()

    /** Coroutine scope tied to this plugin process's lifecycle. Cancel via [dispose]. */
    val pluginScope: CoroutineScope = CoroutineScope(Dispatchers.Default + job)

    /** Current window ID — supplied via BOSS_WINDOW_ID environment variable. */
    val windowId: String? = System.getenv("BOSS_WINDOW_ID")

    /** Current project path — supplied via BOSS_PROJECT_PATH environment variable. */
    val projectPath: String? = System.getenv("BOSS_PROJECT_PATH")

    // ---- Data providers (null until IPC proxies are wired in Phase 7 full implementation) ----
    // Each provider corresponds to a field in PluginContext; IPC proxy objects will be
    // substituted here when the full out-of-process proxy layer is built.

    val performanceDataProvider: Any? = null
    val downloadDataProvider: Any? = null
    val bookmarkDataProvider: Any? = null
    val workspaceDataProvider: Any? = null
    val splitViewOperations: Any? = null
    val gitDataProvider: Any? = null
    val fileSystemDataProvider: Any? = null
    val secretDataProvider: Any? = null
    val runConfigurationDataProvider: Any? = null
    val activeTabsProvider: Any? = null
    val authDataProvider: Any? = null
    val userManagementProvider: Any? = null
    val roleManagementProvider: Any? = null
    val supabaseDataProvider: Any? = null
    val panelEventProvider: Any? = null
    val settingsProvider: Any? = null
    val contextMenuProvider: Any? = null
    val logDataProvider: Any? = null
    val pluginStoreApiKeyProvider: Any? = null
    val notificationProvider: Any? = null
    val applicationEventBus: Any? = null
    val pluginStorageFactory: Any? = null
    val genericDialogProvider: Any? = null
    val clipboardProvider: Any? = null
    val filePickerProvider: Any? = null
    val directoryPickerProvider: Any? = null
    val projectDataProvider: Any? = null
    val backgroundTaskProvider: Any? = null
    val diagnosticProvider: Any? = null
    val browserService: Any? = null

    // ---- Panel / tab registration (forwarded to kernel via IPC) ----

    /**
     * Register a panel UI surface with the kernel.
     *
     * The plugin sends its widget tree via [uiService]; the kernel renders it in the
     * appropriate panel slot. Call this before sending any WidgetUpdates for the surface.
     */
    fun registerPanel(
        surfaceId: String,
        displayName: String,
        iconName: String = "",
        defaultSlot: String = "",
    ) {
        logger.info("Registering panel: id={}, name={}, slot={}", surfaceId, displayName, defaultSlot)
        // Actual IPC registration is handled when the kernel calls RegisterUI on this service
    }

    /**
     * Register a tab type with the kernel via IPC.
     *
     * Full tab-type wiring is part of Phase 7 completion; for now this records intent.
     */
    fun registerTabType(surfaceId: String, displayName: String) {
        logger.info("Registering tab type: id={}, name={}", surfaceId, displayName)
    }

    /**
     * Dispose this context and cancel all coroutines in [pluginScope].
     * Call when the plugin process is shutting down.
     */
    fun dispose() {
        logger.info("RemotePluginContext disposed for process: {}", processId)
        job.cancel()
    }
}
