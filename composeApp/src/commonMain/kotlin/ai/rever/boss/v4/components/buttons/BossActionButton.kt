package ai.rever.boss.v4.components.buttons

import BossDarkTextPrimary
import ai.rever.boss.v4.components.overlays.ContextMenu
import ai.rever.boss.v4.components.overlays.ContextMenuItem
import androidx.compose.foundation.background
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.layout.positionOnScreen
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

@Composable
fun BossActionButton(
    imageVector: ImageVector,
    text: String,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) = BossActionButton(
    imageVector = imageVector,
    text = text,
    isSelected = isSelected,
    modifier = modifier,
    hintText = text,
    showHintWithDelay = false,
    onClick = onClick
)

@Composable
fun BossActionButton(
    imageVector: ImageVector? = null,
    leftLogo: (@Composable () -> Unit)? = null,
    leftIcon: ImageVector? = null,
    text: String,
    fontSize: TextUnit = 13.sp,
    color: Color = BossDarkTextPrimary,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(2.dp),
    isSelected: Boolean = false,
    contextMenuItems: List<ContextMenuItem>? = null,
    hintText: String? = null,
    showHintWithDelay: Boolean = true,
    onClick: () -> Unit = {}
) {
    // State for context menu
    var showContextMenu by remember { mutableStateOf(false) }
    var menuPosition by remember { mutableStateOf(IntOffset.Zero) }
    var buttonPosition by remember { mutableStateOf(Offset.Zero) }
    
    // State for hover popup
    var showHoverPopup by remember { mutableStateOf(false) }
    var hoverPopupPosition by remember { mutableStateOf(IntOffset.Zero) }

    // Use interaction source to track states
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Determine active state based on hover, focus or selection
    val isActive = isHovered || isFocused || isSelected
    
    // Handle hover popup delay
    LaunchedEffect(isHovered) {
        if (isHovered && hintText != null) {
            if (showHintWithDelay) {
                delay(500) // 500 millisecond delay
            }
            if (isHovered) { // Check if still hovering after delay
                val x = buttonPosition.x.toInt()
                val y = buttonPosition.y.toInt() + 40
                hoverPopupPosition = IntOffset(x, y)
                showHoverPopup = true
            }
        } else {
            showHoverPopup = false
        }
    }

    // Show context menu if enabled
    if (showContextMenu && contextMenuItems != null) {
        ContextMenu(
            items = contextMenuItems,
            offset = menuPosition,
            onDismissRequest = { showContextMenu = false }
        )
    }
    
    // Show hover popup if hovering and hint text is provided
    if (showHoverPopup && hintText != null) {
        Popup(
            alignment = Alignment.TopStart,
            offset = hoverPopupPosition,
            properties = PopupProperties(focusable = false)
        ) {
            Box(
                modifier = Modifier
                    .background(
                        color = Color(0xFF2D2D2D),
                        shape = RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = hintText,
                    color = Color.White,
                    fontSize = 12.sp
                )
            }
        }
    }

    @Composable
    fun MainText() {
        Text(
            text = text,
            color = if (isActive) color else color.copy(alpha = 0.8f),
            fontSize = fontSize,
            fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }

    @Composable
    fun MainIcon(icon: ImageVector = Icons.Outlined.KeyboardArrowDown, size: Dp = 16.dp) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.size(size),
            tint = if (isActive) color else color.copy(alpha = 0.8f)
        )
    }

    var _leftLogo = leftLogo
    var _contentPadding = contentPadding


    leftLogo?.let {
        _contentPadding = PaddingValues(vertical = 2.dp, horizontal = 10.dp)
    }?: leftIcon?.let {
        _contentPadding = PaddingValues(vertical = 6.dp, horizontal = 10.dp)
        _leftLogo = { MainIcon(it) }
    }

    // Calculate position tracking modifier for context menu and hover popup
    val positionModifier = Modifier.onGloballyPositioned { coordinates ->
        buttonPosition = coordinates.positionInRoot()
    }

    // Add position tracking modifier to existing modifier
    val combinedModifier = modifier.then(positionModifier)
    
    // Define button click handler
    val handleClick = {
        if (contextMenuItems != null) {
            // Show context menu at the right position
            val x = buttonPosition.x.toInt()
            val y = buttonPosition.y.toInt() + 40 // Position below button
            menuPosition = IntOffset(x, y)
            showContextMenu = true
            showHoverPopup = false // Hide hover popup when showing context menu
        }
        // Always call the provided onClick handler
        onClick()
    }

    // Use Button instead of IconButton to get better hover support
    TextButton(
        onClick = handleClick,
        interactionSource = interactionSource,
        colors = ButtonDefaults.buttonColors(
            backgroundColor = if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent,
            contentColor = color
        ),
        contentPadding = _contentPadding,
        modifier = combinedModifier
            .defaultMinSize(minHeight = 2.dp, minWidth = 2.dp)
            .run {
                if (imageVector != null) {
                    size(28.dp).hoverable(interactionSource)
                } else {
                    hoverable(interactionSource)
                }
            }
    ) {
        if (_leftLogo != null) {
            _leftLogo()
            Spacer(modifier = Modifier.width(8.dp))
            MainText()
            MainIcon()
        } else if (imageVector != null) {
            MainIcon(imageVector, 20.dp)
        } else {
            MainText()
        }
    }
}