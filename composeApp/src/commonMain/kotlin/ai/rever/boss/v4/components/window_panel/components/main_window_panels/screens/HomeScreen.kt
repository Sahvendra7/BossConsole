package ai.rever.boss.v4.components.window_panel.components.main_window_panels.screens

import ai.rever.boss.v4.components.window_panel.components.ContextMenuDemo
import ai.rever.boss.v4.components.window_panel.components.main_window_panels.BossConsoleComponent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Button
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import com.arkivanov.decompose.router.stack.StackNavigation
import com.arkivanov.decompose.router.stack.push

/**
 * Home screen UI
 */
@Composable
fun HomeScreen(component: HomeComponent) {
    var showContextMenuDemo by remember { mutableStateOf(false) }
    
    Scaffold(
        topBar = {
            Text("Home", modifier = Modifier.padding(16.dp))
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (showContextMenuDemo) {
                ContextMenuDemo()
            } else {
                Column(
                    modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Welcome to BOSS v4")

                    Button(
                        onClick = { component.onSettingsClicked() },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text("Go to Settings")
                    }

                    Button(
                        onClick = { component.onDetailClicked("sample-id") },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text("Go to Details")
                    }
                    
                    Button(
                        onClick = { showContextMenuDemo = true },
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text("Show Context Menu Demo")
                    }
                }
            }
            
            if (showContextMenuDemo) {
                Button(
                    onClick = { showContextMenuDemo = false },
                    modifier = Modifier.align(Alignment.TopStart).padding(16.dp)
                ) {
                    Text("Back")
                }
            }
        }
    }
}

/**
 * Home screen component
 */
class HomeComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<BossConsoleComponent.Config>
) : ComponentContext by componentContext {

    fun onSettingsClicked() {
        navigation.push(BossConsoleComponent.Config.Settings)
    }

    fun onDetailClicked(id: String) {
        navigation.push(BossConsoleComponent.Config.Detail(id))
    }
}