package ai.rever.boss.v4.components.buttons

import BossDarkAccent
import BossDarkBorder
import BossDarkTextPrimary
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Diversity2
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

fun findLogo(fileName: String) : ImageVector {
    return Icons.Outlined.Diversity2
}

@Composable
fun BossTabButton(
    fileName: String,
    isSelected: Boolean = false,
    isFocused: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onClose: () -> Unit = {}
) {

    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(IntrinsicSize.Min)
            .hoverable(interactionSource)
    ) {
        TextButton(
            modifier = Modifier.fillMaxHeight(),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = Color.Transparent,
                contentColor = if (isSelected) BossDarkTextPrimary else BossDarkTextPrimary.copy(0.8f)
            ),
            contentPadding = PaddingValues(horizontal = 16.dp),
            onClick = onClick
        ) {
            Icon(
                imageVector = findLogo(fileName),
                contentDescription = fileName,
                modifier = Modifier.offset(x = -4.dp).size(16.dp),
            )
            Text(
                text = fileName,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Center
            )

            // Small close icon with click functionality but no visual hover effect
            Icon(
                imageVector = Icons.Outlined.Close,
                contentDescription = "Close $fileName",
                modifier = Modifier
                    .size(13.dp)
                    .offset(x = 8.dp)
                    .alpha(if (isSelected || isHovered) 1f else 0f)
                    .clickable(onClick = onClose)
            )
        }
        if (isSelected) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        color = if (isFocused) BossDarkAccent else BossDarkBorder,
                        shape = RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}