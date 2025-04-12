package ai.rever.boss.v4.components

import BossDarkTextPrimary
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossActionButton(imageVector: ImageVector? = null, text: String, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val hoverBackground = if (isHovered) Color.LightGray.copy(alpha = 0.2f) else Color.Transparent
    val cornerShape = RoundedCornerShape(4.dp)

    Box(modifier = Modifier.padding(4.dp)) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = text,
                tint = BossDarkTextPrimary,
                modifier = Modifier
                    .clip(cornerShape)
                    .background(hoverBackground)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        } else {
            Text(
                text = text,
                color = BossDarkTextPrimary,
                fontSize = 13.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier
                    .clip(cornerShape)
                    .background(hoverBackground)
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                    .padding(horizontal = 8.dp, vertical = 8.dp)
            )
        }
    }
}