package ai.rever.boss.v3.bossConsole.components

import ai.rever.boss.v3.bossConsole.BossConsoleViewModel
import ai.rever.boss.v3.bossConsole.Section
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.tlaster.precompose.navigation.Navigator

@Composable
fun BossConsoleSiteMap(
    viewModel: BossConsoleViewModel,
    navController: Navigator,
    onNavigationItemClick: () -> Unit = {}
) {
    Column(modifier = Modifier.padding(horizontal = 8.dp)) {
        // Lighthouse Section
        BossSection(
            section = Section.LIGHTHOUSE,
            viewModel = viewModel,
            navController = navController,
            onNavigationItemClick = onNavigationItemClick
        )

        // Lanager Section
        BossSection(
            section = Section.LANAGER,
            viewModel = viewModel,
            navController = navController,
            onNavigationItemClick = onNavigationItemClick
        )
    }
}

