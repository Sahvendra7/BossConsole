package ai.rever.boss.components.plugin.panels.right_top

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ai.rever.boss.components.dialogs.SupabaseSettingsDialog
import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.services.supabase.*
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.launch

object SupabaseDemoInfo : PanelInfo {
    override val id = PanelId("supabase_demo", 20)
    override val displayName = "Supabase"
    override val icon = Icons.Default.Cloud
    override val defaultSlotPosition = right.top.top
}

class SupabaseDemoComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {
    
    @Composable
    override fun Content() {
        SupabaseDemo()
    }
}

fun DefaultPlugin.registerSupabaseDemo() = panelRegistry.registerPanel(SupabaseDemoInfo) { ctx, panelInfo ->
    SupabaseDemoComponent(ctx, panelInfo)
}

@Composable
private fun SupabaseDemo() {
    var showSettings by remember { mutableStateOf(false) }
    var isConnected by remember { mutableStateOf(false) }
    var connectionStatus by remember { mutableStateOf("Not initialized") }
    
    val scope = rememberCoroutineScope()
    
    // Check if Supabase is already initialized
    LaunchedEffect(Unit) {
        isConnected = SupabaseConfig.isInitialized.value
        if (!isConnected) {
            // Try to initialize from saved settings
            val initialized = SupabaseSettingsManager.initializeFromSavedSettings()
            if (initialized) {
                isConnected = true
                connectionStatus = "Connected"
            } else {
                connectionStatus = "Not configured"
            }
        } else {
            connectionStatus = "Connected"
        }
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colors.surface)
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Supabase Integration",
                    style = MaterialTheme.typography.h6,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = connectionStatus,
                    style = MaterialTheme.typography.caption,
                    color = if (isConnected) Color.Green else Color.Red
                )
            }
            
            IconButton(onClick = { showSettings = true }) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        if (!isConnected) {
            // Not connected state
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.CloudOff,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Supabase not configured")
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = { showSettings = true }) {
                        Text("Configure Supabase")
                    }
                }
            }
        } else {
            // Connected state
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color.Green,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Supabase Connected",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Text(
                        text = "Your BOSS application is now connected to Supabase!",
                        style = MaterialTheme.typography.body2
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = "You can now:",
                        style = MaterialTheme.typography.body2,
                        fontWeight = FontWeight.Medium
                    )
                    
                    Spacer(modifier = Modifier.height(4.dp))
                    
                    Column(
                        modifier = Modifier.padding(start = 16.dp)
                    ) {
                        Text("• Store data in the cloud", style = MaterialTheme.typography.caption)
                        Text("• Authenticate users", style = MaterialTheme.typography.caption)
                        Text("• Use real-time subscriptions", style = MaterialTheme.typography.caption)
                        Text("• Upload and manage files", style = MaterialTheme.typography.caption)
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    Button(
                        onClick = {
                            scope.launch {
                                SupabaseSettingsManager.clearSettings()
                                isConnected = false
                                connectionStatus = "Disconnected"
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.error
                        )
                    ) {
                        Text("Disconnect", color = Color.White)
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = "Next Steps",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.body2
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "To use Supabase in your panels, import the SupabaseConfig " +
                               "and use the client to interact with your database.",
                        style = MaterialTheme.typography.caption
                    )
                }
            }
        }
    }
    
    if (showSettings) {
        SupabaseSettingsDialog(
            onDismiss = { showSettings = false },
            onConfigured = {
                isConnected = true
                connectionStatus = "Connected"
            }
        )
    }
}