package ai.rever.boss.components.window_panel.components

import BossDarkSurface
import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.overlays.ContextMenu
import ai.rever.boss.components.overlays.ContextMenuItem
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossPanelTopBar(
    title: String?,
    isHovered: Boolean,
    onReset: (() -> Unit)? = null,
    onReloadPlugin: (() -> Unit)? = null,
    onMinimize: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(28.dp)
            .background(BossDarkSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = title ?: "",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterVertically)
        )

        Spacer(modifier = Modifier.weight(1f))

        // State for dropdown menu (moved outside AnimatedVisibility to be accessible in condition)
        var showMenu by remember { mutableStateOf(false) }
        var buttonHeight by remember { mutableStateOf(0) }

        AnimatedVisibility(
            visible = isHovered || showMenu,  // Keep visible while menu is open
            enter = fadeIn(),
            exit = fadeOut()
        ) {

            Row(modifier = Modifier.padding(end = 2.dp)) {
                content?.invoke()

                // More button with context menu
                Box(
                    modifier = Modifier.onGloballyPositioned { coordinates ->
                        buttonHeight = coordinates.size.height
                    }
                ) {
                    BossActionButton(
                        imageVector = Icons.Outlined.MoreVert,
                        text = "More",
                        color = Color.White,
                        onClick = { showMenu = true }
                    )

                    // Context menu for panel options
                    if (showMenu) {
                        val menuItems = buildList {
                            // Reset option (always first)
                            onReset?.let { resetCallback ->
                                add(
                                    ContextMenuItem(
                                        text = "Reset",
                                        icon = Icons.Outlined.Refresh,
                                        onClick = { resetCallback() }
                                    )
                                )
                            }
                            // Reload Plugin option
                            onReloadPlugin?.let { reloadCallback ->
                                add(
                                    ContextMenuItem(
                                        text = "Reload Plugin",
                                        icon = Icons.Outlined.Refresh,
                                        onClick = { reloadCallback() }
                                    )
                                )
                            }
                        }

                        if (menuItems.isNotEmpty()) {
                            ContextMenu(
                                items = menuItems,
                                offset = IntOffset(0, buttonHeight),
                                onDismissRequest = { showMenu = false }
                            )
                        } else {
                            showMenu = false
                        }
                    }
                }

                BossActionButton(
                    imageVector = Icons.Outlined.Remove,
                    text = "Minimize",
                    color = Color.White,
                    onClick = onMinimize
                )
            }
        }
    }
}
