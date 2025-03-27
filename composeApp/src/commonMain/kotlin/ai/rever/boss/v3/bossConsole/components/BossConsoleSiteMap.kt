package ai.rever.boss.v3.bossConsole.components

import ai.rever.boss.v3.bossConsole.BossConsoleViewModel
import ai.rever.boss.v3.bossConsole.Section
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossConsoleSiteMap(
    viewModel: BossConsoleViewModel,
    onNavigationItemClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Lighthouse Section
        BossSection(Section.LIGHTHOUSE, viewModel, onNavigationItemClick)

        // Lanager Section
        BossSection(Section.LANAGER, viewModel, onNavigationItemClick)
    }
}

