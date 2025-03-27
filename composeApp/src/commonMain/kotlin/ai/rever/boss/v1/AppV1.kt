package ai.rever.boss.v1

import ai.rever.boss.works
import androidx.compose.material.*
import androidx.compose.runtime.*
import org.jetbrains.compose.ui.tooling.preview.Preview

@Composable
@Preview
fun AppV1() {
    MaterialTheme {
        var currentScreenn by remember { mutableStateOf(Screenn.Home) }
        var selectedItem by remember { mutableStateOf(works[0]) }

        when (currentScreenn) {
            Screenn.Home -> HomeScreen(
                onScreenChange = { newScreen -> currentScreenn = newScreen }
            )
            Screenn.WorkList -> WorkList(
                onNavigateToDetails = { work ->
                    selectedItem = work
                    currentScreenn = Screenn.Details
                },
                onNavigateBack = { currentScreenn = Screenn.Home }
            )
            Screenn.Details -> WorkDetails(
                work = selectedItem,
                onBack = { currentScreenn = Screenn.WorkList }
            )
            Screenn.APIIntegration -> ApiIntegration(onBack = { currentScreenn = Screenn.Home })
            Screenn.ERPIntegration -> ERPIntegration(onBack = { currentScreenn = Screenn.Home })
            Screenn.PreviewFileForWorkList -> PreviewFileForWorkList(onBack = { currentScreenn = Screenn.WorkList })
        }
    }
}

enum class Screenn {
    Home, WorkList, Details, APIIntegration, ERPIntegration, PreviewFileForWorkList
}