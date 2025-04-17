package ai.rever.boss.v4.components.buttons

import BossDarkTextPrimary
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.size
import androidx.compose.material.*
import androidx.compose.runtime.Composable
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
    TextButton(
        onClick = onClick,
        modifier = modifier.defaultMinSize(minHeight = 2.dp, minWidth = 2.dp),
        contentPadding = contentPadding,
    ) {
        if (imageVector != null) {
            Icon(
                imageVector = imageVector,
                contentDescription = text,
                modifier = Modifier.size(22.dp),
                tint = color
            )
        } else {
            Text(
                text = text,
                color = color,
                fontSize = fontSize,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}