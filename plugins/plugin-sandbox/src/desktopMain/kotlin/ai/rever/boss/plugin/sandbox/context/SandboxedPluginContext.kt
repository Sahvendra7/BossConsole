package ai.rever.boss.plugin.sandbox.context

import ai.rever.boss.plugin.api.PanelRegistry
import ai.rever.boss.plugin.api.PluginContext
import ai.rever.boss.plugin.api.PluginSandboxRef
import ai.rever.boss.plugin.api.TabRegistry
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
     * Get the underlying sandbox for this context.
     */
    fun getSandbox(): PluginSandbox = _sandbox
}
