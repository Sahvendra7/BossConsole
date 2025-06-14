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
import kotlinx.serialization.json.Json
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
        return try {
            withContext(Dispatchers.IO) {
                val response: HttpResponse = httpClient.post(apiEndpoint.value) {
                    contentType(ContentType.Application.Json)
                    setBody(request)
                }
                
                if (response.status.isSuccess()) {
                    response.body<LLMRpaResponse>()
                } else {
                    LLMRpaResponse(
                        configuration = emptyList(),
                        status = "error",
                        message = "API request failed: ${response.status}"
                    )
                }
            }
        } catch (e: Exception) {
            // If API fails, use enhanced mock response
            super.callLLMApi(request)
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