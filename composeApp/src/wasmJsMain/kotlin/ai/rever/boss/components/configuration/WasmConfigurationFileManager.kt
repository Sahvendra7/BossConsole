package ai.rever.boss.components.configuration

import kotlinx.browser.localStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * WebAssembly implementation of ConfigurationFileManager
 * Uses localStorage for persistence
 */
actual class ConfigurationFileManager {
    private val configPrefix = "boss_config_"
    private val configListKey = "boss_config_list"
    
    actual fun getDefaultConfigurationDirectory(): String = "localStorage://boss/configurations"
    
    actual suspend fun ensureConfigurationDirectory(): Boolean = withContext(Dispatchers.Main) {
        // localStorage is always available in browser
        true
    }
    
    actual suspend fun saveConfiguration(
        config: LayoutConfiguration, 
        fileName: String?
    ): String? = withContext(Dispatchers.Main) {
        try {
            val actualFileName = fileName ?: ConfigurationFileManagerCommon.generateFileName(config.name)
            val storageKey = "$configPrefix$actualFileName"
            
            // Serialize configuration
            val json = ConfigurationSerializer.serialize(config)
            
            // Save to localStorage
            localStorage.setItem(storageKey, json)
            
            // Update configuration list
            updateConfigurationList(actualFileName, true)
            
            storageKey
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    actual suspend fun loadConfiguration(fileName: String): LayoutConfiguration? = withContext(Dispatchers.Main) {
        try {
            val storageKey = "$configPrefix$fileName"
            val json = localStorage.getItem(storageKey) ?: return@withContext null
            ConfigurationSerializer.deserialize(json)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
    
    actual suspend fun listConfigurations(): List<ConfigurationFileInfo> = withContext(Dispatchers.Main) {
        try {
            val listJson = localStorage.getItem(configListKey) ?: return@withContext emptyList()
            val fileList = Json.decodeFromString<List<ConfigFileMetadata>>(listJson)
            
            fileList.mapNotNull { metadata ->
                val storageKey = "$configPrefix${metadata.fileName}"
                val json = localStorage.getItem(storageKey)
                
                if (json != null) {
                    ConfigurationFileInfo(
                        fileName = metadata.fileName,
                        filePath = storageKey,
                        lastModified = metadata.lastModified,
                        size = json.length.toLong()
                    )
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
    
    actual suspend fun deleteConfiguration(fileName: String): Boolean = withContext(Dispatchers.Main) {
        try {
            val storageKey = "$configPrefix$fileName"
            localStorage.removeItem(storageKey)
            updateConfigurationList(fileName, false)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
    
    actual fun getConfigurationFilePath(fileName: String): String {
        return "$configPrefix$fileName"
    }
    
    private fun updateConfigurationList(fileName: String, add: Boolean) {
        try {
            val listJson = localStorage.getItem(configListKey)
            val fileList = if (listJson != null) {
                Json.decodeFromString<MutableList<ConfigFileMetadata>>(listJson)
            } else {
                mutableListOf()
            }
            
            if (add) {
                // Remove existing entry if present
                fileList.removeAll { it.fileName == fileName }
                // Add new entry
                fileList.add(
                    ConfigFileMetadata(
                        fileName = fileName,
                        lastModified = kotlinx.datetime.Clock.System.now().toEpochMilliseconds()
                    )
                )
            } else {
                // Remove entry
                fileList.removeAll { it.fileName == fileName }
            }
            
            localStorage.setItem(configListKey, Json.encodeToString(fileList))
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

/**
 * Metadata for configuration files stored in localStorage
 */
@kotlinx.serialization.Serializable
private data class ConfigFileMetadata(
    val fileName: String,
    val lastModified: Long
)