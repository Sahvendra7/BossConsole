package ai.rever.boss.components.wizard.plugin

import BossDarkBackground
import BossDarkBorder
import BossDarkSurface
import BossDarkTextSecondary
import ai.rever.boss.components.wizard.CheckboxCard
import ai.rever.boss.components.wizard.WizardNote
import ai.rever.boss.components.wizard.WizardStepIndicator
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.LinearProgressIndicator
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Rocket
import androidx.compose.material.icons.outlined.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch

// Dashboard-style colors
private val AccentBlue = Color(0xFF4A9EFF)
private val SuccessGreen = Color(0xFF4CAF50)

/**
 * Plugin installation wizard dialog.
 *
 * Guides users through selecting and installing plugins after login.
 *
 * @param state The wizard state
 * @param onDismiss Callback when the wizard should be dismissed
 * @param onComplete Callback when installation is complete
 * @param onInstallPlugins Callback to perform the actual plugin installation
 */
@Composable
fun PluginInstallWizard(
    state: PluginInstallWizardState,
    onDismiss: () -> Unit,
    onComplete: () -> Unit,
    onInstallPlugins: suspend (List<String>, (Float, String) -> Unit) -> Result<PluginInstallResult>
) {
    val currentStep = state.wizardState.currentStep
    val scope = rememberCoroutineScope()

    // Handle installation when we reach the Installing step
    // Use installationAttempted flag to prevent re-triggering (fixes race condition)
    LaunchedEffect(currentStep) {
        if (currentStep is PluginInstallStep.Installing && !state.isInstalling && !state.installationAttempted) {
            val selectedIds = state.getSelectedPluginIds()
            if (selectedIds.isEmpty()) {
                // No plugins selected, skip to complete
                state.completeInstallation(emptyList())
                state.goToNextStep()
            } else {
                state.startInstallation()
                val result = onInstallPlugins(selectedIds) { progress, status ->
                    state.updateProgress(progress, status)
                }
                result.fold(
                    onSuccess = { installResult ->
                        state.completeInstallation(installResult.installedIds, installResult.failedPlugins)
                        state.goToNextStep()
                    },
                    onFailure = { error ->
                        state.failInstallation(error.message ?: "Installation failed")
                    }
                )
            }
        }
    }

    Dialog(
        onDismissRequest = {
            // Only allow dismiss if not installing
            if (!state.isInstalling) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnClickOutside = !state.isInstalling,
            dismissOnBackPress = !state.isInstalling,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier
                .width(650.dp)
                .heightIn(min = 500.dp, max = 600.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(BossDarkBackground)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp)
            ) {
                // Header
                WizardHeader(
                    currentStep = currentStep,
                    onBack = if (!state.wizardState.isFirstStep && !state.isInstalling && currentStep !is PluginInstallStep.Complete) {
                        { state.goToPreviousStep() }
                    } else null,
                    onDismiss = if (!state.isInstalling && currentStep !is PluginInstallStep.Installing) {
                        onDismiss
                    } else null
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Step indicator (hidden during Installing and Complete)
                if (currentStep !is PluginInstallStep.Installing && currentStep !is PluginInstallStep.Complete) {
                    WizardStepIndicator(
                        currentStep = state.wizardState.currentVisibleStepIndex + 1,
                        totalSteps = state.wizardState.totalVisibleSteps,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Content
                Box(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = currentStep,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "wizard_step_content"
                    ) { step ->
                        when (step) {
                            is PluginInstallStep.Welcome -> WelcomeStepContent()
                            is PluginInstallStep.EssentialPlugins,
                            is PluginInstallStep.DeveloperPlugins,
                            is PluginInstallStep.ProductivityPlugins,
                            is PluginInstallStep.AutomationPlugins,
                            is PluginInstallStep.AdminPlugins,
                            is PluginInstallStep.OtherPlugins -> {
                                step.category?.let { category ->
                                    CategoryStepContent(
                                        category = category,
                                        plugins = state.getPluginsForCategory(category),
                                        isPluginSelected = { state.isPluginSelected(it) },
                                        onTogglePlugin = { state.togglePlugin(it) },
                                        onSelectAll = { state.selectAllInCategory(category) },
                                        onDeselectAll = { state.deselectAllInCategory(category) }
                                    )
                                }
                            }
                            is PluginInstallStep.Installing -> InstallingStepContent(
                                progress = state.installationProgress,
                                status = state.installationStatus,
                                error = state.installationError,
                                onRetry = {
                                    state.reset()
                                }
                            )
                            is PluginInstallStep.Complete -> CompleteStepContent(
                                installedCount = state.installedPluginIds.size,
                                failedPlugins = state.failedPlugins
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Navigation buttons
                WizardNavigation(
                    currentStep = currentStep,
                    isInstalling = state.isInstalling,
                    hasSelectedPlugins = state.hasSelectedPlugins(),
                    onBack = { state.goToPreviousStep() },
                    onNext = { state.goToNextStep() },
                    onSkip = {
                        // Skip all remaining category steps and go to Installing
                        state.skipToInstalling()
                    },
                    onFinish = onComplete
                )
            }
        }
    }
}

@Composable
private fun WizardHeader(
    currentStep: PluginInstallStep,
    onBack: (() -> Unit)?,
    onDismiss: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.padding(end = 12.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "Back",
                    tint = BossDarkTextSecondary
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = currentStep.title,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )
            currentStep.category?.let { category ->
                Text(
                    text = category.description,
                    fontSize = 13.sp,
                    color = BossDarkTextSecondary
                )
            }
        }

        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.Outlined.Close,
                    contentDescription = "Close",
                    tint = BossDarkTextSecondary
                )
            }
        }
    }
}

