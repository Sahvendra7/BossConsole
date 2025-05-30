package ai.rever.boss.components.dialogs

import ai.rever.boss.components.plugin.panels.left_top.Project
import ai.rever.boss.components.plugin.panels.left_top.ProjectState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.History
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

@Composable
fun ProjectSelectionDialog(
    onDismiss: () -> Unit,
    onOpenDirectoryPicker: () -> Unit = {}
) {
    var selectedTab by remember { mutableStateOf(0) } // 0 = Recent, 1 = Browse
    val recentProjects by ProjectState.recentProjects.collectAsState()
    
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .width(600.dp)
                    .height(500.dp)
                    .onKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                            onDismiss()
                            true
                        } else {
                            false
                        }
                    },
                shape = RoundedCornerShape(8.dp),
                backgroundColor = Color(0xFF2B2D30),
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    // Title
                    Text(
                        text = "Select Project",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Tab selector
                    TabRow(
                        selectedTabIndex = selectedTab,
                        backgroundColor = Color(0xFF2B2D30),
                        contentColor = Color.White
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { 
                                selectedTab = 0 
                            },
                            text = { Text("Recent Projects") },
                            icon = { Icon(Icons.Outlined.History, contentDescription = "Recent") }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { 
                                selectedTab = 1
                            },
                            text = { Text("Browse") },
                            icon = { Icon(Icons.Outlined.FolderOpen, contentDescription = "Browse") }
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    
                    // Content based on selected tab
                    when (selectedTab) {
                        0 -> {
                            // Recent projects list
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                items(recentProjects) { project ->
                                    ProjectListItem(
                                        project = project,
                                        onClick = {
                                            ProjectState.selectProject(project)
                                            onDismiss()
                                        }
                                    )
                                }
                            }
                        }
                        1 -> {
                            // Browse for project
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.FolderOpen,
                                        contentDescription = "Select Folder",
                                        tint = Color(0xFF6B9EFF),
                                        modifier = Modifier.size(64.dp)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(16.dp))
                                    
                                    Text(
                                        text = "Select a project folder",
                                        fontSize = 16.sp,
                                        color = Color.White
                                    )
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    
                                    Text(
                                        text = "Click the button below to choose a project directory",
                                        fontSize = 14.sp,
                                        color = Color(0xFF999999)
                                    )
                                    
                                    Spacer(modifier = Modifier.height(24.dp))
                                    
                                    Button(
                                        onClick = {
                                            onOpenDirectoryPicker()
                                        },
                                        colors = ButtonDefaults.buttonColors(
                                            backgroundColor = Color(0xFF4A9EFF),
                                            contentColor = Color.White
                                        )
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Folder,
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Browse...")
                                    }
                                }
                            }
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    // Close button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            colors = ButtonDefaults.textButtonColors(
                                contentColor = Color(0xFF999999)
                            )
                        ) {
                            Text("Close")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProjectListItem(
    project: Project,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable { onClick() },
        backgroundColor = Color(0xFF3C3F41),
        shape = RoundedCornerShape(4.dp),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Outlined.Folder,
                contentDescription = "Project",
                tint = Color(0xFF90A4AE),
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = project.name,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White
                )
                Text(
                    text = project.path,
                    fontSize = 12.sp,
                    color = Color(0xFF999999)
                )
            }
        }
    }
}