package ai.rever.boss.old_version.v3.bossConsole.components

import BossDarkSurface
import ai.rever.boss.old_version.v3.bossConsole.BossConsoleViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
fun BossDrawerContent(
    viewModel: BossConsoleViewModel,
    scope: CoroutineScope,
    scaffoldState: ScaffoldState
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(BossDarkSurface)
    ) {
        BossNavigationRailHeader()
        BossConsoleSiteMap(
            viewModel = viewModel,
            onNavigationItemClick = {
                scope.launch {
                    scaffoldState.drawerState.close()
                }
            }
        )
    }
}

