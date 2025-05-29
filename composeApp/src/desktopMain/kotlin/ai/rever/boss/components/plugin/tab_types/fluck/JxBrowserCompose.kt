package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.config.JxBrowserConfig
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
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
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.geometry.Offset

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JxBrowserCompose(
    modifier: Modifier = Modifier,
    initialUrl: String = JxBrowserConfig.defaultUrl
) {
    var urlInput by remember { mutableStateOf(TextFieldValue(initialUrl, TextRange(initialUrl.length))) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var rightClickPosition by remember { mutableStateOf(Offset.Zero) }
    var hasVideoAtClick by remember { mutableStateOf(false) }
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
                    val newUrl = url()
                    urlInput = TextFieldValue(newUrl, TextRange(newUrl.length))
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
    
    // Create context menu items dynamically based on browser state
    val contextMenuItems = remember(canGoBack, canGoForward, hasVideoAtClick) {
        buildList {
            // Navigation items
            if (canGoBack) {
                add(ContextMenuItem(
                    text = "Back",
                    icon = Icons.AutoMirrored.Filled.ArrowBack,
                    onClick = { browser.navigation().goBack() }
                ))
            }
            
            if (canGoForward) {
                add(ContextMenuItem(
                    text = "Forward",
                    icon = Icons.AutoMirrored.Filled.ArrowForward,
                    onClick = { browser.navigation().goForward() }
                ))
            }
            
            // Always show reload
            add(ContextMenuItem(
                text = "Reload",
                icon = Icons.Default.Refresh,
                onClick = { browser.navigation().reload() }
            ))
            
            add(ContextMenuItem(isDivider = true))
            
            // Picture-in-Picture option if clicking on a video
            if (hasVideoAtClick) {
                add(ContextMenuItem(
                    text = "Picture in Picture",
                    icon = Icons.Outlined.PictureInPictureAlt,
                    onClick = {
                        browser.mainFrame().ifPresent { frame ->
                            // Execute JavaScript to enable PiP on the video
                            frame.executeJavaScript<Unit>("""
                                (function() {
                                    // Find all video elements on the page
                                    const videos = document.querySelectorAll('video');
                                    
                                    // For YouTube and similar sites, find the main video player
                                    let targetVideo = null;
                                    
                                    // Check for YouTube specific video
                                    const ytVideo = document.querySelector('video.html5-main-video, video.video-stream');
                                    if (ytVideo) {
                                        targetVideo = ytVideo;
                                    } else if (videos.length === 1) {
                                        // If there's only one video, use it
                                        targetVideo = videos[0];
                                    } else if (videos.length > 1) {
                                        // If multiple videos, try to find the visible one
                                        for (let video of videos) {
                                            const rect = video.getBoundingClientRect();
                                            if (rect.width > 100 && rect.height > 100 && 
                                                video.readyState >= 2) { // HAVE_CURRENT_DATA
                                                targetVideo = video;
                                                break;
                                            }
                                        }
                                    }
                                    
                                    if (targetVideo) {
                                        if (document.pictureInPictureElement) {
                                            document.exitPictureInPicture();
                                        } else if (targetVideo.requestPictureInPicture) {
                                            targetVideo.requestPictureInPicture().catch(err => {
                                                console.error('PiP failed:', err);
                                            });
                                        }
                                    }
                                })();
                            """.trimIndent())
                        }
                    }
                ))
                
                add(ContextMenuItem(isDivider = true))
            }
            
            // Copy current URL
            add(ContextMenuItem(
                text = "Copy URL",
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                    clipboard.setContents(StringSelection(browser.url()), null)
                }
            ))
            
            // Developer tools
            add(ContextMenuItem(
                text = "Inspect Element",
                icon = Icons.Outlined.Code,
                onClick = { browser.devTools().show() }
            ))
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
                                    var url = urlInput.text
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
                                    var url = urlInput.text
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

        // Browser content using native Compose BrowserView with custom context menu
        BrowserView(
            state = browserViewState,
            modifier = Modifier
                .fillMaxSize()
                .onPointerEvent(PointerEventType.Press) { event ->
                    if (event.buttons.isSecondaryPressed) {
                        // Store the click position
                        val change = event.changes.firstOrNull()
                        if (change != null) {
                            rightClickPosition = change.position
                            
                            // Check if there's a video element on the page
                            browser.mainFrame().ifPresent { frame ->
                                val hasVideo = frame.executeJavaScript<Boolean>("""
                                    (function() {
                                        // Check for any video elements on the page
                                        const videos = document.querySelectorAll('video');
                                        
                                        // Also check for YouTube specific selectors
                                        const ytVideo = document.querySelector('video.html5-main-video, video.video-stream');
                                        
                                        // Return true if we found any video
                                        return videos.length > 0 || ytVideo !== null;
                                    })();
                                """.trimIndent())
                                
                                coroutineScope.launch(Dispatchers.Main) {
                                    hasVideoAtClick = hasVideo ?: false
                                }
                            }
                        }
                    }
                }
                .contextMenu(items = contextMenuItems)
        )
    }
}