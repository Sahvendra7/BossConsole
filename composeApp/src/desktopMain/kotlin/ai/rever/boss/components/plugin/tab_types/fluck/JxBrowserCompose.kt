package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.config.JxBrowserConfig
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Color
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.isSecondaryPressed
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import com.teamdev.jxbrowser.navigation.event.LoadStarted
import com.teamdev.jxbrowser.navigation.event.NavigationFinished
import com.teamdev.jxbrowser.view.compose.BrowserView
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection


// Helper function to process URL input - either as URL or search query
private fun processUrlInput(input: String): String {
    val trimmed = input.trim()
    
    // If it's already a full URL, return as-is
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    
    // Check if it looks like a URL (contains dots and no spaces)
    val looksLikeUrl = trimmed.contains(".") && !trimmed.contains(" ")
    
    // Check for common URL patterns
    val urlPattern = Regex("""^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(/.*)?$""")
    val isLikelyUrl = looksLikeUrl || urlPattern.matches(trimmed)
    
    // Check for localhost patterns
    val isLocalhost = trimmed.startsWith("localhost") || 
                     trimmed.matches(Regex("""^127\.0\.0\.1(:\d+)?(/.*)?$""")) ||
                     trimmed.matches(Regex("""^localhost(:\d+)?(/.*)?$"""))
    
    return when {
        isLocalhost -> "http://$trimmed"
        isLikelyUrl -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${java.net.URLEncoder.encode(trimmed, "UTF-8")}"
    }
}

