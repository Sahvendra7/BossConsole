package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.awt.BorderLayout
import javax.swing.JPanel

@Composable
actual fun FluckView(fileId: String, content: String, onContentChange: (String) -> Unit) {
    var urlInput by remember { mutableStateOf("https://www.google.com") }
    var currentUrl by remember { mutableStateOf("https://www.google.com") }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var isInitialized by remember { mutableStateOf(false) }
    var initError by remember { mutableStateOf<String?>(null) }
    var browserInstance by remember { mutableStateOf<JCEFBrowser.BrowserInstance?>(null) }
    
    // Initialize JCEF
    LaunchedEffect(Unit) {
        JCEFBrowser.initialize { success, error ->
            if (success) {
                isInitialized = true
            } else {
                initError = error
            }
        }
    }
    
    // Create browser instance when initialized
    LaunchedEffect(isInitialized) {
        if (isInitialized && browserInstance == null) {
            // Small delay to ensure JCEF is fully ready
            kotlinx.coroutines.delay(1000)
            browserInstance = JCEFBrowser.createBrowser(
                url = currentUrl,
                onLoadingStateChange = { loading, back, forward ->
                    isLoading = loading
                    canGoBack = back
                    canGoForward = forward
                },
                onAddressChange = { url ->
                    currentUrl = url
                    urlInput = url
                }
            )
            
            // Log success
            if (browserInstance != null) {
                println("Browser instance created successfully")
            } else {
                println("Failed to create browser instance")
            }
        }
    }
    
    DisposableEffect(browserInstance) {
        onDispose {
            browserInstance?.dispose()
        }
    }
    
    Column(modifier = Modifier.fillMaxSize()) {
        // Navigation Bar
        Surface(
            modifier = Modifier.fillMaxWidth().height(56.dp),
            color = MaterialTheme.colors.surface,
            elevation = 4.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                IconButton(
                    onClick = { browserInstance?.goBack() },
                    enabled = canGoBack && browserInstance != null
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                }
                
                // Forward button
                IconButton(
                    onClick = { browserInstance?.goForward() },
                    enabled = canGoForward && browserInstance != null
                ) {
                    Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                }
                
                // Reload button
                IconButton(
                    onClick = { browserInstance?.reload() },
                    enabled = browserInstance != null
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload")
                }
                
                // URL Bar
                TextField(
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                    singleLine = true,
                    colors = TextFieldDefaults.textFieldColors(
                        backgroundColor = MaterialTheme.colors.surface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    placeholder = { Text("Enter URL") },
                    enabled = browserInstance != null
                )
                
                // Go button
                Button(
                    onClick = {
                        var url = urlInput
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            url = "https://$url"
                        }
                        browserInstance?.loadURL(url)
                        currentUrl = url
                    },
                    modifier = Modifier.padding(start = 8.dp),
                    enabled = browserInstance != null
                ) {
                    Text("Go")
                }
            }
        }
        
        // Progress indicator
        if (isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth().height(2.dp)
            )
        }
        
        // Browser content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                initError != null -> {
                    // Error state
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Browser Initialization Error",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.error
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = initError ?: "Unknown error",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                !isInitialized -> {
                    // Loading state
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Initializing browser...")
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "This may take a moment on first run as CEF downloads...",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
                browserInstance != null -> {
                    // Browser content
                    val browser = browserInstance!!
                    SwingPanel(
                        modifier = Modifier.fillMaxSize(),
                        factory = {
                            println("SwingPanel factory called - creating browser panel")
                            // Return the custom JCEFPanel directly
                            val panel = browser.component as JPanel
                            // Ensure the panel is visible and has size
                            panel.preferredSize = java.awt.Dimension(800, 600)
                            panel.validate()
                            panel
                        },
                        update = { panel ->
                            // Force refresh on updates
                            println("SwingPanel update called")
                            panel.validate()
                            panel.repaint()
                            browser.forceRefresh()
                        }
                    )
                }
                else -> {
                    // Fallback state
                    Card(
                        modifier = Modifier.align(Alignment.Center).padding(16.dp),
                        elevation = 4.dp
                    ) {
                        Column(
                            modifier = Modifier.padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Browser Not Available",
                                style = MaterialTheme.typography.h6
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "Failed to create browser instance",
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
            }
        }
    }
}