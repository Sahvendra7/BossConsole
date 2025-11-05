package ai.rever.boss.components.settings.sidebar

import BossDarkAccent
import BossDarkBackground
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SettingsSection {
    FLUCK, CODE_EDITOR, TERMINAL, LLM_PROVIDERS, UPDATES, SECURITY, KEYMAP
}

@Composable
fun SettingsSidebar(
    selectedSection: SettingsSection,
    onSectionChange: (SettingsSection) -> Unit
) {
    Column(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight()
            .background(BossDarkBackground)
            .padding(16.dp)
    ) {
        SidebarItem(
            icon = Icons.Outlined.Language,
            title = "Fluck Browser",
            subtitle = "User agent, profiles",
            isSelected = selectedSection == SettingsSection.FLUCK,
            onClick = { onSectionChange(SettingsSection.FLUCK) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.Code,
            title = "Code Editor",
            subtitle = "Font, size, theme",
            isSelected = selectedSection == SettingsSection.CODE_EDITOR,
            onClick = { onSectionChange(SettingsSection.CODE_EDITOR) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.Terminal,
            title = "Terminal",
            subtitle = "Shell, colors, startup",
            isSelected = selectedSection == SettingsSection.TERMINAL,
            onClick = { onSectionChange(SettingsSection.TERMINAL) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.AutoAwesome,
            title = "LLM Providers",
            subtitle = "API keys, models, settings",
            isSelected = selectedSection == SettingsSection.LLM_PROVIDERS,
            onClick = { onSectionChange(SettingsSection.LLM_PROVIDERS) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.SystemUpdate,
            title = "Updates",
            subtitle = "Auto-update, version info",
            isSelected = selectedSection == SettingsSection.UPDATES,
            onClick = { onSectionChange(SettingsSection.UPDATES) }
        )
        
        Spacer(modifier = Modifier.height(8.dp))
        
        SidebarItem(
            icon = Icons.Outlined.Security,
            title = "Security",
            subtitle = "WebAuthn, Touch ID",
            isSelected = selectedSection == SettingsSection.SECURITY,
            onClick = { onSectionChange(SettingsSection.SECURITY) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        SidebarItem(
            icon = Icons.Outlined.Keyboard,
            title = "Keyboard Shortcuts",
            subtitle = "Edit shortcuts, presets",
            isSelected = selectedSection == SettingsSection.KEYMAP,
            onClick = { onSectionChange(SettingsSection.KEYMAP) }
        )

    }
}

@Composable
private fun SidebarItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) BossDarkAccent.copy(alpha = 0.1f) else Color.Transparent
    val borderColor = if (isSelected) BossDarkAccent else Color.Transparent
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, borderColor, RoundedCornerShape(8.dp))
            .clickable { onClick() },
        color = backgroundColor,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = title,
                tint = if (isSelected) BossDarkAccent else Color.Gray,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text(
                    text = title,
                    color = if (isSelected) Color.White else Color.Gray,
                    fontSize = 15.sp,
                    fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
                )
                Text(
                    text = subtitle,
                    color = Color.Gray.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
            }
        }
    }
}
