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
 */
@Composable
fun BossTopRunBar() {
    val scope = rememberCoroutineScope()
    val detectedConfigs by RunConfigurationManager.detectedConfigurations.collectAsState()
    val userConfigs by RunConfigurationManager.currentSettings.collectAsState()
    val selectedConfig by RunConfigurationManager.selectedConfiguration.collectAsState()
    val isRunning by RunExecutionService.isRunning.collectAsState()

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Configuration selector dropdown
        RunConfigurationSelector(
            selectedConfig = selectedConfig,
            detectedConfigs = detectedConfigs,
            userConfigs = userConfigs.configurations,
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
 */
@Composable
private fun RunConfigurationSelector(
    selectedConfig: RunConfiguration?,
    detectedConfigs: List<RunConfiguration>,
    userConfigs: List<RunConfiguration>,
    onSelect: (RunConfiguration) -> Unit
) {
    // Build context menu items
    val contextMenuItems = buildList {
        // Detected configurations section
        if (detectedConfigs.isNotEmpty()) {
            add(ContextMenuItem(
                text = "Detected",
                icon = null,
                onClick = {}
            ))

            detectedConfigs.forEach { config ->
                add(ContextMenuItem(
                    text = config.name,
                    icon = getLanguageIcon(config.language),
                    onClick = { onSelect(config) }
                ))
            }
        }

        // User configurations section
        if (userConfigs.isNotEmpty()) {
            if (detectedConfigs.isNotEmpty()) {
                add(ContextMenuItem(isDivider = true))
            }

            add(ContextMenuItem(
                text = "Custom",
                icon = null,
                onClick = {}
            ))

            userConfigs.forEach { config ->
                add(ContextMenuItem(
                    text = config.name,
                    icon = getLanguageIcon(config.language),
                    onClick = { onSelect(config) }
                ))
            }
        }

        // No configurations message
        if (detectedConfigs.isEmpty() && userConfigs.isEmpty()) {
            add(ContextMenuItem(
                text = "No configurations found",
                icon = null,
                onClick = {}
            ))
            add(ContextMenuItem(
                text = "Open a project to scan",
                icon = null,
                onClick = {}
            ))
        }
    }

    BossActionButton(
        leftIcon = selectedConfig?.let { getLanguageIcon(it.language) } ?: Icons.Outlined.Code,
        text = selectedConfig?.name ?: "Select Configuration",
        contextMenuItems = contextMenuItems,
        hintText = selectedConfig?.let { "Configuration: ${it.filePath}" } ?: "Select a run configuration"
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
