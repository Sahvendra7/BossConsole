package ai.rever.boss.components.bars.horizontal

import ai.rever.boss.components.buttons.BossActionButton
import ai.rever.boss.components.events.RunEventBus
import ai.rever.boss.icons.LanguageIcons
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
            runningConfigs = runningConfigs,
            onSelect = { config ->
                scope.launch {
                    RunConfigurationManager.selectConfiguration(config.id)
                }
            },
            onRun = { config ->
                scope.launch {
                    RunConfigurationManager.selectConfiguration(config.id)
                    RunnerTerminalService.openRunnerTerminal(config)
                    RunConfigurationManager.addConfiguration(config)
                }
            },
            onRerun = { config ->
                scope.launch {
                    RunConfigurationManager.selectConfiguration(config.id)
                    RunnerTerminalService.rerunRunner(config)
                }
            },
            onStop = { config ->
                scope.launch {
                    RunConfigurationManager.selectConfiguration(config.id)
                    RunnerTerminalService.stopRunner(config.id)
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
 * - Not running: [Play] [Delete]
 * - Running: [Rerun] [Stop]
 * Clicking any action button also selects that configuration.
 */
@Composable
private fun RunConfigurationSelector(
    selectedConfig: RunConfiguration?,
    runHistory: List<RunConfiguration>,
    runningConfigs: Set<String>,
    onSelect: (RunConfiguration) -> Unit,
    onRun: (RunConfiguration) -> Unit,
    onRerun: (RunConfiguration) -> Unit,
    onStop: (RunConfiguration) -> Unit,
    onDelete: (RunConfiguration) -> Unit
) {
    // Build context menu items from run history only
    val contextMenuItems = buildList {
        if (runHistory.isNotEmpty()) {
            // Show run history with action buttons based on running state
            runHistory.forEach { config ->
                val isRunning = config.id in runningConfigs
                add(ContextMenuItem(
                    text = config.name,
                    icon = getLanguageIcon(config.language),
                    onClick = { onSelect(config) },
                    // Primary action: Play (not running) or Rerun (running)
                    trailingIcon = if (isRunning) Icons.Outlined.Refresh else Icons.Outlined.PlayArrow,
                    trailingIconColor = Color(0xFF59A869), // Green for both play and rerun
                    onTrailingClick = { if (isRunning) onRerun(config) else onRun(config) },
                    // Secondary action: Delete (not running) or Stop (running)
                    secondaryTrailingIcon = if (isRunning) Icons.Outlined.Stop else Icons.Outlined.Close,
                    secondaryTrailingIconColor = if (isRunning) Color(0xFFE05555) else Color(0xFF888888),
                    onSecondaryTrailingClick = { if (isRunning) onStop(config) else onDelete(config) }
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
 * Uses official brand icons from LanguageIcons.
 */
private fun getLanguageIcon(language: Language): ImageVector {
    return when (language) {
        Language.KOTLIN -> LanguageIcons.kotlin
        Language.JAVA -> LanguageIcons.java
        Language.PYTHON -> LanguageIcons.python
        Language.JAVASCRIPT -> LanguageIcons.javascript
        Language.TYPESCRIPT -> LanguageIcons.typescript
        Language.GO -> LanguageIcons.go
        Language.RUST -> LanguageIcons.rust
        Language.UNKNOWN -> FeatherIcons.Terminal
    }
}
