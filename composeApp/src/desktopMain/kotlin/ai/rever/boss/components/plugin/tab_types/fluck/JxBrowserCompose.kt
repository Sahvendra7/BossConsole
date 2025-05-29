package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.config.JxBrowserConfig
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import com.teamdev.jxbrowser.navigation.event.LoadStarted
import com.teamdev.jxbrowser.view.compose.BrowserView
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Frame
import java.awt.Window.getWindows

@Composable
fun JxBrowserCompose(
    modifier: Modifier = Modifier,
    initialUrl: String = JxBrowserConfig.defaultUrl
) {
    var urlInput by remember { mutableStateOf(initialUrl) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()
    
    val engine = remember {
        Engine.newInstance(
            EngineOptions.newBuilder(JxBrowserConfig.renderingMode)
                .licenseKey(JxBrowserConfig.licenseKey)
                .build()
        )
    }
    
    val browser = remember {
        engine.newBrowser().apply {
            // Set up navigation listeners
            navigation().on(LoadStarted::class.java) {
                coroutineScope.launch(Dispatchers.Main) {
                    isLoading = true
                    urlInput = url()
                    canGoBack = navigation().canGoBack()
                    canGoForward = navigation().canGoForward()
                }
            }
            
            navigation().on(LoadFinished::class.java) {
                coroutineScope.launch(Dispatchers.Main) {
                    isLoading = false
                    canGoBack = navigation().canGoBack()
                    canGoForward = navigation().canGoForward()
                }
            }
        }
    }

    // Create BrowserViewState using rememberBrowserViewState
    val browserViewState = remember(browser) {
        val window = getWindows().firstOrNull() ?: Frame()
        BrowserViewState(browser, coroutineScope, window)
    }
    
    DisposableEffect(browser) {
        browser.navigation().loadUrl(initialUrl)
        
        onDispose {
            browser.close()
            engine.close()
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
                        onClick = { browser.navigation().goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    
                    // Forward button
                    IconButton(
                        onClick = { browser.navigation().goForward() },
                        enabled = canGoForward
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
                    }
                    
                    // URL Input
                    TextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .onPreviewKeyEvent { keyEvent ->
                                if (keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter) {
                                    var url = urlInput
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        url = "https://$url"
                                    }
                                    browser.navigation().loadUrl(url)
                                    true
                                } else {
                                    false
                                }
                            },
                        singleLine = true,
                        colors = TextFieldDefaults.textFieldColors(
                            backgroundColor = MaterialTheme.colors.surface
                        ),
                        placeholder = { Text("Enter URL") },
                        trailingIcon = {
                            IconButton(
                                onClick = {
                                    var url = urlInput
                                    if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                        url = "https://$url"
                                    }
                                    browser.navigation().loadUrl(url)
                                }
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
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

        // Browser content using native Compose BrowserView
        BrowserView(
            state = browserViewState,
            modifier = Modifier.fillMaxSize()
        )
    }
}