package ai.rever.boss.old_version.v2.ui

import ai.rever.boss.old_version.v2.ui.common.BossHeader
import ai.rever.boss.old_version.v2.ui.lanager.LanagerScreen
import ai.rever.boss.old_version.v2.ui.lighthouse.LighthouseScreen
import ai.rever.boss.old_version.v2.ui.lighthouse.SystemOfRecordsScreen
import ai.rever.boss.old_version.v2.ui.lighthouse.WorklistActions
import ai.rever.boss.old_version.v2.ui.lighthouse.WorklistScreen
import ai.rever.boss.old_version.v2.viewmodel.AppViewModel
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.runtime.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Preview
@Composable
fun AppV2() {
    MaterialTheme {
        // Create and remember the ViewModel
        val viewModel = remember { AppViewModel() }
        // Collect the UI state as a composable state
        val uiState by viewModel.uiState.collectAsState()
        // hiddenBackButton value as compute variable
        val hiddenBackButton by derivedStateOf {
            uiState.currentScreen.parent == null
        }

        Scaffold(
            topBar = {
                BossHeader(
                    headerText = uiState.currentScreen.name,
                    backText = uiState.currentScreen.parent?.name,
                    hiddenBackButton = hiddenBackButton,
                    onNavigateBack = {
                        uiState.currentScreen.parent?.let {
                            viewModel.navigateTo(it)
                        }
                    },
                    actions = uiState.currentScreen.actions
                )
            }
        ) {
            // Main app navigation based on currentScreen
            when (uiState.currentScreen) {
                Screen.BossConsole -> BossScreen(
                    onScreenChange = { screen -> viewModel.navigateTo(screen) }
                )

                Screen.Lighthouse -> LighthouseScreen(
                    onScreenChange = { screen -> viewModel.navigateTo(screen) }
                )

                Screen.Lanager -> LanagerScreen(
                    onScreenChange = { screen -> viewModel.navigateTo(screen) }
                )

                Screen.Worklist -> WorklistScreen(
                    onScreenChange = { screen -> viewModel.navigateTo(screen) }
                )

                Screen.SystemOfRecords -> SystemOfRecordsScreen(
                    onScreenChange = { screen -> viewModel.navigateTo(screen) }
                )
            }
        }
    }
}

enum class Screen(
    val parent: Screen? = null,
    val actions: @Composable (() -> Unit)? = null
) {
    BossConsole,
    Lighthouse(parent = BossConsole),
    Lanager(parent = Lighthouse),
    Worklist(parent = Lighthouse, actions = { WorklistActions(onScreenChange = {}) }),
    SystemOfRecords(parent = Lighthouse)
}