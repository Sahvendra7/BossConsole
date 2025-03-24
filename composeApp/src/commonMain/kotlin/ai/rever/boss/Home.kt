package ai.rever.boss


import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(onNavigateToWorklist: () -> Unit) {
    var promptText by remember { mutableStateOf("") }
    var showSourceSelector by remember { mutableStateOf(false) }
    
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("Lighthouse", style = MaterialTheme.typography.h4)
            OutlinedTextField(
                value = promptText,
                onValueChange = { promptText = it },
                label = { Text("Enter your work description") },
                modifier = Modifier.padding(16.dp)
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onNavigateToWorklist) {
                    Text("Get it done!")
                }
                OutlinedButton(onClick = onNavigateToWorklist) {
                    Text("Worklist >")
                }
            }
        }
        BottomBar(
            onNavigateToWorklist = onNavigateToWorklist,
            onAddWorklistSource = { showSourceSelector = true }
        )
    }

    if (showSourceSelector) {
        SourceSelectorDialog(
            onDismiss = { showSourceSelector = false },
            onSourceSelected = { source ->
                // Handle source selection
                showSourceSelector = false
            }
        )
    }
}

@Composable
private fun BottomBar(
    onNavigateToWorklist: () -> Unit,
    onAddWorklistSource: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlinedButton(onClick = onAddWorklistSource) {
            Text("add worklist sources")
        }
        OutlinedButton(onClick = onNavigateToWorklist) {
            Text("add history of records")
        }
        OutlinedButton(onClick = onNavigateToWorklist) {
            Text("add organisation context")
        }
    }
}

