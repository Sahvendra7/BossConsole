package ai.rever.boss.plugin.browser

import androidx.compose.runtime.Composable

/**
 * Handle to a browser instance created by [BrowserService].
 *
 * This interface provides control over the browser lifecycle and
 * content, without exposing JxBrowser types.
 *
 * ## Lifecycle
 *
 * Browser handles should be disposed when no longer needed to free
 * resources. Failure to dispose may lead to memory leaks.
 *
 * ## Thread Safety
 *
 * All methods are thread-safe and can be called from any thread.
 * The [Content] composable must be called from the main thread.
 */
interface BrowserHandle {
    /**
     * Unique identifier for this browser handle.
     */
    val id: String

    /**
     * Whether this browser handle is still valid.
     *
     * Returns false if:
     * - The browser has been disposed
     * - The browser was closed externally
     * - The browser engine was reinitialized
     */
    val isValid: Boolean

    /**
     * Load a URL in the browser.
     *
     * @param url The URL to load
     */
    suspend fun loadUrl(url: String)

    /**
     * Get the current URL.
     *
     * @return The current URL, or empty string if invalid
     */
    fun getCurrentUrl(): String

    /**
     * Get the current page title.
     *
     * @return The current title, or empty string if invalid
     */
    fun getTitle(): String

    /**
     * Add a listener for navigation events.
     *
     * Called when the browser navigates to a new URL.
     *
     * @param listener Callback receiving the new URL
     */
    fun addNavigationListener(listener: (String) -> Unit)

    /**
     * Remove a navigation listener.
     */
    fun removeNavigationListener(listener: (String) -> Unit)

    /**
     * Add a listener for title changes.
     *
     * @param listener Callback receiving the new title
     */
    fun addTitleListener(listener: (String) -> Unit)

    /**
     * Remove a title listener.
     */
    fun removeTitleListener(listener: (String) -> Unit)

    /**
     * Add a listener for favicon changes.
     *
     * @param listener Callback receiving the favicon URL (or null)
     */
    fun addFaviconListener(listener: (String?) -> Unit)

    /**
     * Remove a favicon listener.
     */
    fun removeFaviconListener(listener: (String?) -> Unit)

    /**
     * Navigate back in history.
     */
    fun goBack()

    /**
     * Navigate forward in history.
     */
    fun goForward()

    /**
     * Reload the current page.
     */
    fun reload()

    /**
     * Check if back navigation is possible.
     */
    fun canGoBack(): Boolean

    /**
     * Check if forward navigation is possible.
     */
    fun canGoForward(): Boolean

    /**
     * Composable content that renders the browser.
     *
     * This should be called within a Compose hierarchy to display
     * the browser content. The browser will fill the available space.
     */
    @Composable
    fun Content()

    /**
     * Dispose this browser handle and release resources.
     *
     * After calling this, [isValid] will return false and
     * all other methods will be no-ops.
     */
    fun dispose()
}
