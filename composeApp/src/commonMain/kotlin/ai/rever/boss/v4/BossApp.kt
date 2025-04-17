package ai.rever.boss.v4

import BossTheme
import ai.rever.boss.platform.CursorUtil.cursorForHorizontalResize
import ai.rever.boss.platform.CursorUtil.cursorForVerticalResize
import ai.rever.boss.v4.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import kotlin.ranges.coerceIn

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
                    WindowPanel(bossConsoleComponent)
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


@Composable
fun RowScope.WindowPanel(bossConsoleComponent: BossConsoleComponent) {
    // State to control panel visibility
    val isLeftPanelVisible by remember { mutableStateOf(true) }
    val isRightPanelVisible by remember { mutableStateOf(true) }
    val isBottomPanelVisible by remember { mutableStateOf(true) }

    // State for panel sizes
    var leftPanelWidth by remember { mutableStateOf(250.dp) }
    var rightPanelWidth by remember { mutableStateOf(250.dp) }
    var bottomPanelHeight by remember { mutableStateOf(200.dp) }
    
    // Min and max constraints for panel sizes
    val minPanelWidth = 150.dp
    val maxPanelWidth = 500.dp
    val minPanelHeight = 100.dp
    val maxPanelHeight = 500.dp
    
    // Density for converting between dp and pixels
    val density = LocalDensity.current
    
    Box(modifier = Modifier
        .weight(1f)
        .fillMaxHeight()
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top area with left, center, and right panels
            Row(modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
            ) {
                // Left panel
                if (isLeftPanelVisible) {
                    Surface(
                        modifier = Modifier
                            .width(leftPanelWidth)
                            .fillMaxHeight(),
                        elevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2B2D30))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(Color(0xFF3C3F41))
                            ) {
                                Text(
                                    "Project",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 10.dp, top = 6.dp)
                                )
                            }
                            // Content here
                        }
                    }
                    
                    VDivider()
                }
                
                // Main center panel
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    elevation = 0.dp
                ) {
                    BossConsoleApp(
                        modifier = Modifier.fillMaxSize(),
                        bossConsoleComponent = bossConsoleComponent
                    )
                }
                
                // Right panel
                if (isRightPanelVisible) {
                    VDivider()
                    
                    Surface(
                        modifier = Modifier
                            .width(rightPanelWidth)
                            .fillMaxHeight(),
                        elevation = 1.dp
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF2B2D30))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(28.dp)
                                    .background(Color(0xFF3C3F41))
                            ) {
                                Text(
                                    "Structure",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(start = 10.dp, top = 6.dp)
                                )
                            }
                            // Content here
                        }
                    }
                }
            }
            
            // Bottom panel
            if (isBottomPanelVisible) {
                Divider()
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(bottomPanelHeight),
                    elevation = 1.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color(0xFF2B2D30))
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .background(Color(0xFF3C3F41))
                        ) {
                            Text(
                                "Terminal",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(start = 10.dp, top = 6.dp)
                            )
                        }
                        // Content here
                    }
                }
            }
        }
        
        // Transparent overlays for resizing - positioned in fixed locations
        if (isLeftPanelVisible) {
            // Left panel resize overlay
            Box(
                modifier = Modifier
                    .offset { IntOffset(leftPanelWidth.roundToPx() - 8.dp.roundToPx(), 0) }
                    .width(16.dp)
                    .fillMaxHeight(if (isBottomPanelVisible) 1f - (bottomPanelHeight / 1000.dp) else 1f)
                    .alpha(0f)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            with(density) {
                                val newWidth = leftPanelWidth + dragAmount.x.toDp()
                                leftPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                            }
                        }
                    }
                    .cursorForHorizontalResize()
            )
        }
        
        if (isRightPanelVisible) {
            // Right panel resize overlay
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(-rightPanelWidth.roundToPx() - 1.dp.roundToPx() + 8.dp.roundToPx(), 0) }
                    .width(16.dp)
                    .fillMaxHeight(if (isBottomPanelVisible) 1f - (bottomPanelHeight / 1000.dp) else 1f)
                    .alpha(0f)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            with(density) {
                                val newWidth = rightPanelWidth - dragAmount.x.toDp()
                                rightPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                            }
                        }
                    }
                    .cursorForHorizontalResize()
            )
        }
        
        if (isBottomPanelVisible) {
            // Bottom panel resize overlay
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset { IntOffset(0, -bottomPanelHeight.roundToPx() - 1.dp.roundToPx() + 8.dp.roundToPx()) }
                    .fillMaxWidth()
                    .height(16.dp)
                    .alpha(0f)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            with(density) {
                                val newHeight = bottomPanelHeight - dragAmount.y.toDp()
                                bottomPanelHeight = newHeight.coerceIn(minPanelHeight, maxPanelHeight)
                            }
                        }
                    }
                    .cursorForVerticalResize()
            )
        }
    }
}