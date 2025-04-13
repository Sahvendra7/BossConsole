package ai.rever.boss.v4.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossRightSideBar() {
    VerticalBar(40.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BossActionButton(imageVector = Icons.Outlined.AttachFile, text = "Folder", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.Audiotrack, text = "Phone Iphone", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.VideoFile, text = "Phone Iphone", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.Replay, text = "Phone Iphone", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.Cast, text = "Phone Iphone", onClick = {})
            SDivider()
            BossActionButton(imageVector = Icons.Outlined.Anchor, text = "Phone Iphone", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.Android, text = "Phone Iphone", onClick = {})
        }
    }
}