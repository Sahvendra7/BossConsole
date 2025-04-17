package ai.rever.boss.v4.components.window_panel.components

import ai.rever.boss.v4.components.dividers.VDivider
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossLeftWindowPanel(isLeftPanelVisible: Boolean, leftPanelWidth: Dp) {
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
}