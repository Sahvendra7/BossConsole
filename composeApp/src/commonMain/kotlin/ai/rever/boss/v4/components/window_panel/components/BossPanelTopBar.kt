package ai.rever.boss.v4.components.window_panel.components

import BossDarkSurface
import ai.rever.boss.v4.components.buttons.BossActionButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossPanelTopBar(title: String?,
                    isHovered: Boolean,
                    onMore: () -> Unit = {},
                    onMinimize: () -> Unit,
                    content: (@Composable () -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(BossDarkSurface),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title ?: "",
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.CenterVertically)
                .padding(bottom = 4.dp)
        )

        Spacer(modifier = Modifier.weight(1f))

        AnimatedVisibility(
            visible = isHovered,
            enter = fadeIn(),
            exit = fadeOut()
        ) {

            Row(modifier = Modifier.padding(end = 4.dp)) {
                content?.invoke()

                BossActionButton(
                    imageVector = Icons.Outlined.MoreVert,
                    text = "More",
                    color = Color.White,
                    onClick = onMore
                )

                BossActionButton(
                    imageVector = Icons.Outlined.Remove,
                    text = "Minimize",
                    color = Color.White,
                    onClick = onMinimize
                )
            }
        }
    }
}
