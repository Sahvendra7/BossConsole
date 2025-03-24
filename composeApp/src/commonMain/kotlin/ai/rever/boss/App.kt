package ai.rever.boss

import androidx.compose.material.*
import androidx.compose.runtime.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun App() {
    MaterialTheme {
        var currentScreen by remember { mutableStateOf("home") }
        var selectedItem by remember { mutableStateOf(works[0]) }

        when (currentScreen) {
            "home" -> HomeScreen(
                onNavigateToWorklist = { currentScreen = "work" }
            )
            "work" -> Worklist(
                onNavigateToDetails = { work ->
                    selectedItem = work
                    currentScreen = "details"
                },
                onNavigateBack = { currentScreen = "home" }
            )
            "details" -> WorkDetails(
                work = selectedItem,
                onBack = { currentScreen = "work" }
            )
        }
    }
}
