package ai.rever.boss.components.plugin.tab_types.fluck

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.services.supabase.models.SecretEntry
import ai.rever.boss.utils.WebsiteMatchingUtil
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Builds context menu for secret auto-fill integration.
 *
 * Creates a hierarchical menu structure:
 * - Top matched secrets (up to 5)
 * - Submenu for each secret (fill username, password, or both)
 * - "Show All Secrets" option
 * - "Add New Secret" quick create option
 *
 * Used by Issue #56 - Secret Access Integration with Fluck Browser
 */
object SecretContextMenuBuilder {
    private val logger = BossLogger.forComponent("SecretContextMenuBuilder")

    /**
     * Build secret context menu for a focused form field.
     *
     * @param browser LockedBrowser instance (thread-safe wrapper) for field injection
     * @param fieldInfo Information about the focused field
     * @param currentUrl Current page URL
     * @param allSecrets All available secrets for matching
     * @param coroutineScope Scope for launching async operations
     * @param onShowAllSecrets Callback to show full secret selection dialog
     * @param onAddNewSecret Callback to show quick secret creation dialog
     * @param onDismiss Callback when menu is dismissed
     * @return List of context menu items
     */
    fun buildSecretMenu(
        browser: LockedBrowser,
        fieldInfo: FormFieldDetector.FormFieldInfo,
        currentUrl: String,
        allSecrets: List<SecretEntry>,
        coroutineScope: CoroutineScope,
        onShowAllSecrets: () -> Unit,
        onAddNewSecret: (websitePrefill: String) -> Unit,
        onDismiss: () -> Unit
    ): List<ContextMenuItem> {
        val menuItems = mutableListOf<ContextMenuItem>()

        // Extract domain for matching
        val domain = WebsiteMatchingUtil.extractMainDomain(currentUrl)

        if (domain == null) {
            // No valid domain - show generic options
            return buildFallbackMenu(onShowAllSecrets, onAddNewSecret)
        }

        // Match secrets for current domain
        val matchedSecrets = WebsiteMatchingUtil.matchSecretsForDomain(
            domain = domain,
            secrets = allSecrets,
            maxResults = 5
        )

        // Header
        menuItems.add(
            ContextMenuItem(
                text = "🔑 Fill Credential",
                icon = Icons.Default.Lock,
                onClick = {}  // Header, non-clickable
            )
        )

        if (matchedSecrets.isNotEmpty()) {
            menuItems.add(ContextMenuItem(isDivider = true))

            // Add matched secrets
            matchedSecrets.forEach { matchedSecret ->
                val secret = matchedSecret.secret
                val displayName = WebsiteMatchingUtil.getDisplayName(secret.website)
                val usernamePreview = if (secret.username.length > 25) {
                    secret.username.take(22) + "..."
                } else {
                    secret.username
                }

                // Main secret item (fills both username and password)
                menuItems.add(
                    ContextMenuItem(
                        text = "$displayName ($usernamePreview)",
                        icon = getIconForWebsite(secret.website),
                        onClick = {
                            coroutineScope.launch {
                                fillCredentials(
                                    browser = browser,
                                    secret = secret,
                                    mode = FormFieldInjector.FillMode.BOTH,
                                    onDismiss = onDismiss
                                )
                            }
                        }
                    )
                )

                // TODO: Add submenu for advanced options (fill username only, password only, copy)
                // This requires extending ContextMenuItem to support submenus
            }
        } else {
            // No matches found
            menuItems.add(
                ContextMenuItem(
                    text = "No matching secrets for $domain",
                    icon = Icons.Default.Info,
                    onClick = {}  // Informational, non-clickable
                )
            )
        }

        // Divider before actions
        menuItems.add(ContextMenuItem(isDivider = true))

        // "Show All Secrets" option
        menuItems.add(
            ContextMenuItem(
                text = "Show All Secrets...",
                icon = Icons.AutoMirrored.Filled.List,
                onClick = {
                    onShowAllSecrets()
                    onDismiss()
                }
            )
        )

        // "Add New Secret" option (with domain pre-filled)
        menuItems.add(
            ContextMenuItem(
                text = "Add New Secret",
                icon = Icons.Default.Add,
                onClick = {
                    onAddNewSecret(domain)
                    onDismiss()
                }
            )
        )

        return menuItems
    }

    /**
     * Build fallback menu when domain cannot be determined.
     */
    private fun buildFallbackMenu(
        onShowAllSecrets: () -> Unit,
        onAddNewSecret: (String) -> Unit
    ): List<ContextMenuItem> {
        return listOf(
            ContextMenuItem(
                text = "🔑 Secrets",
                icon = Icons.Default.Lock,
                onClick = {}
            ),
            ContextMenuItem(isDivider = true),
            ContextMenuItem(
                text = "Show All Secrets...",
                icon = Icons.AutoMirrored.Filled.List,
                onClick = onShowAllSecrets
            ),
            ContextMenuItem(
                text = "Add New Secret",
                icon = Icons.Default.Add,
                onClick = { onAddNewSecret("") }
            )
        )
    }

