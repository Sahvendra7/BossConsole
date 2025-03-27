package ai.rever.boss.v3.bossConsole

import SystemOfRecordsScreen
import ai.rever.boss.v3.bossConsole.components.*
import ai.rever.boss.v3.bossConsole.screens.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun BossConsole() {
    val viewModel = remember { BossConsoleViewModel() }
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
    val scope = rememberCoroutineScope()
    
    // Determine if we're on a small screen where we should use drawer instead of rail
    val isSmallScreen = remember { mutableStateOf(false) }

    BoxWithConstraints {
        // Use drawer for screens narrower than 600dp
        isSmallScreen.value = maxWidth < 600.dp
    }

    Scaffold(
        scaffoldState = scaffoldState,
        topBar = {
            if (isSmallScreen.value) {
                BossTopBar(scope, scaffoldState)
            }
        },
        drawerContent = if (isSmallScreen.value) {
            { BossDrawerContent(viewModel, scope, scaffoldState) }
        } else null,
    ) { paddingValues ->
        Row(modifier = Modifier.padding(paddingValues)) {
            // Show NavigationRail only on larger screens
            if (!isSmallScreen.value) {
                BossNavigationRail(viewModel)
            }
            Surface(
                modifier = Modifier.fillMaxSize().weight(1f),
                elevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
            ) {
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    when (viewModel.currentScreen) {
                        is Screen.Worklist -> WorklistScreen()
                        is Screen.SystemOfRecords -> SystemOfRecordsScreen()
                        is Screen.OrgValues -> OrgValuesScreen()
                        is Screen.GlobalLanager -> GlobalLanagerScreen()
                        is Screen.MasteryRegistry -> MasteryRegisteryScreen()
                        is Screen.TaskResolverRegistry -> TaskResolverRegisteryScreen()
                    }
                }
            }
        }
    }
}

