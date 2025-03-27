package ai.rever.boss.v3.bossConsole.components

import ai.rever.boss.v3.bossConsole.BossConsoleViewModel
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.material.NavigationRail
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossNavigationRail(
    viewModel: BossConsoleViewModel
) {
    NavigationRail(
        modifier = Modifier
            .width(220.dp)
            .fillMaxHeight(),
        header = { BossNavigationRailHeader() }
    ) {
        BossConsoleSiteMap(
            viewModel = viewModel
        )
    }
}

