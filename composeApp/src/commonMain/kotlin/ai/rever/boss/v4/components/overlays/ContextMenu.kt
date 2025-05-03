package ai.rever.boss.v4.components.overlays

import BossDarkBorder
import ai.rever.boss.platform.ContextMenuHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties

/**
 * A context menu item that can be displayed in the context menu.
 *
 * @param text The text to display for this item
 * @param icon The icon to display for this item (optional)
 * @param isDivider Whether this item is a divider
 * @param onClick The action to perform when this item is clicked
 */
data class ContextMenuItem(
    val text: String = "",
    val icon: ImageVector? = null,
    val isDivider: Boolean = false,
    val onClick: () -> Unit = {}
)

/**
 * A custom context menu that can be shown on right-click or long press
 * depending on the platform.
 *
 * @param items The list of menu items to display
 * @param offset The offset from the mouse position to display the menu
 * @param onDismissRequest Callback when the menu should be dismissed
 */
@Composable
fun ContextMenu(
    items: List<ContextMenuItem>,
    offset: IntOffset = IntOffset.Zero,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit
) {
    Popup(
        onDismissRequest = onDismissRequest,
        alignment = Alignment.TopStart,
        offset = offset,
        properties = PopupProperties(focusable = true)
    ) {
        Column(
            modifier = modifier
                .background(
                    color = BossDarkBorder,
                    shape = RoundedCornerShape(4.dp)
                )
                .padding(vertical = 4.dp)
                .width(IntrinsicSize.Max)
        ) {
            items.forEach { item ->
                if (item.isDivider) {
                    Divider(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        color = Color(0xFF444444),
                        thickness = 1.dp
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .clickable {
                                item.onClick()
                                onDismissRequest()
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (item.icon != null) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.text,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Text(
                            text = item.text,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    }
}

/**
 * Extension function to make any Compose UI element show a context menu.
 *
 * Uses platform-specific implementations:
 * - On desktop/web: Right-click activation
 * - On mobile (iOS/Android): Long press activation
 *
 * @param enabled Whether the context menu functionality is enabled
 * @param items The items to show in the context menu
 * @return A modifier that enables platform-appropriate context menu functionality
 */
fun Modifier.contextMenu(
    enabled: Boolean = true,
    items: List<ContextMenuItem>
): Modifier = composed {
    var showMenu by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }

    // Get the platform-specific handler
    val handler = remember { ContextMenuHandler() }

    if (showMenu && enabled) {
        ContextMenu(
            items = items,
            offset = menuPosition,
            onDismissRequest = { showMenu = false }
        )
    }

    // Apply platform-specific behavior
    with(handler) {
        this@composed.applyContextMenuBehavior(
            showMenu = showMenu,
            setShowMenu = { showMenu = it },
            setMenuPosition = { menuPosition = it }
        )
    }
} 