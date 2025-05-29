package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.config.JxBrowserConfig
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.PictureInPictureAlt
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.engine.Engine
import com.teamdev.jxbrowser.engine.EngineOptions
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import com.teamdev.jxbrowser.navigation.event.LoadStarted
import com.teamdev.jxbrowser.view.compose.BrowserView
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.Frame
import java.awt.Toolkit
import java.awt.Window.getWindows
import java.awt.datatransfer.StringSelection
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.SolidColor

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JxBrowserCompose(
    modifier: Modifier = Modifier,
    browser: Browser,
    browserViewState: BrowserViewState,
    initialUrl: String = JxBrowserConfig.defaultUrl,
    onTitleChange: (String) -> Unit = {},
    onOpenInNewTab: (String) -> Unit = {}
) {
    var urlInput by remember { mutableStateOf(TextFieldValue(initialUrl, TextRange(initialUrl.length))) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var rightClickPosition by remember { mutableStateOf(Offset.Zero) }
    var hasVideoAtClick by remember { mutableStateOf(false) }
    var rightClickedLinkUrl by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Set up browser navigation listeners
    LaunchedEffect(browser) {
        // Initial state
        canGoBack = browser.navigation().canGoBack()
        canGoForward = browser.navigation().canGoForward()
        urlInput = TextFieldValue(browser.url(), TextRange(browser.url().length))
        
        // Set up navigation listeners
        browser.navigation().on(LoadStarted::class.java) {
            coroutineScope.launch(Dispatchers.Main) {
                isLoading = true
                val newUrl = browser.url()
                urlInput = TextFieldValue(newUrl, TextRange(newUrl.length))
                canGoBack = browser.navigation().canGoBack()
                canGoForward = browser.navigation().canGoForward()
            }
        }
        
        browser.navigation().on(LoadFinished::class.java) {
            coroutineScope.launch(Dispatchers.Main) {
                isLoading = false
                canGoBack = browser.navigation().canGoBack()
                canGoForward = browser.navigation().canGoForward()
                
                // Inject JavaScript to handle cmd+click on links
                browser.mainFrame().ifPresent { frame ->
                    frame.executeJavaScript<Unit>("""
                        // Override link click behavior
                        if (!window.linkClickHandlerAdded) {
                            document.addEventListener('click', function(event) {
                                // Check if it's a link click
                                const link = event.target.closest('a');
                                if (link && link.href) {
                                    // Check for cmd (Mac) or ctrl (Windows/Linux)
                                    if (event.metaKey || event.ctrlKey) {
                                        event.preventDefault();
                                        event.stopPropagation();
                                        // Store the URL to open in new tab
                                        window._newTabUrl = link.href;
                                        // Trigger custom event
                                        window.dispatchEvent(new CustomEvent('openInNewTab', { detail: link.href }));
                                        return false;
                                    }
                                }
                            }, true);
                            window.linkClickHandlerAdded = true;
                        }
                        
                        // Also handle right clicks to store link URL
                        if (!window.rightClickHandlerAdded) {
                            document.addEventListener('contextmenu', function(event) {
                                const link = event.target.closest('a');
                                if (link && link.href) {
                                    window._rightClickedLinkUrl = link.href;
                                } else {
                                    window._rightClickedLinkUrl = null;
                                }
                            }, true);
                            window.rightClickHandlerAdded = true;
                        }
                    """)
                    
                    // Set up listener for the custom event
                    frame.executeJavaScript<String?>("""
                        (function() {
                            if (window._newTabUrl) {
                                const url = window._newTabUrl;
                                window._newTabUrl = null;
                                return url;
                            }
                            return null;
                        })();
                    """)?.let { newTabUrl ->
                        if (newTabUrl.isNotEmpty()) {
                            coroutineScope.launch(Dispatchers.Main) {
                                onOpenInNewTab(newTabUrl)
                            }
                        }
                    }
                }
                
                // Update title when page finishes loading
                val title = browser.title()
                val url = browser.url()
                
                // println("Page loaded - URL: $url, Title: $title") // Debug log
                
                if (title.isNotEmpty()) {
                    onTitleChange(title)
                } else {
                    // Fallback to domain name if no title
                    try {
                        val host = java.net.URL(url).host.removePrefix("www.")
                        onTitleChange(host)
                    } catch (e: Exception) {
                        onTitleChange("New Tab")
                    }
                }
            }
        }
        
        // Load initial URL if browser hasn't loaded anything yet
        if (browser.url() == "about:blank" || browser.url().isEmpty()) {
            browser.navigation().loadUrl(initialUrl)
        } else {
            // Browser already has content loaded, update the title with current state
            val currentTitle = browser.title()
            val currentUrl = browser.url()
            
            if (currentTitle.isNotEmpty()) {
                onTitleChange(currentTitle)
            } else {
                // Fallback to domain name if no title
                try {
                    val host = java.net.URL(currentUrl).host.removePrefix("www.")
                    onTitleChange(host)
                } catch (e: Exception) {
                    onTitleChange("New Tab")
                }
            }
        }
    }
    
    // Set up polling for new tab requests
    LaunchedEffect(browser) {
        coroutineScope.launch {
            while (true) {
                kotlinx.coroutines.delay(100) // Check every 100ms
                browser.mainFrame().ifPresent { frame ->
                    frame.executeJavaScript<String?>("""
                        (function() {
                            if (window._newTabUrl) {
                                const url = window._newTabUrl;
                                window._newTabUrl = null;
                                return url;
                            }
                            return null;
                        })();
                    """)?.let { newTabUrl ->
                        if (newTabUrl.isNotEmpty()) {
                            onOpenInNewTab(newTabUrl)
                        }
                    }
                }
            }
        }
    }
    
    // Create context menu items dynamically based on browser state
    val contextMenuItems = remember(canGoBack, canGoForward, hasVideoAtClick, rightClickedLinkUrl) {
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
            
            // Open link in new tab option - only show if right-clicked on a link
            if (!rightClickedLinkUrl.isNullOrEmpty()) {
                add(ContextMenuItem(
                    text = "Open Link in New Tab",
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    onClick = {
                        coroutineScope.launch(Dispatchers.Main) {
                            rightClickedLinkUrl?.let { url ->
                                onOpenInNewTab(url)
                            }
                        }
                    }
                ))
            }
            
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
            elevation = 1.dp
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp, horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Back button
                    IconButton(
                        onClick = { browser.navigation().goBack() },
                        enabled = canGoBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack, 
                            contentDescription = "Back",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Forward button
                    IconButton(
                        onClick = { browser.navigation().goForward() },
                        enabled = canGoForward,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward, 
                            contentDescription = "Forward",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // URL Input
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
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
                        textStyle = MaterialTheme.typography.body2.copy(color = MaterialTheme.colors.onSurface),
                        cursorBrush = SolidColor(MaterialTheme.colors.primary),
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(
                                        MaterialTheme.colors.surface,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        1.dp,
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                                        RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 12.dp, vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(modifier = Modifier.weight(1f)) {
                                    if (urlInput.text.isEmpty()) {
                                        Text(
                                            "Enter URL",
                                            style = MaterialTheme.typography.body2,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                        )
                                    }
                                    innerTextField()
                                }
                                IconButton(
                                    onClick = {
                                        var url = urlInput.text
                                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                                            url = "https://$url"
                                        }
                                        browser.navigation().loadUrl(url)
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        Icons.Default.Refresh,
                                        contentDescription = "Refresh",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    )
                }
                
                // Loading indicator
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth().height(2.dp)
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
                            
                            // Get the right-clicked link URL and check for video
                            browser.mainFrame().ifPresent { frame ->
                                // Get the right-clicked link URL
                                val linkUrl = frame.executeJavaScript<String?>("""
                                    (function() {
                                        return window._rightClickedLinkUrl || null;
                                    })();
                                """.trimIndent())
                                
                                coroutineScope.launch(Dispatchers.Main) {
                                    rightClickedLinkUrl = linkUrl
                                }
                                
                                // Check if there's a video element on the page
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