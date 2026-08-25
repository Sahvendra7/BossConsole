package ai.rever.boss.components.settings.sections

import ai.rever.boss.services.llm.LlmProviderAPIAccess
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * AI provider settings delegated to the plugin that owns them (secret-manager).
 *
 * The host used to implement this section itself, including a hardcoded model list
 * that went stale between releases and a key store that wrote plaintext credentials
 * to disk. All of it — provider registry, credentials, environment-variable
 * resolution, and the live model catalogue — now lives in the plugin, which stores
 * keys as encrypted secrets and fetches model lists from the providers themselves.
 *
 * When the plugin is loaded it renders that panel; before its asynchronous startup
 * registration completes, a short notice renders instead and swaps to the real panel
 * automatically (rememberProvider observes API registration).
 */
@Composable
fun LLMProvidersSettings() {
    val provider = LlmProviderAPIAccess.rememberProvider()
    // supportsSettingsPanel distinguishes "no panel" from "blank panel": the API's panel
    // member has a default no-op, so a plugin that registers without overriding it would
    // otherwise render an empty section with no explanation.
    val missingPermissions = LlmProviderAPIAccess.rememberMissingPermissions()
    if (provider != null && provider.supportsSettingsPanel) {
        provider.LlmProviderSettingsPanel(modifier = Modifier.fillMaxSize())
    } else {
        // One notice for every reason there is no panel. It tells the four apart - never
        // installed, switched off, still starting, or inaccessible - and offers to install the
        // plugin in the one case where that is the answer. The permission case is passed in
        // rather than derived there because only this section can ask it.
        PluginSettingsUnavailableNotice(
            what = "AI provider settings",
            pluginName = "Secret Manager",
            pluginId = SECRET_MANAGER_PLUGIN_ID,
            missingPermissions = missingPermissions,
        )
    }
}

/** The plugin that owns AI provider settings. Also the credential vault. */
private const val SECRET_MANAGER_PLUGIN_ID = "ai.rever.boss.plugin.dynamic.secretmanager"
