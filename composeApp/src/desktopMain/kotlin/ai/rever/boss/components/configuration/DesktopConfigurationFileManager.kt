package ai.rever.boss.components.configuration

import ai.rever.boss.utils.SystemUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardOpenOption

/**
 * Desktop implementation of ConfigurationFileManager
 */
actual class ConfigurationFileManager {
    private val configDirectory: String by lazy {
        val userHome = SystemUtils.getUserHome()
        val documentsPath = Paths.get(userHome, "Documents", ConfigurationFileManagerCommon.getDefaultConfigDirectoryName())
        documentsPath.toString()
    }
    
    actual fun getDefaultConfigurationDirectory(): String = configDirectory
    
    actual suspend fun ensureConfigurationDirectory(): Boolean = withContext(Dispatchers.IO) {
        try {
            val dir = File(configDirectory)
            if (!dir.exists()) {
                dir.mkdirs()
            }
            dir.exists() && dir.isDirectory
        } catch (e: Exception) {
            false
        }
    }
    
    actual suspend fun saveConfiguration(
        config: LayoutConfiguration, 
        fileName: String?
    ): String? = withContext(Dispatchers.IO) {
        try {
            ensureConfigurationDirectory()
            
            val actualFileName = fileName ?: ConfigurationFileManagerCommon.generateFileName(config.name)
            val filePath = getConfigurationFilePath(actualFileName)
            val file = File(filePath)
            
            // Serialize configuration
            val json = ConfigurationSerializer.serialize(config)
            
            // Write to file
            file.writeText(json)
            
            filePath
        } catch (e: Exception) {
            null
        }
    }
    
    actual suspend fun loadConfiguration(fileName: String): LayoutConfiguration? = withContext(Dispatchers.IO) {
        try {
            val filePath = getConfigurationFilePath(fileName)
            val file = File(filePath)
            
            if (!file.exists()) {
                return@withContext null
            }
            
            val json = file.readText()
            ConfigurationSerializer.deserialize(json)
        } catch (e: Exception) {
            null
        }
    }
    
    actual suspend fun listConfigurations(): List<ConfigurationFileInfo> = withContext(Dispatchers.IO) {
        try {
            val dir = File(configDirectory)
            if (!dir.exists() || !dir.isDirectory) {
                return@withContext emptyList()
            }
            
            dir.listFiles { file -> 
                file.isFile && file.name.endsWith(".json") 
            }?.map { file ->
                ConfigurationFileInfo(
                    fileName = file.name,
                    filePath = file.absolutePath,
                    lastModified = file.lastModified(),
                    size = file.length()
                )
            } ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }
    
    actual suspend fun deleteConfiguration(fileName: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val filePath = getConfigurationFilePath(fileName)
            val file = File(filePath)
            
            if (file.exists()) {
                file.delete()
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }
    
    actual fun getConfigurationFilePath(fileName: String): String {
        return Paths.get(configDirectory, fileName).toString()
    }
}
