package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.plugin.api.PanelInfo
import com.arkivanov.decompose.ComponentContext

/**
 * iOS-specific LLM RPA component
 */
class IosLLMRpaComponent(
    ctx: ComponentContext,
    panelInfo: PanelInfo
) : LLMRpaComponent(ctx, panelInfo) {
    // iOS implementation can use the base implementation
    // Additional iOS-specific functionality can be added here
}

/**
 * Factory for creating iOS LLM RPA components
 */
actual class LLMRpaFactory {
    actual fun createComponent(ctx: ComponentContext, panelInfo: PanelInfo): LLMRpaComponent {
        return IosLLMRpaComponent(ctx, panelInfo)
    }
}

/**
 * Platform-specific function to create LLM RPA executor
 */
actual fun createPlatformLLMRpaExecutor(browser: Any): RpaActionExecutor? {
    // iOS uses the common implementation via BrowserIntegration
    return null
}
