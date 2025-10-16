package ai.rever.boss.components.plugin.panels.right_top

import com.arkivanov.decompose.ComponentContext

/**
 * Android implementation of RPA Engine Factory
 */
actual class RpaEngineFactory {
    actual fun createComponent(ctx: ComponentContext, panelInfo: ai.rever.boss.components.registery.PanelInfo): RpaEngineComponent {
        return AndroidRpaEngineComponent(ctx, panelInfo)
    }
}

/**
 * Android RPA Engine Component
 */
class AndroidRpaEngineComponent(
    ctx: ComponentContext,
    panelInfo: ai.rever.boss.components.registery.PanelInfo
) : RpaEngineComponent(ctx, panelInfo) {
    
    override fun loadAvailableConfigurations() {
        // Android implementation would load from app storage
        _availableConfigs.value = emptyList()
    }
    
    override fun loadConfiguration(file: ConfigFileInfo) {
        // Android implementation would load from app storage
        _executionStatus.value = ExecutionStatus.ERROR
    }
    
    override suspend fun executeActions() {
        // Android doesn't support direct browser automation
        _executionStatus.value = ExecutionStatus.ERROR
    }
}
