package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.registery.PanelInfo
import com.arkivanov.decompose.ComponentContext
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
import kotlinx.serialization.encodeToString
import java.awt.Desktop
import java.io.File

/**
 * Desktop-specific LLM RPA component
 */
class DesktopLLMRpaComponent(
    ctx: ComponentContext,
    panelInfo: PanelInfo
) : LLMRpaComponent(ctx, panelInfo) {
    
    private val httpClient = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(Json {
                prettyPrint = true
                isLenient = true
                ignoreUnknownKeys = true
            })
        }
    }
    
    override suspend fun callLLMApi(request: LLMRpaRequest): LLMRpaResponse {
        // Load settings first
        LLMSettingsManager.loadSettings()
        
        // Check if we have a valid API key for the selected provider
        if (!LLMSettings.hasValidApiKey()) {
            // Use mock implementation if no API key is configured
            return super.callLLMApi(request)
        }
        
        val provider = LLMSettings.selectedProvider
        val modelId = LLMSettings.selectedModelId
        val apiKey = LLMSettings.getApiKey(provider) ?: return super.callLLMApi(request)
        val endpoint = LLMSettings.getApiEndpoint()
        
        println("DEBUG: LLM RPA - Provider: $provider, Model: $modelId")
        
        return try {
            withContext(Dispatchers.IO) {
                when (provider) {
                    LLMProvider.ANTHROPIC -> callAnthropicApi(request, apiKey, modelId)
                    LLMProvider.OPENAI -> callOpenAIApi(request, apiKey, modelId)
                    LLMProvider.TOGETHER -> callTogetherApi(request, apiKey, modelId)
                    LLMProvider.CUSTOM -> callCustomApi(request, apiKey, endpoint)
                }
            }
        } catch (e: Exception) {
            // If API fails, use enhanced mock response
            println("LLM API call failed: ${e.message}")
            super.callLLMApi(request)
        }
    }
    
    private suspend fun callAnthropicApi(
        request: LLMRpaRequest,
        apiKey: String,
        modelId: String
    ): LLMRpaResponse {
        // Map short model IDs to full IDs if needed (for backward compatibility)
        val actualModelId = when (modelId) {
            "claude-opus-4" -> "claude-opus-4-20250514"
            "claude-3-7-sonnet" -> "claude-3-7-sonnet-20250219"
            "claude-3-5-sonnet" -> "claude-3-5-sonnet-20240620"
            "claude-3-5-haiku" -> "claude-3-5-haiku-20241022"
            else -> modelId
        }
        
        println("DEBUG: Anthropic API - Using model: $actualModelId (from $modelId)")
        println("DEBUG: API Key present: ${apiKey.isNotBlank()}")
        println("DEBUG: API Key length: ${apiKey.length}")
        
        val requestBody = buildJsonObject {
            put("model", JsonPrimitive(actualModelId))
            put("max_tokens", JsonPrimitive(LLMSettings.maxTokens))
            put("temperature", JsonPrimitive(LLMSettings.temperature))
            putJsonArray("messages") {
                addJsonObject {
                    put("role", JsonPrimitive("user"))
                    put("content", JsonPrimitive(buildPrompt(request)))
                }
            }
        }
        
        println("DEBUG: Request body: ${Json.encodeToString(JsonObject.serializer(), requestBody)}")
        
        try {
            val response = httpClient.post("https://api.anthropic.com/v1/messages") {
                headers {
                    append("x-api-key", apiKey)
                    append("anthropic-version", "2023-06-01")
                }
                contentType(ContentType.Application.Json)
                setBody(requestBody)
            }
            
            println("DEBUG: Response status: ${response.status}")
            
            if (!response.status.isSuccess()) {
                val errorBody = response.bodyAsText()
                println("DEBUG: Error response body: $errorBody")
                throw Exception("Anthropic API error: ${response.status} - $errorBody")
            }
            
            val responseBody = response.body<JsonObject>()
            val content = responseBody["content"]?.jsonArray?.get(0)?.jsonObject?.get("text")?.jsonPrimitive?.content
                ?: throw Exception("Invalid response format")
            
            return parseRpaResponse(content)
        } catch (e: Exception) {
            println("DEBUG: Exception in callAnthropicApi: ${e.message}")
            throw e
        }
    }
    
    private suspend fun callOpenAIApi(
        request: LLMRpaRequest,
        apiKey: String,
        modelId: String
    ): LLMRpaResponse {
        val response = httpClient.post("https://api.openai.com/v1/chat/completions") {
            headers {
                append("Authorization", "Bearer $apiKey")
            }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", JsonPrimitive(modelId))
                put("temperature", JsonPrimitive(LLMSettings.temperature))
                put("max_tokens", JsonPrimitive(LLMSettings.maxTokens))
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive("You are an RPA (Robotic Process Automation) assistant that generates browser automation actions based on natural language instructions."))
                    }
                    addJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(buildPrompt(request)))
                    }
                }
            })
        }
        
        if (!response.status.isSuccess()) {
            throw Exception("OpenAI API error: ${response.status}")
        }
        
        val responseBody = response.body<JsonObject>()
        val content = responseBody["choices"]?.jsonArray?.get(0)?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw Exception("Invalid response format")
        
        return parseRpaResponse(content)
    }
    
    private suspend fun callTogetherApi(
        request: LLMRpaRequest,
        apiKey: String,
        modelId: String
    ): LLMRpaResponse {
        val response = httpClient.post("https://api.together.xyz/v1/chat/completions") {
            headers {
                append("Authorization", "Bearer $apiKey")
            }
            contentType(ContentType.Application.Json)
            setBody(buildJsonObject {
                put("model", JsonPrimitive(modelId))
                put("temperature", JsonPrimitive(LLMSettings.temperature))
                put("max_tokens", JsonPrimitive(LLMSettings.maxTokens))
                putJsonArray("messages") {
                    addJsonObject {
                        put("role", JsonPrimitive("system"))
                        put("content", JsonPrimitive("You are an RPA assistant that generates browser automation actions."))
                    }
                    addJsonObject {
                        put("role", JsonPrimitive("user"))
                        put("content", JsonPrimitive(buildPrompt(request)))
                    }
                }
            })
        }
        
        if (!response.status.isSuccess()) {
            throw Exception("Together AI API error: ${response.status}")
        }
        
        val responseBody = response.body<JsonObject>()
        val content = responseBody["choices"]?.jsonArray?.get(0)?.jsonObject
            ?.get("message")?.jsonObject?.get("content")?.jsonPrimitive?.content
            ?: throw Exception("Invalid response format")
        
        return parseRpaResponse(content)
    }
    
    private suspend fun callCustomApi(
        request: LLMRpaRequest,
        apiKey: String,
        endpoint: String
    ): LLMRpaResponse {
        if (endpoint.isBlank()) {
            throw Exception("Custom endpoint not configured")
        }
        
        val response = httpClient.post(endpoint) {
            headers {
                append("Authorization", "Bearer $apiKey")
            }
            contentType(ContentType.Application.Json)
            setBody(request)
        }
        
        if (!response.status.isSuccess()) {
            throw Exception("Custom API error: ${response.status}")
        }
        
        return response.body<LLMRpaResponse>()
    }
    
    private fun buildPrompt(request: LLMRpaRequest): String {
        val instructions = request.actions.joinToString("\n") { action ->
            "- ${action.instruction}"
        }
        
        return """
            Generate RPA browser automation actions for the following instructions:
            
            Instructions:
            $instructions
            
            Source URL: ${request.sourceUrl}
            
            Return the response as a JSON object with the following structure:
            {
                "configuration": [
                    {
                        "name": "Action description",
                        "action_type": "default",
                        "type": "action_type",
                        "selector": {
                            "type": "css|xpath|id|text|none",
                            "value": "selector_value_or_null",
                            "isUnique": true
                        },
                        "value": "value_if_needed",
                        "meta": {
                            "key": "value"
                        }
                    }
                ],
                "status": "success",
                "message": "Explanation of what the actions do"
            }
            
            Available action types: navigate, click, type, wait, scroll, screenshot, extract, select, hover, rightClick
            
            Important selector guidelines:
            - For Google search, use selector: {"type": "css", "value": "textarea[name='q']"} or {"type": "xpath", "value": "//textarea[@name='q']"}
            - Prefer CSS selectors over XPath when possible
            - Use name attributes when available
            - For input/textarea elements, prefer name or id attributes
            - type action requires clicking the element first, then typing
            
            Provide only the JSON response without any additional text or formatting.
        """.trimIndent()
    }
    
    private fun parseRpaResponse(content: String): LLMRpaResponse {
        return try {
            // Try to extract JSON from the response
            val jsonMatch = Regex("""\{[\s\S]*\}""").find(content)
            val jsonString = jsonMatch?.value ?: content
            
            Json.decodeFromString<LLMRpaResponse>(jsonString)
        } catch (e: Exception) {
            // If parsing fails, create a simple response
            LLMRpaResponse(
                configuration = listOf(
                    RpaActionConfig(
                        name = "Wait",
                        action_type = "default",
                        type = "wait",
                        selector = SelectorInfo(type = "none", value = null),
                        value = "1000",
                        meta = mapOf("waitTime" to "1000")
                    )
                ),
                status = "error",
                message = "Failed to parse LLM response: ${e.message}"
            )
        }
    }
}

/**
 * Factory for creating desktop LLM RPA components
 */
actual class LLMRpaFactory {
    actual fun createComponent(ctx: ComponentContext, panelInfo: PanelInfo): LLMRpaComponent {
        return DesktopLLMRpaComponent(ctx, panelInfo)
    }
}

/**
 * Platform-specific function to create LLM RPA executor
 */
actual fun createPlatformLLMRpaExecutor(browser: Any): RpaActionExecutor? {
    // Desktop uses the common implementation via BrowserIntegration
    return null
}