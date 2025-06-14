package ai.rever.boss.components.plugin.panels.right_top

import com.arkivanov.decompose.ComponentContext

/**
 * WASM implementation of RPA Engine Factory
 */
actual class RpaEngineFactory {
    actual fun createComponent(ctx: ComponentContext, panelInfo: ai.rever.boss.components.registery.PanelInfo): RpaEngineComponent {
        return WasmRpaEngineComponent(ctx, panelInfo)
    }
}

/**
 * WASM RPA Engine Component
 */
class WasmRpaEngineComponent(
    ctx: ComponentContext,
    panelInfo: ai.rever.boss.components.registery.PanelInfo
) : RpaEngineComponent(ctx, panelInfo) {
    
    override fun loadAvailableConfigurations() {
        // WASM implementation would use browser's local storage or IndexedDB
        _availableConfigs.value = emptyList()
    }
    
    override fun loadConfiguration(file: ConfigFileInfo) {
        // WASM implementation would load from browser storage
        _executionStatus.value = ExecutionStatus.ERROR
    }
    
    override suspend fun executeActions() {
        // WASM can't directly control browser tabs for security reasons
        _executionStatus.value = ExecutionStatus.ERROR
    }
}