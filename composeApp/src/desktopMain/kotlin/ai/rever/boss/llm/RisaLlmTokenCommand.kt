package ai.rever.boss.llm

import ai.rever.boss.services.supabase.SupabaseConfig
import ai.rever.boss.utils.SingleInstanceManager
import io.github.jan.supabase.auth.auth
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/**
 * Headless credential-helper entrypoint invoked by Codex.
 *
 * stdout contains only the short-lived LiteLLM virtual key. The BOSS/Supabase
 * session and the CoreWeave credential are never printed or returned.
 */
@OptIn(ExperimentalTime::class)
object RisaLlmTokenCommand {
    private const val COMMAND = "llm-token"
    private const val DEFAULT_TOKEN_URL = "https://llm.risa.inc/auth/token"
    private const val TOKEN_REQUEST_TIMEOUT_MS = 90_000L
    private const val SESSION_WAIT_ATTEMPTS = 100
    private const val SESSION_WAIT_DELAY_MS = 100L
    private val responseJson = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class TokenResponse(
        val access_token: String,
    )

    fun isRequested(args: Array<String>): Boolean = args.size == 1 && args[0] == COMMAND

    fun execute(): Int {
        val credentialOutput = System.out
        // BOSS and supabase-kt emit startup diagnostics to stdout. Codex's
        // command-backed auth contract requires stdout to contain only the
        // bearer token, so route all helper-mode diagnostics to stderr.
        System.setOut(System.err)
        return try {
            val token = SingleInstanceManager.requestLlmToken().getOrThrow()
            credentialOutput.print(token)
            0
        } catch (error: Exception) {
            System.err.println(
                error.message ?: "Could not obtain a RISA LLM token. Open BOSS, sign in, and retry."
            )
            1
        } finally {
            System.setOut(credentialOutput)
        }
    }

    internal suspend fun fetchTokenForRunningBoss(): String {
        if (!SupabaseConfig.isInitialized.value) {
            SupabaseConfig.initializeFromEnvironment()
        }

        var session = waitForSession()
            ?: throw IllegalStateException("Open BOSS and sign in with your risalabs.ai account, then retry.")

        val tokenUrl = System.getenv("RISA_LLM_TOKEN_URL")
            ?.takeIf { it.isNotBlank() }
            ?: DEFAULT_TOKEN_URL

        val client = HttpClient(CIO) {
            install(HttpTimeout) {
                requestTimeoutMillis = TOKEN_REQUEST_TIMEOUT_MS
                connectTimeoutMillis = 30_000L
                socketTimeoutMillis = TOKEN_REQUEST_TIMEOUT_MS
            }
        }
        return try {
            var response = client.post(tokenUrl) {
                header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                header(HttpHeaders.Accept, "application/json")
            }
            var body = response.bodyAsText()

            // A locally unexpired access token can still be rejected after a
            // server-side revocation or session migration. Refresh once and
            // retry; never loop or mask authorization/entitlement failures.
            if (response.status.value == 401) {
                session = refreshSession()
                response = client.post(tokenUrl) {
                    header(HttpHeaders.Authorization, "Bearer ${session.accessToken}")
                    header(HttpHeaders.Accept, "application/json")
                }
                body = response.bodyAsText()
            }

            if (response.status.value != 200) {
                val message = parseGatewayError(body)
                throw IllegalStateException(message)
            }

            val parsed: TokenResponse = responseJson.decodeFromString(body)
            parsed.access_token.takeIf { it.isNotBlank() }
                ?: throw IllegalStateException("RISA LLM gateway returned an empty token.")
        } finally {
            client.close()
        }
    }

    private suspend fun refreshSession(): io.github.jan.supabase.auth.user.UserSession {
        val auth = SupabaseConfig.client.auth
        try {
            auth.refreshCurrentSession()
        } catch (_: Exception) {
            throw IllegalStateException(
                "Your BOSS session expired. Open BOSS, sign in again, and retry."
            )
        }

        return auth.currentSessionOrNull()
            ?: throw IllegalStateException(
                "Your BOSS session expired. Open BOSS, sign in again, and retry."
            )
    }

    private suspend fun waitForSession(): io.github.jan.supabase.auth.user.UserSession? {
        repeat(SESSION_WAIT_ATTEMPTS) {
            val auth = SupabaseConfig.client.auth
            val session = auth.currentSessionOrNull()
            if (session != null) {
                if (session.expiresAt > Clock.System.now()) {
                    return session
                }

                val refreshed = refreshSession()
                if (refreshed.expiresAt > Clock.System.now()) {
                    return refreshed
                }

                throw IllegalStateException(
                    "Your BOSS session expired. Open BOSS, sign in again, and retry."
                )
            }
            delay(SESSION_WAIT_DELAY_MS)
        }
        return null
    }

    internal fun parseGatewayError(body: String): String {
        return try {
            val root = Json.parseToJsonElement(body).jsonObject
            val error = root["error"]?.jsonObject
            error?.get("message")?.jsonPrimitive?.content
                ?: "RISA LLM gateway rejected the token request."
        } catch (_: Exception) {
            "RISA LLM gateway rejected the token request."
        }
    }
}
