package ai.rever.boss.v4.components.window_panel.components.main_window_panel.screens

import ai.rever.boss.v4.components.window_panel.components.main_window_panel.BossConsoleComponent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.pop

/**
 * Settings screen UI
 */
@Composable
fun SettingsScreen(component: SettingsComponent) {
    Scaffold(
        topBar = {
            Text("Settings", modifier = Modifier.padding(16.dp))
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Settings Screen")

                Button(
                    onClick = { component.onBackClicked() },
                    modifier = Modifier.padding(8.dp)
                ) {
                    Text("Go Back")
                }
            }
        }
    }
}

/**
 * Settings screen component
 */
class SettingsComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<BossConsoleComponent.Config>
) : ComponentContext by componentContext {

    fun onBackClicked() {
        navigation.pop()
    }
}

