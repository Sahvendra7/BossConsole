package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.plugin.api.PanelInfo
import com.arkivanov.decompose.ComponentContext

/**
 * Wasm-specific LLM RPA component
 */
class WasmLLMRpaComponent(
    ctx: ComponentContext,
    panelInfo: PanelInfo
) : LLMRpaComponent(ctx, panelInfo) {
    // Wasm implementation can use the base implementation
    // Additional Wasm-specific functionality can be added here
}

/**
 * Factory for creating Wasm LLM RPA components
 */
actual class LLMRpaFactory {
    actual fun createComponent(ctx: ComponentContext, panelInfo: PanelInfo): LLMRpaComponent {
        return WasmLLMRpaComponent(ctx, panelInfo)
    }
}

/**
 * Platform-specific function to create LLM RPA executor
 */
actual fun createPlatformLLMRpaExecutor(browser: Any): RpaActionExecutor? {
    // Wasm uses the common implementation via BrowserIntegration
    return null
}
