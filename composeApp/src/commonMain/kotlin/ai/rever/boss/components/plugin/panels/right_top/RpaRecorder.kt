@file:OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)

package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.bars.getPanelScrollbarConfig
import ai.rever.boss.components.bars.lazyListScrollbar
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Data class for Fluck tab information
 */
data class FluckTabInfo(
    val id: String,
    val title: String,
    val url: String,
    val panelId: String,
    val tabComponent: Any? // The actual FluckTabComponent
)

/**
 * Data classes for RPA recording
 */
@Serializable
data class RecordedAction(
    val type: String,
    val selector: SelectorInfo,
    val value: String? = null,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds(),
    val elementText: String? = null,
    val url: String? = null,
    val element_type: String? = null
)

@Serializable
data class SelectorInfo(
    val type: String = "xpath", // css, xpath, text, id, none
    val value: String? = null,
    val isUnique: Boolean? = null
)

@Serializable
data class RpaConfiguration(
    val name: String,
    val description: String = "",
    val actions: List<RpaActionConfig>
)

@Serializable
data class RpaActionConfig(
    val name: String = "",
    val action_type: String = "default", // default, assertion, screenshot, network, custom
    val type: String, // click, input, navigate, wait, select, scroll, switch_frame, run_script, screenshot, assert
    val selector: SelectorInfo,
    val value: String? = null,
    val meta: Map<String, String>? = null
)

open class RpaRecorderComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {
    private val logger = BossLogger.forComponent("RpaRecorder")

    private val _recordedActions = MutableStateFlow<List<RecordedAction>>(emptyList())
    val recordedActions: StateFlow<List<RecordedAction>> = _recordedActions
    
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording
    
    internal val _currentUrl = MutableStateFlow("")
    val currentUrl: StateFlow<String> = _currentUrl
    
    protected val _selectedTab = MutableStateFlow<FluckTabInfo?>(null)
    val selectedTab: StateFlow<FluckTabInfo?> = _selectedTab
    
    private val _availableFluckTabs = MutableStateFlow<List<FluckTabInfo>>(emptyList())
    val availableFluckTabs: StateFlow<List<FluckTabInfo>> = _availableFluckTabs
    
    // View mode for actions display
    private val _viewMode = MutableStateFlow(ViewMode.CLEAN)
    val viewMode: StateFlow<ViewMode> = _viewMode
    
    // Selected actions for multi-selection
    private val _selectedActionIndices = MutableStateFlow<Set<Int>>(emptySet())
    val selectedActionIndices: StateFlow<Set<Int>> = _selectedActionIndices
    
    // Notification/feedback messages
    private val _feedbackMessage = MutableStateFlow<FeedbackMessage?>(null)

    // Video recording status
    protected val _isVideoRecording = MutableStateFlow(false)
    val isVideoRecording: StateFlow<Boolean> = _isVideoRecording
    
