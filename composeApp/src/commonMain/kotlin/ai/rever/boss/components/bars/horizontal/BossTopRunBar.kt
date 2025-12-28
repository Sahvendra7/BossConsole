package ai.rever.boss.components.bars.horizontal

import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.events.RunEventBus
import ai.rever.boss.components.overlays.ContextMenuItem
import ai.rever.boss.run.Language
import ai.rever.boss.run.RunConfiguration
import ai.rever.boss.run.RunConfigurationManager
import ai.rever.boss.run.RunnerTerminalService
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import compose.icons.FeatherIcons
import compose.icons.feathericons.Terminal
import kotlinx.coroutines.launch

/**
 * Run bar component for the top bar.
 * Shows run configuration selector, run/re-run button, and stop button.
 *
 * Issue #347: Square buttons with run/stop state management
 * - Idle state: Run (green), Stop (grayed out)
 * - Running state: Re-run (green), Stop (red active)
 *
 * IntelliJ-style behavior: Only shows previously run configurations (run history),
 * not auto-detected configurations. Auto-detection is handled by a separate plugin.
 */
@Composable
fun BossTopRunBar() {
    val scope = rememberCoroutineScope()
    val settings by RunConfigurationManager.currentSettings.collectAsState()
    val selectedConfig by RunConfigurationManager.selectedConfiguration.collectAsState()
    val runningConfigs by RunnerTerminalService.runningConfigs.collectAsState()

    // Get run history - configurations that have been explicitly run
    val runHistory = settings.configurations

    // Check if selected config is running
    val isSelectedConfigRunning = selectedConfig?.let { it.id in runningConfigs } ?: false

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
            },
            onDelete = { config ->
                scope.launch {
                    RunConfigurationManager.removeConfiguration(config.id)
                }
            }
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Run/Re-run button (square, green)
        RunSquareButton(
            icon = if (isSelectedConfigRunning) Icons.Outlined.Refresh else Icons.Outlined.PlayArrow,
            backgroundColor = Color(0xFF59A869), // IntelliJ's run icon green
            enabled = selectedConfig != null,
            contentDescription = if (isSelectedConfigRunning) "Re-run" else "Run",
            onClick = {
                selectedConfig?.let { config ->
                    scope.launch {
                        if (isSelectedConfigRunning) {
                            // Re-run: stop and run again
                            RunnerTerminalService.rerunRunner(config)
                        } else {
                            // First run
                            RunnerTerminalService.openRunnerTerminal(config)
                        }
                        // Also add to run history
                        RunConfigurationManager.addConfiguration(config)
                    }
                }
            }
        )

        Spacer(modifier = Modifier.width(4.dp))

        // Stop button (square, red when active, gray when disabled)
        RunSquareButton(
            icon = Icons.Outlined.Stop,
            backgroundColor = Color(0xFFE05555), // Red stop color
            enabled = isSelectedConfigRunning,
            contentDescription = "Stop",
            onClick = {
                selectedConfig?.let { config ->
                    scope.launch {
                        RunnerTerminalService.stopRunner(config.id)
                    }
                }
            }
        )
    }
}

/**
 * Square button for run/stop actions.
 * Issue #347: Square button styling with icon only.
 */
@Composable
private fun RunSquareButton(
    icon: ImageVector,
    backgroundColor: Color,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    // Calculate colors based on state
    val bgColor = when {
        !enabled -> Color(0xFF3C3C3C) // Gray background when disabled
        isHovered -> backgroundColor.copy(alpha = 0.9f) // Slightly darker on hover
        else -> backgroundColor
    }
    val iconColor = when {
        !enabled -> Color(0xFF808080) // Gray icon when disabled
        else -> Color.White
    }

    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(bgColor)
            .hoverable(interactionSource)
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else {
                    Modifier
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = iconColor,
            modifier = Modifier.size(18.dp)
        )
    }
}

/**
 * Dropdown selector for run configurations.
 * IntelliJ-style: Only shows run history (previously executed configurations).
 * Each item has a delete button to remove from history.
 */
@Composable
private fun RunConfigurationSelector(
    selectedConfig: RunConfiguration?,
    runHistory: List<RunConfiguration>,
    onSelect: (RunConfiguration) -> Unit,
    onDelete: (RunConfiguration) -> Unit
) {
    // Build context menu items from run history only
    val contextMenuItems = buildList {
        if (runHistory.isNotEmpty()) {
            // Show run history with delete buttons
            runHistory.forEach { config ->
                add(ContextMenuItem(
                    text = config.name,
                    icon = getLanguageIcon(config.language),
                    onClick = { onSelect(config) },
                    trailingIcon = Icons.Outlined.Close,
                    onTrailingClick = { onDelete(config) }
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
