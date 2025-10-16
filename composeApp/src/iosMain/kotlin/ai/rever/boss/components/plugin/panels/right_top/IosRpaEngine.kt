package ai.rever.boss.components.plugin.panels.right_top

import com.arkivanov.decompose.ComponentContext

/**
 * iOS implementation of RPA Engine Factory
 */
actual class RpaEngineFactory {
    actual fun createComponent(ctx: ComponentContext, panelInfo: ai.rever.boss.components.registery.PanelInfo): RpaEngineComponent {
        return IosRpaEngineComponent(ctx, panelInfo)
    }
}

/**
 * iOS RPA Engine Component
 */
class IosRpaEngineComponent(
    ctx: ComponentContext,
    panelInfo: ai.rever.boss.components.registery.PanelInfo
) : RpaEngineComponent(ctx, panelInfo) {
    
    override fun loadAvailableConfigurations() {
        // iOS implementation would load from Documents directory
        _availableConfigs.value = emptyList()
    }
    
    override fun loadConfiguration(file: ConfigFileInfo) {
        // iOS implementation would load from Documents directory
        _executionStatus.value = ExecutionStatus.ERROR
    }
    
    override suspend fun executeActions() {
        // iOS doesn't support direct browser automation
        _executionStatus.value = ExecutionStatus.ERROR
    }
}
