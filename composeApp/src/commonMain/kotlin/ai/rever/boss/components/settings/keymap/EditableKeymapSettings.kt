package ai.rever.boss.components.settings.keymap

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ai.rever.boss.keymap.KeymapSettingsManager
import ai.rever.boss.keymap.handler.KeymapValidator
import ai.rever.boss.keymap.model.KeyBinding
import ai.rever.boss.keymap.model.KeymapSettings
import ai.rever.boss.keymap.lifecycle.ShortcutLifecycleManager
import kotlinx.coroutines.launch

/**
 * Main editable keyboard shortcuts settings screen.
 * Allows users to view, search, edit, and customize keyboard shortcuts.
 */
@Composable
fun EditableKeymapSettings() {
    val keymapSettings by KeymapSettingsManager.currentSettings.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var editingBinding by remember { mutableStateOf<KeyBinding?>(null) }
    var showTestDialog by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    // Compute conflicts
    val conflicts = remember(keymapSettings) {
        KeymapValidator.validate(keymapSettings)
    }

    // Filter shortcuts based on search and category
    val filteredShortcuts = remember(keymapSettings, searchQuery, selectedCategory) {
        val shortcuts = keymapSettings.shortcuts.values.toList()
        shortcuts.filter { binding ->
            val matchesSearch = searchQuery.isBlank() ||
                    binding.description.contains(searchQuery, ignoreCase = true) ||
                    binding.displayString().contains(searchQuery, ignoreCase = true) ||
                    binding.actionId.contains(searchQuery, ignoreCase = true)

            val matchesCategory = selectedCategory == "All" || binding.category == selectedCategory

            matchesSearch && matchesCategory
        }.sortedBy { it.category + it.description }
    }

    // Group by category for display
    val groupedShortcuts = filteredShortcuts.groupBy { it.category }

    // Get all available categories
    val allCategories = remember(keymapSettings) {
        listOf("All") + keymapSettings.shortcuts.values.map { it.category }.distinct().sorted()
    }

    // Make entire content scrollable by putting everything in LazyColumn
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Title and summary
        item {
            Column {
                Text(
                    text = "Keyboard Shortcuts",
                    style = MaterialTheme.typography.h5,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${keymapSettings.shortcuts.size} shortcuts configured",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    if (conflicts.isNotEmpty()) {
                        ConflictWarningBadge(
                            conflicts = conflicts.flatMap { it.bindings }
                        )
                    }
                }
            }
        }

        // Preset Selector
        item {
            PresetSelector(
                currentSettings = keymapSettings,
                onPresetSelected = { presetName ->
                    KeymapSettingsManager.loadPreset(presetName)
                },
                onResetToDefault = {
                    KeymapSettingsManager.resetToDefault()
                }
            )
        }

        // Import/Export
        item {
            KeymapImportExport(
                onImport = { jsonString ->
                    val result = KeymapSettingsManager.importFromJson(jsonString)
                    result != null
                }
            )
        }

        // Test All Shortcuts button
        item {
            OutlinedButton(
                onClick = { showTestDialog = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Test All Shortcuts")
            }
        }

        item {
            Divider()
        }

        // Search and filter
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Search field
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Search shortcuts...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search"
                        )
                    },
                    singleLine = true
                )

                // Category filter dropdown
                Box {
                    OutlinedButton(
                        onClick = { categoryMenuExpanded = true },
                        modifier = Modifier.height(56.dp)
                    ) {
                        Text(if (selectedCategory == "All") "All Categories" else selectedCategory)
                    }

                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false }
                    ) {
                        allCategories.forEach { category ->
                            DropdownMenuItem(
                                onClick = {
                                    selectedCategory = category
                                    categoryMenuExpanded = false
                                }
                            ) {
                                Text(
                                    text = if (category == "All") "All Categories" else category,
                                    fontWeight = if (category == selectedCategory) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
            }
        }

        // Category headers and shortcuts list
            groupedShortcuts.forEach { (category, shortcuts) ->
                item {
                    CategoryHeader(category = category, count = shortcuts.size)
                }

                items(shortcuts) { binding ->
                    // Find conflicts for this binding
                    val bindingConflicts = KeymapValidator.checkBinding(
                        binding,
                        keymapSettings,
                        excludeActionId = binding.actionId
                    )

                    ShortcutItem(
                        binding = binding,
                        hasConflict = bindingConflicts.isNotEmpty(),
                        conflictingBindings = bindingConflicts,
                        onEdit = { editingBinding = binding }
                    )
                }
            }

            // Empty state
            if (filteredShortcuts.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "No shortcuts found",
                                style = MaterialTheme.typography.h6,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                            )
                            Text(
                                text = "Try a different search term",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                            )
                        }
                    }
                }
            }
        }

    // Edit dialog
    editingBinding?.let { binding ->
        KeyCaptureDialog(
            actionId = binding.actionId,
            actionDescription = binding.description,
            context = binding.context,
            category = binding.category,
            currentBinding = binding,
            onKeyCaptured = { newBinding ->
                coroutineScope.launch {
                    val updatedSettings = keymapSettings.withBinding(newBinding)
                    KeymapSettingsManager.updateSettings(updatedSettings)
                    editingBinding = null
                }
            },
            onDismiss = { editingBinding = null }
        )
    }

    // Test dialog
    if (showTestDialog) {
        ShortcutTestDialog(
            keymapSettings = keymapSettings,
            onDismiss = { showTestDialog = false }
        )
    }
}

/**
 * Category header for grouping shortcuts.
 */
@Composable
private fun CategoryHeader(category: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = category,
            style = MaterialTheme.typography.subtitle1,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colors.primary
        )
        Text(
            text = "$count shortcut${if (count != 1) "s" else ""}",
            style = MaterialTheme.typography.caption,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
        )
    }
}

/**
 * Individual shortcut item.
 */
@Composable
private fun ShortcutItem(
    binding: KeyBinding,
    hasConflict: Boolean,
    conflictingBindings: List<KeyBinding>,
    onEdit: () -> Unit
) {
    // Get lifecycle state for this shortcut
    val lifecycleStates by ShortcutLifecycleManager.states.collectAsState()
    val lifecycleState = lifecycleStates[binding.actionId]

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = if (hasConflict) {
            MaterialTheme.colors.error.copy(alpha = 0.05f)
        } else {
            MaterialTheme.colors.surface
        },
        elevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left side: description and context
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = binding.description,
                    style = MaterialTheme.typography.body1,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = binding.context.displayName,
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    if (!binding.enabled) {
                        Text(
                            text = "• DISABLED",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error
                        )
                    }
                    // Show lifecycle state
                    if (lifecycleState != null && !lifecycleState.enabled) {
                        Text(
                            text = "• ${lifecycleState.reason ?: "Unavailable"}",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    }
                }
            }

            // Right side: key display, conflict badge, and edit button
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Conflict badge
                if (hasConflict) {
                    ConflictWarningBadge(conflicts = conflictingBindings)
                }

                // Key display
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                ) {
                    Text(
                        text = binding.displayString(),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colors.primary
                    )
                }

                // Edit button
                IconButton(onClick = onEdit) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit shortcut",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}
