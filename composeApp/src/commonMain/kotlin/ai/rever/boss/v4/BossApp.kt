package ai.rever.boss.v4

import ai.rever.boss.v4.components.BossTitleBar
import ai.rever.boss.v4.components.BossTopBar
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun BossApp(bossConsoleComponent: BossConsoleComponent) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Title bar with BOSS centered
        BossTitleBar()
        // Toolbar
        BossTopBar()
        // Main content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            BossConsoleApp(bossConsoleComponent)
        }
    }
}