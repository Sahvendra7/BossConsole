package ai.rever.boss.components.plugin.panels.right_top


/**
 * Platform-specific function to create LLM RPA executor
 */
actual fun createPlatformLLMRpaExecutor(browser: Any): RpaActionExecutor? {
    // Wasm uses the common implementation via BrowserIntegration
    return null
}
