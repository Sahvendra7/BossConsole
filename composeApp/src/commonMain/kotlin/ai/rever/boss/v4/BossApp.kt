package ai.rever.boss.v4

import BossDarkTextPrimary
import BossTheme
import ai.rever.boss.v4.components.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round

@Composable
fun BossApp(bossConsoleComponent: BossConsoleComponent) {
    BossTheme {
        // Create and remember the model here to share state across sidebars
        val sidebarModel = rememberDraggableSidebarModel()

        Box(modifier = Modifier.fillMaxSize()) { // Use Box to allow overlaying the drag ghost
            Column(modifier = Modifier.fillMaxSize()) {
                BossTitleBar()
                BossTopBar()
                Divider()
                Row(modifier = Modifier.weight(1f)) {
                    // Pass the shared model down to both sidebars
                    BossLeftSideBar(sidebarModel)
                    VDivider()
                    BossConsoleApp(
                        modifier = Modifier.weight(1f),
                        bossConsoleComponent = bossConsoleComponent
                    )
                    VDivider()
                    BossRightSideBar(sidebarModel)
                }
                Divider()
                BossBottomBar()
            }

            // Draw the dragging item overlay (ghost) if an item is being dragged
            DraggingItemOverlay(sidebarModel)
        }
    }
}

// Overlay composable to draw the ghost item following the pointer
@Composable
private fun DraggingItemOverlay(sidebarModel: DraggableSidebarModel) {
    // Observe the dragging item and its position from the model
    val draggedItemInfo = sidebarModel.draggingItem
    // Get the start position and delta from the model
    val startPosition = sidebarModel.dragStartPosition
    val delta = sidebarModel.dragDelta

    if (draggedItemInfo != null && startPosition != null) {
        val (item, _) = draggedItemInfo
        // Calculate the current absolute position
        val currentPosition = startPosition + delta

        // Calculate the offset to center the ghost icon on the pointer
        val iconSizePx = with(LocalDensity.current) { 22.dp.toPx() }
        val centeredOffset = Offset(currentPosition.x - iconSizePx / 2, currentPosition.y - iconSizePx / 2)

        Box(
            modifier = Modifier
                // Position the ghost based on calculated absolute position, centered
                .offset { centeredOffset.round() }
                .alpha(0.7f) // Apply transparency
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = null, // Decorative
                modifier = Modifier.size(22.dp), // Match icon size
                tint = BossDarkTextPrimary
            )
        }
    }
}