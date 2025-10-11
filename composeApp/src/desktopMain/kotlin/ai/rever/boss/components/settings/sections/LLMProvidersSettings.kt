package ai.rever.boss.components.settings.sections

import BossDarkAccent
import BossDarkBackground
import BossDarkBorder
import BossDarkSurface
import ai.rever.boss.components.plugin.panels.right_top.*
import ai.rever.boss.components.settings.shared.DropdownSelector
import ai.rever.boss.components.settings.shared.SectionHeader
import ai.rever.boss.components.settings.shared.SettingSection
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun LLMProvidersSettings() {
    var selectedProvider by remember { mutableStateOf(LLMSettings.selectedProvider) }
    var selectedModelId by remember { mutableStateOf(LLMSettings.selectedModel.modelId) }
    val apiKeys = remember { mutableStateMapOf<LLMProvider, String>().apply {
        LLMProvider.values().forEach { provider ->
            LLMSettings.getApiKey(provider)?.let { put(provider, it) }
        }
    }}
    var customEndpoint by remember { mutableStateOf(LLMSettings.customEndpoint ?: "") }
    var temperature by remember { mutableStateOf(LLMSettings.temperature) }
    var maxTokens by remember { mutableStateOf(LLMSettings.maxTokens.toString()) }
    var enableStreaming by remember { mutableStateOf(LLMSettings.enableStreaming) }
    var enableCaching by remember { mutableStateOf(LLMSettings.enableCaching) }
    var showApiKey by remember { mutableStateOf(false) }
    var showOAuthDialog by remember { mutableStateOf<LLMProvider?>(null) }
    var settingsFeedbackMessage by remember { mutableStateOf<String?>(null) }
    val coroutineScope = rememberCoroutineScope()
    
    // Dynamic models
    val availableModels by LLMModelFetcher.availableModels.collectAsState()
    val isLoadingModels by LLMModelFetcher.isLoading.collectAsState()
    val modelError by LLMModelFetcher.lastError.collectAsState()
    
    // Load models on first launch
    LaunchedEffect(Unit) {
        LLMModelFetcher.fetchLatestModels()
    }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        SectionHeader(
            title = "LLM Provider Settings",
            description = "Configure AI models and API keys for automation"
        )
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Provider Selection
        SettingSection(title = "Provider Selection", description = "Choose your preferred AI provider") {
            Card(
                modifier = Modifier.fillMaxWidth(),
                backgroundColor = BossDarkBackground,
                shape = RoundedCornerShape(8.dp),
                elevation = 2.dp
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    // Provider tabs
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LLMProvider.values().forEach { provider ->
                            ProviderTab(
                                provider = provider,
                                isSelected = selectedProvider == provider,
                                hasApiKey = apiKeys[provider]?.isNotBlank() == true,
                                onClick = { 
                                    selectedProvider = provider
                                    // Update selected model to first available for this provider
                                    val providerModels = availableModels[provider.name] ?: LLMModelFetcher.getModelsForProvider(provider)
                                    if (providerModels.isNotEmpty()) {
                                        selectedModelId = providerModels.first().id
                                    }
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(20.dp))
                    
                    // API Key input for selected provider
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "${selectedProvider.displayName} API Key",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White
                                )
                                
                                // Show if key is from environment
                                val envKey = when (selectedProvider) {
                                    LLMProvider.ANTHROPIC -> getEnvironmentVariable("ANTHROPIC_API_KEY")
                                    LLMProvider.OPENAI -> getEnvironmentVariable("OPENAI_API_KEY")
                                    LLMProvider.TOGETHER -> getEnvironmentVariable("TOGETHER_API_KEY")
                                    LLMProvider.CUSTOM -> getEnvironmentVariable("CUSTOM_LLM_API_KEY")
                                }
                                
                                if (!envKey.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Card(
                                        backgroundColor = Color(0xFF4CAF50).copy(alpha = 0.1f),
                                        contentColor = Color(0xFF4CAF50),
                                        shape = RoundedCornerShape(4.dp),
                                        elevation = 0.dp
                                    ) {
                                        Text(
                                            text = "From Environment",
                                            fontSize = 11.sp,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            
                            if (selectedProvider != LLMProvider.CUSTOM) {
                                TextButton(
                                    onClick = { showOAuthDialog = selectedProvider }
                                ) {
                                    Icon(
                                        Icons.Outlined.Login,
                                        contentDescription = "Sign In",
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sign In", fontSize = 12.sp)
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        val envKey = when (selectedProvider) {
                            LLMProvider.ANTHROPIC -> getEnvironmentVariable("ANTHROPIC_API_KEY")
                            LLMProvider.OPENAI -> getEnvironmentVariable("OPENAI_API_KEY")
                            LLMProvider.TOGETHER -> getEnvironmentVariable("TOGETHER_API_KEY")
                            LLMProvider.CUSTOM -> getEnvironmentVariable("CUSTOM_LLM_API_KEY")
                        }
                        
                        OutlinedTextField(
                            value = apiKeys[selectedProvider] ?: "",
                            onValueChange = { apiKeys[selectedProvider] = it },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = envKey.isNullOrBlank(), // Disable if env key exists
                            placeholder = { 
                                Text(
                                    if (!envKey.isNullOrBlank()) {
                                        "Using environment variable"
                                    } else {
                                        when (selectedProvider) {
                                            LLMProvider.ANTHROPIC -> "sk-ant-..."
                                            LLMProvider.OPENAI -> "sk-..."
                                            LLMProvider.TOGETHER -> "together-..."
                                            LLMProvider.CUSTOM -> "Your API key"
                                        }
                                    },
                                    color = Color.Gray.copy(alpha = 0.5f)
                                ) 
                            },
                            visualTransformation = if (showApiKey) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(
                                    onClick = { showApiKey = !showApiKey }
                                ) {
                                    Icon(
                                        if (showApiKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                        contentDescription = if (showApiKey) "Hide" else "Show",
                                        modifier = Modifier.size(20.dp),
                                        tint = Color.Gray
                                    )
                                }
                            },
                            singleLine = true,
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                textColor = Color.White,
                                focusedBorderColor = BossDarkAccent,
                                unfocusedBorderColor = BossDarkBorder,
                                focusedLabelColor = BossDarkAccent,
                                unfocusedLabelColor = Color.Gray,
                                placeholderColor = Color.Gray.copy(alpha = 0.5f)
                            )
                        )
                        
                        // Help text about environment variables
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (!envKey.isNullOrBlank()) {
                                "API key is set via environment variable"
                            } else {
                                val envVarName = when (selectedProvider) {
                                    LLMProvider.ANTHROPIC -> "ANTHROPIC_API_KEY"
                                    LLMProvider.OPENAI -> "OPENAI_API_KEY"
                                    LLMProvider.TOGETHER -> "TOGETHER_API_KEY"
                                    LLMProvider.CUSTOM -> "CUSTOM_LLM_API_KEY"
                                }
                                "You can also set the $envVarName environment variable"
                            },
                            fontSize = 12.sp,
                            color = Color.Gray.copy(alpha = 0.7f),
                            modifier = Modifier.padding(start = 4.dp)
                        )
                        
                        if (selectedProvider == LLMProvider.CUSTOM) {
                            Spacer(modifier = Modifier.height(12.dp))
                            OutlinedTextField(
                                value = customEndpoint,
                                onValueChange = { customEndpoint = it },
                                label = { Text("API Endpoint") },
                                placeholder = { Text("https://api.example.com/v1/chat", color = Color.Gray.copy(alpha = 0.5f)) },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    textColor = Color.White,
                                    focusedBorderColor = BossDarkAccent,
                                    unfocusedBorderColor = BossDarkBorder,
                                    focusedLabelColor = BossDarkAccent,
                                    unfocusedLabelColor = Color.Gray
                                )
                            )
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Model Selection
        if (selectedProvider != LLMProvider.CUSTOM) {
            SettingSection(title = "Model Selection", description = "Choose the AI model to use") {
                val providerModels = availableModels[selectedProvider.name] ?: LLMModelFetcher.getModelsForProvider(selectedProvider)
                
                if (isLoadingModels && providerModels.isEmpty()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = BossDarkAccent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Loading models...", color = Color.Gray)
                    }
                } else {
                    val currentModel = providerModels.find { it.id == selectedModelId }
                    DropdownSelector(
                        label = "Model",
                        value = currentModel?.name ?: "Select a model",
                        options = providerModels.map { it.name },
                        onValueChange = { displayName ->
                            providerModels.find { it.name == displayName }?.let {
                                selectedModelId = it.id
                            }
                        },
                        modifier = Modifier.width(400.dp)
                    )
                    
                    // Show model details if available
                    currentModel?.let { model ->
                        Spacer(modifier = Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            backgroundColor = BossDarkBackground.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(4.dp),
                            elevation = 0.dp
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                model.description?.let {
                                    Text(
                                        text = it,
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                model.contextLength?.let {
                                    Text(
                                        text = "Context: ${it.toString().reversed().chunked(3).reversed().joinToString(",")} tokens",
                                        fontSize = 11.sp,
                                        color = Color.Gray.copy(alpha = 0.7f)
                                    )
                                }
                                if (model.capabilities.isNotEmpty()) {
                                    Text(
                                        text = "Capabilities: ${model.capabilities.joinToString(", ")}",
                                        fontSize = 11.sp,
                                        color = Color.Gray.copy(alpha = 0.7f)
                                    )
                                }
                            }
                        }
                    }
                }
                
                // Refresh button
                modelError?.let { error ->
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        Text(
                            text = error,
                            fontSize = 12.sp,
                            color = Color(0xFFFF5252)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        TextButton(
                            onClick = {
                                coroutineScope.launch {
                                    LLMModelFetcher.fetchLatestModels(forceRefresh = true)
                                }
                            }
                        ) {
                            Text("Retry", fontSize = 12.sp)
                        }
                    }
                }
                
                // Info about custom models via environment variables
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Custom models can be set via BOSS_LLM_MODELS_${selectedProvider.name} environment variable",
                    fontSize = 11.sp,
                    color = Color.Gray.copy(alpha = 0.5f),
                    modifier = Modifier.padding(horizontal = 4.dp)
                )
                Text(
                    text = "Format: model1:name1:context1;model2:name2:context2",
                    fontSize = 10.sp,
                    color = Color.Gray.copy(alpha = 0.4f),
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Advanced Settings
        SettingSection(title = "Advanced Settings", description = "Fine-tune model behavior") {
            Column {
                // Temperature
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Temperature",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Controls randomness (0 = focused, 2 = creative)",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    Text(
                        text = String.format("%.1f", temperature),
                        fontSize = 14.sp,
                        color = Color.White,
                        modifier = Modifier.width(40.dp)
                    )
                    
                    Slider(
                        value = temperature,
                        onValueChange = { temperature = it },
                        valueRange = 0f..2f,
                        modifier = Modifier.width(200.dp),
                        colors = SliderDefaults.colors(
                            thumbColor = BossDarkAccent,
                            activeTrackColor = BossDarkAccent,
                            inactiveTrackColor = BossDarkBorder
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Max Tokens
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Max Tokens",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White
                        )
                        Text(
                            text = "Maximum response length",
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }
                    
                    OutlinedTextField(
                        value = maxTokens,
                        onValueChange = { 
                            if (it.all { char -> char.isDigit() }) {
                                maxTokens = it
                            }
                        },
                        modifier = Modifier.width(150.dp),
                        singleLine = true,
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            textColor = Color.White,
                            focusedBorderColor = BossDarkAccent,
                            unfocusedBorderColor = BossDarkBorder
                        )
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Options
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableStreaming,
                            onCheckedChange = { enableStreaming = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BossDarkAccent,
                                uncheckedColor = BossDarkBorder
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Streaming",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                    
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = enableCaching,
                            onCheckedChange = { enableCaching = it },
                            colors = CheckboxDefaults.colors(
                                checkedColor = BossDarkAccent,
                                uncheckedColor = BossDarkBorder
                            )
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Enable Response Caching",
                            fontSize = 14.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Apply settings button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Button(
                onClick = {
                    // Apply all settings
                    LLMSettings.selectedProvider = selectedProvider
                    LLMSettings.selectedModelId = selectedModelId
                    apiKeys.forEach { (provider, key) ->
                        LLMSettings.setApiKey(provider, key.takeIf { it.isNotBlank() })
                    }
                    if (selectedProvider == LLMProvider.CUSTOM) {
                        LLMSettings.customEndpoint = customEndpoint.takeIf { it.isNotBlank() }
                    }
                    LLMSettings.temperature = temperature
                    LLMSettings.maxTokens = maxTokens.toIntOrNull() ?: 2000
                    LLMSettings.enableStreaming = enableStreaming
                    LLMSettings.enableCaching = enableCaching
                    
                    // Save settings
                    coroutineScope.launch {
                        try {
                            LLMSettingsManager.saveSettings()
                            settingsFeedbackMessage = "LLM settings applied successfully!"
                            delay(3000) // Show message for 3 seconds
                            settingsFeedbackMessage = null
                        } catch (e: Exception) {
                            settingsFeedbackMessage = "Error saving settings: ${e.message}"
                            delay(5000) // Show error message longer
                            settingsFeedbackMessage = null
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = BossDarkAccent,
                    contentColor = Color.White
                ),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text("Apply Settings")
            }
        }
    }
    
    // OAuth Dialog
    showOAuthDialog?.let { provider ->
        AlertDialog(
            onDismissRequest = { showOAuthDialog = null },
            title = {
                Text(
                    "Sign in to ${provider.displayName}",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        "OAuth authentication for ${provider.displayName} is not yet implemented.",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Please manually enter your API key for now. You can obtain it from:",
                        color = Color.Gray,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        when (provider) {
                            LLMProvider.ANTHROPIC -> "https://console.anthropic.com/account/keys"
                            LLMProvider.OPENAI -> "https://platform.openai.com/api-keys"
                            LLMProvider.TOGETHER -> "https://api.together.xyz/settings/api-keys"
                            else -> ""
                        },
                        color = BossDarkAccent,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { showOAuthDialog = null }
                ) {
                    Text("OK", color = BossDarkAccent)
                }
            },
            backgroundColor = BossDarkSurface,
            contentColor = Color.White
        )
    }
    
    // Settings feedback message
    settingsFeedbackMessage?.let { message ->
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Surface(
                modifier = Modifier.padding(top = 16.dp),
                color = if (message.contains("Error")) Color.Red else BossDarkAccent,
                shape = RoundedCornerShape(6.dp),
                elevation = 4.dp
            ) {
                Text(
                    text = message,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    color = Color.White,
                    style = MaterialTheme.typography.body2
                )
            }
        }
    }
}

@Composable
private fun ProviderTab(
    provider: LLMProvider,
    isSelected: Boolean,
    hasApiKey: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clickable { onClick() }
            .padding(4.dp),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) BossDarkAccent.copy(alpha = 0.2f) else Color.Transparent,
        border = BorderStroke(
            1.dp, 
            if (isSelected) BossDarkAccent else BossDarkBorder
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Provider icon placeholder
            Icon(
                when (provider) {
                    LLMProvider.ANTHROPIC -> Icons.Outlined.AutoAwesome
                    LLMProvider.OPENAI -> Icons.Outlined.Psychology
                    LLMProvider.TOGETHER -> Icons.Outlined.Groups
                    LLMProvider.CUSTOM -> Icons.Outlined.Settings
                },
                contentDescription = provider.displayName,
                modifier = Modifier.size(20.dp),
                tint = if (isSelected) BossDarkAccent else Color.Gray
            )
            
            Spacer(modifier = Modifier.width(8.dp))
            
            Text(
                text = provider.displayName,
                fontSize = 14.sp,
                color = if (isSelected) Color.White else Color.Gray,
                fontWeight = if (isSelected) FontWeight.Medium else FontWeight.Normal
            )
            
            if (hasApiKey) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    Icons.Outlined.CheckCircle,
                    contentDescription = "API Key Set",
                    modifier = Modifier.size(16.dp),
                    tint = Color(0xFF4CAF50)
                )
            }
        }
    }
}