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
fun BossLeftSideBar() {
    VerticalBar(40.dp) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BossActionButton(imageVector = Icons.Outlined.Folder, text = "Folder", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.PhoneIphone, text = "Phone Iphone", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.FormatShapes, text = "Format Shapes", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.Build, text = "Build", onClick = {})
            Spacer(modifier = Modifier.weight(1f))
            BossActionButton(imageVector = Icons.Outlined.RunCircle, text = "Run", onClick = {})
            BossActionButton(imageVector = Icons.Outlined.Code, text = "Code", onClick = {})
        }
    }
}