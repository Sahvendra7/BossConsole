package ai.rever.boss.components.plugin.panels.left_bottom

import ai.rever.boss.components.common.BossSearchBar
import ai.rever.boss.components.events.RunEventBus
import ai.rever.boss.components.model.Panel.Companion.left
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.run.Language
import ai.rever.boss.run.RunConfiguration
import ai.rever.boss.run.RunConfigurationManager
import ai.rever.boss.run.RunConfigurationType
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import compose.icons.FeatherIcons
import compose.icons.feathericons.Terminal
import compose.icons.feathericons.Zap
import kotlinx.coroutines.launch

/**
 * Run Configurations Plugin
 *
 * Detects runnable files in the current project (main functions, scripts, tests).
 * Unlike the top bar run dropdown which shows run history, this plugin auto-detects
 * configurations from the project source code.
 *
 * IntelliJ-style separation:
 * - Top bar dropdown: Shows run history (previously executed configs)
 * - This plugin: Auto-detects and shows all runnable files in project
 */
object RunConfigurationsInfo : PanelInfo {
    override val id = PanelId("run-configurations", 6)
    override val displayName = "Run Configurations"
    override val icon = FeatherIcons.Zap
    override val defaultSlotPosition = left.top.bottom
}

class RunConfigurationsComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {

    @Composable
    override fun Content() {
        RunConfigurationsContent()
    }
}

@Composable
fun RunConfigurationsContent() {
    val scope = rememberCoroutineScope()
    val selectedProject by ProjectState.selectedProject.collectAsState()
    val detectedConfigs by RunConfigurationManager.detectedConfigurations.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var isScanning by remember { mutableStateOf(false) }

    // Auto-scan when project changes (if a project is selected)
    LaunchedEffect(selectedProject.path) {
        if (selectedProject.path.isNotEmpty()) {
            isScanning = true
            RunEventBus.scanProject(selectedProject.path)
            isScanning = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF2B2D30))
            .padding(12.dp)
    ) {
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
            if (selectedProject.path.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .clickable {
                            if (!isScanning) {
                                scope.launch {
                                    isScanning = true
                                    RunEventBus.scanProject(selectedProject.path)
                                    isScanning = false
                                }
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
            selectedProject.path.isEmpty() -> {
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
                        modifier = Modifier.fillMaxSize(),
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
                                        scope.launch {
                                            RunEventBus.execute(config)
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
    language: Language,
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
    config: RunConfiguration,
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
                    .size(18.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onRun() }
                    .padding(2.dp),
                tint = Color(0xFF4CAF50)
            )
        }
    }
}

private fun getLanguageIcon(language: Language): ImageVector {
    return when (language) {
        Language.KOTLIN -> Icons.Outlined.Code
        Language.JAVA -> Icons.Outlined.Code
        Language.PYTHON -> Icons.Outlined.Code
        Language.JAVASCRIPT -> Icons.Outlined.Code
        Language.TYPESCRIPT -> Icons.Outlined.Code
        Language.GO -> Icons.Outlined.Code
        Language.RUST -> Icons.Outlined.Code
        Language.UNKNOWN -> FeatherIcons.Terminal
    }
}

private fun getLanguageColor(language: Language): Color {
    return when (language) {
        Language.KOTLIN -> Color(0xFFA97BFF)  // Kotlin purple
        Language.JAVA -> Color(0xFFE76F00)   // Java orange
        Language.PYTHON -> Color(0xFF3776AB)  // Python blue
        Language.JAVASCRIPT -> Color(0xFFF7DF1E)  // JS yellow
        Language.TYPESCRIPT -> Color(0xFF3178C6)  // TS blue
        Language.GO -> Color(0xFF00ADD8)      // Go cyan
        Language.RUST -> Color(0xFFDEA584)    // Rust orange
        Language.UNKNOWN -> Color.Gray
    }
}

private fun getConfigTypeIcon(type: RunConfigurationType): ImageVector {
    return when (type) {
        RunConfigurationType.MAIN_FUNCTION -> FeatherIcons.Zap
        RunConfigurationType.SCRIPT -> FeatherIcons.Terminal
        RunConfigurationType.TEST -> Icons.Outlined.Science
        RunConfigurationType.CUSTOM -> Icons.Outlined.Code
    }
}

fun DefaultPlugin.registerRunConfigurations() = panelRegistry.registerPanel(RunConfigurationsInfo) {
    ctx, panelInfo -> RunConfigurationsComponent(ctx, panelInfo)
}
