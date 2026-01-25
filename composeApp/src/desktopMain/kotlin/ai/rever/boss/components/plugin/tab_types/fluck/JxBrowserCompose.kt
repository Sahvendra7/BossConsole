package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.cache.FaviconCache
import ai.rever.boss.components.bookmarks.Bookmark
import ai.rever.boss.components.bookmarks.WorkspacePanelTarget
import ai.rever.boss.components.bookmarks.bookmarkManager
import ai.rever.boss.components.dashboard.Dashboard
import ai.rever.boss.components.dialogs.BookmarkDialog
import ai.rever.boss.components.dialogs.InfoDialog
import ai.rever.boss.components.dialogs.RemoveBookmarkConfirmationDialog
import ai.rever.boss.components.events.DashboardEventBus
import ai.rever.boss.components.events.KeyboardEventBus
import ai.rever.boss.components.events.KeyEventSource
import ai.rever.boss.components.events.KeyboardEvent as BossKeyboardEvent
import ai.rever.boss.keymap.model.ShortcutContext
import ai.rever.boss.components.overlays.ContextMenu
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.components.registery.TabIcon
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.components.workspaces.TabConfig
import ai.rever.boss.components.workspaces.workspaceManager
import ai.rever.boss.config.JxBrowserConfig
import ai.rever.boss.window.LocalWindowId
import ai.rever.boss.window.LocalWindowProjectState
import ai.rever.boss.window.WindowOperations
import ai.rever.boss.window.selectProjectInWindow
import ai.rever.boss.utils.MacOSGestureHandler
import java.awt.Window
import javax.swing.JFrame
import androidx.compose.foundation.LocalContextMenuRepresentation
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.IntOffset
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
import com.teamdev.jxbrowser.browser.callback.ShowContextMenuCallback
import com.teamdev.jxbrowser.browser.event.FaviconChanged
import com.teamdev.jxbrowser.navigation.event.FrameLoadFailed
import com.teamdev.jxbrowser.navigation.event.LoadFinished
import com.teamdev.jxbrowser.navigation.event.LoadStarted
import com.teamdev.jxbrowser.navigation.event.NavigationFinished
import com.teamdev.jxbrowser.view.compose.BrowserView
import com.teamdev.jxbrowser.view.compose.BrowserViewState
import com.teamdev.jxbrowser.zoom.ZoomLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private val jxBrowserComposeLogger = BossLogger.forComponent("JxBrowserCompose")

