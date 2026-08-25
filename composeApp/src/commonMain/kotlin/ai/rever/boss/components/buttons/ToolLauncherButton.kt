package ai.rever.boss.components.buttons

import ai.rever.boss.components.dialogs.ToolLauncherDialog
import ai.rever.boss.components.model.BossDraggableComponent
import ai.rever.boss.plugin.api.Panel
import ai.rever.boss.plugin.api.Panel.Companion.bottom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/** Test tag of the launcher, for the layout tests that check it is on screen. */
const val TOOL_LAUNCHER_TAG = "tool-launcher-button"

/**
 * The way into every tool, for windows where the icon strips are not both on screen.
 *
 * The icon is the app-grid, the shape a launcher or a store wears everywhere else - a thing you
 * open to go and pick something. Deliberately not an arrow pointing at the missing strip, which
 * would name a scope this does not have (the dialog lists every tool whichever strip is gone),
 * and deliberately not a puzzle piece: that says "plugin", an implementation detail, where what
 * the user is looking for is a tool. `GlobalSearchDialog` already uses this glyph for the
 * everything-category, so a grid of squares means the same thing in both places.
 *
 * Owns its own dialog state. Every host that renders one of these renders at most one, and the
 * dialog belongs to the button rather than to the window, so nothing has to be threaded through
 * the scaffold to open it.
 *
 * @param hintDirection which way the tooltip opens - away from the edge the button sits on.
 */
@Composable
fun BossDraggableComponent.ToolLauncherButton(hintDirection: Panel = bottom) {
    var showDialog by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(vertical = 4.dp)) {
        BossActionButton(
            imageVector = Icons.Outlined.Apps,
            text = "Tools",
            isSelected = showDialog,
            hintDirection = hintDirection,
            modifier = Modifier.size(32.dp).testTag(TOOL_LAUNCHER_TAG),
        ) {
            showDialog = true
        }
    }

    if (showDialog) {
        ToolLauncherDialog(onDismiss = { showDialog = false })
    }
}
