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

@Composable
fun App() {
    val viewModel = remember { AppViewModel() }

    MaterialTheme {
        Row {
            // Navigation Rail
            NavigationRail(
                modifier = Modifier
                    .width(220.dp)
                    .fillMaxHeight(),
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
                            Icon(imageVector = Icons.Default.Terminal, contentDescription = "Boss Logo")

                            Spacer(modifier = Modifier.width(8.dp))
                            // App name
                            Text(
                                "boss console",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Divider()
                }
            ) {
                Column(modifier = Modifier.padding(horizontal = 8.dp)) {

                    // Lighthouse Section
                    SectionHeader(
                        title = "Lighthouse",
                        isExpanded = Section.LIGHTHOUSE in viewModel.expandedSections,
                        onClick = { viewModel.toggleSection(Section.LIGHTHOUSE) }
                    )
                    
                    if (Section.LIGHTHOUSE in viewModel.expandedSections) {
                        viewModel.getItemsBySection(Section.LIGHTHOUSE).forEach { item ->
                            NavigationItem(
                                item = item,
                                isSelected = viewModel.currentScreen == item.screen,
                                onClick = { viewModel.navigateTo(item.screen) }
                            )
                        }
                    }
                    
                    Divider(color = Color(0xFFDEE2E6))
                    
                    // Lanager Section
                    SectionHeader(
                        title = "Lanager",
                        isExpanded = Section.LANAGER in viewModel.expandedSections,
                        onClick = { viewModel.toggleSection(Section.LANAGER) }
                    )
                    
                    if (Section.LANAGER in viewModel.expandedSections) {
                        viewModel.getItemsBySection(Section.LANAGER).forEach { item ->
                            NavigationItem(
                                item = item,
                                isSelected = viewModel.currentScreen == item.screen,
                                onClick = { viewModel.navigateTo(item.screen) },
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
                    .padding(24.dp)
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        when (viewModel.currentScreen) {
                            is Screen.Worklist -> Text("Worklist", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            is Screen.SystemOfRecords -> Text("System of Records", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            is Screen.OrgValues -> Text("Org Values", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            is Screen.GlobalLanager -> Text("Global Lanager", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            is Screen.MasteryRegistry -> Text("Mastery Registry", fontSize = 24.sp, fontWeight = FontWeight.Medium)
                            is Screen.TaskResolverRegistry -> Text("TaskResolver Registry", fontSize = 24.sp, fontWeight = FontWeight.Medium)
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
) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .clickable { onClick() }
                .padding(4.dp)
            ,
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title.uppercase(),
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp,
                letterSpacing = 1.sp
            )
            Text(
                text = if (isExpanded) "▼" else "▶",
                fontSize = 10.sp
            )
        }
}

@Composable
fun NavigationItem(
    item: NavigationItem,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    
    val backgroundColor = when {
        isSelected -> Color.White
        isHovered -> Color(0xFFF5F5F5) // Light gray for hover
        else -> Color.Transparent
    }
    
    val textColor = if (isSelected) Color(0xFF673AB7) else Color.Black.copy(alpha = 0.6f)
    
    TextButton(
        onClick = onClick,
        interactionSource = interactionSource,
        elevation = ButtonDefaults.elevation(
            defaultElevation = if (isSelected) 2.dp else 0.dp,
        ),
        shape = RoundedCornerShape(4.dp),
        colors = ButtonDefaults.textButtonColors(
            backgroundColor = backgroundColor,
            contentColor = textColor,
            disabledContentColor = textColor
        ),
        modifier = Modifier
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
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