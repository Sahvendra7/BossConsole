package ai.rever.boss.v4.components.buttons

import BossDarkTextPrimary
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossActionButton(
    imageVector: ImageVector? = null,
    text: String,
    fontSize: TextUnit = 13.sp,
    color: Color = BossDarkTextPrimary,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(2.dp),
    isSelected: Boolean = false,
    onClick: () -> Unit
) {

    // Use interaction source to track states
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    // Determine active state based on hover, focus or selection
    val isActive = isHovered || isFocused || isSelected

    if (imageVector != null) {

        // Use Button instead of IconButton to get better hover support
        Button(
            onClick = onClick,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent,
                contentColor = color
            ),
            elevation = ButtonDefaults.elevation(
                defaultElevation = 0.dp,
                pressedElevation = 0.dp,
                hoveredElevation = 0.dp,
                focusedElevation = 0.dp
            ),
            contentPadding = PaddingValues(0.dp),
            modifier = modifier
                .size(32.dp)
                .hoverable(interactionSource)
        ) {
            Icon(
                imageVector = imageVector,
                contentDescription = text,
                modifier = Modifier.size(20.dp),
                tint = if (isActive) color else color.copy(alpha = 0.8f)
            )
        }
    } else {

        TextButton(
            onClick = onClick,
            interactionSource = interactionSource,
            colors = ButtonDefaults.textButtonColors(
                backgroundColor = if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent
            ),
            modifier = modifier
                .defaultMinSize(minHeight = 2.dp, minWidth = 2.dp)
                .hoverable(interactionSource),
            contentPadding = contentPadding,
        ) {
            Text(
                text = text,
                color = if (isActive) color else color.copy(alpha = 0.8f),
                fontSize = fontSize,
                fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}