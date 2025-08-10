package ai.rever.boss.services.supabase

import io.github.jan.supabase.functions.functions
import io.ktor.client.statement.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Service for interacting with Supabase Edge Functions
 */
object FunctionsService {
    
    /**
     * Invoke an Edge Function by name
     * @param functionName The name of the function to invoke
     * @param body The request body to send (optional)
     * @return Result containing the response or error
     */
    suspend fun invoke(
        functionName: String,
        body: String? = null
    ): Result<String> {
        return try {
            val response = SupabaseConfig.functions.invoke(
                function = functionName,
                body = body ?: "{}"
            )
            val responseText = response.bodyAsText()
            Result.success(responseText)
        } catch (e: Exception) {
            println("FunctionsService: Error invoking function '$functionName': ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Invoke an Edge Function with typed request/response
     * @param functionName The name of the function to invoke
     * @param request The typed request object
     * @return Result containing the typed response or error
     */
    suspend inline fun <reified T, reified R> invokeTyped(
        functionName: String,
        request: T
    ): Result<R> {
        return try {
            val requestJson = Json.encodeToString(kotlinx.serialization.serializer<T>(), request)
            val httpResponse = SupabaseConfig.functions.invoke(
                function = functionName,
                body = requestJson
            )
            val responseString = httpResponse.bodyAsText()
            val response = Json.decodeFromString<R>(responseString)
            Result.success(response)
        } catch (e: Exception) {
            println("FunctionsService: Error invoking typed function '$functionName': ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Invoke a function with no parameters (GET-style)
     * @param functionName The name of the function to invoke
     * @return Result containing the response or error
     */
    suspend fun get(functionName: String): Result<String> {
        return invoke(functionName, "{}")
    }
    
    /**
     * Invoke a function with JSON parameters
     * @param functionName The name of the function to invoke
     * @param params Map of parameters to send
     * @return Result containing the response or error
     */
    suspend fun post(
        functionName: String, 
        params: Map<String, Any>
    ): Result<String> {
        return try {
            // Convert Map<String, Any> to JSON manually since Any is not serializable
            val jsonBuilder = StringBuilder("{")
            params.entries.forEachIndexed { index, entry ->
                if (index > 0) jsonBuilder.append(",")
                jsonBuilder.append("\"${entry.key}\":")
                when (val value = entry.value) {
                    is String -> jsonBuilder.append("\"$value\"")
                    is Number -> jsonBuilder.append(value.toString())
                    is Boolean -> jsonBuilder.append(value.toString())
                    else -> jsonBuilder.append("\"$value\"")
                }
            }
            jsonBuilder.append("}")
            invoke(functionName, jsonBuilder.toString())
        } catch (e: Exception) {
            println("FunctionsService: Error encoding parameters for '$functionName': ${e.message}")
            Result.failure(e)
        }
    }
    
    /**
     * Parse a function response as JSON and extract a specific field
     * @param response The function response string
     * @param fieldName The field name to extract
     * @return The field value or null if not found
     */
    fun extractJsonField(response: String, fieldName: String): String? {
        return try {
            val jsonObject = Json.decodeFromString<JsonObject>(response)
            jsonObject[fieldName]?.jsonPrimitive?.content
        } catch (e: Exception) {
            println("FunctionsService: Error parsing JSON response: ${e.message}")
            null
        }
    }
    
    /**
     * Check if the Functions service is available
     * @return true if the service is properly configured
     */
    fun isAvailable(): Boolean {
        return try {
            SupabaseConfig.isInitialized.value && SupabaseConfig.functions != null
        } catch (e: Exception) {
            false
        }
    }
}

/**
 * Example request/response models for typed function calls
 */
@Serializable
data class HelloWorldRequest(
    val name: String
)

@Serializable
data class HelloWorldResponse(
    val message: String,
    val timestamp: String,
    val method: String
)

/**
 * Generic function response wrapper
 */
@Serializable
data class FunctionResponse<T>(
    val data: T? = null,
    val error: String? = null,
    val success: Boolean = true
)

/**
 * Extension functions for easier usage
 */
suspend fun FunctionsService.helloWorld(name: String = "World"): Result<HelloWorldResponse> {
    return invokeTyped<HelloWorldRequest, HelloWorldResponse>(
        "hello-world",
        HelloWorldRequest(name)
    )
}