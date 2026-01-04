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
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import ai.rever.boss.platform.rememberFilePicker
import ai.rever.boss.platform.rememberDirectoryPicker
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.components.plugin.panels.left_top.FileNode
import ai.rever.boss.components.plugin.panels.left_top.NodeLoadingState
import ai.rever.boss.components.plugin.panels.left_top.scanDirectory
import ai.rever.boss.components.plugin.panels.left_top.directoryHasChildren
import ai.rever.boss.components.plugin.panels.left_top.scanDirectoryWithDepth
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Validates and sanitizes a file path to prevent path traversal attacks.
 *
 * @param path The file path to validate
 * @param basePath Optional base path that the file must be within (null allows any path)
 * @return The canonical path if valid, null if the path is invalid or attempts traversal
 */
private fun validateFilePath(path: String, basePath: String? = null): String? {
    if (path.isBlank()) return null

    return try {
        val file = File(path)
        val canonicalPath = file.canonicalPath

        // If a base path is provided, ensure the file is within it
        if (basePath != null) {
            val baseFile = File(basePath)
            val canonicalBase = baseFile.canonicalPath

            // The file must be within the base directory
            if (!canonicalPath.startsWith(canonicalBase)) {
                println("[PathValidation] Path traversal attempt blocked: $path (outside $basePath)")
                return null
            }
        }

        // Validate the file exists
        if (!file.exists()) {
            println("[PathValidation] File does not exist: $path")
            return null
        }

        canonicalPath
    } catch (e: Exception) {
        println("[PathValidation] Invalid path: $path - ${e.message}")
        null
    }
}

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
    var fileText by remember { mutableStateOf("") }
    var terminalCommand by remember { mutableStateOf("") }
    var inputText by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    val terminalFocusRequester = remember { FocusRequester() }
    
    // URL autocomplete state
    var urlSuggestions by remember { mutableStateOf<List<UrlSuggestion>>(emptyList()) }
    var showUrlDropdown by remember { mutableStateOf(false) }
    var selectedSuggestionIndex by remember { mutableStateOf(-1) }
    val listState = rememberLazyListState()

    // File picker for browsing files
    val filePicker = rememberFilePicker(
        onFileSelected = { path, _ ->
            path?.let {
                fileText = it
                inputText = it
            }
        },
        fileExtensions = emptyList() // Allow all files
    )

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
                            inputText = terminalCommand
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Input field
                Column {
                    // Show terminal command input or URL/File input
                    if (selectedType == TabType.TERMINAL) {
                        // Terminal command input
                        LaunchedEffect(selectedType) {
                            if (selectedType == TabType.TERMINAL) {
                                terminalFocusRequester.requestFocus()
                            }
                        }
                        OutlinedTextField(
                            value = terminalCommand,
                            onValueChange = { newValue ->
                                terminalCommand = newValue
                                inputText = newValue
                            },
                            label = {
                                Text(
                                    "Initial command (optional)",
                                    color = Color(0xFF999999)
                                )
                            },
                            placeholder = {
                                Text(
                                    "e.g., npm run dev",
                                    color = Color(0xFF666666)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(terminalFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                        handleCreateTab(selectedType, terminalCommand, onCreateTab, onDismiss)
                                        true
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
                                    handleCreateTab(selectedType, terminalCommand, onCreateTab, onDismiss)
                                }
                            )
                        )
                    } else if (selectedType == TabType.FILE) {
                        // Current project/folder selector
                        val selectedProject by ProjectState.selectedProject.collectAsState()
                        val recentProjects by ProjectState.recentProjects.collectAsState()
                        var showFolderDropdown by remember { mutableStateOf(false) }

                        // File tree state
                        var fileTree by remember { mutableStateOf<FileNode?>(null) }
                        var expandedPaths by remember { mutableStateOf(setOf<String>()) }
                        var isLoadingTree by remember { mutableStateOf(false) }
                        val coroutineScope = rememberCoroutineScope()

                        // Load file tree when project changes
                        LaunchedEffect(selectedProject.path) {
                            if (selectedProject.path.isNotEmpty()) {
                                isLoadingTree = true
                                fileTree = try {
                                    withContext(Dispatchers.IO) {
                                        scanDirectory(selectedProject.path)
                                    }
                                } catch (e: Exception) {
                                    println("[NewTabDialog] Error scanning directory: ${e.message}")
                                    null
                                }
                                isLoadingTree = false
                            } else {
                                fileTree = null
                            }
                        }

                        // Directory picker for selecting new folder
                        val directoryPicker = rememberDirectoryPicker { path ->
                            path?.let {
                                val projectName = it.substringAfterLast('/').ifEmpty { "Unknown" }
                                ProjectState.selectProject(
                                    ai.rever.boss.components.plugin.panels.left_top.Project(
                                        name = projectName,
                                        path = it
                                    )
                                )
                                // Clear expanded paths for new folder
                                expandedPaths = emptySet()
                            }
                        }

                        // Show "Open Project" button when no project is selected
                        if (selectedProject.path.isEmpty()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { directoryPicker.pickDirectory() },
                                    colors = ButtonDefaults.buttonColors(
                                        backgroundColor = Color(0xFF4A9EFF),
                                        contentColor = Color.White
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.FolderOpen,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Open Project")
                                }
                            }
                        } else {
                            // Folder selector dropdown
                            Box(modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { showFolderDropdown = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        backgroundColor = Color(0xFF1E1F22),
                                        contentColor = Color.White
                                    ),
                                    border = ButtonDefaults.outlinedBorder.copy(
                                        brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF555555))
                                    ),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.Folder,
                                        contentDescription = "Folder",
                                        tint = Color(0xFF6B9EFF),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = selectedProject.name,
                                        color = Color.White,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = "Expand",
                                        tint = Color(0xFF999999)
                                    )
                                }

                                DropdownMenu(
                                    expanded = showFolderDropdown,
                                    onDismissRequest = { showFolderDropdown = false },
                                    modifier = Modifier
                                        .width(450.dp)
                                        .background(Color(0xFF2B2D30))
                                ) {
                                    // Recent projects
                                    if (recentProjects.isNotEmpty()) {
                                        recentProjects.forEach { project ->
                                            DropdownMenuItem(
                                                onClick = {
                                                    ProjectState.selectProject(project)
                                                    expandedPaths = emptySet()
                                                    showFolderDropdown = false
                                                }
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Outlined.Folder,
                                                    contentDescription = null,
                                                    tint = Color(0xFF6B9EFF),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Column {
                                                    Text(
                                                        text = project.name,
                                                        color = Color.White,
                                                        fontSize = 14.sp
                                                    )
                                                    Text(
                                                        text = project.path,
                                                        color = Color(0xFF999999),
                                                        fontSize = 11.sp
                                                    )
                                                }
                                            }
                                        }
                                        Divider(color = Color(0xFF555555))
                                    }

                                    // Browse option
                                    DropdownMenuItem(
                                        onClick = {
                                            showFolderDropdown = false
                                            directoryPicker.pickDirectory()
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.FolderOpen,
                                            contentDescription = null,
                                            tint = Color(0xFF999999),
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Browse...",
                                            color = Color.White,
                                            fontSize = 14.sp
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                        // Helper function to find node by path in tree
                        fun findNodeByPath(root: FileNode?, targetPath: String): FileNode? {
                            if (root == null) return null
                            if (root.path == targetPath) return root
                            for (child in root.children) {
                                val found = findNodeByPath(child, targetPath)
                                if (found != null) return found
                            }
                            return null
                        }

                        // Helper function to update node at path with new data
                        fun updateNodeAtPath(
                            root: FileNode,
                            targetPath: String,
                            update: (FileNode) -> FileNode
                        ): FileNode {
                            if (root.path == targetPath) {
                                return update(root)
                            }
                            return root.copy(
                                children = root.children.map { child ->
                                    if (targetPath.startsWith(child.path + "/") || targetPath == child.path) {
                                        updateNodeAtPath(child, targetPath, update)
                                    } else {
                                        child
                                    }
                                }
                            )
                        }

                        // File tree browser
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            backgroundColor = Color(0xFF1E1F22),
                            shape = RoundedCornerShape(4.dp),
                            elevation = 0.dp
                        ) {
                            if (isLoadingTree) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(24.dp),
                                        color = Color(0xFF4A9EFF),
                                        strokeWidth = 2.dp
                                    )
                                }
                            } else if (fileTree != null && fileTree?.children?.isNotEmpty() == true) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .verticalScroll(rememberScrollState())
                                        .padding(4.dp)
                                ) {
                                    fileTree?.children?.forEach { node ->
                                        DialogFileTreeItem(
                                            node = node,
                                            level = 0,
                                            expandedPaths = expandedPaths,
                                            onToggleExpanded = { path ->
                                                if (expandedPaths.contains(path)) {
                                                    // Collapse - just remove from expanded set
                                                    expandedPaths = expandedPaths - path
                                                } else {
                                                    // Expand - add to expanded set and load children
                                                    expandedPaths = expandedPaths + path

                                                    // Load children if needed
                                                    val currentTree = fileTree
                                                    if (currentTree != null) {
                                                        val targetNode = findNodeByPath(currentTree, path)
                                                        if (targetNode?.isDirectory == true && targetNode.children.isEmpty()) {
                                                            // Need to load children
                                                            coroutineScope.launch {
                                                                try {
                                                                    val scannedNode = withContext(Dispatchers.IO) {
                                                                        scanDirectoryWithDepth(path, maxDepth = 1, startDepth = 0)
                                                                    }
                                                                    if (scannedNode != null) {
                                                                        val loadedChildren = scannedNode.children.map { child ->
                                                                            if (child.isDirectory) {
                                                                                val hasKids = try {
                                                                                    directoryHasChildren(child.path)
                                                                                } catch (e: Exception) {
                                                                                    false
                                                                                }
                                                                                child.copy(hasChildren = hasKids)
                                                                            } else {
                                                                                child
                                                                            }
                                                                        }
                                                                        fileTree = updateNodeAtPath(currentTree, path) { existingNode ->
                                                                            existingNode.copy(
                                                                                children = loadedChildren,
                                                                                hasChildren = loadedChildren.isNotEmpty()
                                                                            )
                                                                        }
                                                                    }
                                                                } catch (e: Exception) {
                                                                    println("[NewTabDialog] Error loading folder children: ${e.message}")
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            },
                                            onFileClick = { file ->
                                                inputText = file.path
                                                fileText = file.path
                                            }
                                        )
                                    }
                                }
                            } else if (fileTree != null && fileTree?.children?.isEmpty() == true) {
                                // Empty folder (hidden files like .git are excluded)
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "No visible files",
                                            color = Color(0xFF999999),
                                            fontSize = 13.sp
                                        )
                                        Text(
                                            text = "(hidden files and build folders are excluded)",
                                            color = Color(0xFF666666),
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            } else {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Unable to load files",
                                        color = Color(0xFF999999),
                                        fontSize = 13.sp
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // File input with browse button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = inputText,
                                onValueChange = { newValue ->
                                    inputText = newValue
                                    fileText = newValue
                                },
                                label = {
                                    Text(
                                        "File path",
                                        color = Color(0xFF999999)
                                    )
                                },
                                placeholder = {
                                    Text(
                                        "Select a file above or enter path",
                                        color = Color(0xFF666666)
                                    )
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .focusRequester(focusRequester)
                                    .onPreviewKeyEvent { event ->
                                        if (event.type == KeyEventType.KeyDown && event.key == Key.Enter) {
                                            handleCreateTab(selectedType, inputText, onCreateTab, onDismiss)
                                            true
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
                                        handleCreateTab(selectedType, inputText, onCreateTab, onDismiss)
                                    }
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            IconButton(
                                onClick = { filePicker.pickFile() },
                                modifier = Modifier.size(48.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = "Browse files",
                                    tint = Color(0xFF999999)
                                )
                            }
                        }
                        }
                    } else {
                        // URL input
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { newValue ->
                                inputText = newValue
                                urlText = newValue
                            },
                            label = {
                                Text(
                                    "Enter URL or search term",
                                    color = Color(0xFF999999)
                                )
                            },
                            placeholder = {
                                Text(
                                    "https://example.com or search...",
                                    color = Color(0xFF666666)
                                )
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown) {
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
                        if (showUrlDropdown) {
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
                            val input = if (selectedType == TabType.TERMINAL) terminalCommand else inputText
                            handleCreateTab(selectedType, input, onCreateTab, onDismiss)
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
            // Validate file path to prevent path traversal attacks
            val validatedPath = validateFilePath(input.trim())
            if (validatedPath == null) {
                // Path validation failed - don't create the tab
                println("[NewTabDialog] File path validation failed: ${input.trim()}")
                return
            }
            validatedPath
        }
        TabType.TERMINAL -> {
            // Pass the command (or empty string if none)
            input.trim()
        }
    }

    onCreateTab(type, processedInput)
    onDismiss()
}

// Helper function to process URL input - either as URL or search query
private fun processUrlInput(input: String): String {
    val trimmed = input.trim()
    val lowerTrimmed = trimmed.lowercase()

    // If it's already a full URL or special scheme, return as-is
    if (lowerTrimmed.startsWith("http://") || lowerTrimmed.startsWith("https://") ||
        lowerTrimmed.startsWith("file://") || lowerTrimmed.startsWith("javascript:")) {
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

/**
 * Simplified file tree item for the NewTabDialog file browser.
 */
@Composable
private fun DialogFileTreeItem(
    node: FileNode,
    level: Int,
    expandedPaths: Set<String>,
    onToggleExpanded: (String) -> Unit,
    onFileClick: (FileNode) -> Unit
) {
    val isExpanded = expandedPaths.contains(node.path)
    val hasChildren = node.isDirectory && (node.hasChildren != false || node.children.isNotEmpty())

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(24.dp)
                .clickable {
                    if (node.isDirectory) {
                        onToggleExpanded(node.path)
                    } else {
                        onFileClick(node)
                    }
                }
                .padding(start = (8 + level * 12).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Expand/collapse icon for directories
            if (node.isDirectory && hasChildren) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                    tint = Color(0xFF999999),
                    modifier = Modifier.size(14.dp)
                )
            } else {
                Spacer(modifier = Modifier.width(14.dp))
            }

            Spacer(modifier = Modifier.width(2.dp))

            // File/folder icon
            Icon(
                imageVector = when {
                    node.isDirectory -> if (isExpanded) Icons.Outlined.Folder else Icons.Outlined.Folder
                    node.name.endsWith(".kt") || node.name.endsWith(".kts") -> Icons.Outlined.Code
                    node.name.endsWith(".md") -> Icons.Outlined.Description
                    else -> Icons.AutoMirrored.Outlined.InsertDriveFile
                },
                contentDescription = if (node.isDirectory) "Folder" else "File",
                tint = when {
                    node.isDirectory -> Color(0xFF6B9EFF)
                    node.name.endsWith(".kt") || node.name.endsWith(".kts") -> Color(0xFFE57373)
                    else -> Color(0xFF90A4AE)
                },
                modifier = Modifier.size(14.dp)
            )

            Spacer(modifier = Modifier.width(4.dp))

            // File/folder name
            Text(
                text = node.name,
                fontSize = 12.sp,
                color = Color(0xFFCCCCCC)
            )
        }

        // Show children if expanded
        if (node.isDirectory && isExpanded && node.children.isNotEmpty()) {
            node.children.forEach { child ->
                DialogFileTreeItem(
                    node = child,
                    level = level + 1,
                    expandedPaths = expandedPaths,
                    onToggleExpanded = onToggleExpanded,
                    onFileClick = onFileClick
                )
            }
        }
    }
}
