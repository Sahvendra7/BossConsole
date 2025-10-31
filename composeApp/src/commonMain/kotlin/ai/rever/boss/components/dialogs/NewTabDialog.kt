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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay

enum class TabType {
    URL, FILE, TERMINAL
}

// Simple URL parameter encoding
private fun encodeUrlParameter(input: String): String {
    return input
        .replace(" ", "+")
        .replace("&", "%26")
        .replace("#", "%23")
        .replace("?", "%3F")
        .replace("=", "%3D")
        .replace("/", "%2F")
}

// Platform-specific URL history provider
expect object UrlHistoryProvider {
    fun getSuggestions(query: String, limit: Int = 10): List<UrlSuggestion>
    fun deleteUrl(url: String)
}

data class UrlSuggestion(
    val url: String,
    val title: String,
    val isSearchSuggestion: Boolean = false
)

@Composable
fun NewTabDialog(
    onDismiss: () -> Unit,
    onCreateTab: (type: TabType, path: String) -> Unit,
    initialTabType: TabType? = null
) {
    var selectedType by remember { mutableStateOf(initialTabType ?: TabType.URL) }
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
    
    // URL autocomplete state
    var urlSuggestions by remember { mutableStateOf<List<UrlSuggestion>>(emptyList()) }
    var showUrlDropdown by remember { mutableStateOf(false) }
    var selectedSuggestionIndex by remember { mutableStateOf(-1) }
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    
    // Update suggestions when URL text changes
    LaunchedEffect(urlText, selectedType) {
        if (selectedType == TabType.URL && urlText.isNotEmpty()) {
            delay(100) // Small debounce
            urlSuggestions = UrlHistoryProvider.getSuggestions(urlText)
            showUrlDropdown = urlSuggestions.isNotEmpty()
            selectedSuggestionIndex = -1
        } else {
            urlSuggestions = emptyList()
            showUrlDropdown = false
        }
    }

    // Auto-scroll to selected suggestion when using arrow keys
    LaunchedEffect(selectedSuggestionIndex) {
        if (selectedSuggestionIndex >= 0 && urlSuggestions.isNotEmpty()) {
            listState.animateScrollToItem(selectedSuggestionIndex)
        }
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
                    Column {
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
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (selectedType == TabType.URL && event.type == KeyEventType.KeyDown) {
                                        when (event.key) {
                                            Key.DirectionDown -> {
                                                // Always consume arrow keys to prevent cursor movement in text field
                                                if (showUrlDropdown && urlSuggestions.isNotEmpty()) {
                                                    selectedSuggestionIndex = (selectedSuggestionIndex + 1).coerceAtMost(urlSuggestions.size - 1)
                                                }
                                                true
                                            }
                                            Key.DirectionUp -> {
                                                // Always consume arrow keys to prevent cursor movement in text field
                                                if (showUrlDropdown && urlSuggestions.isNotEmpty()) {
                                                    selectedSuggestionIndex = (selectedSuggestionIndex - 1).coerceAtLeast(-1)
                                                }
                                                true
                                            }
                                            Key.Enter -> {
                                                if (selectedSuggestionIndex >= 0 && selectedSuggestionIndex < urlSuggestions.size) {
                                                    val suggestion = urlSuggestions[selectedSuggestionIndex]
                                                    inputText = suggestion.url
                                                    urlText = suggestion.url
                                                    showUrlDropdown = false
                                                    handleCreateTab(selectedType, inputText, onCreateTab, onDismiss)
                                                    true
                                                } else false
                                            }
                                            Key.Escape -> {
                                                if (showUrlDropdown) {
                                                    showUrlDropdown = false
                                                    true
                                                } else false
                                            }
                                            else -> false
                                        }
                                    } else false
                                },
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
                                    if (selectedSuggestionIndex >= 0 && selectedSuggestionIndex < urlSuggestions.size) {
                                        val suggestion = urlSuggestions[selectedSuggestionIndex]
                                        handleCreateTab(selectedType, suggestion.url, onCreateTab, onDismiss)
                                    } else {
                                        handleCreateTab(selectedType, inputText, onCreateTab, onDismiss)
                                    }
                                }
                            )
                        )
                        
                        // URL suggestions dropdown
                        if (selectedType == TabType.URL && showUrlDropdown) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp),
                                backgroundColor = Color(0xFF2B2D30),
                                elevation = 4.dp,
                                shape = RoundedCornerShape(0.dp, 0.dp, 4.dp, 4.dp)
                            ) {
                                LazyColumn(state = listState) {
                                    itemsIndexed(urlSuggestions) { index, suggestion ->
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(
                                                    if (index == selectedSuggestionIndex) 
                                                        Color(0xFF4A9EFF).copy(alpha = 0.2f)
                                                    else 
                                                        Color.Transparent
                                                )
                                                .clickable {
                                                    inputText = suggestion.url
                                                    urlText = suggestion.url
                                                    showUrlDropdown = false
                                                    handleCreateTab(TabType.URL, suggestion.url, onCreateTab, onDismiss)
                                                }
                                                .padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = if (suggestion.isSearchSuggestion) Icons.Default.Search else Icons.Default.History,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint = Color(0xFF999999)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = suggestion.title.ifEmpty { suggestion.url },
                                                    fontSize = 14.sp,
                                                    color = Color.White,
                                                    maxLines = 1
                                                )
                                                if (suggestion.title.isNotEmpty()) {
                                                    Text(
                                                        text = suggestion.url,
                                                        fontSize = 12.sp,
                                                        color = Color(0xFF999999),
                                                        maxLines = 1
                                                    )
                                                }
                                            }
                                            IconButton(
                                                onClick = {
                                                    UrlHistoryProvider.deleteUrl(suggestion.url)
                                                    // Update suggestions
                                                    urlSuggestions = urlSuggestions.filterNot { it.url == suggestion.url }
                                                    if (urlSuggestions.isEmpty()) {
                                                        showUrlDropdown = false
                                                    }
                                                },
                                                modifier = Modifier.size(24.dp)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Delete",
                                                    modifier = Modifier.size(16.dp),
                                                    tint = Color(0xFF999999)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
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
            processUrlInput(input)
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

// Helper function to process URL input - either as URL or search query
private fun processUrlInput(input: String): String {
    val trimmed = input.trim()
    
    // If it's already a full URL, return as-is
    if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
        return trimmed
    }
    
    // Check if it looks like a URL (contains dots and no spaces)
    val looksLikeUrl = trimmed.contains(".") && !trimmed.contains(" ")
    
    // Check for common URL patterns
    val urlPattern = Regex("""^([a-zA-Z0-9-]+\.)+[a-zA-Z]{2,}(/.*)?$""")
    val isLikelyUrl = looksLikeUrl || urlPattern.matches(trimmed)
    
    // Check for localhost patterns
    val isLocalhost = trimmed.startsWith("localhost") || 
                     trimmed.matches(Regex("""^127\.0\.0\.1(:\d+)?(/.*)?$""")) ||
                     trimmed.matches(Regex("""^localhost(:\d+)?(/.*)?$"""))
    
    return when {
        isLocalhost -> "http://$trimmed"
        isLikelyUrl -> "https://$trimmed"
        else -> "https://www.google.com/search?q=${encodeUrlParameter(trimmed)}"
    }
}