    /**
     * Fill credentials using FormFieldInjector.
     *
     * @param browser LockedBrowser instance (thread-safe wrapper)
     * @param secret Secret to fill
     * @param mode Fill mode (both, username only, password only)
     * @param onDismiss Callback to dismiss menu after filling
     */
    private suspend fun fillCredentials(
        browser: LockedBrowser,
        secret: SecretEntry,
        mode: FormFieldInjector.FillMode,
        onDismiss: () -> Unit
    ) {
        try {
            logger.debug(LogCategory.GENERAL, "Filling credentials", mapOf("website" to secret.website))

            val result = FormFieldInjector.fillCredentials(
                browser = browser,
                username = secret.username,
                password = secret.password,
                mode = mode
            )

            when (result) {
                is FormFieldInjector.FillResult.Success -> {
                    logger.info(LogCategory.GENERAL, "Credentials filled successfully", mapOf("message" to result.message))
                    onDismiss()
                }
                is FormFieldInjector.FillResult.PartialSuccess -> {
                    logger.warn(LogCategory.GENERAL, "Credential fill partial", mapOf("message" to result.message))
                    onDismiss()
                }
                is FormFieldInjector.FillResult.Error -> {
                    logger.warn(LogCategory.GENERAL, "Credential fill failed", mapOf("message" to result.message))
                    // Keep menu open on error so user can try another secret
                }
            }
        } catch (e: Exception) {
            logger.error(LogCategory.GENERAL, "Exception filling credentials", error = e)
        }
    }

    /**
     * Get appropriate icon for a website.
     *
     * Returns custom icons for popular websites, generic icon otherwise.
     *
     * @param website Website domain
     * @return ImageVector icon
     */
    private fun getIconForWebsite(website: String): ImageVector {
        val lowerWebsite = website.lowercase()

        return when {
            lowerWebsite.contains("google") -> Icons.Default.Email  // Gmail-like
            lowerWebsite.contains("github") -> Icons.Default.Code
            lowerWebsite.contains("facebook") -> Icons.Default.AccountCircle
            lowerWebsite.contains("twitter") || lowerWebsite.contains("x.com") -> Icons.AutoMirrored.Filled.Send
            lowerWebsite.contains("linkedin") -> Icons.Default.Work
            lowerWebsite.contains("microsoft") || lowerWebsite.contains("office") -> Icons.Default.Business
            lowerWebsite.contains("apple") -> Icons.Default.PhoneIphone
            lowerWebsite.contains("amazon") -> Icons.Default.ShoppingCart
            lowerWebsite.contains("netflix") -> Icons.Default.PlayArrow
            lowerWebsite.contains("spotify") -> Icons.Default.MusicNote
            lowerWebsite.contains("youtube") -> Icons.Default.VideoLibrary
            lowerWebsite.contains("bank") || lowerWebsite.contains("payment") -> Icons.Default.AccountBalance
            else -> Icons.Default.Language  // Generic website icon
        }
    }

    /**
     * Build advanced submenu for a secret (future enhancement).
     *
     * This would show options like:
     * - Fill Username Only
     * - Fill Password Only
     * - Fill Both (Recommended)
     * - Copy Username
     * - Copy Password
     *
     * Currently not implemented as ContextMenuItem doesn't support submenus yet.
     */
    private fun buildSecretSubmenu(
        browser: LockedBrowser,
        secret: SecretEntry,
        coroutineScope: CoroutineScope,
        onDismiss: () -> Unit
    ): List<ContextMenuItem> {
        return listOf(
            ContextMenuItem(
                text = "Fill Username Only",
                icon = Icons.Default.Person,
                onClick = {
                    coroutineScope.launch {
                        fillCredentials(browser, secret, FormFieldInjector.FillMode.USERNAME_ONLY, onDismiss)
                    }
                }
            ),
            ContextMenuItem(
                text = "Fill Password Only",
                icon = Icons.Default.Lock,
                onClick = {
                    coroutineScope.launch {
                        fillCredentials(browser, secret, FormFieldInjector.FillMode.PASSWORD_ONLY, onDismiss)
                    }
                }
            ),
            ContextMenuItem(
                text = "Fill Both (Recommended)",
                icon = Icons.Default.CheckCircle,
                onClick = {
                    coroutineScope.launch {
                        fillCredentials(browser, secret, FormFieldInjector.FillMode.BOTH, onDismiss)
                    }
                }
            ),
            ContextMenuItem(isDivider = true),
            ContextMenuItem(
                text = "Copy Username",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    coroutineScope.launch {
                        fillCredentials(browser, secret, FormFieldInjector.FillMode.COPY_USERNAME, onDismiss)
                    }
                }
            ),
            ContextMenuItem(
                text = "Copy Password",
                icon = Icons.Default.ContentCopy,
                onClick = {
                    coroutineScope.launch {
                        fillCredentials(browser, secret, FormFieldInjector.FillMode.COPY_PASSWORD, onDismiss)
                    }
                }
            )
        )
    }
}
