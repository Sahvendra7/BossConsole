package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.tab_types.fluck.*
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SmartToy
import com.arkivanov.decompose.ComponentContext

object FluckPanelInfo : PanelInfo {
    override val id = PanelId("fluck", 15)
    override val displayName = "ChatGPT"
    override val icon = Icons.Outlined.SmartToy
    override val defaultSlotPosition = right.top.top
}

class FluckPanelComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    // Create browser instance with error handling
    private var browserError: Throwable? = null
    private val browser: Any? = try {
        createBrowser()
    } catch (e: Throwable) {
        browserError = e
        println("Failed to create browser: ${e.message}")
        null
    }
    
    private val browserViewState: Any? = browser?.let {
        try {
            createBrowserViewState(it)
        } catch (e: Throwable) {
            browserError = e
            println("Failed to create browser view state: ${e.message}")
            null
        }
    }
    
    private var isDisposed = false
    
    // State for browser title
    private val currentTitle = mutableStateOf("Fluck Browser")
    
    @Composable
    override fun Content() {
        if (!isDisposed) {
            when {
                browserError != null -> {
                    // Show error message instead of browser
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF2B2D30)),
                        contentAlignment = Alignment.Center
                    ) {
                        FluckPanelErrorView(error = browserError!!)
                    }
                }
                browser != null && browserViewState != null -> {
                    FluckView(
                        fileId = "fluck_panel",
                        content = "https://chat.openai.com", // Default URL
                        browser = browser,
                        browserViewState = browserViewState,
                        onContentChange = { }, // Not used for browser
                        onTitleChange = { newTitle ->
                            currentTitle.value = newTitle
                        },
                        onIconChange = { }, // Icon changes not needed for panel
                        onTabIconUpdate = { }, // Tab icon not needed for panel
                        onOpenInNewTab = { }, // Opening new tabs not supported in panel
                        onNavigationUpdate = null,
                        onNavigationStateChange = null
                    )
                }
                else -> {
                    // Loading state
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color(0xFF2B2D30)),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }

}

@Composable
fun FluckPanelErrorView(error: Throwable) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Browser Not Available",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        val errorMessage = when {
            error.message?.contains("already in use") == true -> {
                "Another instance of BOSS is using the browser.\nPlease close other instances and refresh."
            }
            else -> "Unable to initialize the browser component."
        }
        
        Text(
            text = errorMessage,
            fontSize = 12.sp,
            color = Color(0xFFCCCCCC),
            textAlign = TextAlign.Center
        )
        
        if (error.message?.contains("already in use") == true) {
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Tip: The browser will automatically try alternative\nprofiles on next restart.",
                fontSize = 11.sp,
                color = Color(0xFF999999),
                textAlign = TextAlign.Center
            )
        }
    }
}

fun DefaultPlugin.registerFluckPanel() = panelRegistry.registerPanel(FluckPanelInfo) {
    ctx, panelInfo -> FluckPanelComponent(ctx, panelInfo)
}
