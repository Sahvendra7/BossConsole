package ai.rever.boss.services

/**
 * URLHandlerService - Platform-specific URL handling
 *
 * Service for handling incoming URLs from the operating system.
 * When BOSS is set as the default browser, the OS passes http/https URLs
 * to this service, which creates Fluck browser tabs to display them.
 */
expect object URLHandlerService {
    /**
     * Mark the app as ready to handle URLs and process any queued URLs
     */
    fun markAppReady()

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
    fun handleURL(url: String)

    /**
     * Handle multiple URLs at once
     *
     * Useful if the OS passes multiple URLs to open simultaneously.
     *
     * @param urls List of URLs to open
     */
    fun handleURLs(urls: List<String>)
}
