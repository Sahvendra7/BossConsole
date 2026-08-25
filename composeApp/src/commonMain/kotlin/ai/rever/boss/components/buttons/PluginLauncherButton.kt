package ai.rever.boss.components.buttons

import ai.rever.boss.components.dialogs.PluginLauncherDialog
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Test tag of the launcher, for the layout tests that check it is on screen. */
const val PLUGIN_LAUNCHER_TAG = "plugin-launcher-button"

/**
 * The way into every plugin, for windows where the icon strips are not both on screen.
 *
 * A puzzle piece rather than an arrow pointing at the missing strip, because the dialog it opens
 * lists ALL plugins whichever strip is gone - an arrow would name a scope this does not have. It
 * is the same icon `PluginListProvider` falls back to for a plugin with no icon of its own, so
 * "plugin" looks the same wherever the app says it.
 *
 * Owns its own dialog state. Every host that renders one of these renders at most one, and the
 * dialog belongs to the button rather than to the window, so nothing has to be threaded through
 * the scaffold to open it.
 *
 * @param hintDirection which way the tooltip opens - away from the edge the button sits on.
 */
@Composable
fun BossDraggableComponent.PluginLauncherButton(hintDirection: Panel = bottom) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(vertical = 4.dp)) {
        BossActionButton(
            imageVector = Icons.Outlined.Extension,
            text = "Plugins",
            isSelected = showDialog,
            hintDirection = hintDirection,
            modifier = Modifier.size(32.dp).testTag(PLUGIN_LAUNCHER_TAG),
        ) {
            showDialog = true
        }
    }

    if (showDialog) {
        PluginLauncherDialog(onDismiss = { showDialog = false })
    }
}
