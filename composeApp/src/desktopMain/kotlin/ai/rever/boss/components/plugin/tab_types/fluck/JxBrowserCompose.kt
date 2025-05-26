package ai.rever.boss.components.plugin.tab_types.fluck

import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.unit.dp
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import com.teamdev.jxbrowser.engine.RenderingMode
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import com.teamdev.jxbrowser.navigation.event.LoadStarted
import com.teamdev.jxbrowser.view.swing.BrowserView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import javax.swing.JPanel
import java.awt.BorderLayout

@Composable
fun JxBrowserCompose(
    modifier: Modifier = Modifier,
    initialUrl: String = "https://www.google.com"
) {
    var engine by remember { mutableStateOf<Engine?>(null) }
    var browser by remember { mutableStateOf<Browser?>(null) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var urlInput by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val browserPanel = remember {
        JPanel(BorderLayout()).also { panel ->
            val engineInstance = Engine.newInstance(
                EngineOptions.newBuilder(RenderingMode.HARDWARE_ACCELERATED)
                    .licenseKey("OK6AEKNYF3K41B5WB4FEKK1C3H7UH3C6ZI1UL63J6E5VJTT3RXZ711M87XU8PLPO0EXR4PNTJWDLDF7FSVO658N5GSB7ZAMNXZ66L8QR115B9B1INDPS5KWSA4RYSUHG1QLPHFPL108ZS9IHW")
                    .build()
            )
            val browserInstance = engineInstance.newBrowser()
            
            // Set up navigation listeners
            browserInstance.navigation().on(LoadStarted::class.java) {
                coroutineScope.launch(Dispatchers.Main) {
                    isLoading = true
                    currentUrl = browserInstance.url()
                    urlInput = browserInstance.url()
                    canGoBack = browserInstance.navigation().canGoBack()
                    canGoForward = browserInstance.navigation().canGoForward()
                }
            }
            
            browserInstance.navigation().on(LoadFinished::class.java) {
                coroutineScope.launch(Dispatchers.Main) {
                    isLoading = false
                    canGoBack = browserInstance.navigation().canGoBack()
                    canGoForward = browserInstance.navigation().canGoForward()
                }
            }
            
            engine = engineInstance
            browser = browserInstance
            
            // Create and add BrowserView
            val browserView = BrowserView.newInstance(browserInstance)
            panel.add(browserView, BorderLayout.CENTER)
            
            browserInstance.navigation().loadUrl(initialUrl)
        }
    }
    
    DisposableEffect(Unit) {
        onDispose {
            browser?.close()
            engine?.close()
        }
    }
    
    Column(modifier = modifier.fillMaxSize()) {
        // Navigation Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 4.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Back button
                    IconButton(
                        onClick = { browser?.navigation()?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                    
                    // Forward button
                    IconButton(
                        onClick = { browser?.navigation()?.goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(Icons.Default.ArrowForward, contentDescription = "Forward")
                    }
                    
                    // Refresh button
                    IconButton(
                        onClick = { browser?.navigation()?.reload() }
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                    
                    // URL Input
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = MaterialTheme.colors.surface
                        ),
                        placeholder = { Text("Enter URL") },
                        trailingIcon = {
                            if (urlInput != currentUrl) {
                                IconButton(
                                    onClick = {
                                        var url = urlInput
                                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                            url = "https://$url"
                                        }
                                        browser?.navigation()?.loadUrl(url)
                                    }
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = "Go")
                                }
                            }
                        }
                    )
                }
                
                // Loading indicator
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
        
        // Browser content using SwingPanel
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = { browserPanel },
            update = { }
        )
    }
}