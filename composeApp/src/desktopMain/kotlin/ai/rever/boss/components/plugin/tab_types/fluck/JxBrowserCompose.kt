package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.cache.FaviconCache
import ai.rever.boss.components.bookmarks.Bookmark
import ai.rever.boss.components.bookmarks.WorkspacePanelTarget
import ai.rever.boss.components.bookmarks.bookmarkManager
import ai.rever.boss.components.dashboard.Dashboard
import ai.rever.boss.components.dialogs.BookmarkDialog
import ai.rever.boss.components.dialogs.InfoDialog
import ai.rever.boss.components.dialogs.RemoveBookmarkConfirmationDialog
import ai.rever.boss.components.events.DashboardEventBus
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.overlays.contextMenu
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.components.workspaces.TabConfig
import ai.rever.boss.components.workspaces.workspaceManager
import ai.rever.boss.config.JxBrowserConfig
import ai.rever.boss.components.events.KeyboardEventBus
import ai.rever.boss.components.events.KeyEventSource
import ai.rever.boss.components.events.KeyboardEvent as BossKeyboardEvent
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.window.LocalWindowProjectState
import ai.rever.boss.window.WindowOperations
import ai.rever.boss.window.selectProjectInWindow
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toComposeImageBitmap
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
import androidx.compose.ui.input.pointer.PointerButton
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.onPointerEvent
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.teamdev.jxbrowser.browser.Browser
import com.teamdev.jxbrowser.browser.event.FaviconChanged
import com.teamdev.jxbrowser.navigation.event.FrameLoadFailed
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import com.teamdev.jxbrowser.navigation.event.LoadStarted
import com.teamdev.jxbrowser.navigation.event.NavigationFinished
import com.teamdev.jxbrowser.view.compose.BrowserView
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage


