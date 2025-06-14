package ai.rever.boss.components.plugin.panels.right_top

import ai.rever.boss.components.model.Panel.Companion.right
import ai.rever.boss.components.model.Panel.Companion.top
import ai.rever.boss.components.plugin.DefaultPlugin
import ai.rever.boss.components.plugin.panels.left_bottom.TopOfMind.LocalSplitViewState
import ai.rever.boss.components.registery.PanelComponentWithUI
import ai.rever.boss.components.registery.PanelId
import ai.rever.boss.components.registery.PanelInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import compose.icons.FontAwesomeIcons
import compose.icons.fontawesomeicons.Brands
import compose.icons.fontawesomeicons.brands.Hotjar
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.datetime.Clock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object LLMRpaInfo : PanelInfo {
    override val id = PanelId("llm_rpa", 18)
    override val displayName = "LLM RPA"
    override val icon = FontAwesomeIcons.Brands.Hotjar
    override val defaultSlotPosition = right.top.top
}

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
    val status: ExecutionStatus,
    val generatedActions: List<RpaActionConfig> = emptyList(),
    val error: String? = null,
    val timestamp: Long = Clock.System.now().toEpochMilliseconds()
)

enum class ExecutionStatus {
    PENDING,
    GENERATING,
    EXECUTING,
    COMPLETED,
    ERROR
}

