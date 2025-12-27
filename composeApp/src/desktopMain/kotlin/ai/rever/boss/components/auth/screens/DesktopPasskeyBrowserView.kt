package ai.rever.boss.components.auth.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ai.rever.boss.components.plugin.tab_types.fluck.FluckEngine
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import com.teamdev.jxbrowser.navigation.event.LoadStarted
import com.teamdev.jxbrowser.view.compose.BrowserView
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch
import java.awt.Frame
import java.awt.Window

/**
 * Desktop implementation of PasskeyBrowserView using JxBrowser
 * Embeds a Chromium browser instance for WebAuthn operations
 */
@Composable
actual fun PasskeyBrowserView(
    url: String,
    onLoadComplete: () -> Unit,
    onError: (String) -> Unit
) {
    var browser by remember { mutableStateOf<Browser?>(null) }
    var initError by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()

    // Initialize browser when composable enters composition
    DisposableEffect(url) {
        try {
            println("DesktopPasskeyBrowserView: Initializing JxBrowser for URL: $url")

            // Get browser instance from FluckEngine (throws exception if initialization fails)
            val engine = FluckEngine.engine

            // Create new browser instance for WebAuthn
            val newBrowser = engine.newBrowser()
            browser = newBrowser

            println("DesktopPasskeyBrowserView: JxBrowser initialized successfully")

            // Register load event handlers
            newBrowser.navigation().on(LoadStarted::class.java) {
                println("DesktopPasskeyBrowserView: Page loading started")
            }

            newBrowser.navigation().on(LoadFinished::class.java) {
                println("DesktopPasskeyBrowserView: Page loaded successfully: ${newBrowser.url()}")
                coroutineScope.launch(Dispatchers.Main) {
                    onLoadComplete()
                }
            }

            // Load the WebAuthn URL
            println("DesktopPasskeyBrowserView: Loading URL: $url")
            newBrowser.navigation().loadUrl(url)

        } catch (e: Exception) {
            val errorMessage = "Failed to initialize browser: ${e.message}"
            println("DesktopPasskeyBrowserView: $errorMessage")
            initError = errorMessage
            onError(errorMessage)
        }

        // Cleanup on disposal
        onDispose {
            println("DesktopPasskeyBrowserView: Disposing browser instance")
            try {
                browser?.close()
            } catch (e: Exception) {
                println("DesktopPasskeyBrowserView: Error closing browser: ${e.message}")
            }
        }
    }

    // Display browser view or error message
    if (initError != null) {
        // Show error message if initialization failed
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = initError ?: "Failed to initialize browser",
                color = Color.White
            )
        }
    } else if (browser != null) {
        // Embed JxBrowser view in Compose using native BrowserView
        // Create BrowserViewState for this specific browser instance
        val window = remember { Window.getWindows().firstOrNull() ?: Frame() }
        val browserViewState = remember(browser) {
            BrowserViewState(browser!!, MainScope(), window)
        }

        BrowserView(
            state = browserViewState,
            modifier = Modifier.fillMaxSize()
        )
    } else {
        // Show placeholder while initializing
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1E1E1E)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Initializing browser...",
                color = Color.White
            )
        }
    }
}