// Helper function to process URL input - either as URL or search query
private fun processUrlInput(input: String): String {
    val trimmed = input.trim()
    val lowerTrimmed = trimmed.lowercase()

    // If it's already a full URL or special scheme, return as-is (case-insensitive check)
    if (lowerTrimmed.startsWith("http://") || lowerTrimmed.startsWith("https://") ||
        lowerTrimmed.startsWith("file://") || lowerTrimmed.startsWith("javascript:") ||
        lowerTrimmed.startsWith("chrome://")) {
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

    // Hybrid context menu state (Issue #XXX)
    // Position captured from Compose pointer event (window-relative, reliable)
    var lastRightClickPosition by remember { mutableStateOf(IntOffset.Zero) }
    // Context info from JxBrowser ShowContextMenuCallback
    var showContextMenu by remember { mutableStateOf(false) }
    var contextMenuInfo by remember { mutableStateOf<ContextMenuInfo?>(null) }

    // Legacy state (kept for backward compatibility with context menu items)
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
    val windowId = LocalWindowId.current ?: "unknown"

    // Secret integration state
    val secretViewModel = remember { BrowserSecretIntegrationViewModel() }
    var focusedFieldInfo by remember { mutableStateOf<FormFieldDetector.FormFieldInfo?>(null) }
    var showSecretContextMenu by remember { mutableStateOf(false) }

    // Zoom state management
    var currentZoomLevel by remember { mutableStateOf(1.0) }
    val zoomLevels = listOf(0.25, 0.33, 0.50, 0.67, 0.75, 0.80, 0.90, 1.0, 1.10, 1.25, 1.50, 1.75, 2.0, 2.50, 3.0)

    // Zoom control functions with per-domain persistence
    // All browser operations run on IO dispatcher to avoid blocking UI thread (THREADING.md compliance)
    fun performZoomIn() {
        coroutineScope.launch(Dispatchers.IO) {
            if (!browser.isClosed) {
                val current = try { browser.zoom().level().value() } catch (e: Exception) { 1.0 }
                // Epsilon 0.001 for floating-point comparison to find next zoom level above current
                val newLevel = zoomLevels.firstOrNull { it > current + 0.001 } ?: zoomLevels.last()
                browser.zoom().level(ZoomLevel.of(newLevel))

                val domain = BrowserZoomSettingsManager.extractDomain(browser.url())
                domain?.let {
                    BrowserZoomSettingsManager.setZoomForDomain(it, newLevel)
                    BrowserZoomSettingsManager.saveSettings()
                }

                withContext(Dispatchers.Main) {
                    currentZoomLevel = newLevel
                }
            }
        }
    }

    fun performZoomOut() {
        coroutineScope.launch(Dispatchers.IO) {
            if (!browser.isClosed) {
                val current = try { browser.zoom().level().value() } catch (e: Exception) { 1.0 }
                // Epsilon 0.001 for floating-point comparison to find next zoom level below current
                val newLevel = zoomLevels.lastOrNull { it < current - 0.001 } ?: zoomLevels.first()
                browser.zoom().level(ZoomLevel.of(newLevel))

                val domain = BrowserZoomSettingsManager.extractDomain(browser.url())
                domain?.let {
                    BrowserZoomSettingsManager.setZoomForDomain(it, newLevel)
                    BrowserZoomSettingsManager.saveSettings()
                }

                withContext(Dispatchers.Main) {
                    currentZoomLevel = newLevel
                }
            }
        }
    }

    fun performZoomReset() {
        coroutineScope.launch(Dispatchers.IO) {
            if (!browser.isClosed) {
                browser.zoom().level(ZoomLevel.P_100)

                val domain = BrowserZoomSettingsManager.extractDomain(browser.url())
                domain?.let {
                    BrowserZoomSettingsManager.clearDomainZoom(it)
                    BrowserZoomSettingsManager.saveSettings()
                }

                withContext(Dispatchers.Main) {
                    currentZoomLevel = 1.0
                }
            }
        }
    }

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
                // Initialize zoom level from browser
                currentZoomLevel = browser.zoom().level().value()
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

                            // Apply saved zoom for this domain (per-domain zoom persistence)
                            BrowserZoomSettingsManager.extractDomain(newUrl)?.let { domain ->
                                val savedZoom = BrowserZoomSettingsManager.getZoomForDomain(domain)
                                val currentBrowserZoom = browser.zoom().level().value()
                                // Only apply if different from current zoom
                                if (kotlin.math.abs(savedZoom - currentBrowserZoom) > 0.001) {
                                    browser.zoom().level(ZoomLevel.of(savedZoom))
                                    currentZoomLevel = savedZoom
                                } else {
                                    currentZoomLevel = currentBrowserZoom
                                }
                            } ?: run {
                                // No domain extracted, just sync current zoom
                                currentZoomLevel = browser.zoom().level().value()
                            }

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

                    // Inject video click tracker for accurate PiP context menu
                    browser.mainFrame().ifPresent { frame ->
                        frame.executeJavaScript<Unit>(BrowserJavaScripts.injectVideoClickTracker)
                    }

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
                        jxBrowserComposeLogger.warn(LogCategory.BROWSER, "Error converting favicon", error = e)
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

    // Periodic zoom level sync to catch keyboard shortcut changes
    // 2000ms interval balances responsiveness with CPU overhead (blocking RPC calls)
    LaunchedEffect(browser) {
        if (!isBrowserEnvironmentValid()) return@LaunchedEffect

        while (isBrowserEnvironmentValid()) {
            delay(2000)
            if (!isBrowserEnvironmentValid()) break

            try {
                // Run browser RPC call on IO to avoid blocking UI thread
                val browserZoom = withContext(Dispatchers.IO) {
                    browser.zoom().level().value()
                }
                // Epsilon 0.001 accounts for floating-point comparison imprecision
                if (kotlin.math.abs(browserZoom - currentZoomLevel) > 0.001) {
                    currentZoomLevel = browserZoom
                }
            } catch (e: Exception) {
                // Browser might be closed
                break
            }
        }
    }

    // Set up macOS native trackpad pinch gesture handler for zoom
    // Note: MoveMouseWheelCallback removed to fix scroll lag - use Ctrl+Plus/Minus for zoom on Windows/Linux
    LaunchedEffect(browser) {
        if (!isBrowserEnvironmentValid()) return@LaunchedEffect

        if (MacOSGestureHandler.isSupported()) {
            val window = Window.getWindows().firstOrNull { it.isDisplayable && it.isShowing }
            if (window is JFrame) {
                MacOSGestureHandler.addMagnificationListener(
                    component = window.contentPane,
                    onZoomIn = { performZoomIn() },
                    onZoomOut = { performZoomOut() }
                )
            }
        }
    }

    // Register JxBrowser ShowContextMenuCallback for hybrid context menu
    // This callback intercepts right-click events and gathers context info (link URL, selected text, etc.)
    // The position is captured from Compose's pointer event (more reliable than JxBrowser's screen coordinates)
    LaunchedEffect(browser) {
        if (!isBrowserEnvironmentValid()) return@LaunchedEffect

        // Use unsafe() for callback registration (done once during setup)
        browser.unsafe().set(ShowContextMenuCallback::class.java,
            BrowserContextMenuCallback(browser) { info ->
                coroutineScope.launch(Dispatchers.Main) {
                    // Store context info from JxBrowser callback
                    contextMenuInfo = info

                    // Update legacy state variables for context menu items
                    rightClickedLinkUrl = info.linkUrl
                    selectedText = info.selectedText
                    hasVideoAtClick = info.hasVideo

                    // Form field detection for secrets (Issue #56)
                    if (info.isEditable) {
                        focusedFieldInfo = FormFieldDetector.getCurrentFocusedField(browser)
                    } else {
                        focusedFieldInfo = null
                    }

                    // Show context menu using Compose position (captured from pointer event)
                    showContextMenu = true
                }
            }
        )
    }

    // Create context menu items dynamically based on browser state
    val contextMenuItems = remember(canGoBack, canGoForward, hasVideoAtClick, rightClickedLinkUrl, selectedText, focusedFieldInfo, secretViewModel.state) {
        // Issue #56: If form field is focused, show combined menu with edit options + secrets
        if (focusedFieldInfo != null) {
            buildList {
                // Edit operations for text fields
                add(ContextMenuItem(
                    text = "Copy",
                    icon = Icons.Default.ContentCopy,
                    onClick = {
                        if (isBrowserEnvironmentValid()) {
                            browser.mainFrame().ifPresent { frame ->
                                frame.executeJavaScript<Unit>("document.execCommand('copy')")
                            }
                        }
                    }
                ))

                add(ContextMenuItem(
                    text = "Paste",
                    icon = Icons.Default.ContentPaste,
                    onClick = {
                        if (isBrowserEnvironmentValid()) {
                            try {
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                val clipboardText = clipboard.getData(java.awt.datatransfer.DataFlavor.stringFlavor) as? String
                                if (!clipboardText.isNullOrEmpty()) {
                                    browser.mainFrame().ifPresent { frame ->
                                        // Insert text at cursor position in the focused field
                                        val escapedText = clipboardText
                                            .replace("\\", "\\\\")
                                            .replace("'", "\\'")
                                            .replace("\n", "\\n")
                                            .replace("\r", "")
                                        frame.executeJavaScript<Unit>("""
                                            (function() {
                                                var el = document.activeElement;
                                                if (el && (el.tagName === 'INPUT' || el.tagName === 'TEXTAREA' || el.isContentEditable)) {
                                                    if (el.isContentEditable) {
                                                        document.execCommand('insertText', false, '$escapedText');
                                                    } else {
                                                        var start = el.selectionStart || 0;
                                                        var end = el.selectionEnd || 0;
                                                        var value = el.value || '';
                                                        el.value = value.substring(0, start) + '$escapedText' + value.substring(end);
                                                        el.selectionStart = el.selectionEnd = start + '$escapedText'.length;
                                                        el.dispatchEvent(new Event('input', { bubbles: true }));
                                                    }
                                                }
                                            })()
                                        """.trimIndent())
                                    }
                                }
                            } catch (e: Exception) {
                                // Paste operation failed
                            }
                        }
                    }
                ))

                add(ContextMenuItem(isDivider = true))

                // Secret menu items
                try {
                    val currentUrl = if (isBrowserEnvironmentValid()) {
                        try { browser.url() } catch (e: Exception) { "" }
                    } else { "" }

                    val secretItems = SecretContextMenuBuilder.buildSecretMenu(
                        browser = browser,
                        fieldInfo = focusedFieldInfo!!,
                        currentUrl = currentUrl,
                        allSecrets = secretViewModel.state.allSecrets,
                        coroutineScope = coroutineScope,
                        onShowAllSecrets = { secretViewModel.showAllSecretsDialog() },
                        onAddNewSecret = { websitePrefill -> secretViewModel.showQuickCreateDialog(websitePrefill) },
                        onDismiss = { focusedFieldInfo = null }
                    )
                    addAll(secretItems)
                } catch (e: Exception) {
                    // If secret menu fails, just skip it
                }

                add(ContextMenuItem(isDivider = true))

                // Reload
                add(ContextMenuItem(
                    text = "Reload",
                    icon = Icons.Default.Refresh,
                    onClick = {
                        if (isBrowserEnvironmentValid()) {
                            try { browser.navigation().reload() } catch (e: Exception) { }
                        }
                    }
                ))

                // Copy Page URL
                add(ContextMenuItem(
                    text = "Copy Page URL",
                    icon = Icons.Outlined.ContentCopy,
                    onClick = {
                        if (isBrowserEnvironmentValid()) {
                            try {
                                val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                                clipboard.setContents(StringSelection(browser.url()), null)
                            } catch (e: Exception) { }
                        }
                    }
                ))

                // Developer tools
                add(ContextMenuItem(
                    text = "Inspect Element",
                    icon = Icons.Outlined.Code,
                    onClick = { if (isBrowserEnvironmentValid()) browser.devTools().show() }
                ))
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

                // Search selected text in new tab (Issue #286)
                // Uses Google as default search provider for consistency with processUrlInput()
                add(ContextMenuItem(
                    text = "Search with Google",
                    icon = Icons.Default.Search,
                    onClick = {
                        if (isBrowserEnvironmentValid()) {
                            coroutineScope.launch(Dispatchers.Main) {
                                selectedText?.let { text ->
                                    try {
                                        val encodedQuery = URLEncoder.encode(text, StandardCharsets.UTF_8)
                                        val searchUrl = "https://www.google.com/search?q=$encodedQuery"
                                        jxBrowserComposeLogger.info(
                                            LogCategory.BROWSER,
                                            "Opening Google search for selected text",
                                            mapOf("query_length" to text.length)
                                        )
                                        onOpenInNewTab(searchUrl)
                                    } catch (e: Exception) {
                                        // Issue #286: Search operation failed
                                        jxBrowserComposeLogger.error(
                                            LogCategory.BROWSER,
                                            "Failed to open Google search",
                                            error = e
                                        )
                                    }
                                }
                            }
                        }
                    }
                ))
            }

            // Copy URL - copies link URL if on a link, otherwise copies page URL
            if (!rightClickedLinkUrl.isNullOrEmpty()) {
                // Right-clicked on a link - copy link URL
                add(ContextMenuItem(
                    text = "Copy Link URL",
                    icon = Icons.Outlined.ContentCopy,
                    onClick = {
                        try {
                            val clipboard = Toolkit.getDefaultToolkit().systemClipboard
                            clipboard.setContents(StringSelection(rightClickedLinkUrl), null)
                        } catch (e: Exception) {
                            // Copy operation failed
                        }
                    }
                ))

                // Open link in new tab
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
            } else {
                // Not on a link - copy current page URL
                add(ContextMenuItem(
                    text = "Copy Page URL",
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
    
    // Use Swing-based context menu for text fields in HARDWARE_ACCELERATED mode (#258)
    CompositionLocalProvider(
        LocalContextMenuRepresentation provides SwingTextContextMenuRepresentation
    ) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
        // Navigation Bar
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 1.dp,
            shape = RectangleShape
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
                                    keyEvent.type == KeyEventType.KeyDown && keyEvent.key == Key.DirectionRight -> {
                                        // Accept inline autocomplete when cursor is at the end of input
                                        if (autocompleteSuggestion != null &&
                                            urlInput.selection.collapsed &&
                                            urlInput.selection.start == urlInput.text.length) {
                                            urlInput = TextFieldValue(
                                                autocompleteSuggestion!!,
                                                TextRange(autocompleteSuggestion!!.length)
                                            )
                                            autocompleteSuggestion = null
                                            showDropdown = false
                                            selectedDropdownIndex = -1
                                            true
                                        } else {
                                            false // Let text field handle normal cursor movement
                                        }
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
                                                    context = ShortcutContext.BROWSER,
                                                    sourceWindowId = windowId
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
                                    .background(MaterialTheme.colors.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
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

                                // Zoom level indicator (only shown when not at 100%)
                                if (kotlin.math.abs(currentZoomLevel - 1.0) > 0.001) {
                                    Text(
                                        text = "${(currentZoomLevel * 100).toInt()}%",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                        modifier = Modifier
                                            .padding(start = 4.dp)
                                            .clickable { performZoomReset() }
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
            val windowId = LocalWindowId.current
            val windowProjectState = LocalWindowProjectState.current
            // Per-window project state (required for multi-window support)
            val selectedProject by windowProjectState?.selectedProject?.collectAsState()
                ?: remember { mutableStateOf(ai.rever.boss.components.plugin.panels.left_top.Project("No Project", "", 0L)) }

            Dashboard(
                onOpenFile = { path ->
                    windowId?.let { wid ->
                        coroutineScope.launch { DashboardEventBus.openFile(path, sourceWindowId = wid) }
                    }
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
                    windowId?.let { wid ->
                        coroutineScope.launch { DashboardEventBus.newTab(sourceWindowId = wid) }
                    }
                },
                onNewTerminal = {
                    windowId?.let { wid ->
                        coroutineScope.launch { DashboardEventBus.newTerminal(sourceWindowId = wid) }
                    }
                },
                onNewWindow = {
                    WindowOperations.createNewWindow()
                },
                onOpenProjectDialog = {
                    windowId?.let { wid ->
                        coroutineScope.launch { DashboardEventBus.showProjectDialog(sourceWindowId = wid) }
                    }
                },
                onOpenFileDialog = {
                    windowId?.let { wid ->
                        coroutineScope.launch { DashboardEventBus.showFileDialog(sourceWindowId = wid) }
                    }
                },
                onNewProject = {
                    windowId?.let { wid ->
                        coroutineScope.launch { DashboardEventBus.showNewProject(sourceWindowId = wid) }
                    }
                },
                onApplySplitTemplate = { template ->
                    windowId?.let { wid ->
                        coroutineScope.launch { DashboardEventBus.applySplitTemplate(template, sourceWindowId = wid) }
                    }
                },
                onActivatePlugin = { pluginId ->
                    windowId?.let { wid ->
                        coroutineScope.launch { DashboardEventBus.activatePlugin(pluginId, sourceWindowId = wid) }
                    }
                }
            )
        } else {
            // Browser content using native Compose BrowserView with custom context menu
            // Note: Pinch-to-zoom gestures are handled via JxBrowser's MoveMouseWheelCallback
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

                        // Handle right-click - capture position only (hybrid context menu approach)
                        // JxBrowser's ShowContextMenuCallback handles gathering context info
                        if (event.button == PointerButton.Secondary) {
                            val change = event.changes.firstOrNull()
                            if (change != null) {
                                // Store Compose position (window-relative, reliable)
                                lastRightClickPosition = IntOffset(
                                    change.position.x.toInt(),
                                    change.position.y.toInt()
                                )
                                // Don't consume - let JxBrowser callback handle context info
                            }
                        }
                    }
            )
        }
        }

        // Context menu (hybrid approach - position from Compose, context from JxBrowser callback)
        // Uses Swing JPopupMenu for HARDWARE_ACCELERATED mode compatibility (#258)
        LaunchedEffect(showContextMenu) {
            if (showContextMenu) {
                // Use actual mouse position - most reliable for context menus
                val mouseLocation = java.awt.MouseInfo.getPointerInfo()?.location
                if (mouseLocation != null) {
                    SwingContextMenu.show(
                        screenX = mouseLocation.x,
                        screenY = mouseLocation.y,
                        items = contextMenuItems,
                        onDismiss = {
                            showContextMenu = false
                            contextMenuInfo = null
                            focusedFieldInfo = null
                        }
                    )
                }
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
                            jxBrowserComposeLogger.debug(LogCategory.GENERAL, "Creating secret", mapOf("website" to request.website))
                            val result = ai.rever.boss.services.supabase.SecretService.createSecret(request)
                            result.fold(
                                onSuccess = {
                                    jxBrowserComposeLogger.info(LogCategory.GENERAL, "Secret created successfully", mapOf("website" to request.website))

                                    // Reload secrets and wait for completion
                                    secretViewModel.reloadSecrets()

                                    // Notify other components about the new secret
                                    SecretChangeNotifier.notifyRefresh()

                                    secretViewModel.hideQuickCreateDialog()
                                    isCreating = false

                                    jxBrowserComposeLogger.debug(LogCategory.GENERAL, "Secret added and list refreshed", mapOf("totalSecrets" to secretViewModel.state.allSecrets.size))
                                },
                                onFailure = { error ->
                                    jxBrowserComposeLogger.warn(LogCategory.GENERAL, "Failed to create secret", mapOf("error" to (error.message ?: "unknown")))
                                    isCreating = false
                                }
                            )
                        } catch (e: Exception) {
                            jxBrowserComposeLogger.error(LogCategory.GENERAL, "Exception creating secret", error = e)
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
    } // CompositionLocalProvider
}
