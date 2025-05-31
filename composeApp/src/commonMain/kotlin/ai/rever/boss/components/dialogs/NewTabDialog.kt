package ai.rever.boss.components.dialogs

import ai.rever.boss.utils.SystemUtils
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

enum class TabType {
    URL, FILE, TERMINAL
}

@Composable
fun NewTabDialog(
    onDismiss: () -> Unit,
    onCreateTab: (type: TabType, path: String) -> Unit
) {
    var selectedType by remember { mutableStateOf(TabType.URL) }
    var urlText by remember { mutableStateOf("") }
    var fileText by remember { mutableStateOf(SystemUtils.getDefaultProjectPath() + "/README.md") }
    var inputText by remember { 
        mutableStateOf(
            when (selectedType) {
                TabType.URL -> urlText
                TabType.FILE -> fileText
                TabType.TERMINAL -> "Terminal"
            }
        )
    }
    val focusRequester = remember { FocusRequester() }
    
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .width(500.dp)
                .onKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                        onDismiss()
                        true
                    } else {
                        false
                    }
                },
            shape = RoundedCornerShape(8.dp),
            backgroundColor = Color(0xFF2B2D30),
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Title
                Text(
                    text = "New Tab",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // Type selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TabTypeOption(
                        icon = Icons.Default.Language,
                        label = "URL",
                        isSelected = selectedType == TabType.URL,
                        onClick = { 
                            // Save current text before switching
                            when (selectedType) {
                                TabType.FILE -> fileText = inputText
                                else -> {}
                            }
                            selectedType = TabType.URL
                            inputText = urlText
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    TabTypeOption(
                        icon = Icons.AutoMirrored.Filled.InsertDriveFile,
                        label = "File",
                        isSelected = selectedType == TabType.FILE,
                        onClick = { 
                            // Save current text before switching
                            when (selectedType) {
                                TabType.URL -> urlText = inputText
                                else -> {}
                            }
                            selectedType = TabType.FILE
                            inputText = fileText
                        },
                        modifier = Modifier.weight(1f)
                    )
                    
                    TabTypeOption(
                        icon = Icons.Outlined.Terminal,
                        label = "Terminal",
                        isSelected = selectedType == TabType.TERMINAL,
                        onClick = { 
                            // Save current text before switching
                            when (selectedType) {
                                TabType.URL -> urlText = inputText
                                TabType.FILE -> fileText = inputText
                                else -> {}
                            }
                            selectedType = TabType.TERMINAL
                            inputText = "Terminal"
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Input field (hide for Terminal type)
                if (selectedType != TabType.TERMINAL) {
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { newValue ->
                            inputText = newValue
                            // Update the appropriate state based on current type
                            when (selectedType) {
                                TabType.URL -> urlText = newValue
                                TabType.FILE -> fileText = newValue
                                else -> {}
                            }
                        },
                        label = { 
                            Text(
                                when (selectedType) {
                                    TabType.URL -> "Enter URL (e.g., https://example.com)"
                                    TabType.FILE -> "Enter file path"
                                    else -> "" // This should never happen since we check selectedType != TERMINAL above
                                },
                                color = Color(0xFF999999)
                            )
                        },
                        placeholder = {
                            Text(
                                when (selectedType) {
                                    TabType.URL -> "https://"
                                    TabType.FILE -> "README.md"
                                    else -> "" // This should never happen since we check selectedType != TERMINAL above
                                },
                                color = Color(0xFF666666)
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            cursorColor = Color.White,
                            focusedBorderColor = Color(0xFF4A9EFF),
                            unfocusedBorderColor = Color(0xFF555555),
                            backgroundColor = Color(0xFF1E1F22)
                        ),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                handleCreateTab(selectedType, inputText, onCreateTab, onDismiss)
                            }
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = Color(0xFF999999)
                        )
                    ) {
                        Text("Cancel")
                    }
                    
                    Spacer(modifier = Modifier.width(8.dp))
                    
                    Button(
                        onClick = {
                            handleCreateTab(selectedType, inputText, onCreateTab, onDismiss)
                        },
                        enabled = selectedType == TabType.TERMINAL || inputText.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4A9EFF),
                            contentColor = Color.White,
                            disabledBackgroundColor = Color(0xFF3A3A3A),
                            disabledContentColor = Color(0xFF666666)
                        )
                    ) {
                        Text(
                            when (selectedType) {
                                TabType.URL -> "Fluck it"
                                TabType.FILE -> "Open"
                                TabType.TERMINAL -> "Open Terminal"
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabTypeOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(80.dp)
            .clickable { onClick() },
        backgroundColor = if (isSelected) Color(0xFF4A9EFF).copy(alpha = 0.2f) else Color(0xFF3C3F41),
        shape = RoundedCornerShape(4.dp),
        elevation = if (isSelected) 2.dp else 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) Color(0xFF4A9EFF) else Color(0xFF999999),
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                fontSize = 12.sp,
                color = if (isSelected) Color.White else Color(0xFF999999)
            )
        }
    }
}

private fun handleCreateTab(
    type: TabType,
    input: String,
    onCreateTab: (TabType, String) -> Unit,
    onDismiss: () -> Unit
) {
    if (type != TabType.TERMINAL && input.isBlank()) return
    
    val processedInput = when (type) {
        TabType.URL -> {
            when {
                input.startsWith("http://") || input.startsWith("https://") -> input
                input.contains("://") -> input
                else -> "https://$input"
            }
        }
        TabType.FILE -> {
            input.trim()
        }
        TabType.TERMINAL -> {
            "Terminal" // Terminal doesn't need input
        }
    }
    
    onCreateTab(type, processedInput)
    onDismiss()
}