package ai.rever.boss.plugin.sandbox.context

import ai.rever.boss.plugin.api.DeepLinkActionHandler
import ai.rever.boss.plugin.api.McpToolProvider
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.api.PanelMenuContribution
import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginManifest
import ai.rever.boss.plugin.api.PluginSandboxRef
import ai.rever.boss.plugin.api.SettingsPageProvider
import ai.rever.boss.plugin.api.ShortcutActionProvider
import ai.rever.boss.plugin.api.StatusBarItemProvider
import ai.rever.boss.plugin.api.TabRegistry
import ai.rever.boss.plugin.sandbox.PluginSandbox
import ai.rever.boss.plugin.sandbox.SandboxState
import ai.rever.boss.plugin.sandbox.health.PluginHealthSummary
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SandboxedPluginContextTest {
    private class FakeSandbox : PluginSandbox {
        override val pluginId: String = "test.plugin"

        var currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

        override val sandboxScope: CoroutineScope
            get() = currentScope

        override val healthMetrics:
            kotlinx.coroutines.flow.StateFlow<ai.rever.boss.plugin.sandbox.health.PluginHealthMetrics>
            get() = error("Not needed")

        override val state: kotlinx.coroutines.flow.StateFlow<SandboxState>
            get() = kotlinx.coroutines.flow.MutableStateFlow(SandboxState.RUNNING)

        override suspend fun start(): Result<Unit> = Result.success(Unit)

        override suspend fun stop(): Result<Unit> = Result.success(Unit)

        override suspend fun restart(): Result<Unit> {
            currentScope.cancel()
            currentScope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
            return Result.success(Unit)
        }

        override fun markUnhealthy() { /* no-op */ }

        override fun resetHealth() { /* no-op */ }

        override fun resetRestartAttempts() { /* no-op */ }

        override fun recordHeartbeat() { /* no-op */ }

        override fun recordSuccess() { /* no-op */ }

        override fun recordError(error: Throwable) { /* no-op */ }
    }

    private class FakePluginContext : PluginContext {
        override val panelRegistry: PanelRegistry get() = error("Not needed")
        override val tabRegistry: TabRegistry get() = error("Not needed")
        override val pluginScope: CoroutineScope get() = error("Not needed")
        override val sandbox: PluginSandboxRef get() = error("Not needed")
        override val manifest: PluginManifest? = null
        override val browserService = null
        override val llmProvider = null
        override val brokeredCredentialProvider = null
        override val runConfigurationDataProvider = null
        override val activeTabsProvider = null
        override val windowId: String? = null
        override val projectPath: String? = null
        override val authDataProvider = null
        override val userManagementProvider = null
        override val roleManagementProvider = null
        override val supabaseDataProvider = null
        override val panelEventProvider = null
        override val settingsProvider = null
        override val contextMenuProvider = null
        override val logDataProvider = null
        override val pluginStoreApiKeyProvider = null
        override val tabUpdateProviderFactory = null
        override val dashboardContentProvider = null
        override val zoomSettingsProvider = null
        override val urlHistoryProvider = null
        override val screenCaptureProvider = null
        override val coBrowseRtcProvider = null
        override val editorContentProvider = null
        override val notificationProvider = null
        override val applicationEventBus = null
        override val pluginStorageFactory = null
        override val genericDialogProvider = null
        override val navigationResolverProvider = null
        override val semanticTokenProvider = null
        override val navigationTargetProvider = null
        override val clipboardProvider = null
        override val filePickerProvider = null
        override val directoryPickerProvider = null
        override val projectDataProvider = null
        override val keyboardShortcutProvider = null
        override val cacheProvider = null
        override val backgroundTaskProvider = null
        override val diagnosticProvider = null

        override fun registerMcpToolProvider(provider: McpToolProvider) { /* no-op */ }

        override fun unregisterMcpToolProvider(providerId: String) { /* no-op */ }

        override val mcpToolRegistry = null

        override fun registerPanelMenuContribution(contribution: PanelMenuContribution) { /* no-op */ }

        override fun unregisterPanelMenuContribution(contributionId: String) { /* no-op */ }

        override fun registerSettingsPage(provider: SettingsPageProvider) { /* no-op */ }

        override fun unregisterSettingsPage(pageId: String) { /* no-op */ }

        override fun registerDeepLinkActionHandler(handler: DeepLinkActionHandler) { /* no-op */ }

        override fun unregisterDeepLinkActionHandler(handlerId: String) { /* no-op */ }

        override fun registerShortcutActionProvider(provider: ShortcutActionProvider) { /* no-op */ }

        override fun unregisterShortcutActionProvider(providerId: String) { /* no-op */ }

        override fun registerStatusBarItem(provider: StatusBarItemProvider) { /* no-op */ }

        override fun unregisterStatusBarItem(itemId: String) { /* no-op */ }

        override fun <T : Any> getPluginAPI(apiClass: Class<T>): T? = null

        override fun registerPluginAPI(api: Any) { /* no-op */ }
    }

    private class FakePanelRegistry : PanelRegistry() {
        override fun getAllPanels(): List<PanelInfo> = emptyList()
    }

    private class FakeTabRegistry : TabRegistry()

    @Test
    fun `pluginScope facade resolves new scope after sandbox restart`() =
        runBlocking {
            val sandbox = FakeSandbox()
            val context =
                SandboxedPluginContext(
                    sandbox,
                    FakePluginContext(),
                    SandboxedPanelRegistry(sandbox, FakePanelRegistry()),
                    SandboxedTabRegistry(sandbox, FakeTabRegistry()),
                )

            val cachedScope = context.pluginScope

            var executedBefore = false
            cachedScope.launch { executedBefore = true }.join()
            assertTrue(executedBefore, "Should execute before restart")

            sandbox.restart()

            var executedAfter = false
            cachedScope.launch { executedAfter = true }.join()
            assertTrue(executedAfter, "Should execute after restart because facade delegates to new scope")
        }

    @Test
    fun `pluginScope facade cancels old coroutines on restart`() =
        runBlocking {
            val sandbox = FakeSandbox()
            val context =
                SandboxedPluginContext(
                    sandbox,
                    FakePluginContext(),
                    SandboxedPanelRegistry(sandbox, FakePanelRegistry()),
                    SandboxedTabRegistry(sandbox, FakeTabRegistry()),
                )

            val cachedScope = context.pluginScope

            var cancelled = false
            val job =
                cachedScope.launch {
                    try {
                        delay(10000)
                    } catch (ignored: CancellationException) {
                        cancelled = true
                    }
                }

            sandbox.restart()
            job.join()

            assertTrue(cancelled, "Old coroutine should be cancelled when sandbox restarts")
        }
}
