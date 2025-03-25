package ai.rever.boss

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import boss_kotlin.composeapp.generated.resources.Res
import boss_kotlin.composeapp.generated.resources.compose_multiplatform
import org.jetbrains.compose.resources.painterResource

@Composable
fun HomeScreen(
    onScreenChange: (Screen) -> Unit
) {
    var promptText by remember { mutableStateOf("") }
    var showSourceSelector by remember { mutableStateOf(false) }
    var file by remember { mutableStateOf("") }
    val snackbarHostState = remember { SnackbarHostState() }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Image(
                    painter = painterResource(Res.drawable.compose_multiplatform),
                    contentDescription = "Lighthouse Logo",
                    modifier = Modifier
                        .size(120.dp)
                        .padding(bottom = 16.dp)
                )
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
                    Button(onClick = { onScreenChange(Screen.WorkList) } ) {
                        Text("Get it done!")
                    }
                    OutlinedButton(onClick = { onScreenChange(Screen.WorkList) } ) {
                        Text("WorkList >")
                    }
                }
            }
            BottomBar(
                onNavigateToWorklist = { onScreenChange(Screen.WorkList) },
                onAddWorklistSource = { showSourceSelector = true }
            )
        }

        if (showSourceSelector) {
            SourceSelectorDialog(
                onDismiss = { showSourceSelector = false },
                onSourceSelected = { selectedSource, selectedFile ->

                    showSourceSelector = false
                    // Now we can change the screen directly if needed
                    // onScreenChange("someOtherScreen")
                    when (selectedSource) {
                        SourceType.API -> onScreenChange(Screen.APIIntegration)
                        SourceType.ERP -> onScreenChange(Screen.ERPIntegration)
                        SourceType.FILE -> {
                            selectedFile?.let {
                                file = it
//                                onScreenChange(Screen.PreviewFileForWorkList)
                            }
                        }
                    }
                }
            )
        }

        if (file != "") {
            LaunchedEffect(file) {
                snackbarHostState.showSnackbar("Selected source: $file")
            }
        }
    }
}

@Composable
fun BottomBar(
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
        OutlinedButton(onClick = onAddWorklistSource) {
            Text("add system of records")
        }
        OutlinedButton(onClick = onNavigateToWorklist) {
            Text("add organisation context")
        }
    }
}