@Composable
private fun WelcomeStepContent() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Extension,
            contentDescription = null,
            modifier = Modifier.size(64.dp),
            tint = AccentBlue
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "Welcome to BOSS Plugins",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Customize your workspace by selecting the plugins you need.\nWe'll help you get started with some recommended essentials.",
            fontSize = 14.sp,
            color = BossDarkTextSecondary,
            textAlign = TextAlign.Center,
            lineHeight = 22.sp,
            modifier = Modifier.padding(horizontal = 32.dp)
        )

        Spacer(modifier = Modifier.height(32.dp))

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .background(BossDarkSurface)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Rocket,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = AccentBlue
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = "Essential plugins will be pre-selected for you",
                fontSize = 13.sp,
                color = BossDarkTextSecondary
            )
        }
    }
}

@Composable
private fun CategoryStepContent(
    category: PluginCategory,
    plugins: List<WizardPluginInfo>,
    isPluginSelected: (String) -> Boolean,
    onTogglePlugin: (String) -> Unit,
    onSelectAll: () -> Unit,
    onDeselectAll: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // Select All / Deselect All buttons
        if (plugins.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onSelectAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = AccentBlue)
                ) {
                    Text("Select All", fontSize = 12.sp)
                }
                TextButton(
                    onClick = onDeselectAll,
                    colors = ButtonDefaults.textButtonColors(contentColor = BossDarkTextSecondary)
                ) {
                    Text("Deselect All", fontSize = 12.sp)
                }
            }
        }

        // Plugin list
        if (plugins.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No plugins available in this category",
                    color = BossDarkTextSecondary,
                    fontSize = 14.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(plugins, key = { it.id }) { plugin ->
                    CheckboxCard(
                        title = plugin.name,
                        description = plugin.description,
                        icon = plugin.icon,
                        isChecked = isPluginSelected(plugin.id),
                        onCheckedChange = { onTogglePlugin(plugin.id) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Note about Plugin Manager
        WizardNote(
            text = "You can manage these plugins later in the Plugin Manager"
        )
    }
}

@Composable
private fun InstallingStepContent(
    progress: Float,
    status: String,
    error: String?,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (error != null) {
            // Error state
            Text(
                text = "Installation Failed",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BossDarkSurface)
                    .padding(16.dp)
            ) {
                Text(
                    text = error,
                    fontSize = 13.sp,
                    color = Color(0xFFF44336),
                    lineHeight = 20.sp
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = AccentBlue,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Try Again")
            }
        } else {
            // Installing state
            CircularProgressIndicator(
                modifier = Modifier.size(64.dp),
                color = AccentBlue,
                strokeWidth = 4.dp
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Installing Plugins",
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color.White
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = status,
                fontSize = 14.sp,
                color = BossDarkTextSecondary
            )

            Spacer(modifier = Modifier.height(24.dp))

            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = AccentBlue,
                backgroundColor = BossDarkSurface
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${(progress * 100).toInt()}%",
                fontSize = 12.sp,
                color = BossDarkTextSecondary
            )
        }
    }
}

