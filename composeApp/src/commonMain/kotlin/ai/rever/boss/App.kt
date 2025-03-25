package ai.rever.boss

import androidx.compose.material.*
import androidx.compose.runtime.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf(Screen.Home) }
        var selectedItem by remember { mutableStateOf(works[0]) }

        when (currentScreen) {
            Screen.Home -> HomeScreen(
                onScreenChange = { newScreen -> currentScreen = newScreen }
            )
            Screen.WorkList -> WorkList(
                onNavigateToDetails = { work ->
                    selectedItem = work
                    currentScreen = Screen.Details
                },
                onNavigateBack = { currentScreen = Screen.Home }
            )
            Screen.Details -> WorkDetails(
                work = selectedItem,
                onBack = { currentScreen = Screen.WorkList }
            )
            Screen.APIIntegration -> ApiIntegration(onBack = { currentScreen = Screen.Home })
            Screen.ERPIntegration -> ERPIntegration(onBack = { currentScreen = Screen.Home })
            Screen.PreviewFileForWorkList -> PreviewFileForWorkList(onBack = { currentScreen = Screen.WorkList })
        }
    }
}

enum class Screen {
    Home, WorkList, Details, APIIntegration, ERPIntegration, PreviewFileForWorkList
}