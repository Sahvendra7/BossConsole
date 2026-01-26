package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.utils.logging.BossLogger
import ai.rever.boss.utils.logging.LogCategory
import ai.rever.boss.components.bars.getPanelScrollbarConfig
import ai.rever.boss.components.bars.lazyListScrollbar
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalSplitViewState
import ai.rever.boss.plugin.api.PanelComponentWithUI
import ai.rever.boss.plugin.api.PanelInfo
import ai.rever.boss.plugin.panel.llmrpa.LLMRpaInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
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
import androidx.compose.ui.unit.sp
import com.arkivanov.decompose.ComponentContext
import androidx.compose.material.icons.outlined.AutoAwesome
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlin.time.Clock
import kotlinx.serialization.Serializable

/**
 * Data classes for LLM RPA
 */
@Serializable
data class LLMAction(
    val instruction: String,
    val actionType: String = "default", // default, custom
    val meta: Map<String, String>? = null
)

@Serializable
data class LLMRpaRequest(
    val actions: List<LLMAction>,
    val sourceUrl: String,
    val configuration: List<RpaActionConfig>? = null
)

@Serializable
data class LLMRpaResponse(
    val configuration: List<RpaActionConfig>,
    val status: String,
    val message: String? = null
)

/**
 * Execution state for LLM RPA
 */
