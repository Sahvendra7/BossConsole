package ai.rever.boss.components.settings.sections

import ai.rever.boss.components.plugin.DynamicPluginManager
import ai.rever.boss.components.plugin.MissingPluginOffer
import ai.rever.boss.plugin.api.PluginState
import ai.rever.boss.plugin.ui.BossPrimaryButton
import ai.rever.boss.plugin.ui.BossTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Shown in a settings section whose panel is served by a plugin that has not registered its API.
 *
 * It says which of the four reasons applies, and when the plugin is genuinely absent it offers to
 * install it - the host can, which is the point: it resolves the id against the store, downloads
 * the jar and loads it, none of which a plugin can do for itself. Pressing Install raises the
 * host's own consent dialog (`MissingDependencyDialog`), which names the plugin from the **store**
 * and shows the id it will install by, rather than installing on this button's say-so.
 *
 * @param what the thing the user came here for, e.g. "AI provider settings"
 * @param pluginName the plugin's display name, for the sentence and the button
 * @param pluginId the id the host installs by
 * @param missingPermissions non-empty when the user cannot access the plugin at all, which is a
 *   different dead end: the host skips `register()` for an inaccessible plugin, so the API is
 *   never contributed and no amount of installing or waiting will help.
 */
@Composable
internal fun PluginSettingsUnavailableNotice(
    what: String,
    pluginName: String,
    pluginId: String,
    missingPermissions: List<String> = emptyList(),
) {
    val absence = rememberPluginSectionAbsence(pluginId)
    var offerDeclined by remember(pluginId) { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text =
                when {
                    // Checked before the absence: an inaccessible plugin is skipped at
                    // register() whether or not it is installed, so "install it" would send the
                    // user to fix the wrong thing.
                    missingPermissions.isNotEmpty() -> {
                        "$what are provided by the $pluginName plugin, which you do not have access to. " +
                            "Ask an administrator to grant: ${missingPermissions.joinToString(", ")}."
                    }

                    absence == PluginSectionAbsence.NOT_INSTALLED -> {
                        "$what are provided by the $pluginName plugin, which is not installed."
                    }

                    absence == PluginSectionAbsence.DISABLED -> {
                        "$what are provided by the $pluginName plugin, which is installed but " +
                            "switched off. Enable it in the Toolbox."
                    }

                    else -> {
                        "$what are provided by the $pluginName plugin, which isn't loaded yet."
                    }
                },
            color = BossTheme.colors.textMuted,
            fontSize = 13.sp,
        )

        if (missingPermissions.isEmpty() && absence == PluginSectionAbsence.NOT_INSTALLED) {
            BossPrimaryButton(
                text = "Install $pluginName",
                onClick = {
                    // False means there was nothing to offer after all - the plugin arrived
                    // between the render and the press, or the installer factory is not wired.
                    // Saying so beats a button that swallows the press: the panel replaces this
                    // notice by itself in the first case, and the second is a real dead end.
                    offerDeclined = !MissingPluginOffer.offerIfMissing(pluginId)
                },
            )
        }

        if (offerDeclined) {
            Text(
                text = "Could not start the install here. Install $pluginName from the Toolbox.",
                color = BossTheme.colors.textMuted,
                fontSize = 12.sp,
            )
        }
    }
}

/**
 * Recomputed whenever any plugin's state changes, so the notice follows the plugin.
 *
 * Observing `pluginStates` is what makes Install self-clearing: the install lands, the manager
 * updates, this recomposes, and the section swaps to the real panel without the user reopening
 * Settings. It is also the signal for enabling or disabling the plugin elsewhere.
 */
@Composable
private fun rememberPluginSectionAbsence(pluginId: String): PluginSectionAbsence {
    // Same shape as the tab bar's bookmarks shelf, deliberately: a `?: return` before a
    // `collectAsState` would make the observation itself conditional on a global that can change
    // between compositions.
    val states =
        DynamicPluginManager
            .anyActiveManager()
            ?.pluginStates
            ?.collectAsState()
            ?.value
    return remember(states, pluginId) {
        pluginSectionAbsence(
            // The Install button's own predicate, so the sentence and the button can never
            // disagree about whether the plugin is here.
            installed = MissingPluginOffer.isInstalled(pluginId),
            isDisabled = states?.get(pluginId)?.state == PluginState.DISABLED,
        )
    }
}
