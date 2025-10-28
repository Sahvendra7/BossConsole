package ai.rever.boss.components.window_panel.components

import BossDarkSurface
import ai.rever.boss.components.buttons.BossActionButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossPanelTopBar(
    title: String?,
    isHovered: Boolean,
    onReset: (() -> Unit)? = null,
    onMinimize: () -> Unit,
    content: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(BossDarkSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title ?: "",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(bottom = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        // State for dropdown menu (moved outside AnimatedVisibility to be accessible in condition)
        var showMenu by remember { mutableStateOf(false) }

        AnimatedVisibility(
            visible = isHovered || showMenu,  // Keep visible while menu is open
            enter = fadeIn(),
            exit = fadeOut()
        ) {

            Row(modifier = Modifier.padding(end = 4.dp)) {
                content?.invoke()

                // More button with dropdown menu
                Box {
                    BossActionButton(
                        imageVector = Icons.Outlined.MoreVert,
                        text = "More",
                        color = Color.White,
                        onClick = { showMenu = true }
                    )

                    // Dropdown menu for panel options
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        // Reset option (always first)
                        onReset?.let { resetCallback ->
                            DropdownMenuItem(
                                onClick = {
                                    showMenu = false
                                    resetCallback()
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Refresh,
                                        contentDescription = "Reset",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Reset",
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }

                        // Future menu options can be added here
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
