package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.registery.PanelInfo
import com.arkivanov.decompose.ComponentContext

/**
 * Android-specific LLM RPA component
 */
class AndroidLLMRpaComponent(
    ctx: ComponentContext,
    panelInfo: PanelInfo
) : LLMRpaComponent(ctx, panelInfo) {
    // Android implementation can use the base implementation
    // Additional Android-specific functionality can be added here
}

/**
 * Factory for creating Android LLM RPA components
 */
actual class LLMRpaFactory {
    actual fun createComponent(ctx: ComponentContext, panelInfo: PanelInfo): LLMRpaComponent {
        return AndroidLLMRpaComponent(ctx, panelInfo)
    }
}

/**
 * Platform-specific function to create LLM RPA executor
 */
actual fun createPlatformLLMRpaExecutor(browser: Any): RpaActionExecutor? {
    // Android uses the common implementation via BrowserIntegration
    return null
}