// Helper function to process URL input - either as URL or search query
private fun processUrlInput(input: String): String {
    val trimmed = input.trim()
    val lowerTrimmed = trimmed.lowercase()

    // If it's already a full URL or special scheme, return as-is (case-insensitive check)
    if (lowerTrimmed.startsWith("http://") || lowerTrimmed.startsWith("https://") ||
        lowerTrimmed.startsWith("file://") || lowerTrimmed.startsWith("javascript:")) {
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

// Helper function to convert JxBrowser Bitmap to AWT BufferedImage
private fun bitmapToBufferedImage(bitmap: com.teamdev.jxbrowser.ui.Bitmap): BufferedImage {
    val size = bitmap.size()
    val width = size.width()
    val height = size.height()

    // Create BufferedImage with ARGB color model
    val bufferedImage = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)

    // Get pixel data from bitmap (BGRA format - Chromium's native format)
    val pixels = bitmap.pixels()

    // Convert BGRA bytes to ARGB integers and set pixels
    var pixelIndex = 0
    for (y in 0 until height) {
        for (x in 0 until width) {
            // Read BGRA bytes (each pixel is 4 bytes)
            val b = pixels[pixelIndex++].toInt() and 0xFF
            val g = pixels[pixelIndex++].toInt() and 0xFF
            val r = pixels[pixelIndex++].toInt() and 0xFF
            val a = pixels[pixelIndex++].toInt() and 0xFF

            // Combine into ARGB integer
            val argb = (a shl 24) or (r shl 16) or (g shl 8) or b
            bufferedImage.setRGB(x, y, argb)
        }
    }

    return bufferedImage
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
    browser: LockedBrowser,
    browserViewState: BrowserViewState,
    initialUrl: String = JxBrowserConfig.defaultUrl,
    onTitleChange: (String) -> Unit = {},
    onIconChange: (ImageVector) -> Unit = {},
    onTabIconUpdate: (TabIcon) -> Unit = {},
    onOpenInNewTab: (String) -> Unit = {},
    onNavigationUpdate: ((String, String) -> Unit)? = null,
    onNavigationStateChange: ((isBack: Boolean) -> Unit)? = null,
    onFaviconCached: ((String?) -> Unit)? = null,
    onCloseTab: (() -> Unit)? = null
) {
    var urlInput by remember { mutableStateOf(TextFieldValue(initialUrl, TextRange(initialUrl.length))) }
    var isLoading by remember { mutableStateOf(false) }
    var canGoBack by remember { mutableStateOf(false) }
    var canGoForward by remember { mutableStateOf(false) }
    var lastNavigationTime by remember { mutableStateOf(0L) }  // Debounce rapid clicks (100ms)
    var rightClickPosition by remember { mutableStateOf(Offset.Zero) }
    var hasVideoAtClick by remember { mutableStateOf(false) }
    var rightClickedLinkUrl by remember { mutableStateOf<String?>(null) }
    var selectedText by remember { mutableStateOf<String?>(null) }
    var autocompleteSuggestion by remember { mutableStateOf<String?>(null) }
    var showDropdown by remember { mutableStateOf(false) }
    var dropdownSuggestions by remember { mutableStateOf<List<UrlHistoryEntry>>(emptyList()) }
    var selectedDropdownIndex by remember { mutableStateOf(-1) }
    var isUserEditingUrl by remember { mutableStateOf(false) }
    var lastUserEditTime by remember { mutableStateOf(0L) }
    val dropdownListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Secret integration state
    val secretViewModel = remember { BrowserSecretIntegrationViewModel() }
    var focusedFieldInfo by remember { mutableStateOf<FormFieldDetector.FormFieldInfo?>(null) }
    var showSecretContextMenu by remember { mutableStateOf(false) }

    // Bookmark state management
    val collections by bookmarkManager.collections.collectAsState()
    var currentTitle by remember { mutableStateOf(initialUrl) }
    var currentFaviconKey by remember { mutableStateOf<String?>(null) }

    // Wrap callbacks to track title and favicon for bookmarks
    val wrappedOnTitleChange: (String) -> Unit = { title ->
        currentTitle = title
        onTitleChange(title)
    }

    val wrappedOnFaviconCached: ((String?) -> Unit)? = if (onFaviconCached != null) {
        { cacheKey ->
            currentFaviconKey = cacheKey
            onFaviconCached(cacheKey)
        }
    } else {
        { cacheKey -> currentFaviconKey = cacheKey }
    }

    // Create TabConfig for current page
    val currentTabConfig = remember(browser.urlOrEmpty(), currentTitle, currentFaviconKey) {
        TabConfig(
            type = "browser",
            title = currentTitle,
            url = browser.urlOrEmpty(),
            faviconCacheKey = currentFaviconKey
        )
    }

    // Check if current page is bookmarked
    val isBookmarked = remember(currentTabConfig, collections) {
        bookmarkManager.isTabBookmarked(currentTabConfig)
    }

    // Dialog states for bookmark management
    var showBookmarkDialog by remember { mutableStateOf(false) }
    var showRemoveBookmarkDialog by remember { mutableStateOf(false) }

    // JavaScript dialog state (for BOSS-styled dialogs from JxBrowser callbacks)
    var jsDialogState by remember { mutableStateOf<JsDialogNotifier.JsDialogEvent?>(null) }

    // Get browser ID for this instance (used to filter JS dialog events)
    val currentBrowserId = remember { System.identityHashCode(browser.unsafe()) }

    // Initialize secret integration
    LaunchedEffect(Unit) {
        secretViewModel.initialize()
    }

    // Observe JavaScript dialog events and show BOSS-styled dialogs (only for this browser instance)
    LaunchedEffect(currentBrowserId) {
        JsDialogNotifier.dialogEvents.collect { event ->
            // Only show dialog if it's for this browser instance (fixes duplicate dialogs issue)
            if (event.browserId == currentBrowserId) {
                jsDialogState = event
            }
        }
    }

    // Auto-scroll to selected suggestion when using arrow keys
    LaunchedEffect(selectedDropdownIndex) {
        if (selectedDropdownIndex >= 0 && dropdownSuggestions.isNotEmpty()) {
            dropdownListState.animateScrollToItem(selectedDropdownIndex)
        }
    }

    // Dispose ViewModel when composable leaves composition
    DisposableEffect(Unit) {
        onDispose {
            secretViewModel.dispose()
        }
    }

    // Helper function to check if browser environment is still valid for operations
    fun isBrowserEnvironmentValid(): Boolean = !browser.isClosed

    // Set up browser navigation listeners
    LaunchedEffect(browser, initialUrl) {
        // Exit immediately if browser environment is not valid
        if (!isBrowserEnvironmentValid()) return@LaunchedEffect

        // Store subscriptions for cleanup to prevent memory leaks
        val subscriptions = mutableListOf<com.teamdev.jxbrowser.event.Subscription>()

        try {
            // Load initial URL if browser is on blank page
            try {
                val currentUrl = browser.url()
                if (currentUrl.isBlank() || currentUrl == "about:blank") {
                    browser.navigation().loadUrl(initialUrl)
                }

                // Initial state
                canGoBack = browser.navigation().canGoBack()
                canGoForward = browser.navigation().canGoForward()
                val url = browser.url()
                urlInput = TextFieldValue(url, TextRange(url.length))
            } catch (e: Exception) {
                // Browser might be disposed
                return@LaunchedEffect
            }

            // Set up navigation listeners (store subscriptions for cleanup)
            // Note: Event registration uses unsafe() since it's done once during setup
            subscriptions += browser.unsafe().navigation().on(LoadStarted::class.java) {
            // Check if browser environment is still valid before accessing
            if (!isBrowserEnvironmentValid()) return@on

            coroutineScope.launch(Dispatchers.Main) {
                // Double-check before UI update
                if (!isBrowserEnvironmentValid()) return@launch

                try {
                    isLoading = true

                    // Only update URL bar if user isn't actively editing
                    // AND sufficient time has passed since last input (300ms buffer for Tab completion)
                    val timeSinceEdit = System.currentTimeMillis() - lastUserEditTime
                    if (!isUserEditingUrl && timeSinceEdit > 300) {
                        val newUrl = browser.url()
                        urlInput = TextFieldValue(newUrl, TextRange(newUrl.length))
                    }

                    canGoBack = browser.navigation().canGoBack()
                    canGoForward = browser.navigation().canGoForward()
                } catch (e: Exception) {
                    // Issue #255: Browser was closed during operation - silently ignore
                }
            }
        }

        // Listen for NavigationFinished to update title even if LoadFinished doesn't fire
        subscriptions += browser.unsafe().navigation().on(NavigationFinished::class.java) { event ->
            // Check if browser environment is still valid before accessing
            if (!isBrowserEnvironmentValid()) return@on

            if (event.isInMainFrame) {
                coroutineScope.launch(Dispatchers.Main) {
                    // Double-check before UI update
                    if (!isBrowserEnvironmentValid()) return@launch

                    try {
                        // Update URL bar only if user isn't actively editing
                        val newUrl = event.url()
                        val timeSinceEdit = System.currentTimeMillis() - lastUserEditTime
                        if (!isUserEditingUrl && timeSinceEdit > 300) {
                            urlInput = TextFieldValue(newUrl, TextRange(newUrl.length))
                        }

                        // Update title immediately when navigation finishes
                        if (isBrowserEnvironmentValid()) {
                            val t = browser.title()
                            val dt = if (t.isNotEmpty()) {
                                truncateTitle(t, newUrl)
                            } else {
                                // Fallback to domain name if no title
                                try {
                                    val host = java.net.URL(newUrl).host.removePrefix("www.")
                                    host
                                } catch (e: Exception) {
                                    "Loading..."
                                }
                            }
                            val title = t
                            val displayTitle = dt

                            wrappedOnTitleChange(displayTitle)

                            // Also update navigation state
                            onNavigationUpdate?.invoke(displayTitle, newUrl)

                            // Schedule a delayed title check for SPAs that update title dynamically
                            launch {
                                try {
                                    delay(1000) // Wait 1 second
                                    if (!browser.isClosed) {
                                        val delayedTitle = browser.title()
                                        if (delayedTitle.isNotEmpty() && delayedTitle != title) {
                                            val delayedDisplayTitle = truncateTitle(delayedTitle, newUrl)
                                            wrappedOnTitleChange(delayedDisplayTitle)
                                            onNavigationUpdate?.invoke(delayedDisplayTitle, newUrl)
                                        }
                                    }
                                } catch (e: Exception) {
                                    // Issue #255: Browser was closed during delayed title check - silently ignore
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // Issue #255: Browser was closed during operation - silently ignore
                    }
                }
            }
        }

        subscriptions += browser.unsafe().navigation().on(LoadFinished::class.java) {
            // Check if browser environment is still valid before accessing
            if (!isBrowserEnvironmentValid()) return@on

            coroutineScope.launch(Dispatchers.Main) {
                // Double-check before UI update
                if (!isBrowserEnvironmentValid()) return@launch

                try {
                    isLoading = false
                    canGoBack = browser.navigation().canGoBack()
                    canGoForward = browser.navigation().canGoForward()

                    // Update secret ViewModel with current URL
                    val currentUrl = browser.url()
                    secretViewModel.onUrlChanged(currentUrl)

                    // Inject form field detection script for secret auto-fill (Issue #56)
                    FormFieldDetector.injectFormDetectionScript(browser)

                    // Install form submission monitor for debugging autofill issues
                    coroutineScope.launch {
                        FormFieldInjector.installFormSubmissionMonitor(browser)
                    }

                    // Inject JavaScript to handle cmd+click on links
                    browser.mainFrame().ifPresent { frame ->
                        try {
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
                        } catch (e: Exception) {
                            // JavaScript execution failed - browser might be closing
                            // Issue #255: Gracefully handle frame access exceptions
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

                    // Note: Favicon is now handled by FaviconChanged event listener below
                    // No longer calling onIconChange here to avoid overriding the favicon
                } catch (e: Exception) {
                    // Issue #255: Browser was closed during operation - silently ignore
                }
            }
        }

        // Handle navigation failures to reset loading state
        subscriptions += browser.unsafe().navigation().on(FrameLoadFailed::class.java) { event ->
            // Only handle main frame failures
            if (!event.frame().isMain) return@on
            if (!isBrowserEnvironmentValid()) return@on

            coroutineScope.launch(Dispatchers.Main) {
                if (!isBrowserEnvironmentValid()) return@launch

                try {
                    isLoading = false
                    canGoBack = browser.navigation().canGoBack()
                    canGoForward = browser.navigation().canGoForward()
                } catch (e: Exception) {
                    // Issue #255: Browser was closed during operation - silently ignore
                }
            }
        }

        // Listen for favicon changes (using JxBrowser's native API)
        subscriptions += browser.unsafe().on(FaviconChanged::class.java) { event ->
            // Check if browser environment is still valid before accessing
            if (!isBrowserEnvironmentValid()) return@on

            coroutineScope.launch(Dispatchers.Main) {
                // Double-check before UI update
                if (!isBrowserEnvironmentValid()) return@launch

                try {
                    // Get favicon from event
                    val favicon = event.favicon()
                    if (favicon != null) {
                        // Convert JxBrowser Bitmap to AWT BufferedImage manually
                        val bufferedImage = bitmapToBufferedImage(favicon)

                        // Convert BufferedImage to Compose ImageBitmap
                        val imageBitmap = bufferedImage.toComposeImageBitmap()

                        // Cache the favicon (Issue #160)
                        val currentUrl = browser.url()
                        val cacheKey = FaviconCache.saveFavicon(currentUrl, imageBitmap)
                        if (cacheKey != null) {
                            wrappedOnFaviconCached?.invoke(cacheKey)
                        }

                        // Create TabIcon and update
                        val tabIcon = TabIcon.Image(BitmapPainter(imageBitmap))
                        onTabIconUpdate(tabIcon)
                    }
                } catch (e: Exception) {
                    // Issue #255: Handle favicon conversion errors (ignore "closed object" exceptions)
                    if (e.message?.contains("closed object") != true) {
                        println("❌ [JxBrowser Native] Error converting favicon: ${e.message}")
                        // Set default Language icon on error
                        onTabIconUpdate(TabIcon.Vector(Icons.Outlined.Language))
                    }
                }
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

                // Note: Favicon is handled by FaviconChanged event listener
                // If browser already has a favicon loaded, the event will fire automatically
            }
        }

            // Keep effect alive - listeners stay active until effect is cancelled
            awaitCancellation()
        } finally {
            // Clean up event listener subscriptions to prevent memory leaks
            subscriptions.forEach { subscription ->
                try {
                    subscription.unsubscribe()
                } catch (e: Exception) {
                    // Ignore errors during cleanup (browser might already be closed)
                }
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
    val contextMenuItems = remember(canGoBack, canGoForward, hasVideoAtClick, rightClickedLinkUrl, selectedText, focusedFieldInfo, secretViewModel.state) {
        // Issue #56: If form field is focused, show secret context menu
        if (focusedFieldInfo != null) {
            try {
                // Issue #255: Protect browser.url() call from "closed object" exception
                val currentUrl = if (isBrowserEnvironmentValid()) {
                    try {
                        browser.url()
                    } catch (e: Exception) {
                        ""
                    }
                } else {
                    ""
                }

                SecretContextMenuBuilder.buildSecretMenu(
                    browser = browser,
                    fieldInfo = focusedFieldInfo!!,
                    currentUrl = currentUrl,
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
            } catch (e: Exception) {
                // If secret menu building fails, fall back to default menu
                emptyList()
            }
        } else {
            // Default context menu
            buildList {
                // Navigation items
                if (canGoBack) {
                    add(ContextMenuItem(
                        text = "Back",
                        icon = Icons.AutoMirrored.Filled.ArrowBack,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (isBrowserEnvironmentValid() && (now - lastNavigationTime) > 100) {
                                try {
                                    lastNavigationTime = now
                                    browser.navigation().goBack()
                                    onNavigationStateChange?.invoke(true)
                                } catch (e: Exception) {
                                    // Issue #255: Browser closed during navigation
                                }
                            }
                        }
                    ))
                }

                if (canGoForward) {
                    add(ContextMenuItem(
                        text = "Forward",
                        icon = Icons.AutoMirrored.Filled.ArrowForward,
                        onClick = {
                            val now = System.currentTimeMillis()
                            if (isBrowserEnvironmentValid() && (now - lastNavigationTime) > 100) {
                                try {
                                    lastNavigationTime = now
                                    browser.navigation().goForward()
                                    onNavigationStateChange?.invoke(false)
                                } catch (e: Exception) {
                                    // Issue #255: Browser closed during navigation
                                }
                            }
                        }
                    ))
                }

            // Always show reload
            add(ContextMenuItem(
                text = "Reload",
                icon = Icons.Default.Refresh,
                onClick = {
                    if (isBrowserEnvironmentValid()) {
                        try {
                            browser.navigation().reload()
                        } catch (e: Exception) {
                            // Issue #255: Browser closed during reload
                        }
                    }
                }
            ))
            
            add(ContextMenuItem(isDivider = true))
            
            // Picture-in-Picture option if clicking on a video
            if (hasVideoAtClick) {
                add(ContextMenuItem(
                    text = "Picture in Picture",
                    icon = Icons.Outlined.PictureInPictureAlt,
                    onClick = {
                        if (isBrowserEnvironmentValid()) {
                            try {
                                browser.mainFrame().ifPresent { frame ->
                                    // Use centralized JavaScript for PiP
                                    frame.executeJavaScript<Unit>(BrowserJavaScripts.enablePictureInPicture)
                                }
                            } catch (e: Exception) {
                                // Issue #255: Browser closed during PiP activation
                            }
                        }
                    }
                ))

                add(ContextMenuItem(isDivider = true))
            }

            // Copy selected text (Issue #159)
            if (!selectedText.isNullOrEmpty()) {
                add(ContextMenuItem(
                    text = "Copy",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        if (isBrowserEnvironmentValid()) {
                            try {
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(StringSelection(selectedText), null)
                            } catch (e: Exception) {
                                // Issue #255: Copy operation failed
                            }
                        }
                    }
                ))
            }

            // Copy current URL
            add(ContextMenuItem(
                text = "Copy URL",
                icon = Icons.Outlined.ContentCopy,
                onClick = {
                    if (isBrowserEnvironmentValid()) {
                        try {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(browser.url()), null)
                        } catch (e: Exception) {
                            // Issue #255: Browser closed during URL copy
                        }
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
                            val now = System.currentTimeMillis()
                            // Debounce: require 100ms between navigations to prevent race conditions
                            if (isBrowserEnvironmentValid() && (now - lastNavigationTime) > 100) {
                                lastNavigationTime = now
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
                            val now = System.currentTimeMillis()
                            // Debounce: require 100ms between navigations to prevent race conditions
                            if (isBrowserEnvironmentValid() && (now - lastNavigationTime) > 100) {
                                lastNavigationTime = now
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

                    // Refresh/Stop button - changes based on loading state
                    IconButton(
                        onClick = {
                            if (isLoading) {
                                // Stop the current navigation
                                if (isBrowserEnvironmentValid()) browser.navigation().stop()
                                isLoading = false  // Immediately reset loading state
                            } else {
                                // Reload/navigate to URL
                                val urlToLoad = if (autocompleteSuggestion != null &&
                                    urlInput.text == autocompleteSuggestion!!.take(urlInput.text.length)) {
                                    processUrlInput(autocompleteSuggestion!!)
                                } else {
                                    val input = urlInput.text.trim()
                                    processUrlInput(input)
                                }
                                // Clear editing state to allow URL bar updates during navigation
                                isUserEditingUrl = false
                                lastUserEditTime = 0L
                                if (isBrowserEnvironmentValid()) browser.navigation().loadUrl(urlToLoad)
                                autocompleteSuggestion = null
                                showDropdown = false
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            if (isLoading) Icons.Default.Close else Icons.Default.Refresh,
                            contentDescription = if (isLoading) "Stop" else "Refresh",
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // URL Input with inline autocomplete
                    BasicTextField(
                        value = urlInput,
                        onValueChange = { newValue ->
                            isUserEditingUrl = true
                            lastUserEditTime = System.currentTimeMillis()
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
                                        isUserEditingUrl = false
                                    }
                                }
                            }
                            .onPreviewKeyEvent { keyEvent ->
                                val consumed = when {
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
                                            else -> {
                                                // Use what the user actually typed
                                                val input = urlInput.text.trim()
                                                processUrlInput(input)
                                            }
                                        }
                                        // Clear editing state to allow URL bar updates during navigation
                                        isUserEditingUrl = false
                                        lastUserEditTime = 0L
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

                                // If URL bar didn't consume the event, emit to KeyboardEventBus
                                // This allows global shortcuts (like Cmd+N) to work when URL bar has focus
                                // Fix for Issue #223: Filter modifier-only keys (Caps Lock, Shift, etc.) to prevent
                                // unnecessary recompositions that cause text overlap
                                if (!consumed && keyEvent.type == KeyEventType.KeyDown) {
                                    // Don't emit events for modifier-only keys
                                    val isModifierKey = keyEvent.key in setOf(
                                        Key.CapsLock, Key.ShiftLeft, Key.ShiftRight,
                                        Key.CtrlLeft, Key.CtrlRight, Key.AltLeft, Key.AltRight,
                                        Key.MetaLeft, Key.MetaRight, Key.NumLock, Key.ScrollLock
                                    )

                                    if (!isModifierKey) {
                                        coroutineScope.launch {
                                            KeyboardEventBus.emit(
                                                BossKeyboardEvent(
                                                    keyEvent = keyEvent,
                                                    source = KeyEventSource.COMPONENT_BROWSER,
                                                    context = ShortcutContext.BROWSER
                                                )
                                            )
                                        }
                                    }
                                }

                                consumed
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
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight(),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    // Fix for Issue #223: Use AnnotatedString to prevent text overlap
                                    // Single text rendering path eliminates layering issues during recomposition

                                    // Show placeholder when empty
                                    if (urlInput.text.isEmpty()) {
                                        Text(
                                            "Enter URL or search",
                                            style = MaterialTheme.typography.body2,
                                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                        )
                                    }

                                    // Show autocomplete using AnnotatedString approach
                                    if (autocompleteSuggestion != null &&
                                        urlInput.text.isNotEmpty() &&
                                        autocompleteSuggestion!!.lowercase().startsWith(urlInput.text.lowercase())) {

                                        // Build styled text: user's input (transparent) + autocomplete suffix (gray)
                                        val autocompleteDisplay = buildAnnotatedString {
                                            // User's typed text in nearly transparent color
                                            // (innerTextField will render actual text on top)
                                            withStyle(SpanStyle(color = Color.Transparent)) {
                                                append(urlInput.text)
                                            }
                                            // Autocomplete suffix in gray
                                            withStyle(SpanStyle(color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f))) {
                                                append(autocompleteSuggestion!!.substring(urlInput.text.length))
                                            }
                                        }

                                        Text(
                                            text = autocompleteDisplay,
                                            style = MaterialTheme.typography.body2,
                                            maxLines = 1
                                        )
                                    }

                                    // User's actual input field (always rendered for cursor)
                                    innerTextField()
                                }

                                // Bookmark star button
                                IconButton(
                                    onClick = {
                                        if (isBookmarked) {
                                            // Show remove confirmation dialog
                                            showRemoveBookmarkDialog = true
                                        } else {
                                            // Show add bookmark dialog
                                            showBookmarkDialog = true
                                        }
                                    },
                                    modifier = Modifier.size(20.dp)
                                ) {
                                    Icon(
                                        imageVector = if (isBookmarked) Icons.Filled.Star else Icons.Outlined.StarBorder,
                                        contentDescription = if (isBookmarked) "Remove from Bookmarks" else "Add to Bookmarks",
                                        tint = if (isBookmarked) Color(0xFFFFD700) else MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
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

        // Content area: Show Dashboard for about:blank, otherwise show browser
        val currentUrl = urlInput.text
        val showDashboard = currentUrl.isEmpty() || currentUrl == "about:blank"

        if (showDashboard) {
            // Dashboard for empty/about:blank URLs
            val windowProjectState = LocalWindowProjectState.current
            val selectedProject by windowProjectState?.selectedProject?.collectAsState()
                ?: ProjectState.selectedProject.collectAsState()

            Dashboard(
                onOpenFile = { path ->
                    coroutineScope.launch { DashboardEventBus.openFile(path) }
                },
                onOpenUrl = { url ->
                    // Navigate browser to the URL
                    if (isBrowserEnvironmentValid()) {
                        browser.navigation().loadUrl(url)
                    }
                },
                onOpenProject = { project ->
                    selectProjectInWindow(windowProjectState, project)
                },
                selectedProject = selectedProject,
                onNewTab = {
                    coroutineScope.launch { DashboardEventBus.newTab() }
                },
                onNewTerminal = {
                    coroutineScope.launch { DashboardEventBus.newTerminal() }
                },
                onNewWindow = {
                    WindowOperations.createNewWindow()
                },
                onOpenProjectDialog = {
                    coroutineScope.launch { DashboardEventBus.showProjectDialog() }
                },
                onOpenFileDialog = {
                    coroutineScope.launch { DashboardEventBus.showFileDialog() }
                },
                onNewProject = {
                    coroutineScope.launch { DashboardEventBus.showNewProject() }
                },
                onApplySplitTemplate = { template ->
                    coroutineScope.launch { DashboardEventBus.applySplitTemplate(template) }
                },
                onActivatePlugin = { pluginId ->
                    coroutineScope.launch { DashboardEventBus.activatePlugin(pluginId) }
                }
            )
        } else {
            // Browser content using native Compose BrowserView with custom context menu
            BrowserView(
                state = browserViewState,
                modifier = Modifier
                    .fillMaxSize()
                    .onPointerEvent(PointerEventType.Press) { event ->
                        // Access native AWT MouseEvent for extended button detection (Issue #325)
                        val awtEvent = event.nativeEvent as? java.awt.event.MouseEvent

                        // Handle middle-click - close tab (Issue #328)
                        if (awtEvent?.button == 2) {
                            onCloseTab?.invoke()
                            event.changes.forEach { it.consume() }
                            return@onPointerEvent
                        }

                        // Handle mouse back button - navigate back
                        // Windows/macOS: awtButton=4, Linux: awtButton=6 or 8 (varies by mouse)
                        if (awtEvent?.button in listOf(4, 6, 8)) {
                            val now = System.currentTimeMillis()
                            if (isBrowserEnvironmentValid() && (now - lastNavigationTime) > 100 && browser.navigation().canGoBack()) {
                                lastNavigationTime = now
                                browser.navigation().goBack()
                            }
                            event.changes.forEach { it.consume() }
                            return@onPointerEvent
                        }

                        // Handle mouse forward button - navigate forward
                        // Windows/macOS: awtButton=5, Linux: awtButton=7 or 9 (varies by mouse)
                        if (awtEvent?.button in listOf(5, 7, 9)) {
                            val now = System.currentTimeMillis()
                            if (isBrowserEnvironmentValid() && (now - lastNavigationTime) > 100 && browser.navigation().canGoForward()) {
                                lastNavigationTime = now
                                browser.navigation().goForward()
                            }
                            event.changes.forEach { it.consume() }
                            return@onPointerEvent
                        }

                        // Handle right-click for context menu
                        if (event.button == PointerButton.Secondary) {
                            // Store the click position
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                rightClickPosition = change.position

                                // Check for form fields first (Issue #56 - Secret integration)
                                coroutineScope.launch {
                                    focusedFieldInfo = FormFieldDetector.getCurrentFocusedField(browser)
                                }

                                // Get the right-clicked link URL, selected text, and check for video
                                browser.mainFrame().ifPresent { frame ->
                                    // Get the right-clicked link URL
                                    val linkUrl = frame.executeJavaScript<String?>(BrowserJavaScripts.getRightClickedLinkUrl)

                                    coroutineScope.launch(Dispatchers.Main) {
                                        rightClickedLinkUrl = linkUrl
                                    }

                                    // Get selected text (Issue #159 - Copy text context menu)
                                    val selection = frame.executeJavaScript<String?>(BrowserJavaScripts.getSelectedText)

                                    coroutineScope.launch(Dispatchers.Main) {
                                        selectedText = if (!selection.isNullOrBlank()) selection else null
                                    }

                                    // Check if there's a video element on the page
                                    val hasVideo = frame.executeJavaScript<Boolean>(BrowserJavaScripts.hasVideoElements)

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
                    state = dropdownListState,
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
                                    isCreating = false
                                }
                            )
                        } catch (e: Exception) {
                            println("❌ [JxBrowserCompose] Exception creating secret: ${e.message}")
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

        // Bookmark Dialog
        if (showBookmarkDialog) {
            val workspaces by workspaceManager.workspaces.collectAsState()
            BookmarkDialog(
                tabTitle = currentTitle,
                collections = collections,
                workspaces = workspaces,
                onDismiss = { showBookmarkDialog = false },
                onConfirm = { collectionIds, workspacePanelMap ->
                    val workspace = workspaceManager.currentWorkspace.value

                    // Convert workspacePanelMap to list of WorkspacePanelTarget
                    val targetWorkspaces = workspacePanelMap.map { (workspaceName, panelId) ->
                        WorkspacePanelTarget(workspaceName = workspaceName, panelId = panelId)
                    }

                    // Create bookmark for each selected collection
                    collectionIds.forEach { collectionId ->
                        val bookmark = Bookmark(
                            tabConfig = currentTabConfig,
                            workspaceName = workspace?.name ?: "Unknown",
                            targetWorkspaces = targetWorkspaces
                        )
                        val collection = collections.find { it.id == collectionId }
                        if (collection != null) {
                            bookmarkManager.addBookmark(collection.name, bookmark)
                        }
                    }

                    showBookmarkDialog = false
                }
            )
        }

        // Remove Bookmark Confirmation Dialog
        if (showRemoveBookmarkDialog) {
            val existingBookmark = bookmarkManager.findBookmarkForTab(currentTabConfig)
            if (existingBookmark != null) {
                val (collectionId, bookmarkId) = existingBookmark
                RemoveBookmarkConfirmationDialog(
                    bookmarkTitle = currentTitle,
                    onDismiss = { showRemoveBookmarkDialog = false },
                    onConfirm = {
                        bookmarkManager.removeBookmark(collectionId, bookmarkId)
                        showRemoveBookmarkDialog = false
                    }
                )
            } else {
                // Bookmark not found, close dialog
                showRemoveBookmarkDialog = false
            }
        }

        // JavaScript Dialog (BOSS-styled) - shown when JS alert/confirm/prompt fires
        jsDialogState?.let { event ->
            val (title, message) = when (event) {
                is JsDialogNotifier.JsDialogEvent.Alert ->
                    event.title to event.message
                is JsDialogNotifier.JsDialogEvent.Confirm -> {
                    val action = if (event.confirmed) "confirmed" else "cancelled"
                    event.title to "⚠️ BOSS auto-$action this dialog to prevent browser freeze.\n\n${event.message}"
                }
                is JsDialogNotifier.JsDialogEvent.Prompt ->
                    event.title to "⚠️ BOSS auto-accepted this prompt to prevent browser freeze.\n\nPrompt: ${event.message}\nValue used: ${event.value.ifEmpty { "(empty)" }}"
            }
            InfoDialog(
                title = title,
                message = message,
                onDismiss = { jsDialogState = null }
            )
        }
    }
}
