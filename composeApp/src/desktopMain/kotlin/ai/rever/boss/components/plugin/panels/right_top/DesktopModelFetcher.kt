package ai.rever.boss.components.plugin.panels.right_top

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.io.File

/**
 * Desktop implementation of model fetcher
 */
actual class PlatformModelFetcher {
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    private val cacheFile = File(System.getProperty("user.home"), ".boss/llm_models_cache.json")
    
    actual suspend fun fetchModelsFromAPI(provider: LLMProvider): List<DynamicLLMModel> {
        return withContext(Dispatchers.IO) {
            // Check for environment variable override first
            val envModels = getModelsFromEnvironment(provider)
            if (envModels.isNotEmpty()) {
                return@withContext envModels
            }
            
            // Otherwise fetch from API or defaults
            when (provider) {
                LLMProvider.OPENAI -> fetchOpenAIModels()
                LLMProvider.ANTHROPIC -> fetchAnthropicModels()
                LLMProvider.TOGETHER -> fetchTogetherModels()
                else -> emptyList()
            }
        }
    }
    
    private suspend fun fetchOpenAIModels(): List<DynamicLLMModel> {
        return try {
            // OpenAI models endpoint (requires API key)
            val apiKey = LLMSettings.getApiKey(LLMProvider.OPENAI)
            if (apiKey.isNullOrBlank()) {
                return getDefaultOpenAIModels()
            }
            
            val response = httpClient.get("https://api.openai.com/v1/models") {
                headers {
                    append("Authorization", "Bearer $apiKey")
                }
            }
            
            if (response.status.isSuccess()) {
                val json = response.body<JsonObject>()
                val models = json["data"]?.jsonArray ?: return getDefaultOpenAIModels()
                
                models.mapNotNull { modelJson ->
                    val obj = modelJson.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    
                    // Filter for chat models
                    if (id.contains("gpt") || id.contains("o1")) {
                        DynamicLLMModel(
                            id = id,
                            name = formatModelName(id),
                            provider = "OPENAI",
                            contextLength = getContextLength(id),
                            capabilities = getModelCapabilities(id)
                        )
                    } else null
                }.sortedByDescending { it.id }
            } else {
                getDefaultOpenAIModels()
            }
        } catch (e: Exception) {
            getDefaultOpenAIModels()
        }
    }
    
    private suspend fun fetchAnthropicModels(): List<DynamicLLMModel> {
        // Anthropic doesn't have a public models endpoint yet
        // Return actual models for June 2025 based on official documentation
        return listOf(
            DynamicLLMModel(
                id = "claude-opus-4-20250514",
                name = "Claude Opus 4",
                provider = "ANTHROPIC",
                contextLength = 200000,
                description = "High-performance model, excels in coding and complex tasks",
                releaseDate = "2025-05",
                capabilities = listOf("text", "vision", "function-calling", "code-execution", "web-search", "extended-thinking")
            ),
            DynamicLLMModel(
                id = "claude-3-7-sonnet-20250219",
                name = "Claude 3.7 Sonnet",
                provider = "ANTHROPIC",
                contextLength = 200000,
                description = "Most intelligent model with extended thinking capabilities",
                releaseDate = "2025-02",
                capabilities = listOf("text", "vision", "function-calling", "reasoning", "extended-thinking")
            ),
            DynamicLLMModel(
                id = "claude-3-5-sonnet-20240620",
                name = "Claude 3.5 Sonnet",
                provider = "ANTHROPIC",
                contextLength = 200000,
                description = "Upgraded model with strong performance in coding and agentic tasks",
                releaseDate = "2024-06",
                capabilities = listOf("text", "vision", "function-calling")
            ),
            DynamicLLMModel(
                id = "claude-3-5-haiku-20241022",
                name = "Claude 3.5 Haiku",
                provider = "ANTHROPIC",
                contextLength = 200000,
                description = "Fast, cost-effective model optimized for lightweight tasks",
                releaseDate = "2024-10",
                capabilities = listOf("text", "vision", "function-calling")
            )
        )
    }
    
    private suspend fun fetchTogetherModels(): List<DynamicLLMModel> {
        return try {
            val apiKey = LLMSettings.getApiKey(LLMProvider.TOGETHER)
            if (apiKey.isNullOrBlank()) {
                return getDefaultTogetherModels()
            }
            
            val response = httpClient.get("https://api.together.xyz/v1/models") {
                headers {
                    append("Authorization", "Bearer $apiKey")
                }
            }
            
            if (response.status.isSuccess()) {
                val json = response.body<JsonArray>()
                
                json.mapNotNull { modelJson ->
                    val obj = modelJson.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
                    val displayName = obj["display_name"]?.jsonPrimitive?.content ?: formatModelName(id)
                    val contextLength = obj["context_length"]?.jsonPrimitive?.intOrNull ?: 8192
                    
                    // Filter for chat/instruct models
                    if (id.contains("instruct", ignoreCase = true) || 
                        id.contains("chat", ignoreCase = true) ||
                        id.contains("turbo", ignoreCase = true)) {
                        DynamicLLMModel(
                            id = id,
                            name = displayName,
                            provider = "TOGETHER",
                            contextLength = contextLength,
                            capabilities = getModelCapabilities(id)
                        )
                    } else null
                }.sortedBy { it.name }
            } else {
                getDefaultTogetherModels()
            }
        } catch (e: Exception) {
            getDefaultTogetherModels()
        }
    }
    
    private fun getDefaultOpenAIModels() = listOf(
        DynamicLLMModel("o3-pro", "o3 Pro", "OPENAI", 128000),
        DynamicLLMModel("o3", "o3", "OPENAI", 128000),
        DynamicLLMModel("o3-mini", "o3 Mini", "OPENAI", 128000),
        DynamicLLMModel("gpt-4-5", "GPT-4.5", "OPENAI", 128000),
        DynamicLLMModel("gpt-4-1", "GPT-4.1", "OPENAI", 128000),
        DynamicLLMModel("gpt-4o", "GPT-4o", "OPENAI", 128000)
    )
    
    private fun getDefaultTogetherModels() = listOf(
        DynamicLLMModel("meta-llama/Llama-4-Maverick", "Llama 4 Maverick", "TOGETHER", 1000000),
        DynamicLLMModel("meta-llama/Llama-4-Scout", "Llama 4 Scout", "TOGETHER", 10000000),
        DynamicLLMModel("deepseek-ai/DeepSeek-V3", "DeepSeek V3", "TOGETHER", 256000),
        DynamicLLMModel("google/gemini-2-5-pro", "Gemini 2.5 Pro", "TOGETHER", 1000000),
        DynamicLLMModel("xai/grok-3", "Grok 3", "TOGETHER", 128000)
    )
    
    private fun formatModelName(id: String): String {
        return id.split("/").last()
            .replace("-", " ")
            .split(" ")
            .joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    }
    
    private fun getContextLength(modelId: String): Int {
        return when {
            modelId.contains("llama-4-scout", ignoreCase = true) -> 10000000
            modelId.contains("gemini-2-5-pro") -> 1000000
            modelId.contains("llama-4-maverick", ignoreCase = true) -> 1000000
            modelId.contains("claude") -> 200000
            modelId.contains("o3") -> 128000
            modelId.contains("gpt-4") -> 128000
            modelId.contains("gpt-3.5-turbo-16k") -> 16385
            modelId.contains("gpt-3.5") -> 4096
            else -> 128000 // Default for June 2025 models
        }
    }
    
    private fun getModelCapabilities(modelId: String): List<String> {
        val capabilities = mutableListOf("text")
        
        when {
            modelId.contains("claude-opus-4") || modelId.contains("claude-3-7-sonnet") -> {
                capabilities.addAll(listOf("vision", "function-calling", "code-execution", "web-search", "extended-thinking"))
            }
            modelId.contains("claude") -> capabilities.addAll(listOf("vision", "function-calling"))
            modelId.contains("o3") -> capabilities.addAll(listOf("reasoning", "math", "code", "chain-of-thought"))
            modelId.contains("gpt-4") -> capabilities.addAll(listOf("vision", "function-calling", "json-mode"))
            modelId.contains("gpt-3.5") -> capabilities.addAll(listOf("function-calling", "json-mode"))
            modelId.contains("llama-4", ignoreCase = true) -> capabilities.addAll(listOf("vision", "code", "multilingual"))
            modelId.contains("deepseek", ignoreCase = true) -> capabilities.addAll(listOf("code", "math", "reasoning"))
            modelId.contains("gemini", ignoreCase = true) -> capabilities.addAll(listOf("vision", "code", "reasoning"))
            modelId.contains("grok", ignoreCase = true) -> capabilities.addAll(listOf("reasoning", "code", "math"))
            modelId.contains("vision", ignoreCase = true) -> capabilities.add("vision")
            modelId.contains("code", ignoreCase = true) -> capabilities.add("code")
        }
        
        return capabilities
    }
    
    private fun getModelsFromEnvironment(provider: LLMProvider): List<DynamicLLMModel> {
        // Check for custom models in environment variable
        // Format: BOSS_LLM_MODELS_<PROVIDER>="model1:name1:context1;model2:name2:context2"
        val envVar = "BOSS_LLM_MODELS_${provider.name}"
        val modelsString = System.getenv(envVar) ?: return emptyList()
        
        return try {
            modelsString.split(";").mapNotNull { modelDef ->
                val parts = modelDef.split(":")
                if (parts.size >= 2) {
                    DynamicLLMModel(
                        id = parts[0].trim(),
                        name = parts[1].trim(),
                        provider = provider.name,
                        contextLength = parts.getOrNull(2)?.trim()?.toIntOrNull() ?: 128000
                    )
                } else null
            }
        } catch (e: Exception) {
            println("Error parsing models from environment: ${e.message}")
            emptyList()
        }
    }
    
    actual suspend fun loadCache(): ModelCache? {
        return withContext(Dispatchers.IO) {
            try {
                if (cacheFile.exists()) {
                    val json = cacheFile.readText()
                    Json.decodeFromString<ModelCache>(json)
                } else null
            } catch (e: Exception) {
                println("Error loading model cache: ${e.message}")
                null
            }
        }
    }
    
    actual suspend fun saveCache(cache: ModelCache) {
        withContext(Dispatchers.IO) {
            try {
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(Json.encodeToString(ModelCache.serializer(), cache))
            } catch (e: Exception) {
                println("Error saving model cache: ${e.message}")
            }
        }
    }
}