package ai.rever.boss.v4.components.buttons

import BossDarkTextPrimary
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
fun BossActionButton(imageVector: ImageVector? = null,
                     text: String,
                     fontSize: TextUnit = 13.sp,
                     color: Color = BossDarkTextPrimary,
                     modifier: Modifier = Modifier,
                     contentPadding: PaddingValues = PaddingValues(4.dp),
                     onClick: () -> Unit) {
    
    if (imageVector != null) {
        // Use interaction source to track hover state
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        
        // Use Button instead of IconButton to get better hover support
        Button(
            onClick = onClick,
            interactionSource = interactionSource,
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.Transparent,
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
                // Make icon slightly larger when hovered for visual feedback
                tint = if (isHovered) color else color.copy(alpha = 0.8f)
            )
        }
    } else {
        val interactionSource = remember { MutableInteractionSource() }
        val isHovered by interactionSource.collectIsHoveredAsState()
        
        TextButton(
            onClick = onClick,
            interactionSource = interactionSource,
            modifier = modifier
                .defaultMinSize(minHeight = 2.dp, minWidth = 2.dp)
                .hoverable(interactionSource),
            contentPadding = contentPadding,
        ) {
            Text(
                text = text,
                color = if (isHovered) color else color.copy(alpha = 0.8f),
                fontSize = fontSize,
                fontWeight = if (isHovered) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}