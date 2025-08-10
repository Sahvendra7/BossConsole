package ai.rever.boss.services.supabase.examples

import ai.rever.boss.services.supabase.FunctionsService
import ai.rever.boss.services.supabase.HelloWorldRequest
import ai.rever.boss.services.supabase.HelloWorldResponse
import ai.rever.boss.services.supabase.helloWorld
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Example usage of FunctionsService in BOSS app
 */
class FunctionsExample {
    
    /**
     * Basic function call example
     */
    suspend fun basicExample(): String? {
        if (!FunctionsService.isAvailable()) {
            println("Functions service is not available")
            return null
        }
        
        // Simple GET request
        FunctionsService.get("hello-world").fold(
            onSuccess = { response ->
                println("Function response: $response")
                return FunctionsService.extractJsonField(response, "message")
            },
            onFailure = { error ->
                println("Function call failed: ${error.message}")
                return null
            }
        )
    }
    
    /**
     * POST request with parameters example
     */
    suspend fun postExample(userName: String): String? {
        return FunctionsService.post(
            "hello-world",
            mapOf("name" to userName)
        ).fold(
            onSuccess = { response ->
                println("POST response: $response")
                FunctionsService.extractJsonField(response, "message")
            },
            onFailure = { error ->
                println("POST request failed: ${error.message}")
                null
            }
        )
    }
    
    /**
     * Typed function call example (recommended approach)
     */
    suspend fun typedExample(userName: String): HelloWorldResponse? {
        return FunctionsService.helloWorld(userName).fold(
            onSuccess = { response ->
                println("Typed response: ${response.message} at ${response.timestamp}")
                response
            },
            onFailure = { error ->
                println("Typed call failed: ${error.message}")
                null
            }
        )
    }
    
    /**
     * Example of using Functions in a Compose UI context
     */
    fun useInCompose(coroutineScope: CoroutineScope, onResult: (String) -> Unit) {
        coroutineScope.launch {
            val result = basicExample()
            if (result != null) {
                onResult(result)
            }
        }
    }
}

/**
 * Usage examples for different scenarios
 */
object FunctionsUsageExamples {
    
    /**
     * Example: AI Chat function call
     */
    suspend fun callChatFunction(message: String, userId: String): Result<String> {
        return FunctionsService.post(
            "ai-chat",
            mapOf(
                "message" to message,
                "userId" to userId,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Example: File processing function
     */
    suspend fun processFile(fileUrl: String, operation: String): Result<String> {
        return FunctionsService.post(
            "file-processor",
            mapOf(
                "fileUrl" to fileUrl,
                "operation" to operation
            )
        )
    }
    
    /**
     * Example: Analytics event function
     */
    suspend fun trackEvent(event: String, properties: Map<String, Any>): Result<String> {
        return FunctionsService.post(
            "analytics-tracker",
            mapOf(
                "event" to event,
                "properties" to properties,
                "timestamp" to System.currentTimeMillis()
            )
        )
    }
    
    /**
     * Example: Email notification function
     */
    suspend fun sendNotification(
        to: String, 
        subject: String, 
        body: String
    ): Result<String> {
        return FunctionsService.post(
            "send-email",
            mapOf(
                "to" to to,
                "subject" to subject,
                "body" to body
            )
        )
    }
}