// Warning/error color
private val WarningOrange = Color(0xFFFF9800)

@Composable
private fun CompleteStepContent(
    installedCount: Int,
    failedPlugins: List<Pair<String, String>> = emptyList()
) {
    val hasFailures = failedPlugins.isNotEmpty()

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = if (hasFailures) "Partial Success" else "Success",
            modifier = Modifier.size(72.dp),
            tint = if (hasFailures) WarningOrange else SuccessGreen
        )

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = if (hasFailures) "Installation Complete" else "You're All Set!",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = if (installedCount > 0) {
                "$installedCount plugin${if (installedCount > 1) "s" else ""} installed successfully"
            } else {
                "No plugins were selected for installation"
            },
            fontSize = 14.sp,
            color = BossDarkTextSecondary,
            textAlign = TextAlign.Center
        )

        // Show failed plugins if any
        if (hasFailures) {
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(BossDarkSurface)
                    .padding(12.dp)
            ) {
                Column {
                    Text(
                        text = "${failedPlugins.size} plugin${if (failedPlugins.size > 1) "s" else ""} failed to install:",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = WarningOrange
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    failedPlugins.forEach { (pluginId, error) ->
                        Text(
                            text = "\u2022 $pluginId: $error",
                            fontSize = 12.sp,
                            color = BossDarkTextSecondary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "You can retry installing these plugins from the Plugin Manager",
                fontSize = 13.sp,
                color = BossDarkTextSecondary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(32.dp))

            Text(
                text = "You can install more plugins anytime from the Plugin Manager",
                fontSize = 13.sp,
                color = BossDarkTextSecondary.copy(alpha = 0.8f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp)
            )
        }
    }
}

@Composable
private fun WizardNavigation(
    currentStep: PluginInstallStep,
    isInstalling: Boolean,
    hasSelectedPlugins: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSkip: () -> Unit,
    onFinish: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (currentStep) {
            is PluginInstallStep.Welcome -> {
                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AccentBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Get Started", fontWeight = FontWeight.Medium)
                }
            }

            is PluginInstallStep.EssentialPlugins,
            is PluginInstallStep.DeveloperPlugins,
            is PluginInstallStep.ProductivityPlugins,
            is PluginInstallStep.AutomationPlugins,
            is PluginInstallStep.AdminPlugins,
            is PluginInstallStep.OtherPlugins -> {
                // Skip remaining steps button
                if (currentStep.canSkip) {
                    TextButton(
                        onClick = onSkip,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = BossDarkTextSecondary
                        )
                    ) {
                        Text("Skip to Install")
                    }

                    Spacer(modifier = Modifier.width(12.dp))
                }

                Button(
                    onClick = onNext,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AccentBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    // Show "Install" on the last category step (Other)
                    val isLastCategoryStep = currentStep is PluginInstallStep.OtherPlugins
                    Text(
                        text = if (isLastCategoryStep) "Install" else "Next",
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            is PluginInstallStep.Installing -> {
                // No navigation during installation
            }

            is PluginInstallStep.Complete -> {
                Button(
                    onClick = onFinish,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = AccentBlue,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Text("Start Using BOSS", fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
