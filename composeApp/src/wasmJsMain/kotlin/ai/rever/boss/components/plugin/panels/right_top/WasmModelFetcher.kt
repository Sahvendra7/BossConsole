package ai.rever.boss.components.plugin.panels.right_top

/**
 * Wasm implementation of model fetcher
 */
actual class PlatformModelFetcher {
    actual suspend fun fetchModelsFromAPI(provider: LLMProvider): List<DynamicLLMModel> {
        // Return default models for Wasm - June 2025 actual releases
        return when (provider) {
            LLMProvider.ANTHROPIC -> listOf(
                DynamicLLMModel("claude-opus-4", "Claude Opus 4", "ANTHROPIC", 200000),
                DynamicLLMModel("claude-sonnet-4", "Claude Sonnet 4", "ANTHROPIC", 200000),
                DynamicLLMModel("claude-3-7-sonnet", "Claude 3.7 Sonnet", "ANTHROPIC", 200000),
                DynamicLLMModel("claude-3-5-sonnet-20241022", "Claude 3.5 Sonnet", "ANTHROPIC", 200000)
            )
            LLMProvider.OPENAI -> listOf(
                DynamicLLMModel("o3-pro", "o3 Pro", "OPENAI", 128000),
                DynamicLLMModel("o3", "o3", "OPENAI", 128000),
                DynamicLLMModel("o3-mini", "o3 Mini", "OPENAI", 128000),
                DynamicLLMModel("gpt-4-5", "GPT-4.5", "OPENAI", 128000),
                DynamicLLMModel("gpt-4-1", "GPT-4.1", "OPENAI", 128000),
                DynamicLLMModel("gpt-4o", "GPT-4o", "OPENAI", 128000)
            )
            LLMProvider.TOGETHER -> listOf(
                DynamicLLMModel("meta-llama/Llama-4-Maverick", "Llama 4 Maverick (400B)", "TOGETHER", 1000000),
                DynamicLLMModel("meta-llama/Llama-4-Scout", "Llama 4 Scout (109B)", "TOGETHER", 10000000),
                DynamicLLMModel("deepseek-ai/DeepSeek-V3", "DeepSeek V3 (671B)", "TOGETHER", 256000),
                DynamicLLMModel("google/gemini-2-5-pro", "Gemini 2.5 Pro", "TOGETHER", 1000000),
                DynamicLLMModel("xai/grok-3", "Grok 3", "TOGETHER", 128000)
            )
            else -> emptyList()
        }
    }
    
    actual suspend fun loadCache(): ModelCache? = null
    
    actual suspend fun saveCache(cache: ModelCache) {
        // No-op for now
    }
}