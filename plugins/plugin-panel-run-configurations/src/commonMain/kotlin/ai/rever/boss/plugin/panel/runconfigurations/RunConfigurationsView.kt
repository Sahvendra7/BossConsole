package ai.rever.boss.plugin.panel.runconfigurations

import ai.rever.boss.plugin.api.LanguageData
import ai.rever.boss.plugin.api.RunConfigurationData
import ai.rever.boss.plugin.api.RunConfigurationTypeData
import ai.rever.boss.plugin.scrollbar.getPanelScrollbarConfig
import ai.rever.boss.plugin.scrollbar.lazyListScrollbar
import ai.rever.boss.plugin.search.BossSearchBar
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Science
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import compose.icons.FeatherIcons
import compose.icons.SimpleIcons
import compose.icons.feathericons.Terminal
import compose.icons.feathericons.Zap
import compose.icons.simpleicons.Go
import compose.icons.simpleicons.Java
import compose.icons.simpleicons.Javascript
import compose.icons.simpleicons.Kotlin
import compose.icons.simpleicons.Python
import compose.icons.simpleicons.Rust
import compose.icons.simpleicons.Typescript

/**
 * Main composable for the Run Configurations panel.
 *
 * @param viewModel The view model managing the panel state
 * @param projectPath Current project path (empty if no project selected)
 * @param windowId The current window ID for multi-window support
 */
