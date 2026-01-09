package ai.rever.boss.components.overlays

import BossDarkBorder
import ai.rever.boss.platform.ContextMenuHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
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
 * @param trailingIcon Optional trailing icon (e.g., action button)
 * @param trailingIconColor Color for trailing icon (defaults to gray)
 * @param onTrailingClick Action when trailing icon is clicked
 * @param secondaryTrailingIcon Optional second trailing icon (e.g., delete button)
 * @param secondaryTrailingIconColor Color for secondary trailing icon (defaults to gray)
 * @param onSecondaryTrailingClick Action when secondary trailing icon is clicked
 * @param onClick The action to perform when this item is clicked (last param for trailing lambda)
 */
data class ContextMenuItem(
    val text: String = "",
    val icon: ImageVector? = null,
    val isDivider: Boolean = false,
    val trailingIcon: ImageVector? = null,
    val trailingIconColor: Color? = null,
    val onTrailingClick: (() -> Unit)? = null,
    val secondaryTrailingIcon: ImageVector? = null,
    val secondaryTrailingIconColor: Color? = null,
    val onSecondaryTrailingClick: (() -> Unit)? = null,
    val subMenu: List<ContextMenuItem>? = null, // Submenu items
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
        ContextMenuContent(
            items = items,
            modifier = modifier,
            onDismissRequest = onDismissRequest
        )
    }
}

@Composable
private fun ContextMenuContent(
    items: List<ContextMenuItem>,
    modifier: Modifier = Modifier,
    onDismissRequest: () -> Unit
) {
    var expandedSubMenuIndex by remember { mutableStateOf<Int?>(null) }
    var isSubMenuHovered by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .background(
                color = BossDarkBorder,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(vertical = 4.dp)
            .width(IntrinsicSize.Max)
    ) {
        items.forEachIndexed { index, item ->
            if (item.isDivider) {
                Divider(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    color = Color(0xFF444444),
                    thickness = 1.dp
                )
            } else {
                val interactionSource = remember { MutableInteractionSource() }
                val isHovered by interactionSource.collectIsHoveredAsState()
                val hasSubMenu = !item.subMenu.isNullOrEmpty()

                // Update expanded submenu on hover - only close if hovering a different item
                LaunchedEffect(isHovered) {
                    if (isHovered) {
                        if (hasSubMenu) {
                            expandedSubMenuIndex = index
                        } else {
                            // Hovering a non-submenu item, close any open submenu
                            expandedSubMenuIndex = null
                        }
                    }
                }

                Box {
                    Row(
                        modifier = Modifier
                            .hoverable(interactionSource)
                            .then(
                                if (hasSubMenu) Modifier else Modifier.clickable {
                                    item.onClick()
                                    onDismissRequest()
                                }
                            )
                            .background(
                                if (isHovered) Color(0xFF3A3D40) else Color.Transparent
                            )
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
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f).align(Alignment.CenterVertically)
                                .padding(bottom = 4.dp)
                        )

                        // Show arrow for submenu
                        if (hasSubMenu) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "›",
                                color = Color(0xFF888888),
                                fontSize = 16.sp
                            )
                        }

                        // Primary trailing icon (e.g., play/stop button or status indicator)
                        if (item.trailingIcon != null) {
                            Spacer(modifier = Modifier.width(12.dp))
                            Box(
                                modifier = Modifier
                                    .size(20.dp)
                                    .then(
                                        if (item.onTrailingClick != null) {
                                            Modifier.clickable(
                                                interactionSource = remember { MutableInteractionSource() },
                                                indication = null
                                            ) {
                                                item.onTrailingClick.invoke()
                                                onDismissRequest()
                                            }
                                        } else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.trailingIcon,
                                    contentDescription = "Action",
                                    tint = item.trailingIconColor ?: Color(0xFF888888),
                                    modifier = Modifier.size(if (item.onTrailingClick != null) 16.dp else 8.dp) // Smaller for indicator dots
                                )
                            }
                        }
                        // Secondary trailing icon (e.g., delete button)
                        if (item.secondaryTrailingIcon != null && item.onSecondaryTrailingClick != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) {
                                        item.onSecondaryTrailingClick.invoke()
                                        onDismissRequest()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = item.secondaryTrailingIcon,
                                    contentDescription = "Delete",
                                    tint = item.secondaryTrailingIconColor ?: Color(0xFF888888),
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    // Render submenu
                    if (hasSubMenu && expandedSubMenuIndex == index) {
                        val subMenuInteractionSource = remember { MutableInteractionSource() }
                        val subMenuHovered by subMenuInteractionSource.collectIsHoveredAsState()

                        // Track submenu hover state
                        LaunchedEffect(subMenuHovered) {
                            isSubMenuHovered = subMenuHovered
                        }

                        Popup(
                            alignment = Alignment.TopEnd,
                            offset = IntOffset(4, 0) // Small offset to create overlap for smooth transition
                        ) {
                            Box(
                                modifier = Modifier.hoverable(subMenuInteractionSource)
                            ) {
                                ContextMenuContent(
                                    items = item.subMenu,
                                    onDismissRequest = onDismissRequest
                                )
                            }
                        }
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
