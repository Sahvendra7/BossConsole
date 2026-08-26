package ai.rever.boss.components.buttons

import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag

/**
 * Opens the Toolbox, beside Settings wherever the host's own actions are drawn.
 *
 * The Toolbox is where plugins are installed, updated and switched off, which makes it the closest
 * neighbour Settings has: both are "go and configure the app", as against Search and the tools
 * launcher, which open something. It sits directly after Settings for that reason.
 *
 * **The puzzle piece, not a toolbox glyph.** It is the mark this app already uses for a plugin -
 * `PluginListProvider` falls back to it for a plugin with no icon of its own - and the Toolbox is
 * the plugin surface. A second, unrelated tool-shaped icon beside the tools launcher's app grid
 * would read as two doors to the same room.
 *
 * Stateless, and deliberately without padding or a size: it shares rows with Sign Out, Settings and
 * Search, and each of those hosts sets both. A size baked in here made the tools launcher the one
 * button that did not match wherever it went.
 */
@Composable
fun ToolboxButton(
    onClick: () -> Unit,
    hintDirection: Panel = bottom,
    modifier: Modifier = Modifier,
) {
    BossActionButton(
        imageVector = Icons.Outlined.Extension,
        text = "Toolbox",
        hintText = TOOLBOX_HINT,
        hintDirection = hintDirection,
        modifier = modifier.testTag(TOOLBOX_TAG),
        onClick = onClick,
    )
}

/** What the hint says, in one place, because four hosts draw this button. */
internal const val TOOLBOX_HINT = "Toolbox - install and manage plugins"

/** Test tag of the Toolbox button - see `HostActionsContentTest`. */
internal const val TOOLBOX_TAG = "toolbox-button"
