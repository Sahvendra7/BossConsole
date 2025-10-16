package ai.rever.boss.services.passkey.supabase

import ai.rever.boss.services.supabase.getSupabaseAnonKey
import ai.rever.boss.services.supabase.getSupabaseFunctionUrl
import io.ktor.client.statement.*
import io.ktor.client.request.*
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.http.*
import kotlinx.serialization.json.Json

/**
 * Handles all HTTP communication with Supabase Edge Functions for passkey operations
 *
 * New API uses RESTful endpoints:
 * - POST /auth/challenge - Generate authentication challenge
 * - POST /auth/complete - Complete authentication
 * - GET /auth/status/{sessionId} - Check authentication status
 * - POST /register/challenge - Generate registration challenge
 * - POST /register/complete - Complete registration
 * - POST /manage/list - List user passkeys
 * - POST /manage/delete - Delete a passkey
 * - POST /manage/update - Update passkey display name
 */
internal object SupabaseApiClient {

    private val httpClient = HttpClient(CIO)

    // Secure configuration - values loaded from ConfigLoader (environment variables, system properties, or local.properties)
    private val supabaseFunctionUrl: String by lazy {
        getSupabaseFunctionUrl()
    }

    private val supabaseAnonKey: String by lazy {
        getSupabaseAnonKey()
    }

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    // ============================================================================
    // Authentication Endpoints
    // ============================================================================

    /**
     * POST /auth/challenge - Generate authentication challenge
     */
    suspend inline fun <reified T> invokeAuthenticationChallenge(requestData: T): HttpResponse {
        val jsonBody = json.encodeToString(requestData)
        println("SupabaseApiClient: POST $supabaseFunctionUrl/auth/challenge")
        println("SupabaseApiClient: Request body: $jsonBody")

        return httpClient.post("$supabaseFunctionUrl/auth/challenge") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseAnonKey)
            setBody(jsonBody)
        }
    }

    /**
     * POST /auth/complete - Complete authentication
     */
    suspend inline fun <reified T> completeAuthentication(requestData: T): HttpResponse {
        val jsonBody = json.encodeToString(requestData)
        println("SupabaseApiClient: POST $supabaseFunctionUrl/auth/complete")
        println("SupabaseApiClient: Request body: $jsonBody")

        return httpClient.post("$supabaseFunctionUrl/auth/complete") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseAnonKey)
            setBody(jsonBody)
        }
    }

    /**
     * GET /auth/status/{sessionId} - Check authentication status
     */
    suspend fun checkAuthenticationStatus(sessionId: String): HttpResponse {
        println("SupabaseApiClient: GET $supabaseFunctionUrl/auth/status/$sessionId")

        return httpClient.get("$supabaseFunctionUrl/auth/status/$sessionId") {
            header("apikey", supabaseAnonKey)
        }
    }

    // ============================================================================
    // Registration Endpoints
    // ============================================================================

    /**
     * POST /register/challenge - Generate registration challenge
     */
    suspend inline fun <reified T> invokeRegistrationChallenge(requestData: T): HttpResponse {
        val jsonBody = json.encodeToString(requestData)
        println("SupabaseApiClient: POST $supabaseFunctionUrl/register/challenge")
        println("SupabaseApiClient: Request body: $jsonBody")

        return httpClient.post("$supabaseFunctionUrl/register/challenge") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseAnonKey)
            setBody(jsonBody)
        }
    }

    /**
     * POST /register/complete - Complete registration
     */
    suspend inline fun <reified T> completeRegistration(requestData: T): HttpResponse {
        val jsonBody = json.encodeToString(requestData)
        println("SupabaseApiClient: POST $supabaseFunctionUrl/register/complete")
        println("SupabaseApiClient: Request body: $jsonBody")

        return httpClient.post("$supabaseFunctionUrl/register/complete") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseAnonKey)
            setBody(jsonBody)
        }
    }

    // ============================================================================
    // Management Endpoints
    // ============================================================================

    /**
     * POST /manage/list - List user passkeys
     */
    suspend inline fun <reified T> listPasskeys(requestData: T): HttpResponse {
        val jsonBody = json.encodeToString(requestData)
        println("SupabaseApiClient: POST $supabaseFunctionUrl/manage/list")

        return httpClient.post("$supabaseFunctionUrl/manage/list") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseAnonKey)
            setBody(jsonBody)
        }
    }

    /**
     * POST /manage/delete - Delete a passkey
     */
    suspend inline fun <reified T> deletePasskey(requestData: T): HttpResponse {
        val jsonBody = json.encodeToString(requestData)
        println("SupabaseApiClient: POST $supabaseFunctionUrl/manage/delete")

        return httpClient.post("$supabaseFunctionUrl/manage/delete") {
            contentType(ContentType.Application.Json)
            header("apikey", supabaseAnonKey)
            setBody(jsonBody)
        }
    }

}