// Helper function to intelligently truncate long titles
private fun truncateTitle(title: String, url: String): String {
    // Special case for RISA Labs - always show full name
    if (title.startsWith("RISA Labs")) {
        return "RISA Labs"
    }
    
    // If title is reasonably short, use it as is
    if (title.length <= 40) return title
    
    // Try to extract site name from title or URL
    val urlHost = try {
        java.net.URL(url).host.removePrefix("www.")
    } catch (e: Exception) {
        ""
    }
    
    // Common patterns for site names in titles
    val patterns = listOf(
        " - ", " | ", " — ", " · ", " :: "
    )
    
    // Try to find a pattern and use the first part (usually the site name)
    for (pattern in patterns) {
        if (title.contains(pattern)) {
            val parts = title.split(pattern)
            val firstPart = parts.firstOrNull()?.trim() ?: ""
            // If the first part is reasonable, use it
            if (firstPart.isNotEmpty() && firstPart.length <= 30) {
                return firstPart
            }
        }
    }
    
    // If title starts with the domain name, try to extract just that part
    if (urlHost.isNotEmpty()) {
        val hostParts = urlHost.split(".")
        val siteName = hostParts.firstOrNull() ?: urlHost
        
        // Check if title starts with site name (case insensitive)
        if (title.lowercase().startsWith(siteName.lowercase())) {
            // Find where the site name ends in the title
            val endIndex = title.indexOfAny(patterns.map { it.first() }.toCharArray())
            if (endIndex > 0) {
                return title.substring(0, endIndex).trim()
            }
        }
    }
    
    // Last resort: truncate to reasonable length with ellipsis
    return title.take(35) + "..."
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun JxBrowserCompose(
    modifier: Modifier = Modifier,
    browser: Browser,
    browserViewState: BrowserViewState,
    initialUrl: String = JxBrowserConfig.defaultUrl,
    onTitleChange: (String) -> Unit = {},
    onIconChange: (ImageVector) -> Unit = {},
    onTabIconChange: (String) -> Unit = {},
    onOpenInNewTab: (String) -> Unit = {},
    onNavigationUpdate: ((String, String) -> Unit)? = null,
    onNavigationStateChange: ((isBack: Boolean) -> Unit)? = null
) {
    var urlInput by remember { mutableStateOf(TextFieldValue(initialUrl, TextRange(initialUrl.length))) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var rightClickPosition by remember { mutableStateOf(Offset.Zero) }
    var hasVideoAtClick by remember { mutableStateOf(false) }
    var rightClickedLinkUrl by remember { mutableStateOf<String?>(null) }
    var autocompleteSuggestion by remember { mutableStateOf<String?>(null) }
    var showDropdown by remember { mutableStateOf(false) }
    var dropdownSuggestions by remember { mutableStateOf<List<UrlHistoryEntry>>(emptyList()) }
    var selectedDropdownIndex by remember { mutableStateOf(-1) }
    val coroutineScope = rememberCoroutineScope()

    // Secret integration state
    val secretViewModel = remember { BrowserSecretIntegrationViewModel() }
    var focusedFieldInfo by remember { mutableStateOf<FormFieldDetector.FormFieldInfo?>(null) }
    var showSecretContextMenu by remember { mutableStateOf(false) }

    // Initialize secret integration
    LaunchedEffect(Unit) {
        secretViewModel.initialize()
    }

    // Dispose ViewModel when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            secretViewModel.dispose()
        }
    }

    // Track if this composable is being disposed to prevent race conditions with browser closure
    val isComposableDisposed = remember { mutableStateOf(false) }

    // Helper function to check if browser environment is still valid
    fun isBrowserEnvironmentValid(): Boolean {
        return !isComposableDisposed.value &&
               !browser.isClosed &&
               try {
                   // Check if any window is still valid by checking AWT window list
                   val windows = java.awt.Window.getWindows()
                   windows.any { window ->
                       try {
                           window.isDisplayable && window.isShowing
                       } catch (e: Exception) {
                           false
                       }
                   }
               } catch (e: Exception) {
                   false
               }
    }

    // Dispose effect for browser lifecycle coordination
    DisposableEffect(browser) {
        onDispose {
            // Signal that composable is being disposed
            isComposableDisposed.value = true
            // Coroutines will detect this flag and exit gracefully via isBrowserEnvironmentValid()
        }
    }

    // Set up browser navigation listeners
    LaunchedEffect(browser, initialUrl) {
        // Exit immediately if browser environment is not valid
        if (!isBrowserEnvironmentValid()) return@LaunchedEffect
        
        // Load initial URL if browser is on blank page
        try {
            val currentUrl = browser.url()
            if (currentUrl.isBlank() || currentUrl == "about:blank") {
                browser.navigation().loadUrl(initialUrl)
            }
            
            // Initial state
            canGoBack = browser.navigation().canGoBack()
            canGoForward = browser.navigation().canGoForward()
            urlInput = TextFieldValue(browser.url(), TextRange(browser.url().length))
        } catch (e: Exception) {
            // Browser might be disposed
            return@LaunchedEffect
        }
        
        // Set up navigation listeners
        browser.navigation().on(LoadStarted::class.java) {
            // Check if browser environment is still valid before accessing
            if (!isBrowserEnvironmentValid()) return@on

            coroutineScope.launch(Dispatchers.Main) {
                // Double-check before UI update
                if (!isBrowserEnvironmentValid()) return@launch

                isLoading = true
                val newUrl = browser.url()
                urlInput = TextFieldValue(newUrl, TextRange(newUrl.length))
                canGoBack = browser.navigation().canGoBack()
                canGoForward = browser.navigation().canGoForward()
            }
        }
        
        // Listen for NavigationFinished to update title even if LoadFinished doesn't fire
        browser.navigation().on(NavigationFinished::class.java) { event ->
            // Check if browser environment is still valid before accessing
            if (!isBrowserEnvironmentValid()) return@on

            if (event.isInMainFrame) {
                coroutineScope.launch(Dispatchers.Main) {
                    // Double-check before UI update
                    if (!isBrowserEnvironmentValid()) return@launch

                    // Update URL bar
                    val newUrl = event.url()
                    urlInput = TextFieldValue(newUrl, TextRange(newUrl.length))

                    // Update title immediately when navigation finishes
                    if (isBrowserEnvironmentValid()) {
                        val title = browser.title()
                        val displayTitle = if (title.isNotEmpty()) {
                            truncateTitle(title, newUrl)
                        } else {
                            // Fallback to domain name if no title
                            try {
                                val host = java.net.URL(newUrl).host.removePrefix("www.")
                                host
                            } catch (e: Exception) {
                                "Loading..."
                            }
                        }
                        onTitleChange(displayTitle)
                        
                        // Also update navigation state
                        onNavigationUpdate?.invoke(displayTitle, newUrl)
                        
                        // Schedule a delayed title check for SPAs that update title dynamically
                        launch {
                            delay(1000) // Wait 1 second
                            if (!browser.isClosed) {
                                val delayedTitle = browser.title()
                                if (delayedTitle.isNotEmpty() && delayedTitle != title) {
                                    val delayedDisplayTitle = truncateTitle(delayedTitle, newUrl)
                                    onTitleChange(delayedDisplayTitle)
                                    onNavigationUpdate?.invoke(delayedDisplayTitle, newUrl)
                                }
                            }
                        }
                    }
                }
            }
        }
        
        browser.navigation().on(LoadFinished::class.java) {
            // Check if browser environment is still valid before accessing
            if (!isBrowserEnvironmentValid()) return@on

            coroutineScope.launch(Dispatchers.Main) {
                // Double-check before UI update
                if (!isBrowserEnvironmentValid()) return@launch

                isLoading = false
                canGoBack = browser.navigation().canGoBack()
                canGoForward = browser.navigation().canGoForward()

                // Update secret ViewModel with current URL
                val currentUrl = browser.url()
                secretViewModel.onUrlChanged(currentUrl)

                // Inject form field detection script for secret auto-fill (Issue #56)
                FormFieldDetector.injectFormDetectionScript(browser)

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
                if (!isBrowserEnvironmentValid()) return@launch
                val title = browser.title()
                val url = browser.url()
                
                // println("Page loaded - URL: $url, Title: $title") // Debug log
                
                val displayTitle = if (title.isNotEmpty()) {
                    truncateTitle(title, url)
                } else {
                    // Fallback to domain name if no title
                    try {
                        val host = java.net.URL(url).host.removePrefix("www.")
                        host
                    } catch (e: Exception) {
                        "New Tab"
                    }
                }
                
                onTitleChange(displayTitle)
                
                // Notify navigation update with title and URL
                onNavigationUpdate?.invoke(displayTitle, url)
                
                // Add to history
                UrlHistoryManager.addUrl(url, displayTitle)
                coroutineScope.launch {
                    UrlHistoryManager.saveHistory()
                }
                
                // Try to extract favicon
                browser.mainFrame().ifPresent { frame ->
                    frame.executeJavaScript<String?>("""
                        (function() {
                            // Try to find favicon in various ways
                            let favicon = null;
                            
                            // Method 1: Look for rel="icon" or rel="shortcut icon"
                            const icons = document.querySelectorAll('link[rel*="icon"]');
                            if (icons.length > 0) {
                                // Prefer larger icons
                                for (let icon of icons) {
                                    const sizes = icon.getAttribute('sizes');
                                    if (sizes && (sizes.includes('32x32') || sizes.includes('64x64') || sizes.includes('128x128'))) {
                                        favicon = icon.href;
                                        break;
                                    }
                                }
                                // If no sized icon found, use the first one
                                if (!favicon) {
                                    favicon = icons[0].href;
                                }
                            }
                            
                            // Method 2: Check for apple-touch-icon (often higher quality)
                            if (!favicon) {
                                const appleIcon = document.querySelector('link[rel="apple-touch-icon"]');
                                if (appleIcon) {
                                    favicon = appleIcon.href;
                                }
                            }
                            
                            // Method 3: Try default favicon.ico
                            if (!favicon && window.location.origin && window.location.origin !== 'null') {
                                favicon = window.location.origin + '/favicon.ico';
                            }
                            
                            return favicon;
                        })();
                    """.trimIndent())?.let { faviconUrl ->
                        if (faviconUrl.isNotEmpty()) {
                            // Pass favicon URL to the callback
                            coroutineScope.launch(Dispatchers.Main) {
                                onTabIconChange(faviconUrl)
                            }
                        }
                    }
                }
                
                // Update icon to filled version when page loads (fallback)
                onIconChange(Icons.Filled.Language)
            }
        }

        // Load initial URL if browser hasn't loaded anything yet
        if (isBrowserEnvironmentValid()) {
            if (browser.url() == "about:blank" || browser.url().isEmpty()) {
                browser.navigation().loadUrl(initialUrl)
            } else {
                // Browser already has content loaded, update the title with current state
                val currentTitle = browser.title()
                val currentUrl = browser.url()

                if (currentTitle.isNotEmpty()) {
                    onTitleChange(truncateTitle(currentTitle, currentUrl))
                } else {
                    // Fallback to domain name if no title
                    try {
                        val host = java.net.URL(currentUrl).host.removePrefix("www.")
                        onTitleChange(host)
                    } catch (e: Exception) {
                        onTitleChange("New Tab")
                    }
                }

                // Try to extract favicon for already loaded page
                browser.mainFrame().ifPresent { frame ->
                frame.executeJavaScript<String?>("""
                    (function() {
                        // Try to find favicon in various ways
                        let favicon = null;
                        
                        // Method 1: Look for rel="icon" or rel="shortcut icon"
                        const icons = document.querySelectorAll('link[rel*="icon"]');
                        if (icons.length > 0) {
                            // Prefer larger icons
                            for (let icon of icons) {
                                const sizes = icon.getAttribute('sizes');
                                if (sizes && (sizes.includes('32x32') || sizes.includes('64x64') || sizes.includes('128x128'))) {
                                    favicon = icon.href;
                                    break;
                                }
                            }
                            // If no sized icon found, use the first one
                            if (!favicon) {
                                favicon = icons[0].href;
                            }
                        }
                        
                        // Method 2: Check for apple-touch-icon (often higher quality)
                        if (!favicon) {
                            const appleIcon = document.querySelector('link[rel="apple-touch-icon"]');
                            if (appleIcon) {
                                favicon = appleIcon.href;
                            }
                        }
                        
                        // Method 3: Try default favicon.ico
                        if (!favicon && window.location.origin && window.location.origin !== 'null') {
                            favicon = window.location.origin + '/favicon.ico';
                        }
                        
                        return favicon;
                    })();
                """.trimIndent())?.let { faviconUrl ->
                    if (faviconUrl.isNotEmpty()) {
                        coroutineScope.launch(Dispatchers.Main) {
                            onTabIconChange(faviconUrl)
                        }
                    }
                }
            }

            // Update icon for already loaded page
            onIconChange(Icons.Filled.Language)
            }
        }
    }
    
    // Set up polling for new tab requests
    LaunchedEffect(browser) {
        // Exit immediately if browser environment is not valid
        if (!isBrowserEnvironmentValid()) return@LaunchedEffect

        coroutineScope.launch {
            while (isBrowserEnvironmentValid()) {
                delay(100) // Check every 100ms

                // Double-check environment validity after delay
                if (!isBrowserEnvironmentValid()) break

                try {
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
                            if (newTabUrl.isNotEmpty() && isBrowserEnvironmentValid()) {
                                onOpenInNewTab(newTabUrl)
                            }
                        }
                    }
                } catch (e: Exception) {
                    // Browser might be closed or disposed, exit the loop
                    break
                }
            }
        }
    }
    
    // Create context menu items dynamically based on browser state
    val contextMenuItems = remember(canGoBack, canGoForward, hasVideoAtClick, rightClickedLinkUrl, focusedFieldInfo, secretViewModel.state) {
        // Issue #56: If form field is focused, show secret context menu
        if (focusedFieldInfo != null) {
            SecretContextMenuBuilder.buildSecretMenu(
                browser = browser,
                fieldInfo = focusedFieldInfo!!,
                currentUrl = browser.url(),
                allSecrets = secretViewModel.state.allSecrets,
                coroutineScope = coroutineScope,
                onShowAllSecrets = {
                    secretViewModel.showAllSecretsDialog()
                },
                onAddNewSecret = { websitePrefill ->
                    secretViewModel.showQuickCreateDialog(websitePrefill)
                },
                onDismiss = {
                    focusedFieldInfo = null
                }
            )
        } else {
            // Default context menu
            buildList {
                // Navigation items
                if (canGoBack) {
                    add(ContextMenuItem(
                        text = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = {
                            if (isBrowserEnvironmentValid()) {
                                browser.navigation().goBack()
                                onNavigationStateChange?.invoke(true)
                            }
                        }
                    ))
                }

                if (canGoForward) {
                    add(ContextMenuItem(
                        text = "Forward",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = {
                            if (isBrowserEnvironmentValid()) {
                                browser.navigation().goForward()
                                onNavigationStateChange?.invoke(false)
                            }
                        }
                    ))
                }

            // Always show reload
            add(ContextMenuItem(
                text = "Reload",
                icon = Icons.Default.Refresh,
                onClick = { if (isBrowserEnvironmentValid()) browser.navigation().reload() }
            ))
            
            add(ContextMenuItem(isDivider = true))
            
            // Picture-in-Picture option if clicking on a video
            if (hasVideoAtClick) {
                add(ContextMenuItem(
                    text = "Picture in Picture",
                    icon = Icons.Outlined.PictureInPictureAlt,
                    onClick = {
                        if (isBrowserEnvironmentValid()) {
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
                    }
                ))
                
                add(ContextMenuItem(isDivider = true))
            }
            
            // Copy current URL
            add(ContextMenuItem(
                text = "Copy URL",
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    if (isBrowserEnvironmentValid()) {
                        val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                        clipboard.setContents(StringSelection(browser.url()), null)
                    }
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
                onClick = { if (isBrowserEnvironmentValid()) browser.devTools().show() }
            ))
            }
        }
    }
    
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
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
                        onClick = {
                            if (isBrowserEnvironmentValid()) {
                                browser.navigation().goBack()
                                onNavigationStateChange?.invoke(true)
                            }
                        },
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
                        onClick = {
                            if (isBrowserEnvironmentValid()) {
                                browser.navigation().goForward()
                                onNavigationStateChange?.invoke(false)
                            }
                        },
                        enabled = canGoForward,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = "Forward",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // URL Input with inline autocomplete
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { newValue ->
                            urlInput = newValue
                            selectedDropdownIndex = -1
                            
                            // Get autocomplete suggestion and dropdown items
                            if (newValue.text.isNotEmpty() && newValue.selection.collapsed) {
                                val suggestions = UrlHistoryManager.getSuggestions(newValue.text, limit = 10)
                                
                                // Set inline autocomplete (first suggestion)
                                if (suggestions.isNotEmpty()) {
                                    val suggestion = suggestions.first()
                                    val suggestionUrl = suggestion.url
                                        .removePrefix("https://")
                                        .removePrefix("http://")
                                        .removePrefix("www.")
                                    
                                    // Only suggest if the URL starts with the input
                                    if (suggestionUrl.lowercase().startsWith(newValue.text.lowercase()) && 
                                        suggestionUrl.length > newValue.text.length) {
                                        autocompleteSuggestion = suggestionUrl
                                    } else {
                                        autocompleteSuggestion = null
                                    }
                                } else {
                                    autocompleteSuggestion = null
                                }
                                
                                // Set dropdown suggestions
                                dropdownSuggestions = suggestions
                                showDropdown = suggestions.isNotEmpty()
                            } else {
                                autocompleteSuggestion = null
                                dropdownSuggestions = emptyList()
                                showDropdown = false
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .onFocusChanged { focusState ->
                                if (!focusState.isFocused) {
                                    // Hide dropdown when focus is lost with a small delay to allow clicks
                                    coroutineScope.launch {
                                        kotlinx.coroutines.delay(200) // Give time for click events
                                        showDropdown = false
                                    }
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                when {
                                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Tab -> {
                                        // Accept autocomplete suggestion
                                        if (autocompleteSuggestion != null) {
                                            urlInput = TextFieldValue(
                                                autocompleteSuggestion!!,
                                                TextRange(autocompleteSuggestion!!.length)
                                            )
                                            autocompleteSuggestion = null
                                            true
                                        } else {
                                            false
                                        }
                                    }
                                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Enter -> {
                                        val urlToLoad = when {
                                            selectedDropdownIndex >= 0 && selectedDropdownIndex < dropdownSuggestions.size -> {
                                                // Use selected dropdown item
                                                dropdownSuggestions[selectedDropdownIndex].url
                                            }
                                            autocompleteSuggestion != null && 
                                            urlInput.text == autocompleteSuggestion!!.take(urlInput.text.length) -> {
                                                // Use the full autocomplete suggestion
                                                processUrlInput(autocompleteSuggestion!!)
                                            }
                                            else -> {
                                                val input = urlInput.text.trim()
                                                processUrlInput(input)
                                            }
                                        }
                                        if (isBrowserEnvironmentValid()) browser.navigation().loadUrl(urlToLoad)
                                        autocompleteSuggestion = null
                                        showDropdown = false
                                        true
                                    }
                                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionDown -> {
                                        if (showDropdown && dropdownSuggestions.isNotEmpty()) {
                                            selectedDropdownIndex = (selectedDropdownIndex + 1).coerceAtMost(dropdownSuggestions.size - 1)
                                        }
                                        true
                                    }
                                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionUp -> {
                                        if (showDropdown && dropdownSuggestions.isNotEmpty()) {
                                            selectedDropdownIndex = (selectedDropdownIndex - 1).coerceAtLeast(-1)
                                        }
                                        true
                                    }
                                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.Escape -> {
                                        autocompleteSuggestion = null
                                        showDropdown = false
                                        selectedDropdownIndex = -1
                                        true
                                    }
                                    else -> false
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
                                            "Enter URL or search",
                                            style = MaterialTheme.typography.body2,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                        )
                                    } else {
                                        // Show inline autocomplete
                                        Box {
                                            // Show the autocomplete suggestion
                                            if (autocompleteSuggestion != null && 
                                                autocompleteSuggestion!!.lowercase().startsWith(urlInput.text.lowercase())) {
                                                Text(
                                                    text = autocompleteSuggestion!!,
                                                    style = MaterialTheme.typography.body2,
                                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                                                )
                                            }
                                            // Show the actual input on top
                                            innerTextField()
                                        }
                                    }
                                }
                                IconButton(
                                    onClick = {
                                        val urlToLoad = if (autocompleteSuggestion != null &&
                                            urlInput.text == autocompleteSuggestion!!.take(urlInput.text.length)) {
                                            processUrlInput(autocompleteSuggestion!!)
                                        } else {
                                            val input = urlInput.text.trim()
                                            processUrlInput(input)
                                        }
                                        if (isBrowserEnvironmentValid()) browser.navigation().loadUrl(urlToLoad)
                                        autocompleteSuggestion = null
                                        showDropdown = false
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

                            // Check for form fields first (Issue #56 - Secret integration)
                            coroutineScope.launch {
                                focusedFieldInfo = FormFieldDetector.getCurrentFocusedField(browser)
                            }

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
        
        // Floating dropdown overlay
        if (showDropdown) {
            Card(
                modifier = Modifier
                    .fillMaxWidth(0.5f) // Half the width of the screen
                    .wrapContentHeight()
                    .align(Alignment.TopCenter)
                    .offset(y = 38.dp), // Position below the navigation bar
                elevation = 8.dp,
                backgroundColor = MaterialTheme.colors.surface
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp)
                ) {
                    items(dropdownSuggestions.withIndex().toList()) { (index, entry) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    if (index == selectedDropdownIndex) 
                                        MaterialTheme.colors.primary.copy(alpha = 0.1f)
                                    else 
                                        MaterialTheme.colors.surface
                                )
                                .clickable {
                                    if (isBrowserEnvironmentValid()) {
                                        browser.navigation().loadUrl(entry.url)
                                        urlInput = TextFieldValue(entry.url)
                                        showDropdown = false
                                        autocompleteSuggestion = null
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Icon to indicate type
                            Icon(
                                if (entry.title.contains("Google Search", ignoreCase = true)) 
                                    Icons.Filled.Search 
                                else 
                                    Icons.Filled.History,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.title.ifEmpty { entry.domain },
                                    style = MaterialTheme.typography.body2,
                                    maxLines = 1,
                                    color = MaterialTheme.colors.onSurface
                                )
                                Text(
                                    text = entry.url,
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1
                                )
                            }
                            // Delete button
                            IconButton(
                                onClick = {
                                    UrlHistoryManager.deleteUrl(entry.url)
                                    // Update dropdown to reflect deletion
                                    dropdownSuggestions = dropdownSuggestions.filterNot { it.url == entry.url }
                                    if (dropdownSuggestions.isEmpty()) {
                                        showDropdown = false
                                    }
                                    // Adjust selected index if needed
                                    if (selectedDropdownIndex >= dropdownSuggestions.size) {
                                        selectedDropdownIndex = dropdownSuggestions.size - 1
                                    }
                                    // Save history after deletion
                                    coroutineScope.launch {
                                        UrlHistoryManager.saveHistory()
                                    }
                                },
                                modifier = Modifier
                                    .size(24.dp)
                                    .padding(0.dp)
                            ) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Delete from history",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Secret Selection Dialog (Issue #56)
        if (secretViewModel.state.showAllSecretsDialog) {
            SecretSelectionDialog(
                browser = browser,
                currentUrl = browser.url(),
                secrets = secretViewModel.state.allSecrets,
                coroutineScope = coroutineScope,
                onDismiss = {
                    secretViewModel.hideAllSecretsDialog()
                },
                onAddNewSecret = { websitePrefill ->
                    secretViewModel.hideAllSecretsDialog()
                    secretViewModel.showQuickCreateDialog(websitePrefill)
                }
            )
        }

        // Quick Create Secret Dialog (Issue #56)
        if (secretViewModel.state.showQuickCreateDialog && secretViewModel.state.quickCreateWebsitePrefill != null) {
            var isCreating by remember { mutableStateOf(false) }

            ai.rever.boss.components.plugin.panels.right_top.QuickCreateSecretDialog(
                websitePrefill = secretViewModel.state.quickCreateWebsitePrefill ?: "",
                onConfirm = { request ->
                    isCreating = true
                    coroutineScope.launch {
                        try {
                            println("🔐 [JxBrowserCompose] Creating secret for: ${request.website}")
                            val result = ai.rever.boss.services.supabase.SecretService.createSecret(request)
                            result.fold(
                                onSuccess = {
                                    println("✅ [JxBrowserCompose] Secret created successfully for ${request.website}")

                                    // Reload secrets and wait for completion
                                    secretViewModel.reloadSecrets()

                                    // Notify other components about the new secret
                                    SecretChangeNotifier.notifyRefresh()

                                    secretViewModel.hideQuickCreateDialog()
                                    isCreating = false

                                    println("✅ [JxBrowserCompose] Secret added and list refreshed - total secrets now: ${secretViewModel.state.allSecrets.size}")
                                },
                                onFailure = { error ->
                                    println("❌ [JxBrowserCompose] Failed to create secret: ${error.message}")
                                    error.printStackTrace()
                                    isCreating = false
                                }
                            )
                        } catch (e: Exception) {
                            println("❌ [JxBrowserCompose] Exception creating secret: ${e.message}")
                            e.printStackTrace()
                            isCreating = false
                        }
                    }
                },
                onDismiss = {
                    secretViewModel.hideQuickCreateDialog()
                },
                isLoading = isCreating
            )
        }
    }
}
