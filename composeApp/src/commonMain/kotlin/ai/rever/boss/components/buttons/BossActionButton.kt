package ai.rever.boss.components.buttons

import BossDarkAccent
import BossDarkBorder
import BossDarkTextPrimary
import ai.rever.boss.components.model.Panel
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.overlays.ContextMenu
import ai.rever.boss.components.overlays.ContextMenuItem
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.delay

@Composable
fun BossActionButton(
    imageVector: ImageVector,
    text: String,
    isSelected: Boolean,
    modifier: Modifier,
    hintDirection: Panel = bottom,
    onClick: () -> Unit
) = BossActionButton(
    imageVector = imageVector,
    text = text,
    isSelected = isSelected,
    modifier = modifier,
    hintText = text,
    showHintWithDelay = false,
    hintDirection = hintDirection,
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
    iconColor: Color? = null, // Optional separate icon color (defaults to color if null)
    iconSize: Dp = 20.dp, // Icon size for imageVector mode
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(2.dp),
    isSelected: Boolean = false,
    contextMenuItems: List<ContextMenuItem>? = null,
    contextDirection: Panel = bottom,
    hintText: String? = null,
    showHintWithDelay: Boolean = true,
    hintDirection:  Panel = bottom,
    onClick: () -> Unit = {}
) {
    // Resolved icon color - use iconColor if provided, otherwise fall back to color
    val resolvedIconColor = iconColor ?: color
    // State for context menu
    var showContextMenu by remember { mutableStateOf(false) }
    var buttonPosition by remember { mutableStateOf(Offset.Zero) }
    var buttonSize by remember { mutableStateOf(IntOffset(0, 0)) }
    var contextMenuSize by remember { mutableStateOf(IntOffset(0, 0)) }
    var hintPopupSize by remember { mutableStateOf(IntOffset(0, 0)) }
    
    val menuPosition = run {
        val x = buttonPosition.x.toInt() +
                when (contextDirection) {
                    right -> buttonSize.x
                    left -> -contextMenuSize.x
                    else -> (buttonSize.x - contextMenuSize.x) / 2
                }
        val y = buttonPosition.y.toInt() +
                when (contextDirection) {
                    top -> -contextMenuSize.y
                    bottom -> buttonSize.y
                    else -> 0
                }
        IntOffset(x, y)
    }

    // State for hover popup
    var showHoverPopup by remember { mutableStateOf(false) }
    val hoverPopupPosition = run {
        val x = buttonPosition.x.toInt() +
                when (hintDirection) {
                    right -> buttonSize.x
                    left -> -hintPopupSize.x
                    else -> (buttonSize.x - hintPopupSize.x) / 2
                }
        val y = buttonPosition.y.toInt() +
                when (hintDirection) {
                    top -> -hintPopupSize.y
                    bottom -> buttonSize.y
                    else -> 0
                }
        IntOffset(x, y)
    }

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
            onDismissRequest = { showContextMenu = false },
            modifier = Modifier.onGloballyPositioned { coordinates ->
                contextMenuSize = IntOffset(
                    coordinates.size.width,
                    coordinates.size.height
                )
            }
        )
    }
    
    // Show hover popup if hovering and hint text is provided
    if (showHoverPopup && hintText != null) {
        Popup(
            alignment = Alignment.TopStart,
            offset = hoverPopupPosition,
            properties = PopupProperties(focusable = false)
        ) {
            Surface(
                modifier = Modifier
                    .onGloballyPositioned { coordinates ->
                        hintPopupSize = IntOffset(
                            coordinates.size.width,
                            coordinates.size.height
                        )
                    },
                color = BossDarkBorder,
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(modifier = Modifier.defaultMinSize(2.dp)
                    .padding(vertical = 0.dp, horizontal = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = hintText,
                        color = Color.White,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                }
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
            tint = if (isActive) resolvedIconColor else resolvedIconColor.copy(alpha = 0.8f)
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

    // Define button click handler
    val handleClick = {
        if (contextMenuItems != null) {
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
            backgroundColor = if (isSelected) {
                if (isFocused) {
                    BossDarkAccent
                } else {
                    color.copy(alpha = 0.1f)
                }
            } else {
                Color.Transparent
            },
            contentColor = color
        ),
        contentPadding = _contentPadding,
        modifier = modifier
            .defaultMinSize(minHeight = 2.dp, minWidth = 2.dp)
            .run {
                if (imageVector != null) {
                    size(28.dp).hoverable(interactionSource)
                } else {
                    hoverable(interactionSource)
                }
            }
            .onGloballyPositioned { coordinates ->
                buttonPosition = coordinates.positionInParent()
                buttonSize = IntOffset(
                    coordinates.size.width,
                    coordinates.size.height
                )
            }
    ) {
        if (_leftLogo != null) {
            _leftLogo()
            Spacer(modifier = Modifier.width(8.dp))
            MainText()
            MainIcon()
        } else if (imageVector != null) {
            MainIcon(imageVector, iconSize)
        } else {
            MainText()
        }
    }
}