data class LLMExecutionState(
    val instruction: String,
    val status: LLMExecutionStatus,
    val generatedActions: List<RpaActionConfig> = emptyList(),
    val error: String? = null,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

enum class LLMExecutionStatus {
    GENERATING,
    EXECUTING,
    COMPLETED,
    ERROR
}

open class LLMRpaComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {
    private val logger = BossLogger.forComponent("LLMRpaComponent")

    private val _executionHistory = MutableStateFlow<List<LLMExecutionState>>(emptyList())
    val executionHistory: StateFlow<List<LLMExecutionState>> = _executionHistory
    
    private val _selectedTab = MutableStateFlow<FluckTabInfo?>(null)
    val selectedTab: StateFlow<FluckTabInfo?> = _selectedTab
    
    private val _availableFluckTabs = MutableStateFlow<List<FluckTabInfo>>(emptyList())
    val availableFluckTabs: StateFlow<List<FluckTabInfo>> = _availableFluckTabs
    
    private val _isExecuting = MutableStateFlow(false)
    val isExecuting: StateFlow<Boolean> = _isExecuting
    
    private val _currentInstruction = MutableStateFlow("")
    val currentInstruction: StateFlow<String> = _currentInstruction
    
    // Browser connection reference
    internal var browserConnection: BrowserIntegration? = null
    internal var rpaExecutor: RpaActionExecutor? = null

    @Composable
    override fun Content() {
        // Use enhanced content with browser integration
        LLMRpaContent(this)
    }
    
    @Composable
    internal fun ContentInternal() {
        LocalSplitViewState.current
        val selectedTab by selectedTab.collectAsState()
        val availableTabs by availableFluckTabs.collectAsState()
        val instruction by currentInstruction.collectAsState()
        val isExecuting by isExecuting.collectAsState()
        val history by executionHistory.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        val listState = rememberLazyListState()

        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp)
                .lazyListScrollbar(
                    listState = listState,
                    direction = Orientation.Vertical,
                    config = getPanelScrollbarConfig()
                ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
            item {
                HeaderSection()
            }
            
            // Tab Selection
            item {
                TabSelectionSection(
                    availableTabs = availableTabs,
                    selectedTab = selectedTab,
                    onTabSelected = { selectTab(it) },
                    enabled = !isExecuting
                )
            }
            
            // Instruction Input
            item {
                InstructionInputSection(
                    instruction = instruction,
                    onInstructionChange = { _currentInstruction.value = it },
                    onExecute = {
                        coroutineScope.launch {
                            executeInstruction(instruction)
                        }
                    },
                    isExecuting = isExecuting,
                    hasSelectedTab = selectedTab != null
                )
            }
            
            // LLM Configuration Status
            item {
                LLMConfigurationSection(
                    enabled = !isExecuting
                )
            }
            
            // Current Execution Status (if executing)
            if (isExecuting && history.isNotEmpty()) {
                val currentExecution = history.lastOrNull { it.status == LLMExecutionStatus.GENERATING || it.status == LLMExecutionStatus.EXECUTING }
                if (currentExecution != null) {
                    item {
                        CurrentExecutionCard(currentExecution)
                    }
                }
            }
            
            // Execution History
            if (history.isNotEmpty()) {
                item {
                    Text(
                        "Execution History",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                
                items(history.reversed()) { execution ->
                    ExecutionHistoryItem(execution)
                }
            }
        }
    }
    
    @Composable
    private fun CurrentExecutionCard(execution: LLMExecutionState) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            backgroundColor = MaterialTheme.colors.primary.copy(alpha = 0.1f),
            elevation = 2.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colors.primary
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        when (execution.status) {
                            LLMExecutionStatus.GENERATING -> "Generating actions..."
                            LLMExecutionStatus.EXECUTING -> "Executing actions..."
                            else -> "Processing..."
                        },
                        style = MaterialTheme.typography.subtitle1,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                if (execution.generatedActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Actions to execute:",
                        style = MaterialTheme.typography.caption,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                    )
                    
                    execution.generatedActions.forEachIndexed { index, action ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier.padding(start = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "• ",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.primary
                            )
                            Text(
                                "${action.type}: ${action.name}",
                                style = MaterialTheme.typography.caption
                            )
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun HeaderSection() {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            backgroundColor = MaterialTheme.colors.surface,
            elevation = 2.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.AutoAwesome,
                    contentDescription = "LLM RPA",
                    modifier = Modifier.size(24.dp),
                    tint = MaterialTheme.colors.primary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        "LLM RPA",
                        style = MaterialTheme.typography.h6,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "Natural language automation powered by AI",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
    
    @Composable
    private fun TabSelectionSection(
        availableTabs: List<FluckTabInfo>,
        selectedTab: FluckTabInfo?,
        onTabSelected: (FluckTabInfo) -> Unit,
        enabled: Boolean
    ) {
        var expanded by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Browser Tab",
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Box {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = enabled && availableTabs.isNotEmpty()) {
                                expanded = true
                            }
                            .border(
                                1.dp,
                                if (selectedTab != null) 
                                    MaterialTheme.colors.primary.copy(alpha = 0.5f)
                                else 
                                    MaterialTheme.colors.onSurface.copy(alpha = 0.3f),
                                RoundedCornerShape(4.dp)
                            ),
                        shape = RoundedCornerShape(4.dp),
                        color = MaterialTheme.colors.surface
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Language,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = if (selectedTab != null) 
                                    MaterialTheme.colors.primary 
                                else 
                                    MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = selectedTab?.title ?: if (availableTabs.isEmpty()) 
                                    "No browser tabs available" 
                                else 
                                    "Select a browser tab...",
                                style = MaterialTheme.typography.body1,
                                modifier = Modifier.weight(1f)
                            )
                            Icon(
                                if (expanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Dropdown",
                                tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    
                    DropdownMenu(
                        expanded = expanded && availableTabs.isNotEmpty(),
                        onDismissRequest = { expanded = false }
                    ) {
                        availableTabs.forEach { tab ->
                            DropdownMenuItem(
                                onClick = {
                                    onTabSelected(tab)
                                    expanded = false
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Default.Language,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            tab.title,
                                            style = MaterialTheme.typography.body2,
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun InstructionInputSection(
        instruction: String,
        onInstructionChange: (String) -> Unit,
        onExecute: () -> Unit,
        isExecuting: Boolean,
        hasSelectedTab: Boolean
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Natural Language Instruction",
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                OutlinedTextField(
                    value = instruction,
                    onValueChange = onInstructionChange,
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = {
                        Text(
                            "e.g., Click on the search button and type 'artificial intelligence'",
                            style = MaterialTheme.typography.body2,
                            color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                        )
                    },
                    enabled = !isExecuting,
                    singleLine = false,
                    minLines = 3,
                    maxLines = 5
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Button(
                    onClick = onExecute,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = instruction.isNotBlank() && hasSelectedTab && !isExecuting,
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = MaterialTheme.colors.primary
                    )
                ) {
                    if (isExecuting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Executing...")
                    } else {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Execute",
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Execute Instruction")
                    }
                }
                
                // Quick action examples
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "Quick Examples:",
                    style = MaterialTheme.typography.caption,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    QuickActionChip("Fill form") {
                        onInstructionChange("Fill out the contact form with test data")
                    }
                    QuickActionChip("Extract data") {
                        onInstructionChange("Extract all product prices from this page")
                    }
                    QuickActionChip("Navigate") {
                        onInstructionChange("Navigate to the login page and sign in")
                    }
                }
            }
        }
    }
    
    @Composable
    private fun QuickActionChip(text: String, onClick: () -> Unit) {
        Surface(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .clickable { onClick() },
            color = MaterialTheme.colors.primary.copy(alpha = 0.1f)
        ) {
            Text(
                text,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.caption,
                color = MaterialTheme.colors.primary
            )
        }
    }
    
    @Composable
    private fun LLMConfigurationSection(
        enabled: Boolean
    ) {
        val hasApiKey = LLMSettings.hasValidApiKey()
        val provider = LLMSettings.selectedProvider
        val modelId = LLMSettings.selectedModelId
        val modelInfo = LLMModelFetcher.findModelById(modelId)
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 1.dp,
            backgroundColor = if (hasApiKey) 
                Color(0xFF4CAF50).copy(alpha = 0.05f) 
            else 
                Color(0xFFFF5252).copy(alpha = 0.05f)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (hasApiKey) Icons.Default.CheckCircle else Icons.Default.Warning,
                        contentDescription = "LLM Status",
                        modifier = Modifier.size(20.dp),
                        tint = if (hasApiKey) 
                            Color(0xFF4CAF50) 
                        else 
                            Color(0xFFFF5252)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            if (hasApiKey) 
                                "LLM Provider: ${provider.displayName}"
                            else 
                                "No API Key Configured",
                            style = MaterialTheme.typography.subtitle2,
                            fontWeight = FontWeight.Medium
                        )
                        if (hasApiKey) {
                            Text(
                                "Model: ${modelInfo?.name ?: modelId}",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        } else {
                            Text(
                                "Configure in Settings > LLM Providers",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                            )
                        }
                    }
                    if (!hasApiKey) {
                        TextButton(
                            onClick = { /* Open settings */ },
                            enabled = enabled
                        ) {
                            Text("Configure", style = MaterialTheme.typography.button)
                        }
                    }
                }
            }
        }
    }
    
    @Composable
    private fun ExecutionHistoryItem(execution: LLMExecutionState) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(8.dp),
            elevation = 1.dp,
            backgroundColor = when (execution.status) {
                LLMExecutionStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                LLMExecutionStatus.ERROR -> Color(0xFFFF5252).copy(alpha = 0.1f)
                else -> MaterialTheme.colors.surface
            }
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            execution.instruction,
                            style = MaterialTheme.typography.body2,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                when (execution.status) {
                                    LLMExecutionStatus.COMPLETED -> Icons.Default.CheckCircle
                                    LLMExecutionStatus.ERROR -> Icons.Default.Error
                                    LLMExecutionStatus.EXECUTING -> Icons.Default.PlayArrow
                                    LLMExecutionStatus.GENERATING -> Icons.Default.AutoAwesome
                                },
                                contentDescription = execution.status.name,
                                modifier = Modifier.size(16.dp),
                                tint = when (execution.status) {
                                    LLMExecutionStatus.COMPLETED -> Color(0xFF4CAF50)
                                    LLMExecutionStatus.ERROR -> Color(0xFFFF5252)
                                    LLMExecutionStatus.EXECUTING -> MaterialTheme.colors.primary
                                    LLMExecutionStatus.GENERATING -> Color(0xFFFF9800)
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                execution.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.caption,
                                color = when (execution.status) {
                                    LLMExecutionStatus.COMPLETED -> Color(0xFF4CAF50)
                                    LLMExecutionStatus.ERROR -> Color(0xFFFF5252)
                                    else -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                }
                            )
                        }
                    }
                    
                    Text(
                        formatTimestamp(execution.timestamp),
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.5f)
                    )
                }
                
                if (execution.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(
                        color = Color(0xFFFF5252).copy(alpha = 0.1f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            execution.error,
                            style = MaterialTheme.typography.caption,
                            color = Color(0xFFFF5252),
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
                
                if (execution.generatedActions.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Generated ${execution.generatedActions.size} actions:",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary,
                        fontWeight = FontWeight.Medium
                    )
                    
                    // Show generated actions
                    execution.generatedActions.forEachIndexed { index, action ->
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(
                                "${index + 1}.",
                                style = MaterialTheme.typography.caption,
                                color = MaterialTheme.colors.onSurface.copy(alpha = 0.6f),
                                modifier = Modifier.width(20.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "${action.type.uppercase()}: ${action.name}",
                                    style = MaterialTheme.typography.caption,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    "Selector: ${action.selector.type} = ${action.selector.value ?: "none"}",
                                    style = MaterialTheme.typography.caption,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                )
                                if (!action.value.isNullOrEmpty()) {
                                    Text(
                                        "Value: ${action.value}",
                                        style = MaterialTheme.typography.caption,
                                        color = MaterialTheme.colors.onSurface.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
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
     * Select a tab for execution
     */
    private fun selectTab(tab: FluckTabInfo) {
        _selectedTab.value = tab
    }
    
    /**
     * Execute natural language instruction
     */
    private suspend fun executeInstruction(instruction: String) {
        if (instruction.isBlank() || _selectedTab.value == null || browserConnection == null) {
            return
        }
        
        _isExecuting.value = true
        val executionState = LLMExecutionState(
            instruction = instruction,
            status = LLMExecutionStatus.GENERATING
        )
        _executionHistory.value = _executionHistory.value + executionState
        val historyIndex = _executionHistory.value.size - 1
        
        try {
            // Get current URL from browser
            val currentUrl = browserConnection?.getCurrentUrl() ?: _selectedTab.value?.url ?: ""
            
            // Call LLM API to generate RPA configuration
            val request = LLMRpaRequest(
                actions = listOf(LLMAction(instruction)),
                sourceUrl = currentUrl
            )
            
            updateExecutionStatus(historyIndex, LLMExecutionStatus.GENERATING)
            
            val response = callLLMApi(request)
            
            if (response.status == "success" && response.configuration.isNotEmpty()) {
                updateExecutionStatus(
                    historyIndex, 
                    LLMExecutionStatus.EXECUTING,
                    generatedActions = response.configuration
                )
                
                // Log the generated actions for debugging
                logger.debug(LogCategory.SYSTEM, "Generated RPA actions", mapOf("count" to response.configuration.size))
                response.configuration.forEachIndexed { index, action ->
                    logger.debug(LogCategory.SYSTEM, "Action details", mapOf(
                        "index" to index,
                        "type" to action.type,
                        "name" to action.name,
                        "selectorType" to action.selector.type,
                        "selectorValue" to (action.selector.value ?: "none"),
                        "value" to (action.value ?: "none")
                    ))
                }
                
                // Execute the generated actions
                executeGeneratedActions(response.configuration)
                
                updateExecutionStatus(
                    historyIndex,
                    LLMExecutionStatus.COMPLETED,
                    generatedActions = response.configuration
                )
                
                // Clear instruction after successful execution
                _currentInstruction.value = ""
            } else {
                updateExecutionStatus(
                    historyIndex,
                    LLMExecutionStatus.ERROR,
                    error = response.message ?: "Failed to generate configuration"
                )
            }
            
        } catch (e: Exception) {
            updateExecutionStatus(
                historyIndex,
                LLMExecutionStatus.ERROR,
                error = e.message ?: "Unknown error occurred"
            )
        } finally {
            _isExecuting.value = false
        }
    }
    
    /**
     * Call LLM API to generate RPA configuration
     */
    protected open suspend fun callLLMApi(request: LLMRpaRequest): LLMRpaResponse {
        // This must be implemented in platform-specific code
        throw NotImplementedError("LLM API integration must be implemented in platform-specific component")
    }
    
    
    /**
     * Execute generated RPA actions
     */
    private suspend fun executeGeneratedActions(actions: List<RpaActionConfig>) {
        if (rpaExecutor == null) {
            logger.error(LogCategory.SYSTEM, "RPA executor is null")
            throw Exception("RPA executor not initialized")
        }

        logger.info(LogCategory.SYSTEM, "Starting RPA execution", mapOf("actionCount" to actions.size))

        for ((index, action) in actions.withIndex()) {
            try {
                logger.debug(LogCategory.SYSTEM, "Executing action", mapOf("index" to (index + 1), "total" to actions.size, "type" to action.type, "name" to action.name))

                val result = rpaExecutor!!.executeAction(
                    action = action,
                    humanLikeMode = true,
                    speedMultiplier = 1.0f
                )

                if (!result.success) {
                    logger.warn(LogCategory.SYSTEM, "Action failed", mapOf("error" to (result.error ?: "unknown")))
                    throw Exception(result.error ?: "Action failed")
                }

                logger.debug(LogCategory.SYSTEM, "Action completed", mapOf("index" to (index + 1)))

                // Small delay between actions
                if (index < actions.size - 1) {
                    delay(500)
                }

            } catch (e: Exception) {
                logger.error(LogCategory.SYSTEM, "Exception during action", mapOf("index" to (index + 1)), error = e)
                throw Exception("Failed at action ${index + 1}: ${e.message}")
            }
        }

        logger.info(LogCategory.SYSTEM, "All RPA actions completed successfully")
    }
    
    /**
     * Update execution status in history
     */
    private fun updateExecutionStatus(
        index: Int,
        status: LLMExecutionStatus,
        generatedActions: List<RpaActionConfig> = emptyList(),
        error: String? = null
    ) {
        val history = _executionHistory.value.toMutableList()
        if (index < history.size) {
            history[index] = history[index].copy(
                status = status,
                generatedActions = if (generatedActions.isNotEmpty()) generatedActions else history[index].generatedActions,
                error = error ?: history[index].error
            )
            _executionHistory.value = history
        }
    }
    
    /**
     * Format timestamp for display
     */
    private fun formatTimestamp(timestamp: Long): String {
        val now = Clock.System.now().toEpochMilliseconds()
        val diff = now - timestamp
        
        return when {
            diff < 60000 -> "just now"
            diff < 3600000 -> "${diff / 60000}m ago"
            diff < 86400000 -> "${diff / 3600000}h ago"
            else -> "${diff / 86400000}d ago"
        }
    }
}

/**
 * Factory for creating platform-specific LLM RPA components
 */
expect class LLMRpaFactory() {
    fun createComponent(ctx: ComponentContext, panelInfo: PanelInfo): LLMRpaComponent
}

/**
 * Registration function for LLM RPA panel
 */
fun DefaultPlugin.registerLLMRpa() = panelRegistry.registerPanel(LLMRpaInfo) {
    ctx, panelInfo -> LLMRpaFactory().createComponent(ctx, panelInfo)
}