@Composable
fun RunConfigurationsView(
    viewModel: RunConfigurationsViewModel,
    projectPath: String = "",
    windowId: String? = null
) {
    val detectedConfigs by viewModel.detectedConfigurations.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val lastError by viewModel.lastError.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Auto-scan when project changes (if a project is selected)
    LaunchedEffect(projectPath, windowId) {
        if (projectPath.isNotEmpty() && windowId != null) {
            viewModel.scanProject(projectPath, windowId)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2D30))
            .padding(12.dp)
    ) {
        // Error banner (if any)
        if (lastError != null) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                color = Color(0xFF5C2020),
                shape = RoundedCornerShape(4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lastError ?: "",
                        fontSize = 10.sp,
                        color = Color(0xFFFF8080),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "✕",
                        fontSize = 12.sp,
                        color = Color(0xFFFF8080),
                        modifier = Modifier
                            .clickable { viewModel.clearError() }
                            .padding(4.dp)
                    )
                }
            }
        }

        // Header with scan button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Detected Configurations",
                fontSize = 10.sp,
                color = Color.Gray,
                modifier = Modifier.weight(1f)
            )

            // Scan/Refresh button
            if (projectPath.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            if (!isScanning && windowId != null) {
                                viewModel.scanProject(projectPath, windowId)
                            }
                        }
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isScanning) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.dp,
                            color = MaterialTheme.colors.primary
                        )
                    } else {
                        Icon(
                            Icons.Outlined.Refresh,
                            contentDescription = "Rescan project",
                            modifier = Modifier.size(12.dp),
                            tint = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                        )
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isScanning) "Scanning..." else "Rescan",
                        fontSize = 9.sp,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.8f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Search bar
        BossSearchBar(
            query = searchQuery,
            onQueryChange = { searchQuery = it },
            placeholder = "Search configurations...",
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Content
        when {
            projectPath.isEmpty() -> {
                // No project selected
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Code,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No project selected",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Open a project to detect run configurations",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            isScanning && detectedConfigs.isEmpty() -> {
                // Scanning in progress
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Scanning for run configurations...",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                }
            }

            detectedConfigs.isEmpty() -> {
                // No configurations found
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Outlined.Science,
                            contentDescription = null,
                            modifier = Modifier.size(32.dp),
                            tint = Color.Gray.copy(alpha = 0.5f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "No run configurations found",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Add main functions or scripts to your project",
                            color = Color.Gray.copy(alpha = 0.7f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            else -> {
                // Show detected configurations
                val filteredConfigs = if (searchQuery.isBlank()) {
                    detectedConfigs
                } else {
                    detectedConfigs.filter { config ->
                        config.name.contains(searchQuery, ignoreCase = true) ||
                                config.filePath.contains(searchQuery, ignoreCase = true) ||
                                config.language.displayName.contains(searchQuery, ignoreCase = true)
                    }
                }

                // Group by language
                val groupedConfigs = filteredConfigs.groupBy { it.language }

                if (filteredConfigs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No configurations matching \"$searchQuery\"",
                            color = Color.Gray,
                            fontSize = 12.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .lazyListScrollbar(
                                listState = listState,
                                direction = Orientation.Vertical,
                                config = getPanelScrollbarConfig()
                            ),
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        groupedConfigs.forEach { (language, configs) ->
                            // Language group header
                            item(key = "header-${language.name}") {
                                LanguageGroupHeader(language = language, count = configs.size)
                            }

                            // Configuration items
                            items(
                                items = configs,
                                key = { it.id }
                            ) { config ->
                                RunConfigurationItem(
                                    config = config,
                                    onRun = {
                                        windowId?.let { wid ->
                                            viewModel.execute(config, wid)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LanguageGroupHeader(
    language: LanguageData,
    count: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            getLanguageIcon(language),
            contentDescription = language.displayName,
            modifier = Modifier.size(14.dp),
            tint = getLanguageColor(language)
        )

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = language.displayName,
            fontSize = 11.sp,
            color = MaterialTheme.colors.onSurface.copy(alpha = 0.9f)
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = "($count)",
            fontSize = 10.sp,
            color = Color.Gray
        )

        Spacer(modifier = Modifier.width(8.dp))

        // Divider line
        Box(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(Color(0xFF4B5563))
        )
    }
}

@Composable
private fun RunConfigurationItem(
    config: RunConfigurationData,
    onRun: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
            .clip(RoundedCornerShape(4.dp))
            .clickable { onRun() },
        color = Color(0xFF3C3F43),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Config type icon
            Icon(
                getConfigTypeIcon(config.type),
                contentDescription = config.type.name,
                modifier = Modifier.size(14.dp),
                tint = getLanguageColor(config.language).copy(alpha = 0.8f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Config info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = config.name,
                    fontSize = 11.sp,
                    color = MaterialTheme.colors.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // File path (relative)
                val relativePath = config.filePath
                    .removePrefix(config.workingDirectory)
                    .removePrefix("/")

                Text(
                    text = relativePath,
                    fontSize = 9.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Run button
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = "Run",
                modifier = Modifier
                    .size(24.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onRun() }
                    .padding(2.dp),
                tint = Color(0xFF4CAF50)
            )
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════
// LANGUAGE ICONS AND COLORS
// ═══════════════════════════════════════════════════════════════════════════

private fun getLanguageIcon(language: LanguageData): ImageVector {
    return when (language) {
        LanguageData.KOTLIN -> SimpleIcons.Kotlin
        LanguageData.JAVA -> SimpleIcons.Java
        LanguageData.PYTHON -> SimpleIcons.Python
        LanguageData.JAVASCRIPT -> SimpleIcons.Javascript
        LanguageData.TYPESCRIPT -> SimpleIcons.Typescript
        LanguageData.GO -> SimpleIcons.Go
        LanguageData.RUST -> SimpleIcons.Rust
        LanguageData.UNKNOWN -> FeatherIcons.Terminal
    }
}

private fun getLanguageColor(language: LanguageData): Color {
    return when (language) {
        LanguageData.KOTLIN -> Color(0xFF7F52FF)
        LanguageData.JAVA -> Color(0xFFE76F00)
        LanguageData.PYTHON -> Color(0xFF3776AB)
        LanguageData.JAVASCRIPT -> Color(0xFFF7DF1E)
        LanguageData.TYPESCRIPT -> Color(0xFF3178C6)
        LanguageData.GO -> Color(0xFF00ADD8)
        LanguageData.RUST -> Color(0xFFDEA584)
        LanguageData.UNKNOWN -> Color.Gray
    }
}

private fun getConfigTypeIcon(type: RunConfigurationTypeData): ImageVector {
    return when (type) {
        RunConfigurationTypeData.MAIN_FUNCTION -> FeatherIcons.Zap
        RunConfigurationTypeData.SCRIPT -> FeatherIcons.Terminal
        RunConfigurationTypeData.TEST -> Icons.Outlined.Science
        RunConfigurationTypeData.CUSTOM -> Icons.Outlined.Code
    }
}