    // Browser connection reference
    internal var browserConnection: BrowserIntegration? = null
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }
    
    @Composable
    override fun Content() {
        // Use the enhanced content with browser integration
        RpaRecorderContent(this)
    }
    
    @Composable
    internal fun ContentInternal() {
        val recording by isRecording.collectAsState()
        val actions by recordedActions.collectAsState()
        val url by currentUrl.collectAsState()
        val connected by isConnected.collectAsState()
        val selectedTab by selectedTab.collectAsState()
        val availableTabs by availableFluckTabs.collectAsState()
        val viewMode by viewMode.collectAsState()
        val selectedActionIndices by selectedActionIndices.collectAsState()
        val isVideoRecordingActive by isVideoRecording.collectAsState()
        
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(12.dp)
        ) {
            // Compact header with tab selection and controls
            CompactHeader(
                availableTabs = availableTabs,
                selectedTab = selectedTab,
                onTabSelected = { selectTab(it) },
                isRecording = recording,
                isVideoRecording = isVideoRecordingActive,
                onToggleRecording = { toggleRecording() },
                onClear = { clearRecording() },
                onExport = { exportConfiguration() },
                hasSelectedTab = selectedTab != null,
                hasRecordedActions = actions.isNotEmpty()
            )
            
            // Connection status - only show when recording and not connected
            if (!connected && recording) {
                Spacer(modifier = Modifier.height(8.dp))
                ConnectionStatusBar(connected = false)
            }
            
            // Current URL display - make it more compact
            if (recording && url.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                CompactUrlDisplay(url = url)
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Action controls and statistics
            ActionControls(
                totalActions = actions.size,
                filteredActions = getFilteredActions(actions, viewMode).size,
                selectedCount = selectedActionIndices.size,
                viewMode = viewMode,
                onViewModeChange = { _viewMode.value = it },
                onSelectAll = { selectAllActions() },
                onClearSelection = { _selectedActionIndices.value = emptySet() },
                onRefresh = { refreshActions() }
            )
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Recorded actions list with more space
            RecordedActionsList(
                actions = getFilteredActions(actions, viewMode),
                selectedIndices = selectedActionIndices,
                onRemoveAction = { removeAction(it) },
                onEditAction = { index, action -> editAction(index, action) },
                onSelectionChange = { index ->
                    _selectedActionIndices.value = if (selectedActionIndices.contains(index)) {
                        selectedActionIndices - index
                    } else {
                        selectedActionIndices + index
                    }
                }
            )
        }
    }
    
    @Composable
    private fun ActionControls(
        totalActions: Int,
        filteredActions: Int,
        selectedCount: Int,
        viewMode: ViewMode,
        onViewModeChange: (ViewMode) -> Unit,
        onSelectAll: () -> Unit,
        onClearSelection: () -> Unit,
        onRefresh: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                // View mode selector and stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // View mode tabs
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ViewMode.values().forEach { mode ->
                            Surface(
                                modifier = Modifier
                                    .clickable { onViewModeChange(mode) }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(4.dp),
                                color = if (viewMode == mode) 
                                    MaterialTheme.colors.primary.copy(alpha = 0.2f)
                                else 
                                    Color.Transparent
                            ) {
                                Text(
                                    text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.caption,
                                    fontWeight = if (viewMode == mode) FontWeight.Bold else FontWeight.Normal,
                                    color = if (viewMode == mode)
                                        MaterialTheme.colors.primary
                                    else
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                    
                    // Action stats
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (selectedCount > 0) {
                            Surface(
                                shape = RoundedCornerShape(4.dp),
                                color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                            ) {
                                Text(
                                    text = "$selectedCount selected",
                                    style = MaterialTheme.typography.caption,
                                    color = MaterialTheme.colors.primary,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                        
                        Text(
                            text = if (viewMode == ViewMode.CLEAN && filteredActions < totalActions) 
                                "$filteredActions of $totalActions"
                            else 
                                "$totalActions actions",
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        
                        // Refresh button
                        IconButton(
                            onClick = onRefresh,
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "Refresh",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colors.primary
                            )
                        }
                    }
                }
                
                // Selection controls (shown when items exist)
                if (totalActions > 0) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextButton(
                            onClick = onSelectAll,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "Select All",
                                style = MaterialTheme.typography.caption
                            )
                        }
                        if (selectedCount > 0) {
                            TextButton(
                                onClick = onClearSelection,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    "Clear Selection",
                                    style = MaterialTheme.typography.caption
                                )
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun CompactHeader(
        availableTabs: List<FluckTabInfo>,
        selectedTab: FluckTabInfo?,
        onTabSelected: (FluckTabInfo) -> Unit,
        isRecording: Boolean,
        isVideoRecording: Boolean,
        onToggleRecording: () -> Unit,
        onClear: () -> Unit,
        onExport: () -> Unit,
        hasSelectedTab: Boolean,
        hasRecordedActions: Boolean
    ) {
        var dropdownExpanded by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            backgroundColor = if (isRecording) 
                Color(0xFFFFEBEE).copy(alpha = 0.3f) // Light red tint when recording
            else 
                MaterialTheme.colors.surface,
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Title row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.PlayCircle,
                            contentDescription = "RPA Recorder",
                            modifier = Modifier.size(20.dp),
                            tint = if (isRecording) Color(0xFFD32F2F) else MaterialTheme.colors.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "RPA Recorder",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    
                    if (isRecording) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Recording indicator
                            Surface(
                                modifier = Modifier.size(8.dp),
                                shape = CircleShape,
                                color = Color(0xFFD32F2F)
                            ) {}
                            
                            // Video recording indicator
                            if (isVideoRecording) {
                                Icon(
                                    Icons.Default.Videocam,
                                    contentDescription = "Video Recording",
                                    modifier = Modifier.size(16.dp),
                                    tint = Color(0xFFD32F2F)
                                )
                            }
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // Controls row with dropdown and buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Tab selection dropdown (takes most space)
                    Box(modifier = Modifier.weight(1f)) {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(32.dp)
                                .clickable(
                                    enabled = !isRecording && availableTabs.isNotEmpty(),
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { 
                                    dropdownExpanded = true 
                                }
                                .background(
                                    MaterialTheme.colors.surface,
                                    RoundedCornerShape(4.dp)
                                )
                                .border(
                                    1.dp,
                                    if (selectedTab != null && !isRecording)
                                        MaterialTheme.colors.primary.copy(alpha = 0.5f)
                                    else
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                                    RoundedCornerShape(4.dp)
                                ),
                            shape = RoundedCornerShape(4.dp),
                            color = Color.Transparent
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Language,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                    tint = if (selectedTab != null) 
                                        MaterialTheme.colors.primary 
                                    else 
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                                
                                Spacer(modifier = Modifier.width(6.dp))
                                
                                Text(
                                    text = selectedTab?.title ?: if (availableTabs.isEmpty()) 
                                        "No tabs"
                                    else 
                                        "Select tab...",
                                    style = MaterialTheme.typography.body2,
                                    color = if (selectedTab != null) 
                                        MaterialTheme.colors.onSurface 
                                    else 
                                        MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                
                                Icon(
                                    if (dropdownExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                    contentDescription = "Dropdown",
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                            }
                        }
                        
                        DropdownMenu(
                            expanded = dropdownExpanded && availableTabs.isNotEmpty(),
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            availableTabs.forEach { tab ->
                                DropdownMenuItem(
                                    onClick = {
                                        onTabSelected(tab)
                                        dropdownExpanded = false
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Language,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (selectedTab?.id == tab.id)
                                                MaterialTheme.colors.primary
                                            else
                                                MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                tab.title,
                                                style = MaterialTheme.typography.body2,
                                                fontWeight = if (selectedTab?.id == tab.id) FontWeight.Medium else FontWeight.Normal,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                tab.url,
                                                style = MaterialTheme.typography.caption,
                                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                        if (selectedTab?.id == tab.id) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = "Selected",
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colors.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    
                    // Record/Stop button - icon only for compactness
                    Surface(
                        modifier = Modifier.size(32.dp),
                        shape = RoundedCornerShape(4.dp),
                        color = if (isRecording) 
                            Color(0xFFD32F2F)
                        else if (hasSelectedTab)
                            MaterialTheme.colors.primary
                        else
                            MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                        elevation = if (isRecording) 4.dp else 2.dp
                    ) {
                        IconButton(
                            onClick = onToggleRecording,
                            enabled = hasSelectedTab || isRecording,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                imageVector = if (isRecording) 
                                    Icons.Default.Stop 
                                else 
                                    Icons.Default.FiberManualRecord,
                                contentDescription = if (isRecording) "Stop Recording" else "Start Recording",
                                modifier = Modifier.size(18.dp),
                                tint = Color.White
                            )
                        }
                    }
                    
                    // Clear button
                    IconButton(
                        onClick = onClear,
                        enabled = !isRecording && hasRecordedActions,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Clear,
                            contentDescription = "Clear",
                            tint = if (!isRecording && hasRecordedActions) 
                                MaterialTheme.colors.error 
                            else 
                                MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    
                    // Export button
                    IconButton(
                        onClick = onExport,
                        enabled = !isRecording && hasRecordedActions,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "Export",
                            tint = if (!isRecording && hasRecordedActions) 
                                MaterialTheme.colors.primary 
                            else 
                                MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    private fun CompactUrlDisplay(url: String) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(4.dp),
            color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Language,
                    contentDescription = "URL",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = url,
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
    
    @Composable
    private fun ConnectionStatusBar(connected: Boolean) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = if (connected) Color(0xFF4CAF50) else Color(0xFFFF9800),
            shape = RoundedCornerShape(4.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (connected) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = Color.White
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (connected) "Connected to browser" else "No browser connection - Open a Fluck tab",
                    style = MaterialTheme.typography.caption,
                    color = Color.White
                )
            }
        }
    }
    
    
    @Composable
    private fun RecordedActionsList(
        actions: List<RecordedAction>,
        selectedIndices: Set<Int>,
        onRemoveAction: (Int) -> Unit,
        onEditAction: (Int, RecordedAction) -> Unit,
        onSelectionChange: (Int) -> Unit
    ) {
        if (actions.isEmpty()) {
            EmptyStateMessage()
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .lazyListScrollbar(
                        listState = listState,
                        direction = Orientation.Vertical,
                        config = getPanelScrollbarConfig()
                    ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(actions.size) { index ->
                    ActionItem(
                        action = actions[index],
                        index = index,
                        isSelected = selectedIndices.contains(index),
                        onRemove = { onRemoveAction(index) },
                        onEdit = { onEditAction(index, it) },
                        onSelectionChange = { onSelectionChange(index) }
                    )
                }
            }
        }
    }
    
    @Composable
    private fun EmptyStateMessage() {
        Card(
            modifier = Modifier
                .fillMaxSize()
                .padding(vertical = 24.dp),
            shape = RoundedCornerShape(12.dp),
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 0.dp,
            border = BorderStroke(1.dp, MaterialTheme.colors.onSurface.copy(alpha = 0.1f))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(32.dp)
                ) {
                    Surface(
                        modifier = Modifier.size(80.dp),
                        shape = CircleShape,
                        color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                Icons.Default.TouchApp,
                                contentDescription = null,
                                modifier = Modifier.size(40.dp),
                                tint = MaterialTheme.colors.primary
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        "Ready to Record",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Select a browser tab and click Start Recording",
                        style = MaterialTheme.typography.body2,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "All your interactions will be captured automatically",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
    
    @Composable
    private fun ActionItem(
        action: RecordedAction,
        index: Int,
        isSelected: Boolean,
        onRemove: () -> Unit,
        onEdit: (RecordedAction) -> Unit,
        onSelectionChange: () -> Unit
    ) {
        var isEditing by remember { mutableStateOf(false) }
        var editedValue by remember { mutableStateOf(action.value ?: "") }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp)),
            backgroundColor = MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // Selection checkbox
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onSelectionChange() },
                        modifier = Modifier.padding(end = 8.dp),
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colors.primary
                        )
                    )
                    
                    Column(modifier = Modifier.weight(1f)) {
                        // Action type with icon
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = when (action.type) {
                                    "click", "auto_click" -> Icons.Default.TouchApp
                                    "input" -> Icons.Default.Keyboard
                                    "select" -> Icons.Default.ArrowDropDown
                                    "navigation" -> Icons.Default.Navigation
                                    "scroll" -> Icons.Default.SwapVert
                                    "wait" -> Icons.Default.Schedule
                                    else -> Icons.Default.Code
                                },
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colors.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "${index + 1}. ${action.type.uppercase()}",
                                style = MaterialTheme.typography.subtitle1,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colors.primary
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        // Selector info with validation indicator
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Selector type badge
                            Surface(
                                color = when (action.selector.type) {
                                    "id" -> Color(0xFF4CAF50).copy(alpha = 0.2f)
                                    "css" -> Color(0xFF2196F3).copy(alpha = 0.2f)
                                    "xpath" -> Color(0xFFFF9800).copy(alpha = 0.2f)
                                    else -> MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
                                },
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.padding(end = 4.dp)
                            ) {
                                Text(
                                    text = action.selector.type.uppercase(),
                                    style = MaterialTheme.typography.caption,
                                    color = when (action.selector.type) {
                                        "id" -> Color(0xFF4CAF50)
                                        "css" -> Color(0xFF2196F3)
                                        "xpath" -> Color(0xFFFF9800)
                                        else -> MaterialTheme.colors.onSurface
                                    },
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            
                            Text(
                                text = action.selector.value ?: "[No selector]",
                                style = MaterialTheme.typography.body2,
                                fontFamily = FontFamily.Monospace,
                                color = MaterialTheme.colors.onSurface,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            // Validation indicator based on uniqueness
                            if (action.selector.value != null && action.selector.type != "none") {
                                Spacer(modifier = Modifier.width(4.dp))
                                
                                val tooltipText = when (action.selector.isUnique) {
                                    true -> "Unique selector"
                                    false -> "Multiple matches"
                                    null -> "Not validated"
                                }
                                
                                when (action.selector.isUnique) {
                                    true -> Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = tooltipText,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.Green.copy(alpha = 0.7f)
                                    )
                                    false -> Icon(
                                        Icons.Default.Warning,
                                        contentDescription = tooltipText,
                                        modifier = Modifier.size(14.dp),
                                        tint = Color.Yellow.copy(alpha = 0.7f)
                                    )
                                    null -> Icon(
                                        Icons.Default.QuestionMark,
                                        contentDescription = tooltipText,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                                    )
                                }
                            }
                        }
                        
                        // Value if present
                        if (action.value != null && action.type != "click") {
                            Spacer(modifier = Modifier.height(4.dp))
                            if (isEditing) {
                                OutlinedTextField(
                                    value = editedValue,
                                    onValueChange = { editedValue = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    singleLine = true,
                                    textStyle = MaterialTheme.typography.body2
                                )
                            } else {
                                Text(
                                    text = "Value: ${action.value}",
                                    style = MaterialTheme.typography.body2,
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                        }
                        
                        // Element text if present
                        if (!action.elementText.isNullOrEmpty()) {
                            Text(
                                text = "Text: ${action.elementText}",
                                style = MaterialTheme.typography.body2,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    
                    // Action buttons
                    Row {
                        if (action.type == "input" || action.type == "select") {
                            IconButton(
                                onClick = {
                                    if (isEditing) {
                                        onEdit(action.copy(value = editedValue))
                                        isEditing = false
                                    } else {
                                        isEditing = true
                                    }
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    if (isEditing) Icons.Default.Check else Icons.Default.Edit,
                                    contentDescription = if (isEditing) "Save" else "Edit",
                                    modifier = Modifier.size(16.dp),
                                    tint = if (isEditing) Color.Green else MaterialTheme.colors.onSurface
                                )
                            }
                        }
                        
                        IconButton(
                            onClick = onRemove,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "Remove",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colors.error
                            )
                        }
                    }
                }
            }
        }
    }
    
    /**
     * Select a tab to record from
     */
    fun selectTab(tab: FluckTabInfo) {
        _selectedTab.value = tab
        _currentUrl.value = tab.url
    }
    
    /**
     * Update available Fluck tabs
     */
    fun updateAvailableTabs(tabs: List<FluckTabInfo>) {
        _availableFluckTabs.value = tabs
        
        // If selected tab is no longer available, clear selection
        if (_selectedTab.value != null && tabs.none { it.id == _selectedTab.value?.id }) {
            _selectedTab.value = null
        }
        
        // Auto-select first tab if no tab is selected and tabs are available
        if (_selectedTab.value == null && tabs.isNotEmpty()) {
            _selectedTab.value = tabs.first()
            logger.debug(LogCategory.SYSTEM, "Auto-selected tab", mapOf("tabId" to tabs.first().id))
        }
    }
    
    /**
     * Toggle recording state and inject/remove event listeners
     */
    private fun toggleRecording() {
        if (_isRecording.value) {
            stopRecording()
        } else {
            startRecording()
        }
    }
    
    /**
     * Start recording browser interactions
     */
    private fun startRecording() {
        val selectedTab = _selectedTab.value
        if (selectedTab == null) {
            logger.warn(LogCategory.SYSTEM, "No tab selected for recording")
            return
        }

        logger.debug(LogCategory.SYSTEM, "Starting recording", mapOf("tab" to selectedTab.title, "hasConnection" to (browserConnection != null)))

        // Don't capture URL here - it will be done after browser connection is established

        _isRecording.value = true
        injectEventListeners()
    }
    
    /**
     * Stop recording and remove event listeners
     */
    private fun stopRecording() {
        _isRecording.value = false
        removeEventListeners()
    }
    
    /**
     * Clear all recorded actions
     */
    private fun clearRecording() {
        _recordedActions.value = emptyList()
    }
    
    /**
     * Remove a specific action
     */
    private fun removeAction(index: Int) {
        _recordedActions.value = _recordedActions.value.toMutableList().apply {
            removeAt(index)
        }
    }
    
    /**
     * Edit a specific action
     */
    private fun editAction(index: Int, newAction: RecordedAction) {
        _recordedActions.value = _recordedActions.value.toMutableList().apply {
            set(index, newAction)
        }
    }
    
    /**
     * Get filtered actions based on view mode
     */
    private fun getFilteredActions(actions: List<RecordedAction>, viewMode: ViewMode): List<RecordedAction> {
        return when (viewMode) {
            ViewMode.CLEAN -> {
                // Group actions by selector and keep only the most relevant ones
                val actionGroups = mutableMapOf<String, MutableList<RecordedAction>>()
                val otherActions = mutableListOf<RecordedAction>()
                
                // Group actions by selector
                for (action in actions) {
                    when (action.type) {
                        "input" -> {
                            val key = action.selector.value ?: "unknown"
                            actionGroups.getOrPut(key) { mutableListOf() }.add(action)
                        }
                        "click", "auto_click" -> {
                            // Keep all clicks as they represent user intent
                            otherActions.add(action)
                        }
                        "scroll" -> {
                            // Only keep scrolls that are followed by a meaningful action
                            if (otherActions.isNotEmpty() && otherActions.last().type != "scroll") {
                                otherActions.add(action)
                            }
                        }
                        else -> otherActions.add(action)
                    }
                }
                
                // For each input group, keep only the last (final) value
                val cleanedInputs = actionGroups.values.map { group ->
                    group.last() // Keep only the final input value
                }
                
                // Combine and sort by timestamp
                (otherActions + cleanedInputs).sortedBy { it.timestamp }
            }
            ViewMode.RAW -> actions // Show everything as recorded
            ViewMode.EDITOR -> {
                // Editor mode could show actions with edit capabilities
                // For now, same as raw but could be enhanced later
                actions
            }
        }
    }
    
    /**
     * Select all visible actions
     */
    private fun selectAllActions() {
        val visibleActions = getFilteredActions(_recordedActions.value, _viewMode.value)
        _selectedActionIndices.value = visibleActions.indices.toSet()
    }
    
    /**
     * Refresh actions (placeholder for future server integration)
     */
    private fun refreshActions() {
        // In the future, this could fetch latest actions from server
        showFeedback("Actions refreshed", FeedbackType.INFO)
    }
    
    /**
     * Show feedback message
     */
    protected fun showFeedback(message: String, type: FeedbackType) {
        _feedbackMessage.value = FeedbackMessage(message, type)
        // Auto-hide after 3 seconds
        kotlinx.coroutines.GlobalScope.launch {
            delay(3000)
            _feedbackMessage.value = null
        }
    }

    /**
     * Export recorded actions as RPA configuration
     */
    private fun exportConfiguration() {
        // Export selected actions if any, otherwise export based on view mode
        val actionsToExport = if (_selectedActionIndices.value.isNotEmpty()) {
            val allActions = getFilteredActions(_recordedActions.value, _viewMode.value)
            allActions.filterIndexed { index, _ -> _selectedActionIndices.value.contains(index) }
        } else {
            getFilteredActions(_recordedActions.value, _viewMode.value)
        }
        
        val config = generateRpaConfiguration(actionsToExport)
        val jsonString = json.encodeToString(config)
        
        // Generate filename with timestamp
        val timestamp = Clock.System.now().toEpochMilliseconds()
        val filename = "rpa_configuration_${timestamp}.json"
        
        // Save to downloads directory and open location
        saveAndOpenConfiguration(filename, jsonString)
        
        // Show feedback
        val actionCount = actionsToExport.size
        val message = if (_selectedActionIndices.value.isNotEmpty()) {
            "Exported $actionCount selected actions"
        } else {
            "Exported $actionCount ${_viewMode.value.name.lowercase()} actions"
        }
        showFeedback(message, FeedbackType.SUCCESS)
    }
    
    /**
     * Platform-specific file save and location open
     */
    protected open fun saveAndOpenConfiguration(filename: String, content: String) {
        // This will be implemented in platform-specific code
        logger.debug(LogCategory.SYSTEM, "RPA configuration export", mapOf("filename" to filename))
    }
    
    /**
     * Generate RPA configuration from recorded actions
     */
    private fun generateRpaConfiguration(actions: List<RecordedAction> = _recordedActions.value): RpaConfiguration {
        val rpaActions = actions.mapIndexed { index, action ->
            when (action.type) {
                "click", "auto_click" -> RpaActionConfig(
                    name = "Click on ${action.elementText ?: action.selector.value ?: "element"}",
                    action_type = "default",
                    type = "click",
                    selector = action.selector,
                    value = null,
                    meta = buildMap {
                        put("button", "left")
                        action.elementText?.let { put("text", it) }
                    }
                )
                "input" -> RpaActionConfig(
                    name = "Type into ${action.selector.value ?: "input field"}",
                    action_type = "default",
                    type = "input",
                    selector = action.selector,
                    value = action.value
                )
                "select" -> RpaActionConfig(
                    name = "Select ${action.value ?: "option"} in ${action.selector.value ?: "dropdown"}",
                    action_type = "default",
                    type = "select",
                    selector = action.selector,
                    value = action.value
                )
                "navigation" -> RpaActionConfig(
                    name = "Navigate to ${action.url}",
                    action_type = "default",
                    type = "navigate",
                    selector = SelectorInfo("none", null),
                    value = action.url
                )
                "wait" -> RpaActionConfig(
                    name = "Wait for ${action.selector.value ?: "${action.value}ms"}",
                    action_type = "default",
                    type = "wait",
                    selector = action.selector,
                    value = action.value ?: "1000"
                )
                "scroll" -> RpaActionConfig(
                    name = "Scroll to position",
                    action_type = "default",
                    type = "scroll",
                    selector = SelectorInfo("none", null),
                    value = action.value ?: "0,0"
                )
                else -> RpaActionConfig(
                    name = "Action ${index + 1}",
                    action_type = "default",
                    type = action.type,
                    selector = action.selector,
                    value = action.value
                )
            }
        }
        
        return RpaConfiguration(
            name = "Recorded RPA Process",
            description = "Automatically recorded browser interactions",
            actions = rpaActions
        )
    }
    
    /**
     * Inject event listeners into the browser
     */
    protected open fun injectEventListeners() {
        // This will be implemented in platform-specific code
    }
    
    /**
     * Remove event listeners from the browser
     */
    protected open fun removeEventListeners() {
        // This will be implemented in platform-specific code
    }
    
    
    /**
     * Add initial navigation after browser connection is established
     */
    fun addInitialNavigation() {
        kotlinx.coroutines.GlobalScope.launch {
            try {
                val currentUrl = browserConnection?.getCurrentUrl()
                logger.debug(LogCategory.SYSTEM, "Capturing initial URL", mapOf("url" to (currentUrl ?: "null")))

                if (!currentUrl.isNullOrEmpty() && currentUrl != "about:blank") {
                    val navigationAction = RecordedAction(
                        type = "navigate",
                        selector = SelectorInfo(
                            type = "none",
                            value = null
                        ),
                        value = currentUrl,
                        timestamp = Clock.System.now().toEpochMilliseconds(),
                        elementText = null
                    )
                    _recordedActions.value = listOf(navigationAction) + _recordedActions.value
                    logger.debug(LogCategory.SYSTEM, "Added initial navigation action")
                } else {
                    logger.debug(LogCategory.SYSTEM, "No valid URL to capture")
                }
            } catch (e: Exception) {
                logger.warn(LogCategory.SYSTEM, "Error capturing initial URL", error = e)
            }
        }
    }
    
    /**
     * Handle recorded action from browser
     */
    fun onActionRecorded(action: RecordedAction) {
        if (_isRecording.value) {
            _recordedActions.value = _recordedActions.value + action
            
            // Update current URL if it's a navigation action
            if (action.type == "navigation") {
                _currentUrl.value = action.url ?: ""
            }
        }
    }
}

/**
 * View modes for displaying actions
 */
enum class ViewMode {
    CLEAN,  // Filtered view without redundant actions
    RAW,    // All captured actions
    EDITOR  // Editable view
}

/**
 * Feedback message types
 */
data class FeedbackMessage(
    val text: String,
    val type: FeedbackType,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

enum class FeedbackType {
    SUCCESS,
    INFO
}

/**
 * Factory for creating platform-specific RPA Recorder components
 */
expect class RpaRecorderFactory() {
    fun createComponent(ctx: ComponentContext, panelInfo: PanelInfo): RpaRecorderComponent
}


