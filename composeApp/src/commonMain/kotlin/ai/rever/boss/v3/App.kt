package ai.rever.boss.v3

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

// GitHub Dark Mode Theme Colors
private val GitHubDarkBackground = Color(0xFF0D1117)
private val GitHubDarkSurface = Color(0xFF161B22)
private val GitHubDarkBorder = Color(0xFF3F4448)
private val GitHubDarkTextPrimary = Color(0xFFF0F6FC)
private val GitHubDarkTextSecondary = Color(0xFF8B949E)
private val GitHubDarkAccent = Color(0xFF58A6FF)

@Composable
fun App() {
    val viewModel = remember { AppViewModel() }

    MaterialTheme(
        colors = darkColors(
            primary = GitHubDarkAccent,
            primaryVariant = GitHubDarkAccent,
            background = GitHubDarkBackground,
            surface = GitHubDarkSurface,
            onPrimary = GitHubDarkTextPrimary,
            onSecondary = GitHubDarkTextPrimary,
            onBackground = GitHubDarkTextPrimary,
            onSurface = GitHubDarkTextPrimary,
        )
    ) {
        Row {
            // Navigation Rail
            NavigationRail(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight()
                    .background(GitHubDarkSurface),
                header = {
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
                                tint = GitHubDarkTextPrimary
                            )

                            Spacer(modifier = Modifier.width(8.dp))
                            // App name
                            Text(
                                "BOSS console",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = GitHubDarkTextPrimary
                            )
                        }
                    }
                    Divider(color = GitHubDarkBorder)
                }
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {

                    // Lighthouse Section
                    SectionHeader(
                        title = "Lighthouse",
                        isExpanded = Section.LIGHTHOUSE in viewModel.expandedSections,
                        onClick = { viewModel.toggleSection(Section.LIGHTHOUSE) },
                        textColor = GitHubDarkTextSecondary
                    )
                    
                    if (Section.LIGHTHOUSE in viewModel.expandedSections) {
                        viewModel.getItemsBySection(Section.LIGHTHOUSE).forEach { item ->
                            NavigationItem(
                                item = item,
                                isSelected = viewModel.currentScreen == item.screen,
                                onClick = { viewModel.navigateTo(item.screen) },
                                selectedColor = GitHubDarkAccent
                            )
                        }
                    }
                    
                    Divider(color = GitHubDarkBorder)
                    
                    // Lanager Section
                    SectionHeader(
                        title = "Lanager",
                        isExpanded = Section.LANAGER in viewModel.expandedSections,
                        onClick = { viewModel.toggleSection(Section.LANAGER) },
                        textColor = GitHubDarkTextSecondary
                    )
                    
                    if (Section.LANAGER in viewModel.expandedSections) {
                        viewModel.getItemsBySection(Section.LANAGER).forEach { item ->
                            NavigationItem(
                                item = item,
                                isSelected = viewModel.currentScreen == item.screen,
                                onClick = { viewModel.navigateTo(item.screen) },
                                selectedColor = GitHubDarkAccent
                            )
                        }
                    }
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
                    color = GitHubDarkSurface
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (viewModel.currentScreen) {
                            is Screen.Worklist -> Text("Worklist", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = GitHubDarkTextPrimary)
                            is Screen.SystemOfRecords -> Text("System of Records", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = GitHubDarkTextPrimary)
                            is Screen.OrgValues -> Text("Org Values", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = GitHubDarkTextPrimary)
                            is Screen.GlobalLanager -> Text("Global Lanager", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = GitHubDarkTextPrimary)
                            is Screen.MasteryRegistry -> Text("Mastery Registry", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = GitHubDarkTextPrimary)
                            is Screen.TaskResolverRegistry -> Text("TaskResolver Registry", fontSize = 24.sp, fontWeight = FontWeight.Medium, color = GitHubDarkTextPrimary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    isExpanded: Boolean,
    onClick: () -> Unit,
    textColor: Color = Color.Unspecified
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
            color = textColor
        )
        Text(
            text = if (isExpanded) "▼" else "▶",
            fontSize = 10.sp,
            color = textColor
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