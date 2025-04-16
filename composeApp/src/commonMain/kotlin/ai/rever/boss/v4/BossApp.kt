package ai.rever.boss.v4

import BossDarkTextPrimary
import BossTheme
import ai.rever.boss.platform.CursorUtil.cursorForHorizontalResize
import ai.rever.boss.platform.CursorUtil.cursorForVerticalResize
import ai.rever.boss.v4.components.*
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material.Divider
import androidx.compose.material.Icon
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import androidx.compose.ui.unit.sp

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

// Resizable divider for vertical resizing (left/right panels)
@Composable
fun VerticalResizeHandle(
    onResize: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(modifier = modifier.width(8.dp).fillMaxHeight()) {
        Divider(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxHeight()
                .width(if (isHovered) 2.dp else 1.dp)
                .background(if (isHovered) Color.Gray else Color(0xFF555555))
                .hoverable(interactionSource)
                .cursorForHorizontalResize()
        )
        
        // This invisible box covers the entire area to make dragging easier
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cursorForHorizontalResize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.x)
                    }
                }
        )
    }
}

// Resizable divider for horizontal resizing (bottom panel)
@Composable
fun HorizontalResizeHandle(
    onResize: (Float) -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(modifier = modifier.height(8.dp).fillMaxWidth()) {
        Divider(
            modifier = Modifier
                .align(Alignment.Center)
                .fillMaxWidth()
                .height(if (isHovered) 2.dp else 1.dp)
                .background(if (isHovered) Color.Gray else Color(0xFF555555))
                .hoverable(interactionSource)
                .cursorForVerticalResize()
        )
        
        // This invisible box covers the entire area to make dragging easier
        Box(
            modifier = Modifier
                .fillMaxSize()
                .cursorForVerticalResize()
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        change.consume()
                        onResize(dragAmount.y)
                    }
                }
        )
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
                // Left panel with resize handle
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
                    
                    // Vertical resize handle for left panel
                    VerticalResizeHandle(
                        onResize = { delta ->
                            with(density) {
                                val newWidth = leftPanelWidth + delta.toDp()
                                leftPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                            }
                        }
                    )
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
                
                // Right panel with resize handle
                if (isRightPanelVisible) {
                    // Vertical resize handle for right panel (positioned before the panel)
                    VerticalResizeHandle(
                        onResize = { delta ->
                            with(density) {
                                // Negative delta because we're resizing from right to left
                                val newWidth = rightPanelWidth - delta.toDp()
                                rightPanelWidth = newWidth.coerceIn(minPanelWidth, maxPanelWidth)
                            }
                        }
                    )
                    
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
            
            // Bottom panel with resize handle
            if (isBottomPanelVisible) {
                // Horizontal resize handle for bottom panel (positioned before the panel)
                HorizontalResizeHandle(
                    onResize = { delta ->
                        with(density) {
                            // Negative delta because we're resizing from bottom to top
                            val newHeight = bottomPanelHeight - delta.toDp()
                            bottomPanelHeight = newHeight.coerceIn(minPanelHeight, maxPanelHeight)
                        }
                    }
                )
                
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
    }
}