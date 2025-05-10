package ai.rever.boss.old_version.v3.bossConsole

import SystemOfRecordsScreen
import ai.rever.boss.old_version.v3.bossConsole.components.*
import ai.rever.boss.old_version.v3.bossConsole.screens.*
import ai.rever.boss.old_version.v3.navigation.Screen
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import moe.tlaster.precompose.navigation.NavHost
import moe.tlaster.precompose.navigation.rememberNavigator
import moe.tlaster.precompose.viewmodel.viewModel

@Composable
fun BossConsole() {
    val viewModel = viewModel(
        modelClass = BossConsoleViewModel::class,
        creator = { BossConsoleViewModel() }
    )
    val navigator = rememberNavigator()
    val scaffoldState = rememberScaffoldState(rememberDrawerState(DrawerValue.Closed))
    val scope = rememberCoroutineScope()
    
    // Initialize the viewModel with the navigator
    LaunchedEffect(Unit) {
        viewModel.initialize(navigator)
    }
    
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
            ) {
                NavHost(
                    navigator = navigator,
                    initialRoute = Screen.Worklist.route
                ) {
                    scene(Screen.Worklist.route) {
                        WorklistScreen()
                    }
                    scene(Screen.SystemOfRecords.route) {
                        SystemOfRecordsScreen()
                    }
                    scene(Screen.OrgValues.route) {
                        OrgValuesScreen()
                    }
                    scene(Screen.GlobalLanager.route) {
                        GlobalLanagerScreen()
                    }
                    scene(Screen.MasteryRegistry.route) {
                        MasteryRegisteryScreen()
                    }
                    scene(Screen.TaskResolverRegistry.route) {
                        TaskResolverRegistryScreen()
                    }
                }
            }
        }
    }
}

