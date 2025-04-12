package ai.rever.boss.v4.screens

import ai.rever.boss.v4.BossConsoleComponent
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
 * Detail screen UI
 */
@Composable
fun DetailScreen(component: DetailComponent) {
    Scaffold(
        topBar = {
            Text("Details", modifier = Modifier.padding(16.dp))
        }
    ) { padding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Detail Screen for ID: ${component.id}")

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
 * Detail screen component
 */
class DetailComponent(
    componentContext: ComponentContext,
    private val navigation: StackNavigation<BossConsoleComponent.Config>,
    val id: String
) : ComponentContext by componentContext {

    fun onBackClicked() {
        navigation.pop()
    }
}