open class LLMRpaComponent(
    ctx: ComponentContext,
    override val panelInfo: PanelInfo
) : PanelComponentWithUI, ComponentContext by ctx {
    
    // State management
    private val _instructions = MutableStateFlow<List<String>>(emptyList())
    val instructions: StateFlow<List<String>> = _instructions
    
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
    
    // API endpoint configuration
    private val _apiEndpoint = MutableStateFlow("http://localhost:8000/api/v1/rpa/create-rpa-config")
    val apiEndpoint: StateFlow<String> = _apiEndpoint
    
    // Browser connection reference
    internal var browserConnection: BrowserIntegration? = null
    internal var rpaExecutor: RpaActionExecutor? = null
    
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
    }

    @Composable
    override fun Content() {
        // Use enhanced content with browser integration
        LLMRpaContent(this)
    }
    
    @Composable
    internal fun ContentInternal() {
        val splitViewState = LocalSplitViewState.current
        val selectedTab by selectedTab.collectAsState()
        val availableTabs by availableFluckTabs.collectAsState()
        val instruction by currentInstruction.collectAsState()
        val isExecuting by isExecuting.collectAsState()
        val history by executionHistory.collectAsState()
        val apiEndpoint by apiEndpoint.collectAsState()
        val coroutineScope = rememberCoroutineScope()
        
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colors.background)
                .padding(16.dp),
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
            
            // API Configuration
            item {
                ApiConfigurationSection(
                    apiEndpoint = apiEndpoint,
                    onEndpointChange = { _apiEndpoint.value = it },
                    enabled = !isExecuting
                )
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
                    FontAwesomeIcons.Brands.Hotjar,
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
    private fun ApiConfigurationSection(
        apiEndpoint: String,
        onEndpointChange: (String) -> Unit,
        enabled: Boolean
    ) {
        var isExpanded by remember { mutableStateOf(false) }
        
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            shape = RoundedCornerShape(8.dp),
            elevation = 1.dp
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Settings,
                            contentDescription = "API Configuration",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "API Configuration",
                            style = MaterialTheme.typography.subtitle2,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle",
                        tint = MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                    )
                }
                
                if (isExpanded) {
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = apiEndpoint,
                        onValueChange = onEndpointChange,
                        label = { Text("API Endpoint") },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = enabled,
                        singleLine = true,
                        textStyle = MaterialTheme.typography.body2.copy(
                            fontFamily = FontFamily.Monospace
                        )
                    )
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
                ExecutionStatus.COMPLETED -> Color(0xFF4CAF50).copy(alpha = 0.1f)
                ExecutionStatus.ERROR -> Color(0xFFFF5252).copy(alpha = 0.1f)
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
                                    ExecutionStatus.COMPLETED -> Icons.Default.CheckCircle
                                    ExecutionStatus.ERROR -> Icons.Default.Error
                                    ExecutionStatus.EXECUTING -> Icons.Default.PlayArrow
                                    ExecutionStatus.GENERATING -> Icons.Default.AutoAwesome
                                    else -> Icons.Default.Schedule
                                },
                                contentDescription = execution.status.name,
                                modifier = Modifier.size(16.dp),
                                tint = when (execution.status) {
                                    ExecutionStatus.COMPLETED -> Color(0xFF4CAF50)
                                    ExecutionStatus.ERROR -> Color(0xFFFF5252)
                                    ExecutionStatus.EXECUTING -> MaterialTheme.colors.primary
                                    ExecutionStatus.GENERATING -> Color(0xFFFF9800)
                                    else -> MaterialTheme.colors.onSurface.copy(alpha = 0.6f)
                                }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                execution.status.name.lowercase().replaceFirstChar { it.uppercase() },
                                style = MaterialTheme.typography.caption,
                                color = when (execution.status) {
                                    ExecutionStatus.COMPLETED -> Color(0xFF4CAF50)
                                    ExecutionStatus.ERROR -> Color(0xFFFF5252)
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
                        "Generated ${execution.generatedActions.size} actions",
                        style = MaterialTheme.typography.caption,
                        color = MaterialTheme.colors.primary
                    )
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
            status = ExecutionStatus.GENERATING
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
            
            updateExecutionStatus(historyIndex, ExecutionStatus.GENERATING)
            
            val response = callLLMApi(request)
            
            if (response.status == "success" && response.configuration.isNotEmpty()) {
                updateExecutionStatus(
                    historyIndex, 
                    ExecutionStatus.EXECUTING,
                    generatedActions = response.configuration
                )
                
                // Execute the generated actions
                executeGeneratedActions(response.configuration)
                
                updateExecutionStatus(
                    historyIndex,
                    ExecutionStatus.COMPLETED,
                    generatedActions = response.configuration
                )
                
                // Clear instruction after successful execution
                _currentInstruction.value = ""
            } else {
                updateExecutionStatus(
                    historyIndex,
                    ExecutionStatus.ERROR,
                    error = response.message ?: "Failed to generate configuration"
                )
            }
            
        } catch (e: Exception) {
            updateExecutionStatus(
                historyIndex,
                ExecutionStatus.ERROR,
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
        // This will be implemented in platform-specific code
        // For now, return a mock response with enhanced patterns
        delay(2000) // Simulate API call
        
        val instruction = request.actions.firstOrNull()?.instruction ?: ""
        
        // Enhanced mock response based on instruction patterns
        val mockActions = when {
            // Search pattern
            instruction.contains("search", ignoreCase = true) -> {
                val searchQuery = extractSearchQuery(instruction)
                listOf(
                    RpaActionConfig(
                        name = "Click search box",
                        type = "click",
                        selector = SelectorInfo("xpath", "//input[@type='search' or @name='q' or @placeholder[contains(., 'Search')]]"),
                        action_type = "default"
                    ),
                    RpaActionConfig(
                        name = "Clear search box",
                        type = "click",
                        selector = SelectorInfo("xpath", "//input[@type='search' or @name='q']"),
                        action_type = "default"
                    ),
                    RpaActionConfig(
                        name = "Type search query",
                        type = "input",
                        selector = SelectorInfo("xpath", "//input[@type='search' or @name='q']"),
                        value = searchQuery,
                        action_type = "default"
                    ),
                    RpaActionConfig(
                        name = "Submit search",
                        type = "keypress",
                        selector = SelectorInfo("xpath", "//input[@type='search' or @name='q']"),
                        value = "Enter",
                        action_type = "default"
                    )
                )
            }
            
            // Form filling pattern
            instruction.contains("fill", ignoreCase = true) && instruction.contains("form", ignoreCase = true) -> {
                listOf(
                    RpaActionConfig(
                        name = "Fill name field",
                        type = "input",
                        selector = SelectorInfo("xpath", "//input[@name='name' or @placeholder[contains(., 'Name')]]"),
                        value = "Test User",
                        action_type = "default"
                    ),
                    RpaActionConfig(
                        name = "Fill email field",
                        type = "input",
                        selector = SelectorInfo("xpath", "//input[@type='email' or @name='email']"),
                        value = "test@example.com",
                        action_type = "default"
                    ),
                    RpaActionConfig(
                        name = "Fill message field",
                        type = "input",
                        selector = SelectorInfo("xpath", "//textarea[@name='message' or @placeholder[contains(., 'Message')]]"),
                        value = "This is a test message generated by LLM RPA",
                        action_type = "default"
                    ),
                    RpaActionConfig(
                        name = "Submit form",
                        type = "click",
                        selector = SelectorInfo("xpath", "//button[@type='submit' or contains(text(), 'Submit')]"),
                        action_type = "default"
                    )
                )
            }
            
            // Navigation pattern
            instruction.contains("navigate", ignoreCase = true) || instruction.contains("go to", ignoreCase = true) -> {
                val destination = extractDestination(instruction)
                listOf(
                    RpaActionConfig(
                        name = "Click on $destination link",
                        type = "click",
                        selector = SelectorInfo("xpath", "//a[contains(text(), '$destination') or contains(@href, '${destination.lowercase()}')]"),
                        action_type = "default"
                    )
                )
            }
            
            // Click pattern
            instruction.contains("click", ignoreCase = true) -> {
                val target = extractClickTarget(instruction)
                listOf(
                    RpaActionConfig(
                        name = "Click on $target",
                        type = "click",
                        selector = SelectorInfo("xpath", "//*[contains(text(), '$target') or @aria-label='$target']"),
                        action_type = "default"
                    )
                )
            }
            
            // Extract data pattern
            instruction.contains("extract", ignoreCase = true) -> {
                listOf(
                    RpaActionConfig(
                        name = "Extract data from page",
                        type = "custom",
                        selector = SelectorInfo("xpath", "//body"),
                        action_type = "custom",
                        meta = mapOf(
                            "action" to "extract_text",
                            "target" to extractDataTarget(instruction)
                        )
                    )
                )
            }
            
            else -> emptyList()
        }
        
        return LLMRpaResponse(
            configuration = mockActions,
            status = if (mockActions.isNotEmpty()) "success" else "error",
            message = if (mockActions.isEmpty()) "Could not generate actions for this instruction" else null
        )
    }
    
    private fun extractSearchQuery(instruction: String): String {
        val patterns = listOf(
            "search for (.+)",
            "search (.+)",
            "type (.+) in",
            "type '(.+)'",
            "\"(.+)\""
        )
        
        patterns.forEach { pattern ->
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(instruction)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim().removeSuffix(" and").removeSuffix(" then")
            }
        }
        
        return "artificial intelligence" // Default search query
    }
    
    private fun extractDestination(instruction: String): String {
        val patterns = listOf(
            "navigate to (?:the )?(.+?)(?:\\s+page)?",
            "go to (?:the )?(.+?)(?:\\s+page)?",
            "open (?:the )?(.+?)(?:\\s+page)?"
        )
        
        patterns.forEach { pattern ->
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(instruction)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }
        
        return "home"
    }
    
    private fun extractClickTarget(instruction: String): String {
        val patterns = listOf(
            "click (?:on )?(?:the )?(.+?)(?:\\s+button)?",
            "press (?:the )?(.+?)(?:\\s+button)?"
        )
        
        patterns.forEach { pattern ->
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(instruction)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }
        
        return "button"
    }
    
    private fun extractDataTarget(instruction: String): String {
        val patterns = listOf(
            "extract (?:all )?(.+?)(?:\\s+from)?",
            "get (?:all )?(.+?)(?:\\s+from)?"
        )
        
        patterns.forEach { pattern ->
            val regex = Regex(pattern, RegexOption.IGNORE_CASE)
            val match = regex.find(instruction)
            if (match != null && match.groupValues.size > 1) {
                return match.groupValues[1].trim()
            }
        }
        
        return "data"
    }
    
    /**
     * Execute generated RPA actions
     */
    private suspend fun executeGeneratedActions(actions: List<RpaActionConfig>) {
        if (rpaExecutor == null) return
        
        for ((index, action) in actions.withIndex()) {
            try {
                val result = rpaExecutor!!.executeAction(
                    action = action,
                    humanLikeMode = true,
                    speedMultiplier = 1.0f
                )
                
                if (!result.success) {
                    throw Exception(result.error ?: "Action failed")
                }
                
                // Small delay between actions
                if (index < actions.size - 1) {
                    delay(500)
                }
                
            } catch (e: Exception) {
                throw Exception("Failed at action ${index + 1}: ${e.message}")
            }
        }
    }
    
    /**
     * Update execution status in history
     */
    private fun updateExecutionStatus(
        index: Int,
        status: ExecutionStatus,
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