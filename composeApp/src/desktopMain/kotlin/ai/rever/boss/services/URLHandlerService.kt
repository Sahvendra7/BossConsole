package ai.rever.boss.services

import ai.rever.boss.components.events.URLEventBus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service for handling incoming URLs from the operating system
 *
 * When BOSS is set as the default browser, the OS passes http/https URLs
 * to this service, which creates Fluck browser tabs to display them.
 *
 * URL Flow:
 * 1. User clicks http/https link in another app
 * 2. OS passes URL to BOSS via registered protocol handler
 * 3. DeepLinkHandler receives URL
 * 4. URLHandlerService validates and processes URL
 * 5. New Fluck tab created with URL
 * 6. Tab displayed in active window (or new window if none exist)
 */
object URLHandlerService {

    // Queue for URLs received before the app is ready
    private val urlQueue = mutableListOf<String>()

    // Flag to track if the app is ready to handle URLs
    @Volatile
    private var isAppReady = false

    /**
     * Mark the app as ready to handle URLs and process any queued URLs
     */
    fun markAppReady() {
        isAppReady = true
        processQueuedURLs()
    }

    /**
     * Process all URLs that were queued while app was initializing
     */
    private fun processQueuedURLs() {
        if (urlQueue.isEmpty()) return

        println("URLHandlerService: Processing ${urlQueue.size} queued URL(s)")
        val urls = urlQueue.toList()
        urlQueue.clear()

        urls.forEach { url ->
            handleURLInternal(url)
        }
    }

    /**
     * Handle an incoming URL from the operating system
     *
     * Creates a new Fluck browser tab with the URL and adds it to
     * an existing window, or creates a new window if needed.
     *
     * If the app is not ready yet, queues the URL for later processing.
     *
     * @param url The http/https URL to open
     */
    fun handleURL(url: String) {
        if (!isAppReady) {
            println("URLHandlerService: App not ready, queueing URL: $url")
            urlQueue.add(url)
            return
        }

        handleURLInternal(url)
    }

    /**
     * Internal URL handler that does the actual processing
     *
     * Validates the URL and emits an event through URLEventBus.
     * All windows listen to this event, and the active window will create a tab.
     */
    private fun handleURLInternal(url: String) {
        try {
            println("URLHandlerService: Received URL: $url")

            // Validate URL
            if (!isValidURL(url)) {
                println("URLHandlerService: Invalid URL: $url")
                return
            }

            // Extract domain for tab title
            val title = extractDomain(url) ?: "Loading..."

            // Emit URL open event - all windows will receive it and the active window handles it
            CoroutineScope(Dispatchers.Main).launch {
                URLEventBus.openURL(url, title)
                println("URLHandlerService: Emitted URL open event for $url")
            }
        } catch (e: Exception) {
            println("URLHandlerService: Error handling URL: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Validate that a URL is acceptable for opening
     *
     * Only allows http:// and https:// URLs for security.
     *
     * @param url The URL to validate
     * @return true if the URL is valid and should be opened
     */
    private fun isValidURL(url: String): Boolean {
        // Only allow http and https URLs
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return false
        }

        // Basic URL validation - must have at least a protocol and domain
        try {
            // Simple validation: check for protocol and domain separator
            val protocolEnd = url.indexOf("://")
            if (protocolEnd < 0) return false

            val afterProtocol = url.substring(protocolEnd + 3)
            if (afterProtocol.isEmpty()) return false

            // Must have at least a domain name
            val domainEnd = afterProtocol.indexOfAny(charArrayOf('/', '?', '#'))
            val domain = if (domainEnd >= 0) {
                afterProtocol.substring(0, domainEnd)
            } else {
                afterProtocol
            }

            // Domain must not be empty and should contain at least one character
            return domain.isNotEmpty() && domain.contains(".")
        } catch (e: Exception) {
            println("URLHandlerService: URL validation error: ${e.message}")
            return false
        }
    }

    /**
     * Extract domain name from URL for display as tab title
     *
     * Examples:
     * - "https://www.example.com/path" -> "example.com"
     * - "http://github.com/user/repo" -> "github.com"
     *
     * @param url The URL to extract domain from
     * @return The domain name, or null if extraction fails
     */
    private fun extractDomain(url: String): String? {
        return try {
            val protocolEnd = url.indexOf("://")
            if (protocolEnd < 0) return null

            val afterProtocol = url.substring(protocolEnd + 3)
            val domainEnd = afterProtocol.indexOfAny(charArrayOf('/', '?', '#'))

            val fullDomain = if (domainEnd >= 0) {
                afterProtocol.substring(0, domainEnd)
            } else {
                afterProtocol
            }

            // Remove port if present
            val domain = fullDomain.substringBefore(':')

            // Remove "www." prefix for cleaner display
            val cleanDomain = if (domain.startsWith("www.")) {
                domain.substring(4)
            } else {
                domain
            }

            cleanDomain.ifEmpty { null }
        } catch (e: Exception) {
            println("URLHandlerService: Domain extraction error: ${e.message}")
            null
        }
    }

    /**
     * Handle multiple URLs at once
     *
     * Useful if the OS passes multiple URLs to open simultaneously.
     *
     * @param urls List of URLs to open
     */
    fun handleURLs(urls: List<String>) {
        urls.forEach { url ->
            handleURL(url)
        }
    }
}
