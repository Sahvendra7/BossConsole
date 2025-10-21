package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.model.Panel.Companion.bottom
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelInfo
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.plugin.tab_types.fluck.FluckTabInfo
import ai.rever.boss.components.plugin.tab_types.fluck.Fluck
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalSplitViewState
import ai.rever.boss.components.window_panel.SplitViewState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Language
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.arkivanov.decompose.ComponentContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.serialization.Serializable

/**
 * RPA Engine Panel - Executes RPA configurations in browser
 */
object RpaEngineInfo : PanelInfo {
    override val id = PanelId(panelId = "rpa_engine", defaultOrder = 20)
    override val displayName = "RPA Engine"
    override val icon = Icons.Default.SmartToy // Robot icon for automation
    override val defaultSlotPosition = right.top.top
}

/**
 * Execution status for RPA actions
 */
enum class ExecutionStatus {
    IDLE,
    LOADING,
    EXECUTING,
    PAUSED,
    COMPLETED,
    ERROR
}

/**
 * Execution result for tracking action outcomes
 */
data class ActionExecutionResult(
    val actionIndex: Int,
    val actionName: String,
    val success: Boolean,
    val error: String? = null,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

/**
 * Configuration file info
 */
@Serializable
data class ConfigFileInfo(
    val name: String,
    val path: String,
    val lastModified: Long
)

/**
 * Main RPA Engine component
 */
open class RpaEngineComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {
    
    // Configuration management
    protected val _selectedConfig = MutableStateFlow<RpaConfiguration?>(null)
    val selectedConfig: StateFlow<RpaConfiguration?> = _selectedConfig
    
    protected val _availableConfigs = MutableStateFlow<List<ConfigFileInfo>>(emptyList())
    val availableConfigs: StateFlow<List<ConfigFileInfo>> = _availableConfigs
    
    // Execution state
    protected val _executionStatus = MutableStateFlow(ExecutionStatus.IDLE)
    val executionStatus: StateFlow<ExecutionStatus> = _executionStatus
    
    protected val _currentActionIndex = MutableStateFlow(-1)
    val currentActionIndex: StateFlow<Int> = _currentActionIndex
    
    protected val _executionResults = MutableStateFlow<List<ActionExecutionResult>>(emptyList())
    val executionResults: StateFlow<List<ActionExecutionResult>> = _executionResults
    
    
    // Execution settings
    protected val _executionSpeed = MutableStateFlow(1.0f) // 1.0 = normal, 0.5 = slow, 2.0 = fast
    val executionSpeed: StateFlow<Float> = _executionSpeed
    
    protected val _humanLikeMode = MutableStateFlow(true)
    val humanLikeMode: StateFlow<Boolean> = _humanLikeMode
    
    @Composable
    override fun Content() {
        RpaEngineContent(this)
    }
    
    @Composable
    internal fun ContentInternal() {
        val selectedConfig by selectedConfig.collectAsState()
        val availableConfigs by availableConfigs.collectAsState()
        val executionStatus by executionStatus.collectAsState()
        val currentActionIndex by currentActionIndex.collectAsState()
        val executionResults by executionResults.collectAsState()
        val executionSpeed by executionSpeed.collectAsState()
        val humanLikeMode by humanLikeMode.collectAsState()
        
        // Access SplitViewState for creating tabs
        val splitViewState = LocalSplitViewState.current
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                EngineHeader(
                    executionStatus = executionStatus,
                    onRefreshConfigs = { loadAvailableConfigurations() }
                )
            }
            
            // Configuration selector
            item {
                ConfigurationSelector(
                    availableConfigs = availableConfigs,
                    selectedConfig = selectedConfig,
                    onConfigSelected = { loadConfiguration(it) },
                    enabled = executionStatus == ExecutionStatus.IDLE
                )
            }
            
            if (selectedConfig != null) {
                // Execution controls
                item {
                    ExecutionControls(
                        executionStatus = executionStatus,
                        executionSpeed = executionSpeed,
                        humanLikeMode = humanLikeMode,
                        onSpeedChange = { _executionSpeed.value = it },
                        onHumanModeChange = { _humanLikeMode.value = it },
                        onStart = { startExecution(splitViewState) },
                        onPause = { pauseExecution() },
                        onStop = { stopExecution() },
                        onReset = { resetExecution() },
                        hasTargetTab = true // Always true since we'll create a new tab
                    )
                }
                
                // Action list with execution progress
                item {
                    ActionListCard(
                        actions = selectedConfig!!.actions,
                        currentActionIndex = currentActionIndex,
                        executionResults = executionResults,
                        executionStatus = executionStatus
                    )
                }
            }
        }
    }
    
    @Composable
    private fun EngineHeader(
        executionStatus: ExecutionStatus,
        onRefreshConfigs: () -> Unit
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.SmartToy,
                        contentDescription = "RPA Engine",
                        modifier = Modifier.size(24.dp),
                        tint = when (executionStatus) {
                            ExecutionStatus.EXECUTING -> Color(0xFF4CAF50)
                            ExecutionStatus.ERROR -> MaterialTheme.colors.error
                            else -> MaterialTheme.colors.primary
                        }
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            "RPA Engine",
                            style = MaterialTheme.typography.h6,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            getStatusText(executionStatus),
                            style = MaterialTheme.typography.caption,
                            color = when (executionStatus) {
                                ExecutionStatus.EXECUTING -> Color(0xFF4CAF50)
                                ExecutionStatus.ERROR -> MaterialTheme.colors.error
                                else -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            }
                        )
                        if (executionStatus == ExecutionStatus.IDLE) {
                            Text(
                                "• Runs in background browser instance",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                        if (executionStatus == ExecutionStatus.EXECUTING) {
                            Text(
                                "• Check console for execution logs",
                                style = MaterialTheme.typography.caption,
                                color = Color(0xFF4CAF50).copy(alpha = 0.8f),
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                            )
                        }
                    }
                }
                
                IconButton(
                    onClick = onRefreshConfigs,
                    enabled = executionStatus == ExecutionStatus.IDLE
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh Configurations",
                        tint = if (executionStatus == ExecutionStatus.IDLE) 
                            MaterialTheme.colors.primary 
                        else 
                            MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
    
    @Composable
    private fun ConfigurationSelector(
        availableConfigs: List<ConfigFileInfo>,
        selectedConfig: RpaConfiguration?,
        onConfigSelected: (ConfigFileInfo) -> Unit,
        enabled: Boolean
    ) {
        var expanded by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Text(
                    "RPA Configuration",
                    style = MaterialTheme.typography.subtitle2,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = enabled) { expanded = true }
                        .border(
                            1.dp,
                            if (selectedConfig != null)
                                MaterialTheme.colors.primary.copy(alpha = 0.5f)
                            else
                                MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                            RoundedCornerShape(4.dp)
                        ),
                    color = MaterialTheme.colors.surface,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Default.Description,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (selectedConfig != null) 
                                    MaterialTheme.colors.primary 
                                else 
                                    MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedConfig?.name ?: if (availableConfigs.isEmpty()) 
                                    "No configurations found"
                                else 
                                    "Select configuration...",
                                style = MaterialTheme.typography.body2
                            )
                        }
                        
                        Icon(
                            if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                            contentDescription = "Dropdown",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                
                DropdownMenu(
                    expanded = expanded && availableConfigs.isNotEmpty(),
                    onDismissRequest = { expanded = false }
                ) {
                    availableConfigs.forEach { configFile ->
                        DropdownMenuItem(
                            onClick = {
                                onConfigSelected(configFile)
                                expanded = false
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Default.Description,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        configFile.name,
                                        style = MaterialTheme.typography.body2
                                    )
                                    Text(
                                        getFileDate(configFile.lastModified),
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                if (selectedConfig != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "${selectedConfig.actions.size} actions • ${selectedConfig.description}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
    
    
    @Composable
    private fun ExecutionControls(
        executionStatus: ExecutionStatus,
        executionSpeed: Float,
        humanLikeMode: Boolean,
        onSpeedChange: (Float) -> Unit,
        onHumanModeChange: (Boolean) -> Unit,
        onStart: () -> Unit,
        onPause: () -> Unit,
        onStop: () -> Unit,
        onReset: () -> Unit,
        hasTargetTab: Boolean
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(6.dp),
            elevation = 1.dp,
            backgroundColor = if (executionStatus == ExecutionStatus.EXECUTING)
                Color(0xFF4CAF50).copy(alpha = 0.1f)
            else
                MaterialTheme.colors.surface
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Control buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Start/Resume button
                    Button(
                        onClick = onStart,
                        enabled = hasTargetTab && (executionStatus == ExecutionStatus.IDLE || executionStatus == ExecutionStatus.PAUSED),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            if (executionStatus == ExecutionStatus.PAUSED) Icons.Default.PlayArrow else Icons.Default.PlayCircle,
                            contentDescription = if (executionStatus == ExecutionStatus.PAUSED) "Resume" else "Start",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (executionStatus == ExecutionStatus.PAUSED) "Resume" else "Start")
                    }
                    
                    // Pause button
                    Button(
                        onClick = onPause,
                        enabled = executionStatus == ExecutionStatus.EXECUTING,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFFF9800)
                        )
                    ) {
                        Icon(
                            Icons.Default.Pause,
                            contentDescription = "Pause",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pause")
                    }
                    
                    // Stop button
                    Button(
                        onClick = onStop,
                        enabled = executionStatus != ExecutionStatus.IDLE,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = MaterialTheme.colors.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Stop,
                            contentDescription = "Stop",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop")
                    }
                    
                    // Reset button
                    IconButton(
                        onClick = onReset,
                        enabled = executionStatus == ExecutionStatus.COMPLETED || executionStatus == ExecutionStatus.ERROR
                    ) {
                        Icon(
                            Icons.Default.Refresh,
                            contentDescription = "Reset",
                            tint = if (executionStatus == ExecutionStatus.COMPLETED || executionStatus == ExecutionStatus.ERROR)
                                MaterialTheme.colors.primary
                            else
                                MaterialTheme.colors.onSurface.copy(alpha = 0.3f)
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                // Speed control
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Speed:",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.width(60.dp)
                    )
                    
                    Slider(
                        value = executionSpeed,
                        onValueChange = onSpeedChange,
                        valueRange = 0.5f..2.0f,
                        steps = 5,
                        enabled = executionStatus == ExecutionStatus.IDLE,
                        modifier = Modifier.weight(1f)
                    )
                    
                    Text(
                        "${(executionSpeed * 100).toInt()}%",
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.width(50.dp)
                    )
                }
                
                // Human-like mode toggle
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = executionStatus == ExecutionStatus.IDLE) { 
                            onHumanModeChange(!humanLikeMode) 
                        }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = humanLikeMode,
                        onCheckedChange = onHumanModeChange,
                        enabled = executionStatus == ExecutionStatus.IDLE
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Human-like behavior (random delays, mouse movements)",
                        style = MaterialTheme.typography.body2
                    )
                }
            }
        }
    }
    
    @Composable
    private fun ActionListCard(
        actions: List<RpaActionConfig>,
        currentActionIndex: Int,
        executionResults: List<ActionExecutionResult>,
        executionStatus: ExecutionStatus
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 1.dp
        ) {
            Column {
                // Header
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Actions",
                            style = MaterialTheme.typography.subtitle1,
                            fontWeight = FontWeight.Bold
                        )
                        
                        if (executionStatus == ExecutionStatus.EXECUTING || 
                            executionStatus == ExecutionStatus.COMPLETED ||
                            executionResults.isNotEmpty()) {
                            Text(
                                "${executionResults.count { it.success }}/${actions.size} completed",
                                style = MaterialTheme.typography.caption,
                                color = Color(0xFF4CAF50)
                            )
                        }
                    }
                }
                
                // Action items - no nested scrolling, just a column
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    actions.forEachIndexed { index, action ->
                        ActionItem(
                            action = action,
                            index = index,
                            isCurrent = index == currentActionIndex,
                            result = executionResults.find { it.actionIndex == index }
                        )
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ActionItem(
        action: RpaActionConfig,
        index: Int,
        isCurrent: Boolean,
        result: ActionExecutionResult?
    ) {
        val backgroundColor = when {
            isCurrent -> MaterialTheme.colors.primary.copy(alpha = 0.2f)
            result?.success == true -> Color(0xFF4CAF50).copy(alpha = 0.1f)
            result?.success == false -> MaterialTheme.colors.error.copy(alpha = 0.1f)
            else -> MaterialTheme.colors.surface
        }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = backgroundColor,
            shape = RoundedCornerShape(6.dp),
            elevation = if (isCurrent) 2.dp else 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Status indicator
                Surface(
                    modifier = Modifier.size(32.dp),
                    shape = CircleShape,
                    color = when {
                        isCurrent -> MaterialTheme.colors.primary
                        result?.success == true -> Color(0xFF4CAF50)
                        result?.success == false -> MaterialTheme.colors.error
                        else -> MaterialTheme.colors.onSurface.copy(alpha = 0.1f)
                    }
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        when {
                            isCurrent -> Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "Executing",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            result?.success == true -> Icon(
                                Icons.Default.Check,
                                contentDescription = "Success",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            result?.success == false -> Icon(
                                Icons.Default.Close,
                                contentDescription = "Failed",
                                modifier = Modifier.size(16.dp),
                                tint = Color.White
                            )
                            else -> Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Action details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        action.name,
                        style = MaterialTheme.typography.body1,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                    )
                    Text(
                        "${action.type.uppercase()} - ${action.selector.value ?: ""}",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                    
                    if (result?.error != null) {
                        Text(
                            result.error,
                            style = MaterialTheme.typography.caption,
                            color = MaterialTheme.colors.error
                        )
                    }
                }
                
                // Action type icon
                Icon(
                    imageVector = when (action.type) {
                        "click" -> Icons.Default.TouchApp
                        "input" -> Icons.Default.Keyboard
                        "select" -> Icons.Default.ArrowDropDown
                        "navigate" -> Icons.Default.Navigation
                        "wait" -> Icons.Default.Schedule
                        "scroll" -> Icons.Default.SwapVert
                        else -> Icons.Default.Code
                    },
                    contentDescription = action.type,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colors.onSurface.copy(alpha = 0.4f)
                )
            }
        }
    }
    
    /**
     * Load available RPA configurations from the file system
     */
    open fun loadAvailableConfigurations() {
        // This will be implemented in platform-specific code
    }
    
    /**
     * Load a specific configuration file
     */
    protected open fun loadConfiguration(file: ConfigFileInfo) {
        // Clear previous execution state when loading new config
        _currentActionIndex.value = -1
        _executionResults.value = emptyList()
        _executionStatus.value = ExecutionStatus.IDLE
        // This will be implemented in platform-specific code
    }
    
    /**
     * Start or resume execution
     */
    private fun startExecution(splitViewState: SplitViewState?) {
        if (_selectedConfig.value == null) return
        
        val config = _selectedConfig.value!!
        
        // Create a new Fluck tab for RPA execution
        if (splitViewState != null) {
            val activeComponent = splitViewState.getActiveTabsComponent()
            if (activeComponent != null) {
                val timestamp = Clock.System.now().toEpochMilliseconds()
                val firstNavUrl = config.actions.firstOrNull { it.type == "navigate" }?.value ?: "about:blank"
                
                val rpaTab = FluckTabInfo(
                    id = "rpa-$timestamp",
                    typeId = Fluck.typeId,
                    _title = "RPA: ${config.name}",
                    _icon = Icons.Outlined.Language,
                    url = firstNavUrl
                )
                
                val tabIndex = activeComponent.addTab(rpaTab)
                if (tabIndex >= 0) {
                    activeComponent.selectTab(tabIndex)
                    
                    println("============================================================")
                    println("RPA Engine: Created new Fluck tab")
                    println("Tab: RPA: ${config.name}")
                    println("Initial URL: $firstNavUrl")
                    println("============================================================")
                }
            }
        }
        
        _executionStatus.value = ExecutionStatus.EXECUTING
        
        // Start execution coroutine
        kotlinx.coroutines.GlobalScope.launch {
            executeActions()
        }
    }
    
    /**
     * Pause execution
     */
    private fun pauseExecution() {
        _executionStatus.value = ExecutionStatus.PAUSED
    }
    
    /**
     * Stop execution
     */
    private fun stopExecution() {
        _executionStatus.value = ExecutionStatus.IDLE
        _currentActionIndex.value = -1
    }
    
    /**
     * Reset execution state
     */
    private fun resetExecution() {
        _executionStatus.value = ExecutionStatus.IDLE
        _currentActionIndex.value = -1
        _executionResults.value = emptyList()
    }
    
    /**
     * Execute RPA actions
     */
    protected open suspend fun executeActions() {
        // This will be implemented in platform-specific code
    }
    
    /**
     * Get status text for display
     */
    private fun getStatusText(status: ExecutionStatus): String {
        return when (status) {
            ExecutionStatus.IDLE -> "Ready to execute"
            ExecutionStatus.LOADING -> "Loading configuration..."
            ExecutionStatus.EXECUTING -> "Executing actions..."
            ExecutionStatus.PAUSED -> "Execution paused"
            ExecutionStatus.COMPLETED -> "Execution completed"
            ExecutionStatus.ERROR -> "Execution failed"
        }
    }
    
    /**
     * Get formatted file date
     */
    private fun getFileDate(lastModified: Long): String {
        // Simple date formatting - using epoch millis directly
        return "Last modified: ${lastModified / 1000}s ago"
    }
    
}


/**
 * Factory for creating platform-specific RPA Engine components
 */
expect class RpaEngineFactory() {
    fun createComponent(ctx: ComponentContext, panelInfo: PanelInfo): RpaEngineComponent
}

/**
 * Registration function for RPA Engine panel
 */
fun DefaultPlugin.registerRpaEngine() = panelRegistry.registerPanel(RpaEngineInfo) {
    ctx, panelInfo -> RpaEngineFactory().createComponent(ctx, panelInfo)
}
