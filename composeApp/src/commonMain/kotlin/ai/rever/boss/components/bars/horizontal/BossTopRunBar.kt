package ai.rever.boss.components.bars.horizontal

import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.events.RunEventBus
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.run.Language
import ai.rever.boss.run.RunConfiguration
import ai.rever.boss.run.RunConfigurationManager
import ai.rever.boss.run.RunExecutionService
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Terminal
import kotlinx.coroutines.launch

/**
 * Run bar component for the top bar.
 * Shows run configuration selector, run button, and stop button.
 *
 * IntelliJ-style behavior: Only shows previously run configurations (run history),
 * not auto-detected configurations. Auto-detection is handled by a separate plugin.
 */
@Composable
fun BossTopRunBar() {
    val scope = rememberCoroutineScope()
    val settings by RunConfigurationManager.currentSettings.collectAsState()
    val selectedConfig by RunConfigurationManager.selectedConfiguration.collectAsState()
    val isRunning by RunExecutionService.isRunning.collectAsState()

    // Get run history - configurations that have been explicitly run
    val runHistory = settings.configurations

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Configuration selector dropdown - shows only run history (IntelliJ style)
        RunConfigurationSelector(
            selectedConfig = selectedConfig,
            runHistory = runHistory,
            onSelect = { config ->
                scope.launch {
                    RunConfigurationManager.selectConfiguration(config.id)
                }
            }
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Run button
        BossActionButton(
            imageVector = Icons.Outlined.PlayArrow,
            text = "Run",
            hintText = if (selectedConfig != null) {
                "Run ${selectedConfig?.name}"
            } else {
                "No configuration selected"
            }
        ) {
            selectedConfig?.let { config ->
                scope.launch {
                    RunEventBus.execute(config)
                }
            }
        }

        Spacer(modifier = Modifier.width(2.dp))

        // Stop button
        BossActionButton(
            imageVector = Icons.Outlined.Stop,
            text = "Stop",
            hintText = if (isRunning) "Stop running processes" else "No processes running"
        ) {
            if (isRunning) {
                scope.launch {
                    RunEventBus.stop()
                }
            }
        }
    }
}

/**
 * Dropdown selector for run configurations.
 * IntelliJ-style: Only shows run history (previously executed configurations).
 */
@Composable
private fun RunConfigurationSelector(
    selectedConfig: RunConfiguration?,
    runHistory: List<RunConfiguration>,
    onSelect: (RunConfiguration) -> Unit
) {
    // Build context menu items from run history only
    val contextMenuItems = buildList {
        if (runHistory.isNotEmpty()) {
            // Show run history
            runHistory.forEach { config ->
                add(ContextMenuItem(
                    text = config.name,
                    icon = getLanguageIcon(config.language),
                    onClick = { onSelect(config) }
                ))
            }
        } else {
            // No run history yet
            add(ContextMenuItem(
                text = "No run history",
                icon = null,
                onClick = {}
            ))
            add(ContextMenuItem(
                text = "Run a file to add it here",
                icon = null,
                onClick = {}
            ))
        }
    }

    BossActionButton(
        leftIcon = selectedConfig?.let { getLanguageIcon(it.language) } ?: Icons.Outlined.Code,
        text = selectedConfig?.name ?: "Run History",
        contextMenuItems = contextMenuItems,
        hintText = selectedConfig?.let { "Configuration: ${it.filePath}" } ?: "Select from run history"
    )
}

/**
 * Get the appropriate icon for a programming language.
 * Using Material Icons since SimpleIcons are not available in this project.
 */
private fun getLanguageIcon(language: Language): ImageVector {
    // Use generic code icon for all languages since language-specific icons aren't available
    // This could be enhanced later by adding a custom icon set
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
