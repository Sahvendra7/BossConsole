package ai.rever.boss.components.settings.sections

import BossDarkAccent
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import ai.rever.boss.components.workspaces.PredefinedWorkspaces
import ai.rever.boss.components.workspaces.WorkspaceSettingsManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

/**
 * Settings UI section for workspace configuration.
 */
@Composable
fun WorkspaceSettings() {
    val settings by WorkspaceSettingsManager.currentSettings.collectAsState()
    val coroutineScope = rememberCoroutineScope()

    // Build workspace options including "None" option
    val workspaceOptions = buildList {
        add(WorkspaceOption(
            id = "none",
            name = "None",
            description = "Don't auto-apply workspace when project is selected"
        ))
        PredefinedWorkspaces.allWorkspaces.forEach { workspace ->
            add(WorkspaceOption(
                id = workspace.id,
                name = workspace.name,
                description = workspace.description
            ))
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        SectionHeader(
            title = "Workspace",
            description = "Configure workspace behavior when opening projects"
        )

        Spacer(modifier = Modifier.height(32.dp))

        // Default Workspace Selection
        SettingSection(
            title = "Default Workspace",
            description = "Automatically apply this workspace when a project is selected"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                workspaceOptions.forEach { option ->
                    WorkspaceOptionItem(
                        title = option.name,
                        description = option.description,
                        selected = settings.defaultWorkspaceId == option.id,
                        onClick = {
                            coroutineScope.launch {
                                WorkspaceSettingsManager.setDefaultWorkspaceId(option.id)
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Info about workspaces
        SettingSection(
            title = "About Workspaces",
            description = "How workspaces work"
        ) {
            Column(
                modifier = Modifier.padding(vertical = 8.dp)
            ) {
                InfoItem(text = "Workspaces define panel layouts with terminals and browsers")
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(text = "Terminal commands use {projectPath} placeholder for the current project")
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(text = "Browser tabs use {gitRemoteUrl} to open the project's GitHub page")
                Spacer(modifier = Modifier.height(8.dp))
                InfoItem(text = "Save custom workspaces via the Workspace button in the top bar")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
    }
}

private data class WorkspaceOption(
    val id: String,
    val name: String,
    val description: String
)

@Composable
private fun WorkspaceOptionItem(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor = if (selected) BossDarkAccent else Color(0xFF3C3C3C)
    val backgroundColor = if (selected) BossDarkAccent.copy(alpha = 0.1f) else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = if (selected) BossDarkAccent else MaterialTheme.colors.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = description,
                fontSize = 12.sp,
                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
            )
        }

        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = "Selected",
                tint = BossDarkAccent,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun InfoItem(text: String) {
    Text(
        text = "• $text",
        fontSize = 12.sp,
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
        lineHeight = 18.sp
    )
}
