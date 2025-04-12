package ai.rever.boss.v4

import BossTheme
import ai.rever.boss.v4.components.*
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.Divider
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FormatShapes
import androidx.compose.material.icons.filled.Gite
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FormatShapes
import androidx.compose.material.icons.outlined.PhoneIphone
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color

@Composable
fun BossApp(bossConsoleComponent: BossConsoleComponent) {
    BossTheme {
        Column(modifier = Modifier.fillMaxSize()) {
            // Title bar with BOSS centered
            BossTitleBar()
            // Toolbar
            BossTopBar()
            // Add border below top bar
            Divider()
            // Main content
            Row(modifier = Modifier.weight(1f)) {
                BossLeftSideBar()
                VDivider()
                BossConsoleApp(
                    modifier = Modifier.weight(1f),
                    bossConsoleComponent = bossConsoleComponent
                )
                VDivider()
                BossRightSideBar()
            }
            Divider()
            BossBottomBar()
        }
    }
}