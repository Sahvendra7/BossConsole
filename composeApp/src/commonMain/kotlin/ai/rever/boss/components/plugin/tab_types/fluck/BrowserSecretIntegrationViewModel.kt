package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.services.supabase.SecretService
import ai.rever.boss.services.supabase.models.SecretEntry
import ai.rever.boss.utils.WebsiteMatchingUtil
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * ViewModel for browser-secret integration state management.
 *
 * Manages:
 * - Loading secrets from SecretService
 * - Matching secrets to current website domain
 * - UI state for context menu and dialogs
 * - Secret selection and filling actions
 * - Automatic reload when secrets change in other components
 *
 * Lifecycle management:
 * - Uses SupervisorJob to prevent child coroutine failures from cancelling the entire scope
 * - Observes SecretChangeNotifier for automatic synchronization
 * - Call dispose() when the ViewModel is no longer needed
 *
 * Used by Issue #56 - Secret Access Integration with Fluck Browser
 */
class BrowserSecretIntegrationViewModel {

    /**
     * Current integration state
     */
    var state by mutableStateOf(BrowserSecretState())
        private set

    private val coroutineScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    /**
     * Initialize and load all secrets for the user.
     *
     * Should be called once when browser tab is created or user logs in.
     * Also starts observing secret change events for automatic synchronization.
     */
    fun initialize() {
        coroutineScope.launch {
            loadAllSecrets()
        }

        // Observe secret change events for automatic synchronization
        coroutineScope.launch {
            SecretChangeNotifier.secretChangeEvents.collect { event ->
                println("🔔 [BrowserSecretIntegrationVM] Received event: $event")
                // Reload secrets whenever they change in other components
                loadAllSecrets()
            }
        }
    }

    /**
     * Dispose the ViewModel and cancel all coroutines.
     * Should be called when the browser tab is closed.
     */
    fun dispose() {
        coroutineScope.cancel()
        println("🧹 [BrowserSecretIntegrationVM] Disposed and cancelled all coroutines")
    }

    /**
     * Load all secrets from SecretService.
     */
    private suspend fun loadAllSecrets() {
        state = state.copy(isLoadingSecrets = true)

        try {
            val result = SecretService.getUserSecretsWithShared(limit = 1000, offset = 0)

            result.fold(
                onSuccess = { paginatedSecrets ->
                    state = state.copy(
                        allSecrets = paginatedSecrets.data,
                        isLoadingSecrets = false,
                        error = null
                    )

                    println("✅ [BrowserSecretIntegrationVM] Loaded ${paginatedSecrets.data.size} secrets")

                    // Re-match if we have a current URL
                    if (state.currentDomain != null) {
                        updateMatchedSecrets(state.currentDomain!!)
                    }
                },
                onFailure = { error ->
                    state = state.copy(
                        isLoadingSecrets = false,
                        error = "Failed to load secrets: ${error.message}"
                    )
                    println("❌ [BrowserSecretIntegrationVM] Failed to load secrets: ${error.message}")
                }
            )
        } catch (e: Exception) {
            state = state.copy(
                isLoadingSecrets = false,
                error = "Exception loading secrets: ${e.message}"
            )
            println("❌ [BrowserSecretIntegrationVM] Exception: ${e.message}")
        }
    }

    /**
     * Update current URL and match secrets.
     *
     * Called when browser navigates to a new page.
     *
     * @param url New page URL
     */
    fun onUrlChanged(url: String) {
        val domain = WebsiteMatchingUtil.extractMainDomain(url)

        state = state.copy(
            currentUrl = url,
            currentDomain = domain
        )

        // Match secrets for new domain
        if (domain != null) {
            updateMatchedSecrets(domain)
        } else {
            state = state.copy(matchingSecrets = emptyList())
        }
    }

    /**
     * Update matched secrets for current domain.
     */
    private fun updateMatchedSecrets(domain: String) {
        val matched = WebsiteMatchingUtil.matchSecretsForDomain(
            domain = domain,
            secrets = state.allSecrets,
            maxResults = 10
        )

        state = state.copy(
            matchingSecrets = matched.map { it.secret }
        )

        println("🔍 [BrowserSecretIntegrationVM] Matched ${matched.size} secrets for: $domain")
    }

    /**
     * Show secret context menu.
     *
     * Called when user right-clicks on a form field.
     */
    fun showSecretMenu() {
        state = state.copy(showSecretMenu = true)
    }

    /**
     * Hide secret context menu.
     */
    fun hideSecretMenu() {
        state = state.copy(showSecretMenu = false)
    }

    /**
     * Show all secrets dialog.
     *
     * Called when user clicks "Show All Secrets..." in context menu.
     */
    fun showAllSecretsDialog() {
        state = state.copy(
            showAllSecretsDialog = true,
            showSecretMenu = false
        )
    }

    /**
     * Hide all secrets dialog.
     */
    fun hideAllSecretsDialog() {
        state = state.copy(showAllSecretsDialog = false)
    }

    /**
     * Show quick secret creation dialog.
     *
     * Called when user clicks "Add New Secret" in context menu.
     *
     * @param websitePrefill Domain to pre-fill in the dialog
     */
    fun showQuickCreateDialog(websitePrefill: String) {
        state = state.copy(
            showQuickCreateDialog = true,
            quickCreateWebsitePrefill = websitePrefill,
            showSecretMenu = false
        )
    }

    /**
     * Hide quick secret creation dialog.
     */
    fun hideQuickCreateDialog() {
        state = state.copy(
            showQuickCreateDialog = false,
            quickCreateWebsitePrefill = null
        )
    }

    /**
     * Reload secrets after a new secret is created or modified.
     */
    suspend fun reloadSecrets() {
        println("🔄 [BrowserSecretIntegrationVM] Reloading secrets...")
        loadAllSecrets()
        println("✅ [BrowserSecretIntegrationVM] Reload complete, now have ${state.allSecrets.size} secrets")
    }

    /**
     * Search secrets by query.
     *
     * Used in the "Show All Secrets" dialog for filtering.
     *
     * @param query Search query
     */
    fun searchSecrets(query: String) {
        if (query.isBlank()) {
            // Reset to all secrets
            state = state.copy(filteredSecrets = state.allSecrets)
            return
        }

        val lowerQuery = query.lowercase().trim()

        val filtered = state.allSecrets.filter { secret ->
            secret.website.lowercase().contains(lowerQuery) ||
            secret.username.lowercase().contains(lowerQuery) ||
            secret.notes?.lowercase()?.contains(lowerQuery) == true ||
            secret.tags.any { it.lowercase().contains(lowerQuery) }
        }

        state = state.copy(filteredSecrets = filtered)
    }

    /**
     * Clear any error messages.
     */
    fun clearError() {
        state = state.copy(error = null)
    }
}

/**
 * State for browser-secret integration.
 */
data class BrowserSecretState(
    // Current page info
    val currentUrl: String = "",
    val currentDomain: String? = null,

    // Secrets data
    val allSecrets: List<SecretEntry> = emptyList(),
    val matchingSecrets: List<SecretEntry> = emptyList(),
    val filteredSecrets: List<SecretEntry> = emptyList(),

    // UI state
    val isLoadingSecrets: Boolean = false,
    val showSecretMenu: Boolean = false,
    val showAllSecretsDialog: Boolean = false,
    val showQuickCreateDialog: Boolean = false,

    // Quick create prefill
    val quickCreateWebsitePrefill: String? = null,

    // Error state
    val error: String? = null
)
