package ai.rever.boss.components.window_panel.components

import ai.rever.boss.components.observability.AgentTraceStore
import ai.rever.boss.components.observability.McpTraceEvent
import ai.rever.boss.components.observability.TraceStatus
import ai.rever.boss.components.ui.theme.BossTheme
import ai.rever.boss.components.window_panel.model.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import kotlinx.datetime.Instant

class AgentTracePanelComponent(
    componentContext: ComponentContext,
    val info: PanelInfo,
) : PanelComponentWithUI, ComponentContext by componentContext {
    override val contentId: ai.rever.boss.plugin.api.PanelId = info.id

    @Composable
    override fun Content(modifier: Modifier) {
        val events by AgentTraceStore.events.collectAsState()
        var selectedEventId by remember { mutableStateOf<String?>(null) }
        val selectedEvent = events.find { it.id == selectedEventId }

        Column(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Agent Trace", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
                Button(
                    onClick = { 
                        AgentTraceStore.clear() 
                        selectedEventId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant)
                ) {
                    Text("Clear")
                }
            }
            Divider(color = MaterialTheme.colorScheme.outlineVariant)

            Row(modifier = Modifier.fillMaxSize()) {
                // Master List
                LazyColumn(
                    modifier = Modifier
                        .weight(0.4f)
                        .fillMaxHeight()
                        .padding(end = 8.dp)
                ) {
                    items(events, key = { it.id }) { event ->
                        val isSelected = event.id == selectedEventId
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedEventId = event.id }
                                .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
                                .padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val icon = when (event.status) {
                                TraceStatus.RUNNING -> Icons.Default.HourglassEmpty
                                TraceStatus.SUCCESS -> Icons.Default.CheckCircle
                                TraceStatus.FAILURE -> Icons.Default.Error
                                TraceStatus.TIMEOUT -> Icons.Default.Block
                                TraceStatus.CANCELLED -> Icons.Default.Cancel
                            }
                            val color = when (event.status) {
                                TraceStatus.RUNNING -> MaterialTheme.colorScheme.primary
                                TraceStatus.SUCCESS -> BossTheme.colors.success
                                TraceStatus.FAILURE, TraceStatus.TIMEOUT -> MaterialTheme.colorScheme.error
                                TraceStatus.CANCELLED -> MaterialTheme.colorScheme.outline
                            }
                            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(event.toolName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onBackground)
                                val durationText = event.durationMs?.let { "${it}ms" } ?: "..."
                                Text(durationText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                    }
                }

                Divider(
                    modifier = Modifier.fillMaxHeight().width(1.dp),
                    color = MaterialTheme.colorScheme.outlineVariant
                )

                // Detail View
                Column(
                    modifier = Modifier
                        .weight(0.6f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    if (selectedEvent != null) {
                        DetailSection("Tool", selectedEvent.toolName)
                        DetailSection("Status", selectedEvent.status.name)
                        DetailSection("Started At", Instant.fromEpochMilliseconds(selectedEvent.startedAtMs).toString())
                        if (selectedEvent.durationMs != null) {
                            DetailSection("Duration", "${selectedEvent.durationMs}ms")
                        }
                        
                        Text("Arguments", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.onBackground)
                        SelectionContainer {
                            Text(
                                text = selectedEvent.argumentsJson,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (selectedEvent.resultJson != null) {
                            Text("Result", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.onBackground)
                            SelectionContainer {
                                Text(
                                    text = selectedEvent.resultJson!!,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BossTheme.colors.success
                                )
                            }
                        }
                        
                        if (selectedEvent.errorMessage != null) {
                            Text("Error", fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 16.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.onBackground)
                            SelectionContainer {
                                Text(
                                    text = selectedEvent.errorMessage!!,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    } else {
                        Text("Select a trace event to view details", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.align(Alignment.CenterHorizontally))
                    }
                }
            }
        }
    }
    
    @Composable
    private fun DetailSection(label: String, value: String) {
        Row(modifier = Modifier.padding(bottom = 4.dp)) {
            Text("$label: ", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
            SelectionContainer {
                Text(value, color = MaterialTheme.colorScheme.onBackground)
            }
        }
    }
}
