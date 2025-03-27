package ai.rever.boss.v3.bossConsole.components

import GitHubDarkSurface
import ai.rever.boss.v3.bossConsole.BossConsoleViewModel
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.ScaffoldState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import moe.tlaster.precompose.navigation.Navigator

@Composable
fun BossDrawerContent(
    viewModel: BossConsoleViewModel,
    scope: CoroutineScope,
    scaffoldState: ScaffoldState,
    navController: Navigator
) {
    Column(
        modifier = Modifier.fillMaxSize()
            .background(GitHubDarkSurface)
    ) {
        BossNavigationRailHeader()
        BossConsoleSiteMap(
            viewModel = viewModel,
            navController = navController,
            onNavigationItemClick = {
                scope.launch {
                    scaffoldState.drawerState.close()
                }
            }
        )
    }
}

