package ai.rever.boss.v3

import BossTheme
import GitHubDarkAccent
import GitHubDarkBackground
import GitHubDarkBorder
import GitHubDarkSurface
import GitHubDarkTextPrimary
import GitHubDarkTextSecondary
import SystemOfRecordsScreen
import ai.rever.boss.v3.Screens.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun BossConsole() {
    val viewModel = remember { BossConsoleViewModel() }

    Row {
        // Navigation Rail
        NavigationRail(
            modifier = Modifier
                .width(220.dp)
                .fillMaxHeight(),
            header = navigationRailHeader()
        ) {
            Column(modifier = Modifier.padding(horizontal = 8.dp)) {

                // Lighthouse Section
                BossSection(Section.LIGHTHOUSE, viewModel)

                // Lanager Section
                BossSection(Section.LANAGER, viewModel)
            }
        }

        // Content area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .background(GitHubDarkBackground)
        ) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                elevation = 2.dp,
                shape = RoundedCornerShape(8.dp),
            ) {
                Box(
                    modifier = Modifier.padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when (viewModel.currentScreen) {
                        is Screen.Worklist -> WorklistScreen()
                        is Screen.SystemOfRecords -> SystemOfRecordsScreen()
                        is Screen.OrgValues -> OrgValuesScreen()
                        is Screen.GlobalLanager -> GlobalLanagerScreen()
                        is Screen.MasteryRegistry -> MasteryRegisteryScreen()
                        is Screen.TaskResolverRegistry -> TaskResolverRegisteryScreen()
                    }
                }
            }
        }
    }
}

@Composable
private fun BossSection(section: Section, viewModel: BossConsoleViewModel) {
    SectionHeader(
        title = section.name,
        isExpanded = section in viewModel.expandedSections,
        onClick = { viewModel.toggleSection(section) },
    )

    if (section in viewModel.expandedSections) {
        viewModel.getItemsBySection(section).forEach { item ->
            NavigationItem(
                item = item,
                isSelected = viewModel.currentScreen == item.screen,
                onClick = { viewModel.navigateTo(item.screen) },
                selectedColor = GitHubDarkAccent
            )
        }
    }

    Divider(color = GitHubDarkBorder)
}

private fun navigationRailHeader(): @Composable (ColumnScope.() -> Unit) =
    {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Terminal,
                    contentDescription = "Boss Logo",
                )

                Spacer(modifier = Modifier.width(8.dp))
                // App name
                Text(
                    "BOSS console",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Divider(color = GitHubDarkBorder)
    }

@Composable
fun SectionHeader(
    title: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(),
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            letterSpacing = 1.sp,
            color = GitHubDarkTextSecondary
        )
        Text(
            text = if (isExpanded) "▼" else "▶",
            fontSize = 10.sp,
            color = GitHubDarkTextSecondary
        )
    }
}

@Composable
fun NavigationItem(
    item: NavigationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
    selectedColor: Color = Color(0xFF673AB7)
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()

    val backgroundColor = when {
        isSelected -> GitHubDarkSurface.copy(alpha = 0.5f)
        isHovered -> GitHubDarkBorder.copy(alpha = 0.3f)
        else -> Color.Transparent
    }

    val textColor = if (isSelected) selectedColor else GitHubDarkTextPrimary

    TextButton(
        onClick = onClick,
        interactionSource = interactionSource,
        elevation = ButtonDefaults.elevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            hoveredElevation = 0.dp,
            focusedElevation = 0.dp
        ),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = backgroundColor,
            contentColor = textColor,
            disabledContentColor = textColor
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon container
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                tint = textColor
            )

            Spacer(modifier = Modifier.width(12.dp))

            // Label
            Text(
                text = item.title,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal,
                textAlign = TextAlign.Start,
                color = textColor,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}