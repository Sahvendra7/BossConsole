package ai.rever.boss.v4

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

/**
 * Home screen UI
 */
@Composable
fun HomeScreen(component: HomeComponent) {
    Scaffold(
        topBar = {
            Text("Home", modifier = Modifier.padding(16.dp))
        }
    ) { padding ->
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
        }
    }
}