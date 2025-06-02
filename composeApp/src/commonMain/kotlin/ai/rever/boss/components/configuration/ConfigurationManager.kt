package ai.rever.boss.components.configuration

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock

/**
 * Manages layout configurations with file-based storage
 */
class ConfigurationManager {
    private val _currentConfiguration = MutableStateFlow<LayoutConfiguration?>(null)
    val currentConfiguration: StateFlow<LayoutConfiguration?> = _currentConfiguration.asStateFlow()
    
    private val _configurations = MutableStateFlow<List<LayoutConfiguration>>(emptyList())
    val configurations: StateFlow<List<LayoutConfiguration>> = _configurations.asStateFlow()
    
    private val fileManager = ConfigurationFileManager()
    private val scope = CoroutineScope(Dispatchers.Default)
    
    init {
        // Load configurations from both predefined and saved files
        loadAllConfigurations()
    }
    
    private fun loadAllConfigurations() {
        scope.launch {
            // Start with predefined configurations
            val allConfigs = mutableListOf<LayoutConfiguration>()
            allConfigs.addAll(PredefinedConfigurations.allConfigurations)
            
            // Load saved configurations from disk
            try {
                val savedConfigs = fileManager.listConfigurations()
                savedConfigs.forEach { fileInfo ->
                    fileManager.loadConfiguration(fileInfo.fileName)?.let { config ->
                        // Only add if not already in predefined list
                        if (allConfigs.none { it.name == config.name }) {
                            allConfigs.add(config)
                        }
                    }
                }
            } catch (e: Exception) {
                // Log error but continue with predefined configs
                e.printStackTrace()
            }
            
            _configurations.value = allConfigs
        }
    }
    
    /**
     * Load a configuration
     */
    fun loadConfiguration(config: LayoutConfiguration) {
        _currentConfiguration.value = config
    }
    
    /**
     * Save current configuration to disk
     */
    fun saveCurrentConfiguration(name: String? = null): LayoutConfiguration? {
        val current = _currentConfiguration.value ?: return null
        val savedConfig = current.copy(
            name = name ?: current.name,
            timestamp = Clock.System.now().toEpochMilliseconds()
        )
        
        scope.launch {
            // Save to disk
            val filePath = fileManager.saveConfiguration(savedConfig)
            if (filePath != null) {
                // Update configurations list
                val configs = _configurations.value.toMutableList()
                val existingIndex = configs.indexOfFirst { it.name == savedConfig.name }
                
                if (existingIndex >= 0) {
                    configs[existingIndex] = savedConfig
                } else {
                    configs.add(savedConfig)
                }
                
                _configurations.value = configs
                _currentConfiguration.value = savedConfig
            }
        }
        
        return savedConfig
    }
    
    /**
     * Reset to default configuration
     */
    fun resetToDefault() {
        _currentConfiguration.value = null
    }
    
    /**
     * Export configuration to JSON
     */
    fun exportConfiguration(config: LayoutConfiguration): String {
        return ConfigurationSerializer.serialize(config)
    }
    
    /**
     * Import configuration from JSON
     */
    fun importConfiguration(jsonString: String): LayoutConfiguration? {
        return try {
            val config = ConfigurationSerializer.deserialize(jsonString)
            
            // Save the imported configuration to disk
            scope.launch {
                fileManager.saveConfiguration(config)
                
                // Update configurations list
                val configs = _configurations.value.toMutableList()
                if (configs.none { it.name == config.name }) {
                    configs.add(config)
                    _configurations.value = configs
                }
            }
            
            config
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Delete a configuration
     */
    fun deleteConfiguration(name: String) {
        scope.launch {
            // Find configuration
            val config = _configurations.value.find { it.name == name }
            if (config != null && !PredefinedConfigurations.allConfigurations.any { it.name == name }) {
                // Only delete if it's not a predefined configuration
                val fileName = ConfigurationFileManagerCommon.generateFileName(name)
                if (fileManager.deleteConfiguration(fileName)) {
                    _configurations.value = _configurations.value.filter { it.name != name }
                    
                    // If current configuration was deleted, reset
                    if (_currentConfiguration.value?.name == name) {
                        resetToDefault()
                    }
                }
            }
        }
    }
    
    /**
     * Update current configuration with new layout
     */
    fun updateCurrentConfiguration(newConfig: LayoutConfiguration) {
        _currentConfiguration.value = newConfig
    }
    
    /**
     * Get the configuration directory path
     */
    fun getConfigurationDirectory(): String {
        return fileManager.getDefaultConfigurationDirectory()
    }
}

// Global instance
val configurationManager = ConfigurationManager()